package com.hisham.scanner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hisham.scanner.data.FindingsStore
import com.hisham.scanner.net.AccessPoint
import com.hisham.scanner.net.BleDevice
import com.hisham.scanner.net.CameraHeuristics
import com.hisham.scanner.net.CameraPorts
import com.hisham.scanner.net.Confidence
import com.hisham.scanner.net.DiscoveredHost
import com.hisham.scanner.net.NetworkReport
import com.hisham.scanner.net.NetworkScanner
import com.hisham.scanner.net.RadioScanner
import kotlinx.coroutines.launch

@Composable
fun NetworkScreen(store: FindingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NetworkScanner(context) }
    val radio = remember { RadioScanner(context) }

    var running by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var report by remember { mutableStateOf<NetworkReport?>(null) }
    var aps by remember { mutableStateOf<List<AccessPoint>>(emptyList()) }
    var ble by remember { mutableStateOf<List<BleDevice>>(emptyList()) }
    var radioDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        radioDenied = granted.values.any { !it }
        if (!radioDenied) {
            scope.launch {
                aps = radio.accessPoints()
                ble = radio.bleDevices()
            }
        }
    }

    fun runScan() {
        if (running) return
        running = true
        report = null
        scope.launch {
            try {
                if (!radio.wifiPermissionsGranted() || !radio.blePermissionsGranted()) {
                    permissionLauncher.launch(
                        radio.requiredWifiPermissions() + radio.requiredBlePermissions()
                    )
                } else {
                    stage = "Scanning nearby radios"
                    aps = radio.accessPoints()
                    ble = radio.bleDevices()
                }
                report = scanner.scan { label, f ->
                    stage = label
                    progress = f
                }
            } finally {
                running = false
                stage = ""
                progress = 0f
            }
        }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Metrics.Gutter)) {
        item {
            Spacer(Modifier.height(26.dp))
            Text("NETWORK & RADIO", style = LabelStyle)
            Spacer(Modifier.height(10.dp))
            Display("Ask the network what is on it.")
            Spacer(Modifier.height(6.dp))
            Lead("Four probes at once: a TCP sweep of the subnet, a UPnP listen, an ONVIF discovery probe, and mDNS. Plus nearby Wi-Fi access points and Bluetooth advertisements, which catch a camera that has not joined any network at all.")
            Spacer(Modifier.height(24.dp))

            Btn(
                if (running) "Scanning…" else "Run the scan",
                ::runScan,
                Modifier.fillMaxWidth(),
                BtnKind.Primary,
                enabled = !running
            )

            if (running) {
                Spacer(Modifier.height(18.dp))
                Meter(progress, Ink.Fg2)
                Spacer(Modifier.height(8.dp))
                Text(stage.uppercase(), style = LabelStyle)
            }

            if (radioDenied) {
                Spacer(Modifier.height(20.dp))
                CautionAside(
                    "Radio scan needs permission",
                    "Android gates Wi-Fi and Bluetooth scan results behind a permission prompt, and refuses them silently without it. The subnet scan below still works; the access-point and Bluetooth lists will stay empty until the permission is granted."
                )
            }

            Spacer(Modifier.height(36.dp))
        }

        report?.let { r ->
            item {
                SectionLabel("Devices on ${r.subnetLabel ?: "this network"}")
                if (r.hosts.isEmpty()) {
                    EmptyState("Nothing answered on any camera port, over UPnP, ONVIF or mDNS.")
                    Spacer(Modifier.height(20.dp))
                }
            }

            items(r.hosts, key = { it.ip }) { h ->
                HostRow(h) {
                    store.add(
                        "network",
                        "Network — ${h.ip}",
                        h.hostname ?: h.ip,
                        h.verdict.reasons.joinToString(" ")
                    )
                }
            }

            item {
                if (r.notes.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    r.notes.forEach {
                        Aside("Note", it)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        if (aps.isNotEmpty()) {
            item {
                SectionLabel("Nearby access points")
                Body("A camera in pairing mode broadcasts its own network. You do not have to join anything to see these — which matters when the network you are standing in is the one you do not trust.")
                Spacer(Modifier.height(18.dp))
            }
            items(aps, key = { it.bssid }) { ap -> ApRow(ap) }
            item { Spacer(Modifier.height(30.dp)) }
        }

        if (ble.isNotEmpty()) {
            item {
                SectionLabel("Bluetooth advertisements")
                Body("Some cameras advertise over Bluetooth LE while pairing. Most of these will be headphones, watches and trackers — look for a name or vendor you cannot account for.")
                Spacer(Modifier.height(18.dp))
            }
            items(ble.take(30), key = { it.address }) { d -> BleRow(d) }
            item { Spacer(Modifier.height(30.dp)) }
        }

        item {
            SectionLabel("What this cannot see")
            CameraHeuristics.blindSpots.forEachIndexed { i, s ->
                IndexRow(i + 1, s)
            }
            Spacer(Modifier.height(20.dp))
            AlertAside(
                "A clean network scan is not a clean room",
                "The most common concealed camera in the reported Indian cases was a switched-on mobile phone, and the second was a device recording to an SD card. Neither appears in any list above. Run the lens scan and walk the sweep."
            )
            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun HostRow(h: DiscoveredHost, onFlag: () -> Unit) {
    val (label, color) = when (h.verdict.confidence) {
        Confidence.LIKELY -> "Likely camera" to Ink.Signal
        Confidence.POSSIBLE -> "Possible" to Ink.Caution
        Confidence.UNKNOWN -> "Unremarkable" to Ink.Fg3
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(h.ip, style = MonoValueStyle.copy(color = Ink.Fg), modifier = Modifier.weight(1f))
            Text(label.uppercase(), style = LabelStyle.copy(color = color))
        }

        h.hostname?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = BodyStyle)
        }
        h.verdict.vendorGuess?.let {
            Spacer(Modifier.height(2.dp))
            Text("VENDOR — ${it.uppercase()}", style = LabelStyle)
        }

        if (h.openPorts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("OPEN PORTS", style = LabelStyle)
            Spacer(Modifier.height(3.dp))
            h.openPorts.forEach { p ->
                val meaning = CameraPorts.meaning(p)?.label ?: "Unknown service"
                Text("$p  —  $meaning", style = MonoValueStyle.copy(color = Ink.Fg3))
            }
        }

        if (h.verdict.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            h.verdict.reasons.forEach {
                Text("— $it", style = BodyStyle.copy(color = Ink.Fg2))
                Spacer(Modifier.height(3.dp))
            }
        }

        if (h.verdict.confidence != Confidence.UNKNOWN) {
            Spacer(Modifier.height(10.dp))
            Btn("Flag this", onFlag, small = true)
        }
    }
    Rule()
}

@Composable
private fun ApRow(ap: AccessPoint) {
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(ap.ssid, style = TitleStyle, modifier = Modifier.weight(1f))
            if (ap.looksLikeCamera) {
                Text("CAMERA-LIKE NAME", style = LabelStyle.copy(color = Ink.Signal))
            } else {
                Text("${ap.rssi} dBm", style = LabelStyle)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(ap.bssid.uppercase(), style = MonoValueStyle.copy(color = Ink.Fg3))
            ap.vendor?.let {
                Spacer(Modifier.width(10.dp))
                Text(it.uppercase(), style = LabelStyle.copy(color = Ink.Caution))
            }
        }
    }
    Rule()
}

@Composable
private fun BleRow(d: BleDevice) {
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(d.name ?: "(unnamed)", style = TitleStyle, modifier = Modifier.weight(1f))
            Text("${d.rssi} dBm", style = LabelStyle)
        }
        Spacer(Modifier.height(3.dp))
        Row {
            Text(d.address.uppercase(), style = MonoValueStyle.copy(color = Ink.Fg3))
            d.vendor?.let {
                Spacer(Modifier.width(10.dp))
                Text(it.uppercase(), style = LabelStyle.copy(color = Ink.Caution))
            }
        }
    }
    Rule()
}
