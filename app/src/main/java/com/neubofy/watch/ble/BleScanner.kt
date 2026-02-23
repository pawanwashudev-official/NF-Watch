package com.neubofy.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a discovered BLE device
 */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isGoBoult: Boolean
)

/**
 * BLE Scanner — discovers nearby Bluetooth Low Energy devices
 * Filters for GoBoult/Drift watches
 */
class BleScanner(private val context: Context) {

    companion object {
        fun hasPermissions(context: Context): Boolean {
            val bluetooth = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            
            val mic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val dnd = nm.isNotificationPolicyAccessGranted
            
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val battery = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true

            return bluetooth && mic && dnd && battery
        }

    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredDevices = mutableMapOf<String, BleDevice>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: return

            val bleDevice = BleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                isGoBoult = isGoBoultDevice(name)
            )
            discoveredDevices[device.address] = bleDevice
            _devices.value = discoveredDevices.values
                .sortedWith(compareByDescending<BleDevice> { it.isGoBoult }.thenByDescending { it.rssi })
                .toList()
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        if (scanner == null) return

        discoveredDevices.clear()
        
        // Add already bonded devices that match our filter
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val name = device.name ?: ""
                if (isGoBoultDevice(name)) {
                    val bleDevice = BleDevice(
                        name = name,
                        address = device.address,
                        rssi = -50, // Arbitrary RSSI for bonded devices
                        isGoBoult = true
                    )
                    discoveredDevices[device.address] = bleDevice
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted yet
        }

        _devices.value = discoveredDevices.values.toList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    private fun isGoBoultDevice(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("drift") ||
               lower.contains("goboult") ||
               lower.contains("go boult") ||
               lower.contains("boult")
    }
}
