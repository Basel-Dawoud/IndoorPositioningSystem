package com.example.ble_ips

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.app.ActivityCompat
import com.example.ble_ips.ui.screens.LandingPage
import com.example.ble_ips.ui.screens.MainScreen
import com.example.ble_ips.ui.theme.BLE_IPSTheme
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// --- MQTT Configuration ---
private const val MQTT_BROKER_URI = "192.168.8.10"
private const val MQTT_PUBLISH_TOPIC = "ips/beacons"
private const val MQTT_SUBSCRIBE_TOPIC = "ips/result"

// --- iBeacon Configuration ---
private const val TARGET_BEACON_UUID = "B381396EA6914028B3B6CC5F87D350AA"
private val BEACON_MINOR_MAP = mapOf(
    1 to "b1",
    2 to "b2",
    3 to "b3"
)

// --- UI Configuration ---
private const val UI_SMOOTHING_FACTOR = 0.15f

data class IBeaconInfo(val uuid: String, val major: Int, val minor: Int, val txPower: Int)

data class DeviceInfo(
    val mac: String,
    val rssiReadings: MutableList<Int> = Collections.synchronizedList(mutableListOf<Int>()),
    var lastSeen: Long,
    var iBeaconInfo: IBeaconInfo? = null
) {
    fun getAverageRssi(): Int {
        if (rssiReadings.isEmpty()) return -100
        return rssiReadings.average().toInt()
    }

    fun addRssi(rssi: Int) {
        if (rssiReadings.size >= 10) { rssiReadings.removeAt(0) }
        rssiReadings.add(rssi)
    }
}

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var scanner: BluetoothLeScanner
    private val discoveredDevices = ConcurrentHashMap<String, DeviceInfo>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mqttClient: Mqtt5AsyncClient

    // --- UI Status States ---
    private val mqttStatus = mutableStateOf("MQTT: Disconnected")
    private val beaconStatus = mutableStateOf("Beacons: 0/3 visible")
    private val lastPublishStatus = mutableStateOf("Last Publish: Waiting")
    private val lastReceivedStatus = mutableStateOf("Last Rcvd: Waiting")
    private val smoothedPosition = mutableStateOf<Offset?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BLE_IPSTheme {
                var showLandingPage by remember { mutableStateOf(true) }
                
                // Monitor connectivity when on MainScreen
                if (!showLandingPage) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        while (true) {
                            val bluetoothOn = isBluetoothEnabled(context)
                            val wifiOn = isWifiConnected(context)
                            
                            if (!bluetoothOn || !wifiOn) {
                                // Return to landing page if connectivity is lost
                                showLandingPage = true
                                // Stop services
                                stopServices()
                            }
                            kotlinx.coroutines.delay(2000L)
                        }
                    }
                }

                if (showLandingPage) {
                    LandingPage(
                        onGetStarted = {
                            showLandingPage = false
                            // Start services only when user enters main app
                            checkAndStartServices() 
                        }
                    )
                } else {
                    MainScreen(
                        mqttStatus = mqttStatus.value,
                        beaconStatus = beaconStatus.value,
                        lastPublishStatus = lastPublishStatus.value,
                        lastReceivedStatus = lastReceivedStatus.value,
                        smoothedPosition = smoothedPosition
                    )
                }
            }
        }
    }
    
    private fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun stopServices() {
        try {
            handler.removeCallbacksAndMessages(null)
            if (::scanner.isInitialized) {
                scanner.stopScan(scanCallback)
            }
            if (::mqttClient.isInitialized && mqttClient.state.isConnected) {
                mqttClient.disconnect()
            }
            mqttStatus.value = "MQTT: Disconnected"
            beaconStatus.value = "Beacons: 0/3 visible"
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun checkAndStartServices() {
        if (hasPermissions()) {
             setupMqtt()
             startBleScan()
        } else {
            requestPermissions()
        }
    }

    private fun hasPermissions(): Boolean {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupMqtt() {
        mqttClient = MqttClient.builder()
            .useMqttVersion5()
            .identifier(Build.MODEL.replace(" ", "_"))
            .serverHost(MQTT_BROKER_URI)
            .serverPort(1883)
            .buildAsync()

        mqttClient.connect()
            .whenComplete { _, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        mqttStatus.value = "MQTT: Connection Failed"
                    } else {
                        mqttStatus.value = "MQTT: Connected"
                        subscribeToPositionTopic()
                    }
                }
            }
    }

    private fun subscribeToPositionTopic() {
        mqttClient.subscribeWith()
            .topicFilter(MQTT_SUBSCRIBE_TOPIC)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes)
                try {
                    val json = JSONObject(payload)
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    val rawPosition = Offset(x, y)

                    runOnUiThread {
                        // Apply smoothing filter
                        val currentSmoothed = smoothedPosition.value
                        if (currentSmoothed == null) {
                            smoothedPosition.value = rawPosition
                        } else {
                            smoothedPosition.value = currentSmoothed + (rawPosition - currentSmoothed) * UI_SMOOTHING_FACTOR
                        }

                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        lastReceivedStatus.value = "Last Rcvd: ${sdf.format(Date())}"
                    }
                } catch (e: Exception) { /* Ignore malformed JSON */ }
            }
            .send()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupMqtt()
            startBleScan()
        } else {
            mqttStatus.value = "Permissions denied. App cannot function."
        }
    }

    private fun startBleScan() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (manager == null || manager.adapter == null) {
             beaconStatus.value = "BLE Not Supported on this device"
             return
        }
        scanner = manager.adapter.bluetoothLeScanner
        if (scanner == null) {
             beaconStatus.value = "BLE Not Enabled"
             return
        }
        startScan()
        startCleanupTask()
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms, 1)
    }

    private fun startScan() {
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            beaconStatus.value = "Scan Error: ${e.message}"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord?.bytes ?: return
            val iBeaconInfo = parseIBeacon(scanRecord)
            if (iBeaconInfo != null && iBeaconInfo.uuid.equals(TARGET_BEACON_UUID, ignoreCase = true)) {
                if (BEACON_MINOR_MAP.containsKey(iBeaconInfo.minor)) {
                    val mac = result.device.address
                    val rssi = result.rssi
                    val device = discoveredDevices.getOrPut(mac) { DeviceInfo(mac, lastSeen = 0L) }
                    device.iBeaconInfo = iBeaconInfo
                    device.addRssi(rssi)
                    device.lastSeen = System.currentTimeMillis()
                }
            }
        }
    }

    private fun parseIBeacon(scanRecord: ByteArray): IBeaconInfo? {
        var offset = 0
        while (offset < scanRecord.size - 2) {
            val len = scanRecord[offset++].toInt() and 0xFF
            if (len == 0) break
            val type = scanRecord[offset++].toInt() and 0xFF
            if (type == 0xFF && len >= 26 && scanRecord[offset].toInt() and 0xFF == 0x4C && scanRecord[offset + 1].toInt() and 0xFF == 0x00 && scanRecord[offset + 2].toInt() and 0xFF == 0x02 && scanRecord[offset + 3].toInt() and 0xFF == 0x15) {
                val uuid = bytesToHex(scanRecord, offset + 4, 16)
                val major = (scanRecord[offset + 20].toInt() and 0xFF) * 256 + (scanRecord[offset + 21].toInt() and 0xFF)
                val minor = (scanRecord[offset + 22].toInt() and 0xFF) * 256 + (scanRecord[offset + 23].toInt() and 0xFF)
                val txPower = scanRecord[offset + 24].toInt()
                return IBeaconInfo(uuid, major, minor, txPower)
            }
            offset += len - 1
        }
        return null
    }

    private fun bytesToHex(bytes: ByteArray, start: Int, len: Int): String {
        val hexChars = CharArray(len * 2)
        for (j in 0 until len) {
            val v = bytes[start + j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun startCleanupTask() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                discoveredDevices.entries.removeIf { now - it.value.lastSeen > 2000 } 
                val visibleBeacons = discoveredDevices.values.count { BEACON_MINOR_MAP.containsKey(it.iBeaconInfo?.minor) }
                beaconStatus.value = "Beacons: $visibleBeacons/3 visible"
                publishMqttData()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun publishMqttData() {
        if (!::mqttClient.isInitialized || !mqttClient.state.isConnected) {
            lastPublishStatus.value = "Last Publish: FAILED (disconnected)"
            return
        }

        val payload = JSONObject()
        payload.put("device_id", Build.MODEL.replace(" ", "_"))

        var beaconsFound = 0
        for ((minor, key) in BEACON_MINOR_MAP) {
            val device = discoveredDevices.values.find { it.iBeaconInfo?.minor == minor }
            if (device != null) {
                payload.put(key, device.getAverageRssi())
                beaconsFound++
            } else {
                payload.put(key, -100) 
            }
        }

        if (beaconsFound > 0) {
            mqttClient.publishWith()
                .topic(MQTT_PUBLISH_TOPIC)
                .payload(payload.toString().toByteArray())
                .send()
                .whenComplete { _, throwable ->
                    runOnUiThread {
                        if (throwable != null) {
                            lastPublishStatus.value = "Last Publish: FAILED (${throwable.cause?.message ?: "Unknown"})"
                        } else {
                            lastPublishStatus.value = "Last Publish: OK"
                        }
                    }
                }
        } else {
            lastPublishStatus.value = "Last Publish: SKIPPED (no beacons)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::scanner.isInitialized) {
            scanner.stopScan(scanCallback)
        }
        if (::mqttClient.isInitialized) {
            mqttClient.disconnect()
        }
    }
}