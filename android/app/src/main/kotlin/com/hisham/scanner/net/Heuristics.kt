package com.hisham.scanner.net

/**
 * Everything the network scanner uses to decide "is this thing a camera".
 *
 * Pure Kotlin on purpose: no Android imports, so the scoring runs under JVM
 * unit tests rather than only on a handset.
 *
 * A note on honesty, because this is where detector apps usually start lying.
 * None of this proves anything. An open RTSP port is strong evidence and an
 * ONVIF reply is stronger, but a network scan can only ever see devices that
 * are on the network you are on and that transmit. The classes it can never
 * see are listed in Verdict.blindSpots and surfaced in the UI.
 */

/** Ports worth probing, and what an open one means. */
object CameraPorts {

    data class PortMeaning(val port: Int, val label: String, val weight: Int)

    val PROBE: List<PortMeaning> = listOf(
        PortMeaning(554, "RTSP video stream", 4),
        PortMeaning(8554, "RTSP (alternate)", 4),
        PortMeaning(10554, "RTSP (alternate)", 3),
        PortMeaning(37777, "Dahua device service", 4),
        PortMeaning(34567, "XiongMai / generic DVR service", 4),
        PortMeaning(8000, "Hikvision SDK service", 3),
        PortMeaning(8899, "Generic DVR service", 3),
        PortMeaning(80, "Web interface", 1),
        PortMeaning(443, "Web interface (TLS)", 1),
        PortMeaning(8080, "Web interface (alternate)", 1)
    )

    val ALL: IntArray = PROBE.map { it.port }.toIntArray()

    fun meaning(port: Int): PortMeaning? = PROBE.firstOrNull { it.port == port }
}

enum class Confidence { LIKELY, POSSIBLE, UNKNOWN }

data class HostEvidence(
    val openPorts: List<Int> = emptyList(),
    val hostname: String? = null,
    val ssdpServer: String? = null,
    val mdnsServiceTypes: List<String> = emptyList(),
    val onvifReplied: Boolean = false,
    val onvifScopes: String? = null
)

data class HostVerdict(
    val confidence: Confidence,
    val score: Int,
    val reasons: List<String>,
    val vendorGuess: String?
)

object CameraHeuristics {

    /**
     * Strings that appear in hostnames, SSDP SERVER headers and ONVIF scopes
     * when the device is a camera or recorder.
     */
    private val NAME_HINTS = listOf(
        "ipcam", "ipcamera", "ip-cam", "camera", "webcam", "netcam",
        "onvif", "rtsp", "nvr", "dvr", "hikvision", "dahua", "ezviz",
        "reolink", "amcrest", "foscam", "axis", "vivotek", "uniview",
        "tapo", "wyze", "yi-", "xiaoyi", "v380", "camhi", "icsee",
        "doorbell", "babymonitor", "baby-monitor", "spycam"
    )

    /**
     * Names cheap wireless cameras broadcast when they are in pairing mode and
     * running their own access point. This is the case a Wi-Fi scan catches
     * that nothing else does - the camera has not joined any network yet.
     */
    private val SSID_PATTERNS = listOf(
        Regex("""(?i)\b(ipc|ipcam|cam|camera|dvr|nvr|onvif|webcam)\b"""),
        Regex("""(?i)^(hik|dahua|ezviz|reolink|amcrest|foscam|tapo|wyze|yi|xiaoyi)[-_]"""),
        Regex("""(?i)(v380|camhi|icsee|hdminicam|lsc[-_]|ai[-_]?cam|minicam|spycam|clevercam)"""),
        Regex("""(?i)^esp[-_]?(32|cam)"""),
        Regex("""(?i)^[a-z]{2,6}[-_]?\d{6,}$""")   // MODEL-123456789, the stock pairing-AP shape
    )

    /**
     * MAC prefixes, uppercase and colon-free. Only entries worth standing
     * behind are here; an OUI table is a guess at a manufacturer, never at a
     * device's purpose, and a wrong vendor label is worse than none.
     *
     * Espressif matters most: the cheap Wi-Fi camera modules sold as "mini
     * spy cams" are overwhelmingly ESP32-CAM boards.
     */
    private val OUI: Map<String, String> = mapOf(
        "00408C" to "Axis Communications",
        "ACCC8E" to "Axis Communications",
        "4419B6" to "Hikvision",
        "4CBD8F" to "Hikvision",
        "BCAD28" to "Hikvision",
        "C056E3" to "Hikvision",
        "2857BE" to "Hikvision",
        "3CEF8C" to "Dahua",
        "4C11BF" to "Dahua",
        "9002A9" to "Dahua",
        "50C7BF" to "TP-Link",
        "A42BB0" to "TP-Link",
        "54AF97" to "TP-Link",
        "7811DC" to "Xiaomi",
        "640980" to "Xiaomi",
        "00E04C" to "Realtek",
        "240AC4" to "Espressif",
        "30AEA4" to "Espressif",
        "A4CF12" to "Espressif",
        "84F3EB" to "Espressif",
        "7C9EBD" to "Espressif",
        "B4E62D" to "Espressif",
        "ECFABC" to "Espressif"
    )

    fun ouiVendor(macOrBssid: String?): String? {
        if (macOrBssid.isNullOrBlank()) return null
        val clean = macOrBssid.replace(":", "").replace("-", "").uppercase()
        if (clean.length < 6) return null
        return OUI[clean.substring(0, 6)]
    }

    fun ssidLooksLikeCamera(ssid: String?): Boolean {
        if (ssid.isNullOrBlank()) return false
        val s = ssid.trim()
        return SSID_PATTERNS.any { it.containsMatchIn(s) }
    }

    private fun nameHint(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        return NAME_HINTS.firstOrNull { lower.contains(it) }
    }

    /**
     * Weigh the evidence for one host. Scores are additive and deliberately
     * blunt - the point is to sort a subnet so the user looks at four hosts
     * instead of two hundred, not to render a judgement.
     */
    fun score(evidence: HostEvidence): HostVerdict {
        var score = 0
        val reasons = mutableListOf<String>()

        if (evidence.onvifReplied) {
            score += 5
            reasons.add("Answered an ONVIF discovery probe. ONVIF is the IP-camera control standard; almost nothing else implements it.")
        }

        val camMdns = evidence.mdnsServiceTypes.filter { t ->
            val l = t.lowercase()
            l.contains("onvif") || l.contains("rtsp") || l.contains("axis-video") || l.contains("dahua")
        }
        if (camMdns.isNotEmpty()) {
            score += 5
            reasons.add("Advertises ${camMdns.joinToString(", ")} over mDNS - a camera service announcing itself.")
        }

        val rtsp = evidence.openPorts.filter { it == 554 || it == 8554 || it == 10554 }
        if (rtsp.isNotEmpty()) {
            score += 4
            reasons.add("RTSP port ${rtsp.joinToString(", ")} is open. RTSP exists to carry a live video stream.")
        }

        val vendorPorts = evidence.openPorts.filter { it == 37777 || it == 34567 || it == 8000 || it == 8899 }
        if (vendorPorts.isNotEmpty()) {
            val labels = vendorPorts.mapNotNull { CameraPorts.meaning(it)?.label }
            score += 4
            reasons.add("Open on ${vendorPorts.joinToString(", ")} (${labels.joinToString("; ")}).")
        }

        nameHint(evidence.ssdpServer)?.let {
            score += 4
            reasons.add("Its UPnP server string mentions \"$it\".")
        }
        nameHint(evidence.onvifScopes)?.let {
            score += 3
            reasons.add("Its ONVIF scopes mention \"$it\".")
        }
        nameHint(evidence.hostname)?.let {
            score += 3
            reasons.add("Its hostname mentions \"$it\".")
        }

        val webOnly = evidence.openPorts.any { it == 80 || it == 443 || it == 8080 }
        if (webOnly && score == 0) {
            score += 1
            reasons.add("Only a web interface is exposed. That is true of most devices on a network and means little on its own.")
        }

        // No MAC address to work from: Android 10 removed access to the ARP
        // table, so OUI lookup only applies to Wi-Fi BSSIDs, not to hosts on
        // the subnet. Vendor here comes from what the device says about itself.
        val vendor = listOfNotNull(evidence.ssdpServer, evidence.onvifScopes, evidence.hostname)
            .firstNotNullOfOrNull { text ->
                listOf(
                    "hikvision", "dahua", "ezviz", "reolink", "amcrest",
                    "foscam", "axis", "vivotek", "uniview", "tapo", "wyze"
                ).firstOrNull { text.lowercase().contains(it) }
            }?.replaceFirstChar { it.uppercase() }

        val confidence = when {
            score >= 5 -> Confidence.LIKELY
            score >= 3 -> Confidence.POSSIBLE
            else -> Confidence.UNKNOWN
        }

        return HostVerdict(confidence, score, reasons, vendor)
    }

    /**
     * Stated in the UI next to every result. A scan that does not say what it
     * cannot see is not an honest scan.
     */
    val blindSpots: List<String> = listOf(
        "Cameras that record to an SD card and never transmit. These are invisible to every network scan in existence - the Delhi rented-home case was exactly this. Use the lens scan and the physical sweep.",
        "Cameras on a different network from the one you joined, including ones on the building's own separate VLAN.",
        "Cameras using mobile data rather than Wi-Fi.",
        "Cameras that are powered off or idle at the moment you scan.",
        "Wired cameras on a segment your phone cannot reach."
    )
}
