package com.hisham.scanner.net

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections

data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val vendor: String?,
    val looksLikeCamera: Boolean
)

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val vendor: String?
)

/**
 * Radio-side discovery: nearby Wi-Fi access points and BLE advertisements.
 *
 * This catches the case nothing else does - a camera that has not joined any
 * network, sitting in pairing mode broadcasting its own access point. You do
 * not have to join anything to see it, which matters when the network you are
 * standing in is the one you do not trust.
 */
class RadioScanner(private val context: Context) {

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun wifiPermissionsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            has(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun blePermissionsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            has(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            has(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun requiredWifiPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /**
     * Android throttles Wi-Fi scans hard - four per two minutes for a
     * foreground app since Android 9 - so this reads the last results rather
     * than insisting on a fresh sweep every time.
     */
    @SuppressLint("MissingPermission")
    suspend fun accessPoints(): List<AccessPoint> = withContext(Dispatchers.IO) {
        if (!wifiPermissionsGranted()) return@withContext emptyList()
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext emptyList()

        runCatching { @Suppress("DEPRECATION") wifi.startScan() }
        delay(2500)

        runCatching {
            wifi.scanResults.map { r ->
                @Suppress("DEPRECATION")
                val ssid = r.SSID ?: ""
                AccessPoint(
                    ssid = ssid.ifBlank { "(hidden network)" },
                    bssid = r.BSSID ?: "",
                    rssi = r.level,
                    vendor = CameraHeuristics.ouiVendor(r.BSSID),
                    looksLikeCamera = CameraHeuristics.ssidLooksLikeCamera(ssid)
                )
            }.sortedWith(
                compareByDescending<AccessPoint> { it.looksLikeCamera }
                    .thenByDescending { it.rssi }
            )
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    suspend fun bleDevices(durationMs: Long = 6000L): List<BleDevice> = withContext(Dispatchers.IO) {
        if (!blePermissionsGranted()) return@withContext emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return@withContext emptyList()
        val scanner = manager.adapter?.bluetoothLeScanner ?: return@withContext emptyList()

        val seen = Collections.synchronizedMap(LinkedHashMap<String, BleDevice>())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device?.address ?: return
                val name = runCatching { result.device?.name }.getOrNull()
                    ?: result.scanRecord?.deviceName
                seen[addr] = BleDevice(
                    name = name,
                    address = addr,
                    rssi = result.rssi,
                    vendor = CameraHeuristics.ouiVendor(addr)
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching { scanner.startScan(null, settings, callback) }
            .onFailure { return@withContext emptyList() }

        delay(durationMs)
        runCatching { scanner.stopScan(callback) }

        seen.values.sortedByDescending { it.rssi }
    }
}
