package com.hisham.scanner.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredHost(
    val ip: String,
    val hostname: String?,
    val openPorts: List<Int>,
    val ssdpServer: String?,
    val mdnsTypes: List<String>,
    val onvifReplied: Boolean,
    val onvifScopes: String?,
    val verdict: HostVerdict
)

data class NetworkReport(
    val subnetLabel: String?,
    val hostsProbed: Int,
    val hosts: List<DiscoveredHost>,
    val notes: List<String>
)

/**
 * The capability a website cannot have on any browser: enumerate what is on
 * the network with you, and ask each thing what it is.
 *
 * Four independent probes, because each one catches a case the others miss:
 *
 *   TCP port sweep   - finds anything listening, including cameras that
 *                      advertise nothing.
 *   SSDP / UPnP      - many consumer cameras announce themselves with a
 *                      SERVER string naming the vendor.
 *   ONVIF WS-Discovery - the IP-camera control standard. A reply here is
 *                      close to conclusive, and works even when the web UI
 *                      is locked down.
 *   mDNS / DNS-SD    - _rtsp._tcp and _onvif._tcp are cameras announcing
 *                      themselves by service type.
 *
 * What it cannot do is in CameraHeuristics.blindSpots, and the UI prints that
 * list next to every result. A scan that does not say what it cannot see is
 * not an honest scan.
 */
class NetworkScanner(private val context: Context) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 420
        private const val PARALLELISM = 96
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val WS_DISCOVERY_PORT = 3702
        private const val UDP_LISTEN_MS = 2800L

        /** /24 is 254 hosts. Anything wider is not worth sweeping from a phone. */
        private const val MAX_HOSTS = 254
    }

    /** IPv4 address and prefix length of the interface we are actually on. */
    private fun localIpv4(): Pair<Inet4Address, Int>? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        val addr: LinkAddress = props.linkAddresses.firstOrNull { it.address is Inet4Address }
            ?: return null
        return (addr.address as Inet4Address) to addr.prefixLength
    }

    suspend fun scan(onProgress: (String, Float) -> Unit): NetworkReport =
        withContext(Dispatchers.IO) {
            val notes = mutableListOf<String>()
            val local = localIpv4()

            if (local == null) {
                return@withContext NetworkReport(
                    subnetLabel = null,
                    hostsProbed = 0,
                    hosts = emptyList(),
                    notes = listOf("No IPv4 network. Join the Wi-Fi you want to scan, then run this again. Mobile data cannot be scanned - there is no local network to look at.")
                )
            }

            val (ip, prefix) = local
            val octets = ip.address.map { it.toInt() and 0xFF }
            val base = "${octets[0]}.${octets[1]}.${octets[2]}"
            val subnetLabel = "$base.0/${if (prefix >= 24) prefix else 24}"

            if (prefix < 24) {
                notes.add("The network is a /$prefix, which is wider than a phone can sweep in reasonable time. Only $base.0/24 was probed, so devices outside that range were not seen.")
            }

            // --- 1. TCP sweep -----------------------------------------------
            onProgress("Probing $subnetLabel", 0f)
            val gate = Semaphore(PARALLELISM)
            val hostPorts = HashMap<String, MutableList<Int>>()
            var completed = 0
            val totalProbes = MAX_HOSTS * CameraPorts.ALL.size

            coroutineScope {
                val jobs = ArrayList<kotlinx.coroutines.Deferred<Unit>>(totalProbes)
                for (h in 1..MAX_HOSTS) {
                    val target = "$base.$h"
                    if (target == ip.hostAddress) continue
                    for (port in CameraPorts.ALL) {
                        jobs.add(
                            async {
                                gate.withPermit {
                                    if (tcpOpen(target, port)) {
                                        synchronized(hostPorts) {
                                            hostPorts.getOrPut(target) { mutableListOf() }.add(port)
                                        }
                                    }
                                }
                                synchronized(this@NetworkScanner) { completed++ }
                                if (completed % 200 == 0) {
                                    onProgress("Probing $subnetLabel", completed.toFloat() / totalProbes * 0.7f)
                                }
                            }
                        )
                    }
                }
                jobs.awaitAll()
            }

            // --- 2. SSDP / UPnP ---------------------------------------------
            onProgress("Listening for UPnP announcements", 0.74f)
            val ssdp = multicastLocked { ssdpProbe() }

            // --- 3. ONVIF WS-Discovery --------------------------------------
            onProgress("Sending ONVIF discovery probe", 0.84f)
            val onvif = multicastLocked { onvifProbe() }

            // --- 4. mDNS / DNS-SD -------------------------------------------
            onProgress("Asking for mDNS service announcements", 0.88f)
            // The lock has to be held for the duration of discovery, not just
            // while constructing the scanner - multicastLocked is inline, so the
            // suspending body runs inside it.
            val mdns: Map<String, List<String>> = multicastLocked {
                withTimeoutOrNull(9000L) { MdnsScanner(context).discover() } ?: emptyMap()
            }

            // --- 5. reverse DNS on hosts that answered ----------------------
            onProgress("Resolving names", 0.93f)
            val names = HashMap<String, String?>()
            coroutineScope {
                hostPorts.keys.map { host ->
                    async {
                        val n = withTimeoutOrNull(1200L) { reverseDns(host) }
                        synchronized(names) { names[host] = n }
                    }
                }.awaitAll()
            }

            // --- combine ----------------------------------------------------
            val allIps = (hostPorts.keys + ssdp.keys + onvif.keys + mdns.keys)
                .toSortedSet(compareBy { ipSortKey(it) })

            val hosts = allIps.map { host ->
                val evidence = HostEvidence(
                    openPorts = hostPorts[host]?.sorted() ?: emptyList(),
                    hostname = names[host],
                    ssdpServer = ssdp[host],
                    mdnsServiceTypes = mdns[host] ?: emptyList(),
                    onvifReplied = onvif.containsKey(host),
                    onvifScopes = onvif[host]
                )
                DiscoveredHost(
                    ip = host,
                    hostname = evidence.hostname,
                    openPorts = evidence.openPorts,
                    ssdpServer = evidence.ssdpServer,
                    mdnsTypes = evidence.mdnsServiceTypes,
                    onvifReplied = evidence.onvifReplied,
                    onvifScopes = evidence.onvifScopes,
                    verdict = CameraHeuristics.score(evidence)
                )
            }.sortedByDescending { it.verdict.score }

            if (hosts.isEmpty()) {
                notes.add("Nothing on this network answered on any camera port, over UPnP or over ONVIF. That rules out one class of camera and nothing else - see what this cannot see, below.")
            }

            onProgress("Done", 1f)
            NetworkReport(subnetLabel, MAX_HOSTS, hosts, notes)
        }

    private fun ipSortKey(ip: String): Int =
        ip.substringAfterLast('.').toIntOrNull() ?: 0

    private fun tcpOpen(host: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            true
        }
    } catch (e: Exception) {
        false
    }

    private fun reverseDns(ip: String): String? = try {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name == ip) null else name
    } catch (e: Exception) {
        null
    }

    /**
     * Multicast replies are dropped by the Wi-Fi chip unless a multicast lock
     * is held. Without this both SSDP and ONVIF silently return nothing, which
     * is the single most common reason a hand-rolled scanner "finds no
     * cameras" on a network that has them.
     */
    private inline fun <T> multicastLocked(body: () -> T): T {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("scanner-discovery")?.apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }
        return try {
            body()
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    /** ip -> SERVER header (or the location line if there is no server). */
    private fun ssdpProbe(): Map<String, String> {
        val found = HashMap<String, String>()
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: ssdp:all\r\n")
            append("\r\n")
        }.toByteArray()

        runCatching {
            DatagramSocket().use { sock ->
                sock.soTimeout = 500
                sock.broadcast = true
                sock.send(
                    DatagramPacket(
                        request, request.size,
                        InetAddress.getByName(SSDP_ADDR), SSDP_PORT
                    )
                )

                val deadline = System.currentTimeMillis() + UDP_LISTEN_MS
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (e: Exception) {
                        continue
                    }
                    val from = packet.address?.hostAddress ?: continue
                    val text = String(packet.data, 0, packet.length)
                    val server = headerValue(text, "SERVER")
                        ?: headerValue(text, "LOCATION")
                        ?: continue
                    // keep the most descriptive reply per host
                    val existing = found[from]
                    if (existing == null || server.length > existing.length) found[from] = server
                }
            }
        }
        return found
    }

    /** ip -> ONVIF scopes string. Presence of a key means the device replied. */
    private fun onvifProbe(): Map<String, String> {
        val found = HashMap<String, String>()
        val uuid = java.util.UUID.randomUUID().toString()
        val probe = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                        xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                        xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
              <e:Header>
                <w:MessageID>uuid:$uuid</w:MessageID>
                <w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
                <w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
              </e:Header>
              <e:Body>
                <d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>
              </e:Body>
            </e:Envelope>
        """.trimIndent().toByteArray()

        runCatching {
            DatagramSocket().use { sock ->
                sock.soTimeout = 500
                sock.broadcast = true
                sock.send(
                    DatagramPacket(
                        probe, probe.size,
                        InetAddress.getByName(SSDP_ADDR), WS_DISCOVERY_PORT
                    )
                )

                val deadline = System.currentTimeMillis() + UDP_LISTEN_MS
                val buf = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (e: Exception) {
                        continue
                    }
                    val from = packet.address?.hostAddress ?: continue
                    val text = String(packet.data, 0, packet.length)
                    if (!text.contains("ProbeMatch", ignoreCase = true)) continue
                    found[from] = extractScopes(text) ?: "ONVIF device"
                }
            }
        }
        return found
    }

    private fun headerValue(response: String, header: String): String? =
        response.lineSequence()
            .firstOrNull { it.startsWith("$header:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun extractScopes(xml: String): String? {
        val start = xml.indexOf("<d:Scopes", ignoreCase = true).takeIf { it >= 0 }
            ?: xml.indexOf("<Scopes", ignoreCase = true).takeIf { it >= 0 }
            ?: return null
        val open = xml.indexOf('>', start).takeIf { it >= 0 } ?: return null
        val close = xml.indexOf("</", open).takeIf { it >= 0 } ?: return null
        return xml.substring(open + 1, close).trim().takeIf { it.isNotEmpty() }
    }
}
