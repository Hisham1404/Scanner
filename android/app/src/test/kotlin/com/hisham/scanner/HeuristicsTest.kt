package com.hisham.scanner

import com.hisham.scanner.net.CameraHeuristics
import com.hisham.scanner.net.Confidence
import com.hisham.scanner.net.HostEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicsTest {

    @Test
    fun `an ONVIF reply is treated as strong evidence`() {
        val v = CameraHeuristics.score(HostEvidence(onvifReplied = true))
        assertEquals(Confidence.LIKELY, v.confidence)
        assertTrue(v.reasons.any { it.contains("ONVIF") })
    }

    @Test
    fun `an open RTSP port plus a camera hostname reads as likely`() {
        val v = CameraHeuristics.score(
            HostEvidence(openPorts = listOf(554, 80), hostname = "HIKVISION-DS2CD")
        )
        assertEquals(Confidence.LIKELY, v.confidence)
        assertEquals("Hikvision", v.vendorGuess)
    }

    @Test
    fun `a web interface alone is not evidence of a camera`() {
        val v = CameraHeuristics.score(HostEvidence(openPorts = listOf(80, 443)))
        assertEquals(
            "most devices on a network serve a web page; this must not be flagged",
            Confidence.UNKNOWN, v.confidence
        )
        assertTrue(v.reasons.any { it.contains("means little") })
    }

    @Test
    fun `a host with no evidence at all scores nothing`() {
        val v = CameraHeuristics.score(HostEvidence())
        assertEquals(Confidence.UNKNOWN, v.confidence)
        assertEquals(0, v.score)
        assertNull(v.vendorGuess)
    }

    @Test
    fun `vendor-specific DVR ports are recognised`() {
        assertEquals(Confidence.POSSIBLE, CameraHeuristics.score(HostEvidence(openPorts = listOf(37777))).confidence)
        assertEquals(Confidence.POSSIBLE, CameraHeuristics.score(HostEvidence(openPorts = listOf(34567))).confidence)
    }

    @Test
    fun `an mDNS camera service is strong evidence`() {
        val v = CameraHeuristics.score(HostEvidence(mdnsServiceTypes = listOf("_onvif._tcp")))
        assertEquals(Confidence.LIKELY, v.confidence)
    }

    @Test
    fun `camera pairing SSIDs are matched`() {
        listOf(
            "IPC-123456789", "HIK-A1B2C3", "Tapo_Cam_4F2A", "V380_889900",
            "CamHi-772211", "ESP32-CAM", "MiniCam_0091", "EZVIZ_C6N"
        ).forEach {
            assertTrue("should match pairing SSID: $it", CameraHeuristics.ssidLooksLikeCamera(it))
        }
    }

    @Test
    fun `ordinary SSIDs are not matched`() {
        listOf(
            "Airtel_5GHz", "JioFiber-2.4G", "Seaview Lodge Guest", "TP-Link_Home",
            "ACT Fibernet", "OnePlus Hotspot", ""
        ).forEach {
            assertFalse("should not match ordinary SSID: $it", CameraHeuristics.ssidLooksLikeCamera(it))
        }
    }

    @Test
    fun `BSSID vendor lookup handles real formats`() {
        assertEquals("Espressif", CameraHeuristics.ouiVendor("24:0A:C4:11:22:33"))
        assertEquals("Espressif", CameraHeuristics.ouiVendor("240ac4112233"))
        assertEquals("Hikvision", CameraHeuristics.ouiVendor("44-19-B6-00-00-01"))
        assertEquals("Axis Communications", CameraHeuristics.ouiVendor("00:40:8C:AA:BB:CC"))
    }

    @Test
    fun `BSSID vendor lookup fails safely on junk`() {
        assertNull(CameraHeuristics.ouiVendor(null))
        assertNull(CameraHeuristics.ouiVendor(""))
        assertNull(CameraHeuristics.ouiVendor("zz"))
        assertNull(CameraHeuristics.ouiVendor("FF:FF:FF:FF:FF:FF"))
    }

    @Test
    fun `blind spots are stated and lead with offline cameras`() {
        val spots = CameraHeuristics.blindSpots
        assertTrue(spots.isNotEmpty())
        assertTrue(
            "the SD-card case must be the first thing said",
            spots.first().contains("SD card")
        )
    }

    @Test
    fun `evidence accumulates rather than saturating on one signal`() {
        val weak = CameraHeuristics.score(HostEvidence(openPorts = listOf(554)))
        val strong = CameraHeuristics.score(
            HostEvidence(
                openPorts = listOf(554, 37777),
                hostname = "dahua-nvr",
                onvifReplied = true
            )
        )
        assertTrue("more evidence must score higher", strong.score > weak.score)
        assertTrue("and list every reason", strong.reasons.size >= 3)
    }
}
