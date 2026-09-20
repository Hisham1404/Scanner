package com.hisham.scanner.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.Executors

/**
 * mDNS / DNS-SD discovery.
 *
 * A camera that advertises _rtsp._tcp or _onvif._tcp is telling the network
 * what it is. That is the cheapest strong signal available, and it costs one
 * multicast query.
 */
class MdnsScanner(private val context: Context) {

    companion object {
        /** Service types worth asking for. */
        val TYPES = listOf(
            "_rtsp._tcp.",
            "_onvif._tcp.",
            "_axis-video._tcp.",
            "_dahua._tcp.",
            "_http._tcp."
        )

        private const val DISCOVER_MS = 3500L
        private const val RESOLVE_GAP_MS = 120L
        private const val RESOLVE_TIMEOUT_MS = 1500L
    }

    /** ip -> the service types it advertised. */
    suspend fun discover(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@withContext emptyMap()

        val found = Collections.synchronizedMap(LinkedHashMap<String, MutableSet<String>>())
        val pending = Collections.synchronizedList(mutableListOf<NsdServiceInfo>())
        val listeners = mutableListOf<Pair<String, NsdManager.DiscoveryListener>>()

        TYPES.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    pending.add(serviceInfo)
                }
            }
            runCatching {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(type to listener)
            }
        }

        delay(DISCOVER_MS)

        listeners.forEach { (_, l) -> runCatching { nsd.stopServiceDiscovery(l) } }

        // NsdManager resolves one service at a time on older releases, so this
        // walks the queue rather than firing them all at once.
        val snapshot = synchronized(pending) { pending.toList() }
        for (info in snapshot) {
            val addresses = resolveAddresses(nsd, info)
            for (addr in addresses) {
                if (addr is Inet4Address) {
                    val ip = addr.hostAddress ?: continue
                    found.getOrPut(ip) { linkedSetOf() }.add(info.serviceType.trim('.'))
                }
            }
            delay(RESOLVE_GAP_MS)
        }

        found.mapValues { it.value.toList() }
    }

    /**
     * resolveService and NsdServiceInfo.host were deprecated in API 34 in
     * favour of a callback that can report several addresses. Android 15 is
     * exactly where the new path matters, so both are implemented rather than
     * suppressing the warning and hoping.
     */
    private suspend fun resolveAddresses(
        nsd: NsdManager,
        info: NsdServiceInfo
    ): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveModern(nsd, info)
        } else {
            listOfNotNull(resolveLegacy(nsd, info))
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveModern(
        nsd: NsdManager,
        info: NsdServiceInfo
    ): List<InetAddress> {
        var result: List<InetAddress> = emptyList()
        var done = false

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) { done = true }
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                result = serviceInfo.hostAddresses
                done = true
            }
            override fun onServiceLost() { done = true }
            override fun onServiceInfoCallbackUnregistered() = Unit
        }

        val executor = Executors.newSingleThreadExecutor()
        runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
            .onFailure {
                executor.shutdown()
                return emptyList()
            }

        var waited = 0L
        while (!done && waited < RESOLVE_TIMEOUT_MS) {
            delay(60L)
            waited += 60L
        }

        runCatching { nsd.unregisterServiceInfoCallback(callback) }
        executor.shutdown()
        return result
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(nsd: NsdManager, info: NsdServiceInfo): InetAddress? {
        var result: NsdServiceInfo? = null
        var done = false
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                done = true
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                result = serviceInfo
                done = true
            }
        }
        runCatching { nsd.resolveService(info, listener) }.onFailure { return null }

        var waited = 0L
        while (!done && waited < RESOLVE_TIMEOUT_MS) {
            delay(60L)
            waited += 60L
        }
        return result?.host
    }
}
