package com.neubofy.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.*
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.content.ContentValues
import android.provider.MediaStore
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES
}

data class GattServiceInfo(
    val uuid: String,
    val characteristics: List<GattCharacteristicInfo>
)

data class GattCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean
)

data class BleLogEntry(
    val timestamp: String,
    val direction: String,
    val type: String,
    val serviceUuid: String?,
    val characteristicUuid: String?,
    val rawHex: String?,
    val description: String,
    val rawBytes: ByteArray? = null
)

/**
 * Core BLE Manager for MoYoung-V2 (GoBoult Drift+).
 * Refined to rely on Android's native autoConnect and background persistence.
 */
class BleConnectionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BleConnection"
        private const val MAX_LOG_ENTRIES = 500
        
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: BleConnectionManager? = null

        fun getInstance(context: Context): BleConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val healthConnectManager = com.neubofy.watch.data.HealthConnectManager(context)
    private val appCache = com.neubofy.watch.data.AppCache(context)
    private var bluetoothGatt: BluetoothGatt? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthRepository: com.neubofy.watch.data.HealthRepository? = null
    private val weatherService = com.neubofy.watch.data.WeatherService(context)

    fun setRepository(repository: com.neubofy.watch.data.HealthRepository) {
        this.healthRepository = repository
    }

    // --- Public State Flows ---
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _heartRate = MutableStateFlow<Int>(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _steps = MutableStateFlow<Int>(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _spO2 = MutableStateFlow<Int>(0)
    val spO2: StateFlow<Int> = _spO2.asStateFlow()

    private val _bloodPressure = MutableStateFlow<String?>(null)
    val bloodPressure: StateFlow<String?> = _bloodPressure.asStateFlow()

    private val _calories = MutableStateFlow<Int>(0)
    val calories: StateFlow<Int> = _calories.asStateFlow()

    private val _distanceMeters = MutableStateFlow<Int>(0)
    val distanceMeters: StateFlow<Int> = _distanceMeters.asStateFlow()

    private val _sleepMinutes = MutableStateFlow<Int>(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()

    private val _stress = MutableStateFlow<Int>(0)
    val stress: StateFlow<Int> = _stress.asStateFlow()

    private val _findPhoneRinging = MutableStateFlow<Boolean>(false)
    val findPhoneRinging: StateFlow<Boolean> = _findPhoneRinging.asStateFlow()

    private val _weatherCity = MutableStateFlow("London")
    private val _weatherTemp = MutableStateFlow(25)
    private val _weatherIcon = MutableStateFlow(0)

    // Which metric is being measured ("heart_rate", "spo2", "blood_pressure", "stress", or null)
    private val _measuringType = MutableStateFlow<String?>(null)
    val measuringType: StateFlow<String?> = _measuringType.asStateFlow()

    private val _parsedDataFlow = MutableSharedFlow<GoBoultProtocol.ParsedData>(extraBufferCapacity = 100)

    private val _bleLogs = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val bleLogs: StateFlow<List<BleLogEntry>> = _bleLogs.asStateFlow()

    // --- Internal State ---
    private var connectedDeviceAddress: String? = null
    private var expectedHrIndex = 0
    private var userDisconnected = false
    private var pendingNotifications = mutableListOf<BluetoothGattCharacteristic>()
    private var notificationIndex = 0
    private var heartbeatJob: Job? = null
    private var weatherSyncJob: Job? = null

    private var lastHrSyncTime = 0L
    private var lastHrValue = 0
    private var lastSpO2SyncTime = 0L
    private var lastSpO2Value = 0
    private var lastBpSyncTime = 0L
    private var lastBpValue = ""
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var originalVolume: Int = -1
    private var volumeGuardianJob: Job? = null
    private var sosJob: Job? = null
    private var vibrator: Vibrator? = null
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var isRecordingVoice = false

    private val pendingManualValues = mutableMapOf<String, Double>()
    private var measurementWatchdog: Job? = null
    
    private val packetManager = MoyoungPacketManager()

    private var isDndSyncEnabled = false
    private val dndReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                if (isDndSyncEnabled && _connectionState.value == ConnectionState.CONNECTED) {
                    val nm = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    val filter = nm?.currentInterruptionFilter ?: return

                    val dndOn = filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
                                filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                                filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
                    
                    // Activate DND to cover the whole day if turned on
                    setDnd(dndOn, startHour = 0, startMin = 0, endHour = 23, endMin = 59)
                }
            }
        }
    }

    init {
        // Load last cached values into state flows so UI always shows something
        scope.launch {
            appCache.lastHeartRate.collect { if (it > 0 && _heartRate.value == 0) _heartRate.value = it }
        }
        scope.launch {
            appCache.lastSteps.collect { if (it > 0 && _steps.value == 0) _steps.value = it }
        }
        scope.launch {
            appCache.weatherCity.collect { _weatherCity.value = it }
        }
        scope.launch {
            appCache.weatherTemp.collect { _weatherTemp.value = it }
        }
        scope.launch {
            appCache.weatherIcon.collect { _weatherIcon.value = it }
        }
        scope.launch {
            appCache.lastCalories.collect { if (it > 0 && _calories.value == 0) _calories.value = it }
        }
        scope.launch {
            appCache.lastDistance.collect { if (it > 0 && _distanceMeters.value == 0) _distanceMeters.value = it }
        }
        scope.launch {
            appCache.lastSpO2.collect { if (it > 0 && _spO2.value == 0) _spO2.value = it }
        }
        scope.launch {
            appCache.lastBloodPressure.collect { if (it != null && _bloodPressure.value == null) _bloodPressure.value = it }
        }
        scope.launch {
            appCache.lastSleepMinutes.collect { if (it > 0 && _sleepMinutes.value == 0) _sleepMinutes.value = it }
        }
        scope.launch {
            appCache.lastStress.collect { if (it > 0 && _stress.value == 0) _stress.value = it }
        }
        scope.launch {
            appCache.syncDndWithPhone.collect { isDndSyncEnabled = it }
        }

        context.registerReceiver(
            dndReceiver,
            android.content.IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        addLog("EVENT", "CONNECT", null, null, null, "Connected to $deviceAddress")
                        _connectionState.value = ConnectionState.DISCOVERING_SERVICES
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            gatt.requestMtu(512)
                        }, 500)
                    } else {
                        addLog("EVENT", "CONNECT_ERR", null, null, null, "Connect failed: $status")
                        gatt.close()
                        if (bluetoothGatt == gatt) bluetoothGatt = null
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasManual = userDisconnected
                    addLog("EVENT", "DISCONNECT", null, null, null,
                        "Disconnected (status=$status, userManual=$wasManual)")
                    
                    _connectionState.value = ConnectionState.DISCONNECTED
                    stopAllBackgroundWork()

                    if (wasManual) {
                        // unpair() already handled gatt.close()
                    } else {
                        // Do NOT close GATT — autoConnect=true will reconnect automatically
                        addLog("EVENT", "WAITING_RECONNECT", null, null, null, "Signal lost, Android auto-reconnect active")
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.CONNECTED
                
                val serviceInfos = gatt.services.map { service ->
                    GattServiceInfo(
                        service.uuid.toString(),
                        service.characteristics.map { char ->
                            GattCharacteristicInfo(
                                char.uuid.toString(), char.properties,
                                (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
                                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
                                (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                            )
                        }
                    )
                }
                _services.value = serviceInfos
                
                subscribeToAllNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
            val hex = value.joinToString(" ") { "%02X".format(it) }
            addLog("IN", "NOTIFY", char.service.uuid.toString(), char.uuid.toString(), hex, "Notification: $hex", value)
            
            // Handle raw 2-byte pulse updates (format 06 XX) found in some Moyoung logs
            if (value.size == 2 && value[0] == 0x06.toByte()) {
                val bpm = value[1].toInt() and 0xFF
                if (bpm > 0) {
                    addLog("IN", "PULSE", char.service.uuid.toString(), char.uuid.toString(), hex, "Pulse Update: $bpm bpm", value)
                    updateStateFromParsedData(GoBoultProtocol.ParsedData.HeartRate(bpm, isStable = false))
                    return
                }
            }

            val lowerUuid = char.uuid.toString().lowercase()
            if (lowerUuid.contains("fee3")) {
                packetManager.append(value).forEach { (packetType, payload) ->
                    val cmdName = MoyoungPacketManager.getCommandName(packetType)
                    val pHex = payload.joinToString(" ") { "%02X".format(it) }
                    addLog("IN", "PACKET", char.service.uuid.toString(), char.uuid.toString(), pHex, "Decoded: $cmdName", payload)
                    
                    GoBoultProtocol.parseMoyoungPayload(packetType, payload)?.let { parsed ->
                        updateStateFromParsedData(parsed)
                    }
                }
            } else {
                addLog("IN", "NOTIFY", char.service.uuid.toString(), char.uuid.toString(), hex, "Notification: $hex", value)
                GoBoultProtocol.parseStandardNotification(char.uuid.toString(), value)?.let { parsed ->
                    updateStateFromParsedData(parsed)
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val hex = value.joinToString(" ") { "%02X".format(it) }
            addLog("IN", "READ", char.service.uuid.toString(), char.uuid.toString(), hex, "Read [$status]: $hex", value)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (char.uuid.toString() == "00002a19-0000-1000-8000-00805f9b34fb" && value.isNotEmpty()) {
                    _batteryLevel.value = value[0].toInt() and 0xFF
                } else {
                    val lowerUuid = char.uuid.toString().lowercase()
                    if (lowerUuid.contains("fee3")) {
                        packetManager.append(value).forEach { (packetType, payload) ->
                            val cmdName = MoyoungPacketManager.getCommandName(packetType)
                            val pHex = payload.joinToString(" ") { "%02X".format(it) }
                            addLog("IN", "PACKET", char.service.uuid.toString(), char.uuid.toString(), pHex, "Decoded: $cmdName", payload)

                            GoBoultProtocol.parseMoyoungPayload(packetType, payload)?.let { parsed ->
                                updateStateFromParsedData(parsed)
                            }
                        }
                    } else {
                        GoBoultProtocol.parseStandardNotification(char.uuid.toString(), value)?.let { parsed ->
                            updateStateFromParsedData(parsed)
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(gatt)
        }
    }

    private fun updateStateFromParsedData(parsed: GoBoultProtocol.ParsedData) {
        _parsedDataFlow.tryEmit(parsed)

        when (parsed) {
            is GoBoultProtocol.ParsedData.HeartRate -> {
                _heartRate.value = parsed.bpm
                scope.launch { appCache.updateHealthValues(heartRate = parsed.bpm) }
                
                if (parsed.isStable) {
                    // This is a final result or background sync value - save it!
                    healthRepository?.insertHealthData("heart_rate", parsed.bpm.toDouble())
                    // If we were measuring, this cancels the watchdog/buffer
                    if (_measuringType.value == "heart_rate") {
                        pendingManualValues.remove("heart_rate")
                        _measuringType.value = null
                        measurementWatchdog?.cancel()
                    }
                } else {
                    // This is a "live" pulse - buffer if measuring, otherwise just update UI
                    if (_measuringType.value == "heart_rate") {
                        pendingManualValues["heart_rate"] = parsed.bpm.toDouble()
                        resetMeasurementWatchdog()
                    }
                }
            }
            is GoBoultProtocol.ParsedData.WeatherQuery -> {
                addLog("EVENT", "WEATHER", null, null, null, "Watch queried weather. Fetching real weather...")
                scope.launch { sendWeatherUpdate() }
            }
            is GoBoultProtocol.ParsedData.Summary -> {
                addLog("EVENT", "SUMMARY", null, null, null, "Summary: ${parsed.steps} steps, ${parsed.calories} kcal, ${parsed.distanceMeters}m")
                _steps.value = parsed.steps
                _distanceMeters.value = parsed.distanceMeters
                _calories.value = parsed.calories
                
                scope.launch {
                    appCache.updateHealthValues(steps = parsed.steps, calories = parsed.calories, distance = parsed.distanceMeters)
                }
                // Removed direct healthRepository.insertHealthData(...) here to prevent massive DB bloat.
                // Live steps update the UI and Cache instantly. Final values are synced to DB/Health Connect 
                // in periodic background sweeps or by pulling the daily/hourly history.
            }
            is GoBoultProtocol.ParsedData.HourlySteps -> {
                // Categories:
                // Cat 0: Today Morning (00:00 to 11:30) - 24 half-hours
                // Cat 1: Today Afternoon (12:00 to 23:30) - 24 half-hours
                // Cat 2: Yesterday Morning - 24 half-hours
                // Cat 3: Yesterday Afternoon - 24 half-hours
                
                val isYesterday = parsed.category >= 2
                val isAfternoon = parsed.category % 2 == 1
                val date = java.time.LocalDate.now().minusDays(if (isYesterday) 1L else 0L)
                val baseHalfHour = if (isAfternoon) 24 else 0
                
                val nonZero = parsed.steps.mapIndexedNotNull { i, s -> if (s > 0) "h$i=$s" else null }
                addLog("EVENT", "HALF_HOURLY_STEPS", null, null, null, 
                    "Cat=${parsed.category} day=-${if (isYesterday) 1 else 0}, ${parsed.steps.size} values, total=${parsed.steps.sum()}, non-zero: ${nonZero.joinToString(", ")}")
                
                parsed.steps.forEachIndexed { index, steps ->
                    if (steps > 0) {
                        val halfHourOfDay = baseHalfHour + index
                        val hour = halfHourOfDay / 2
                        val minute = if (halfHourOfDay % 2 == 0) 0 else 30
                        
                        val timestamp = date.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        // Write incremental chunks directly to build granular history
                        healthRepository?.insertHealthData("steps", steps.toDouble(), timestamp = timestamp, isIncremental = true)
                    }
                }
                
                // Chain requests until we get all 4 categories (0 -> 1 -> 2 -> 3)
                if (parsed.category < 3) {
                    writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getQueryHourlyStepsCmd(parsed.category + 1))
                }
            }
            is GoBoultProtocol.ParsedData.Steps -> {
                _steps.value = parsed.count
            }
            is GoBoultProtocol.ParsedData.PastSteps -> {
                // Calculate correct date using daysAgo 
                val pastDate = java.time.LocalDate.now().minusDays(parsed.daysAgo.toLong())
                val pastTimestamp = pastDate.atTime(23, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                addLog("EVENT", "HISTORY_STEPS", null, null, null, "Past steps (${parsed.daysAgo}d ago): ${parsed.count}")
                if (parsed.count > 0) {
                    healthRepository?.insertHealthData("steps", parsed.count.toDouble(), timestamp = pastTimestamp)
                }
            }
            is GoBoultProtocol.ParsedData.SleepSummary -> {
                // Calculate correct date using daysAgo
                val sleepDate = java.time.LocalDate.now().minusDays(parsed.daysAgo.toLong())
                val sleepTimestamp = sleepDate.atTime(parsed.stats.endHour, parsed.stats.endMinute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                addLog("EVENT", "SLEEP", null, null, null, "Sleep (${parsed.daysAgo}d ago): ${parsed.stats.totalMinutes} mins (Wake: ${parsed.stats.endHour}:${parsed.stats.endMinute})")
                if (parsed.stats.totalMinutes > 0) {
                    if (parsed.daysAgo == 0) {
                        _sleepMinutes.value = parsed.stats.totalMinutes
                        scope.launch { appCache.updateHealthValues(sleepMinutes = parsed.stats.totalMinutes) }
                    }
                    healthRepository?.insertHealthData(
                        "sleep", 
                        parsed.stats.totalMinutes.toDouble(),
                        systolic = parsed.stats.deepMinutes,
                        diastolic = parsed.stats.lightMinutes,
                        timestamp = sleepTimestamp
                    )
                }
            }
            is GoBoultProtocol.ParsedData.HrHistory -> {
                val date = java.time.LocalDate.now().minusDays(parsed.daysAgo.toLong())
                val zone = java.time.ZoneId.systemDefault()
                
                addLog("EVENT", "HR_HISTORY", null, null, null, 
                    "HR History idx=${parsed.index} day=-${parsed.daysAgo} h=${parsed.startHour} (${parsed.samples.count { it > 0 }} valid of ${parsed.samples.size})")
                
                var minuteOffset = 0
                for (hr in parsed.samples) {
                    val hour = parsed.startHour + (minuteOffset / 60)
                    val minute = minuteOffset % 60
                    if (hr in 30..220 && hour < 24) {
                        val ts = date.atTime(hour, minute)
                            .atZone(zone).toInstant().toEpochMilli()
                        if (ts <= System.currentTimeMillis()) {
                            healthRepository?.insertHealthData("heart_rate", hr.toDouble(), timestamp = ts, isIncremental = true)
                        }
                    }
                    minuteOffset += 5
                }

                if (parsed.index != expectedHrIndex) {
                    addLog("EVENT", "HR_HISTORY", null, null, null, "HR history index mismatch: got ${parsed.index}, expected $expectedHrIndex.")
                }
            }
            is GoBoultProtocol.ParsedData.Battery -> {
                addLog("EVENT", "BATTERY", null, null, null, "Battery level: ${parsed.level}%")
                _batteryLevel.value = parsed.level
                scope.launch { appCache.updateHealthValues(battery = parsed.level) }
            }
            is GoBoultProtocol.ParsedData.SpO2 -> {
                _spO2.value = parsed.value
                if (_measuringType.value == "spo2") {
                    pendingManualValues["spo2"] = parsed.value.toDouble()
                    resetMeasurementWatchdog()
                } else {
                    scope.launch { appCache.updateHealthValues(spO2 = parsed.value) }
                    healthRepository?.insertHealthData("spo2", parsed.value.toDouble())
                }
            }
            is GoBoultProtocol.ParsedData.BloodPressure -> {
                val bp = "${parsed.systolic}/${parsed.diastolic}"
                if (parsed.systolic != 255 && parsed.diastolic != 255) {
                    _bloodPressure.value = bp
                    if (_measuringType.value == "blood_pressure") {
                        pendingManualValues["bp_sys"] = parsed.systolic.toDouble()
                        pendingManualValues["bp_dia"] = parsed.diastolic.toDouble()
                        resetMeasurementWatchdog()
                    } else {
                        scope.launch { appCache.updateHealthValues(bloodPressure = bp) }
                        healthRepository?.insertHealthData("blood_pressure", 0.0, systolic = parsed.systolic, diastolic = parsed.diastolic)
                    }
                }
            }
            is GoBoultProtocol.ParsedData.Stress -> {
                _stress.value = parsed.level
                if (_measuringType.value == "stress") {
                    pendingManualValues["stress"] = parsed.level.toDouble()
                    resetMeasurementWatchdog()
                } else {
                    scope.launch { appCache.updateHealthValues(stress = parsed.level) }
                    healthRepository?.insertHealthData("stress", parsed.level.toDouble())
                }
            }
            is GoBoultProtocol.ParsedData.StressHistory -> {
                // 26 half-hour slots (covers 0:00 - 12:30)
                val date = java.time.LocalDate.now().minusDays(parsed.daysAgo.toLong())
                val zone = java.time.ZoneId.systemDefault()
                var savedCount = 0
                parsed.halfHourSlots.forEachIndexed { i, level ->
                    if (level in 1..100) {
                        val hour = i / 2
                        val minute = if (i % 2 == 0) 0 else 30
                        val ts = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        if (ts <= System.currentTimeMillis()) {
                            healthRepository?.insertHealthData("stress", level.toDouble(), timestamp = ts, isIncremental = true)
                            savedCount++
                        }
                    }
                }
                addLog("EVENT", "STRESS_HISTORY", null, null, null, 
                    "Stress history (${parsed.daysAgo}d ago): saved $savedCount of ${parsed.halfHourSlots.size} slots")
            }
            is GoBoultProtocol.ParsedData.MeasurementFinished -> {
                addLog("EVENT", "MEASURE", null, null, null, "Measurement finished: ${parsed.metricType}")
                commitAndStopMeasurement()
            }
            is GoBoultProtocol.ParsedData.FindPhone -> {
                _findPhoneRinging.value = parsed.ringing
                if (parsed.ringing) startPhoneRing() else stopPhoneRing()
            }
            is GoBoultProtocol.ParsedData.WatchFaceChanged -> {
                addLog("EVENT", "WATCH_FACE", null, null, null, "Watch face changed to: ${parsed.faceIndex}")
            }
            is GoBoultProtocol.ParsedData.RaiseToWake -> {
                addLog("EVENT", "RAISE_TO_WAKE", null, null, null, "Raise to wake: ${if (parsed.enabled) "ON" else "OFF"}")
            }
            is GoBoultProtocol.ParsedData.MusicControl -> handleMusicControl(parsed.action)

            is GoBoultProtocol.ParsedData.VoiceAssistantTrigger -> {
                if (parsed.isStart) triggerVoiceAssistant()
            }
            is GoBoultProtocol.ParsedData.CameraEvent -> handleCameraEvent(parsed.action)

            is GoBoultProtocol.ParsedData.DndToggle -> {
                addLog("EVENT", "DND_MODE", null, null, null, "Do Not Disturb is now: ${if (parsed.enabled) "ON" else "OFF"}")
                if (isDndSyncEnabled) {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(if (parsed.enabled) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY else android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                            addLog("EVENT", "DND_SYNC", null, null, null, "Phone DND synced to ${if (parsed.enabled) "ON" else "OFF"}")
                        } else {
                            addLog("EVENT", "DND_SYNC", null, null, null, "Need Notification Access to sync Phone DND")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync DND to phone", e)
                    }
                }
            }
            is GoBoultProtocol.ParsedData.PowerSavingToggle -> {
                addLog("EVENT", "POWER_SAVE", null, null, null, "Power Saving Mode is now: ${if (parsed.enabled) "ON" else "OFF"}")
            }
            is GoBoultProtocol.ParsedData.WatchStateReport -> {
                val stateHex = parsed.rawData.joinToString(" ") { "%02X".format(it) }
                addLog("EVENT", "WATCH_STATE", null, null, null, "Watch state changed: $stateHex")
            }
            is GoBoultProtocol.ParsedData.UnknownPacket -> {
                val cmdName = MoyoungPacketManager.getCommandName(parsed.packetType)
                addLog("EVENT", "UNKNOWN_PACKET", null, null, null, "Unhandled packet type: $cmdName (0x${"%02X".format(parsed.packetType)})")
            }
            else -> {}
        }
    }

    private fun resetMeasurementWatchdog() {
        measurementWatchdog?.cancel()
        measurementWatchdog = scope.launch {
            delay(8000) 
            if (_measuringType.value != null) {
                addLog("EVENT", "MEASURE", null, null, null, "Measurement watchdog timeout")
                commitAndStopMeasurement()
            }
        }
    }

    private fun commitAndStopMeasurement() {
        val type = _measuringType.value ?: return
        
        when (type) {
            "heart_rate" -> {
                pendingManualValues["heart_rate"]?.let {
                    healthRepository?.insertHealthData("heart_rate", it)
                }
            }
            "spo2" -> {
                pendingManualValues["spo2"]?.let {
                    healthRepository?.insertHealthData("spo2", it)
                }
            }
            "blood_pressure" -> {
                val sys = pendingManualValues["bp_sys"]
                val dia = pendingManualValues["bp_dia"]
                if (sys != null && dia != null) {
                    healthRepository?.insertHealthData("blood_pressure", 0.0, systolic = sys.toInt(), diastolic = dia.toInt())
                }
            }
            "stress" -> {
                pendingManualValues["stress"]?.let {
                    healthRepository?.insertHealthData("stress", it)
                }
            }
        }
        
        pendingManualValues.clear()
        _measuringType.value = null
        measurementWatchdog?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String, isPriority: Boolean = false) {
        // If already connected, do nothing
        if (_connectionState.value == ConnectionState.CONNECTED) return
        
        // If already connecting and we aren't changing the priority, do nothing
        // (Note: we use connectedDeviceAddress to check if it's the same device)
        if (_connectionState.value == ConnectionState.CONNECTING && connectedDeviceAddress == deviceAddress) {
             // In current design, we just wait. If we want to flip priority, we could close and restart, 
             // but GATT operations are sensitive. We'll stick to a clean start if DISCONNECTED.
             return 
        }

        closeGatt()
        
        val adapter = bluetoothManager.adapter ?: return
        val device = adapter.getRemoteDevice(deviceAddress)
        
        _connectionState.value = ConnectionState.CONNECTING
        connectedDeviceAddress = deviceAddress
        userDisconnected = false
        
        val autoConnect = !isPriority // High priority = active connect (autoConnect=false)
        addLog("EVENT", "CONNECT_START", null, null, null, "Priority=$isPriority | AutoConnect=$autoConnect")
        
        bluetoothGatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun closeGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT: ${e.message}")
        }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun reconnect(deviceAddress: String? = null, isPriority: Boolean = false) {
        val address = deviceAddress ?: connectedDeviceAddress ?: return
        
        // Force state to DISCONNECTED if we need to restart a stalled connection attempt
        if (_connectionState.value == ConnectionState.CONNECTING && isPriority) {
             _connectionState.value = ConnectionState.DISCONNECTED
        }
        
        connect(address, isPriority)
    }

    /** Called ONLY when user taps Unpair. Destroys everything. */
    @SuppressLint("MissingPermission")
    fun unpair() {
        userDisconnected = true
        stopAllBackgroundWork()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connectedDeviceAddress = null
        _connectionState.value = ConnectionState.DISCONNECTED
        addLog("EVENT", "UNPAIR", null, null, null, "Device unpaired, GATT destroyed")
    }

    /**
     * Called on BT OFF. We log it, but we don't manually set DISCONNECTED.
     * We let the Bluetooth stack tell us if the actual GATT link is dead via onConnectionStateChange.
     */
    fun onBluetoothOff() {
        addLog("EVENT", "BT_OFF_SIGNAL", null, null, null, "System Bluetooth Toggle OFF - Observing BLE link status")
        // No longer manually setting _connectionState to DISCONNECTED here.
        // This allows BLE to persist if the hardware/OS "Scanning Always Available" feature keeps the radio alive.
    }

    private fun stopAllBackgroundWork() {
        stopHeartbeat()
        stopPeriodicWeatherSync()
        stopAutoMeasureLoop()
        stopPeriodicHealthCommit()
    }

    fun setHeartRateInterval(intervalMinutes: Int) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetHeartRateIntervalCmd(intervalMinutes)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent HR Auto Interval: ${intervalMinutes}m")
        }
    }

    // --- Commands ---

    fun startMeasurement(metricType: String) {
        val packet = when (metricType) {
            "heart_rate" -> GoBoultProtocol.getMeasureHeartRateCmd(true)
            "blood_pressure" -> GoBoultProtocol.getMeasureBloodPressureCmd(true)
            "spo2" -> GoBoultProtocol.getMeasureSpO2Cmd(true)
            "stress" -> GoBoultProtocol.getMeasureStressCmd(true)
            else -> return
        }
        _measuringType.value = metricType
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet)
        addLog("EVENT", "MEASURE", null, null, null, "Requested $metricType measurement start")
    }

    fun stopMeasurement(metricType: String) {
        val packet = when (metricType) {
            "heart_rate" -> GoBoultProtocol.getMeasureHeartRateCmd(false)
            "blood_pressure" -> GoBoultProtocol.getMeasureBloodPressureCmd(false)
            "spo2" -> GoBoultProtocol.getMeasureSpO2Cmd(false)
            "stress" -> GoBoultProtocol.getMeasureStressCmd(false)
            else -> return
        }
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet)
        addLog("EVENT", "MEASURE", null, null, null, "Requested $metricType measurement stop")
        
        // Commit immediately when manually stopped from UI if it was actually measuring this type
        if (_measuringType.value == metricType) {
            commitAndStopMeasurement()
        }
    }

    fun sendWeatherUpdate(icon: Int? = null, temp: Int? = null, min: Int? = null, max: Int? = null, city: String? = null) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // If explicit values given, use them. Otherwise fetch real weather.
                var finalIcon: Int
                var finalTemp: Int
                var finalMin: Int
                var finalMax: Int
                var finalCity: String

                if (icon != null && temp != null && city != null) {
                    // Manual override from Settings
                    finalIcon = icon
                    finalTemp = temp
                    finalMin = min ?: (temp - 2)
                    finalMax = max ?: (temp + 3)
                    finalCity = city
                    addLog("EVENT", "WEATHER", null, null, null, "Using manual weather: $finalCity ${finalTemp}°C")
                } else {
                    // Fetch real weather from Open-Meteo using device location
                    val weather = weatherService.fetchWeather()
                    if (weather != null) {
                        finalIcon = weather.moyoungIcon
                        finalTemp = weather.currentTemp
                        finalMin = weather.minTemp
                        finalMax = weather.maxTemp
                        finalCity = weather.city
                        // Cache for quick re-sync
                        appCache.updateWeather(finalCity, finalTemp, finalIcon)
                        addLog("EVENT", "WEATHER", null, null, null, "Real weather: $finalCity ${finalTemp}°C (${weather.description})")
                    } else {
                        // Fallback to cached values
                        finalIcon = _weatherIcon.value
                        finalTemp = _weatherTemp.value
                        finalMin = finalTemp - 2
                        finalMax = finalTemp + 3
                        finalCity = _weatherCity.value
                        addLog("EVENT", "WEATHER", null, null, null, "Using cached weather: $finalCity ${finalTemp}°C")
                    }
                }

                // 1: Today Weather
                val packet1 = GoBoultProtocol.getWeatherCmd(finalIcon, finalTemp, finalMin, finalMax, finalCity)
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet1)

                // 2: Weather Location String
                val timeStr = java.text.SimpleDateFormat("HH:mm ", java.util.Locale.getDefault()).format(java.util.Date()) + finalCity
                val packet2 = GoBoultProtocol.getWeatherLocationCmd(timeStr)
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet2)

                // 3: Future Weather Payload
                val packet3 = GoBoultProtocol.getWeatherFutureCmd(finalIcon, finalMin, finalMax)
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet3)
                
                addLog("EVENT", "WEATHER_SYNC", null, null, null, "Weather sent to watch: $finalCity ${finalTemp}°C")
            } catch (e: Exception) {
                Log.e(TAG, "Weather sync failed", e)
                addLog("ERROR", "WEATHER", null, null, null, "Weather sync failed: ${e.message}")
            }
        }
    }


    /**
     * Public method for pull-to-refresh: syncs time + all data from watch.
     */
    fun syncAllData() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        addLog("EVENT", "SYNC_ALL", null, null, null, "Manual sync triggered")
        sendSyncCommand()
    }

    fun setWatchCameraState(open: Boolean) {
        val payload = byteArrayOf(if (open) 0x01 else 0x00)
        val packet = MoyoungPacketManager.buildPacket(102.toByte(), payload)
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, packet)
        addLog("EVENT", "CAMERA_STATE", null, null, null, "Requested watch camera state: ${if (open) "OPEN" else "CLOSED"}")
    }

    fun setWatchFace(index: Int) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetWatchFaceCmd(index)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Watch Face request: $index")
            appCache.setWatchSettings(watchFace = index)
        }
    }

    fun setPowerSaving(enabled: Boolean) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetPowerSavingCmd(enabled)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Power Saving request: $enabled")
            appCache.setWatchSettings(powerSaving = enabled)
        }
    }

    fun setDnd(enabled: Boolean, startHour: Int = 22, startMin: Int = 0, endHour: Int = 8, endMin: Int = 0) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetDndCmd(enabled, startHour, startMin, endHour, endMin)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent DND request: $enabled ($startHour:$startMin - $endHour:$endMin)")
            appCache.setWatchSettings(dnd = enabled)
        }
    }

    fun setQuickView(enabled: Boolean) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetQuickViewCmd(enabled)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Quick View request: $enabled")
            appCache.setWatchSettings(quickView = enabled)
        }
    }

    fun setTimeFormat(is24Hour: Boolean) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetTimeSystemCmd(is24Hour)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Time Format: 24h=$is24Hour")
            appCache.setWatchSettings(is24HourSys = is24Hour)
        }
    }

    fun setMetricSystem(isMetric: Boolean) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetMetricSystemCmd(isMetric)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Metric System: metric=$isMetric")
            appCache.setWatchSettings(isMetricSys = isMetric)
        }
    }

    fun setGoalSteps(goal: Int) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetGoalStepCmd(goal)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Goal Steps: $goal")
            appCache.setWatchSettings(goalStepsCount = goal)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(1800000) // 30 minutes — highly optimized for battery, relies more on native GATT
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val cmd = byteArrayOf(0x05, 0x01) // Basic ping
                    writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private var healthCommitJob: Job? = null

    private fun startPeriodicHealthCommit() {
        healthCommitJob?.cancel()
        healthCommitJob = scope.launch {
            // Give the app some time to gather initial data on connect
            delay(60000) 
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                // Commit current summary values to DB and Health Connect
                val currentSteps = _steps.value
                val currentCals = _calories.value
                val currentDist = _distanceMeters.value
                
                if (currentSteps > 0) healthRepository?.insertHealthData("steps", currentSteps.toDouble())
                if (currentCals > 0) healthRepository?.insertHealthData("calories", currentCals.toDouble())
                if (currentDist > 0) healthRepository?.insertHealthData("distance", currentDist.toDouble())
                
                // Smart 2-Way Sync & Cleanup for current day
                healthRepository?.syncTodayFromHealthConnect()
                
                addLog("EVENT", "BACKGROUND_COMMIT", null, null, null, "Periodic hourly health sync ran")
                
                delay(60 * 60 * 1000L) // 1 hour
            }
        }
    }

    private fun stopPeriodicHealthCommit() {
        healthCommitJob?.cancel()
        healthCommitJob = null
    }

    private fun startPeriodicWeatherSync() {
        weatherSyncJob?.cancel()
        weatherSyncJob = scope.launch {
            appCache.weatherSyncHours.collectLatest { hours ->
                if (hours > 0) {
                    val delayMillis = hours * 60 * 60 * 1000L
                    
                    // Trigger once immediately when interval starts/changes
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        try {
                            sendWeatherUpdate()
                            addLog("EVENT", "WEATHER_AUTO", null, null, null, "Periodic auto-sync triggered (initial/change): ${hours}h")
                        } catch (e: Exception) {
                            Log.e(TAG, "Initial periodic weather sync failed", e)
                        }
                    }

                    while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                        delay(delayMillis)
                        if (_connectionState.value == ConnectionState.CONNECTED) {
                            try {
                                sendWeatherUpdate()
                                addLog("EVENT", "WEATHER_AUTO", null, null, null, "Periodic auto-sync ran for interval: ${hours}h")
                            } catch (e: Exception) {
                                Log.e(TAG, "Periodic weather sync failed", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPeriodicWeatherSync() {
        weatherSyncJob?.cancel()
        weatherSyncJob = null
    }

    // ── Independent Auto-Measure Jobs (reactive, not polling) ──
    private val autoMeasureJobs = mutableMapOf<String, Job>()
    private val measurementMutex = Mutex()

    fun startAutoMeasureLoop() {
        stopAutoMeasureLoop()
        
        startMetricMeasureJob("heart_rate")
        startMetricMeasureJob("spo2")
        startMetricMeasureJob("blood_pressure")
        startMetricMeasureJob("stress")
    }

    private fun startMetricMeasureJob(type: String) {
        autoMeasureJobs[type]?.cancel()
        autoMeasureJobs[type] = scope.launch {
            delay(10000) // Initial grace period after connect
            val enabledFlow = when(type) {
                "heart_rate" -> appCache.autoMeasureHrEnabled
                "spo2" -> appCache.autoMeasureSpO2Enabled
                "blood_pressure" -> appCache.autoMeasureBpEnabled
                "stress" -> appCache.autoMeasureStressEnabled
                else -> flowOf(false)
            }
            val intervalFlow = when(type) {
                "heart_rate" -> appCache.autoMeasureHrInterval
                "spo2" -> appCache.autoMeasureSpO2Interval
                "blood_pressure" -> appCache.autoMeasureBpInterval
                "stress" -> appCache.autoMeasureStressInterval
                else -> flowOf(60)
            }
            // Reactive: when enabled/interval/connection changes, the block restarts automatically
            combine(enabledFlow, intervalFlow, _connectionState) { enabled, interval, state ->
                Triple(enabled, interval, state)
            }.collectLatest { (enabled, interval, state) ->
                if (enabled && state == ConnectionState.CONNECTED) {
                    addLog("EVENT", "AUTO_MEASURE", null, null, null, "Starting auto $type (interval: ${interval}m)")
                    while (isActive) {
                        runSingleMeasurement(type)
                        delay(interval.toLong() * 60 * 1000)
                    }
                }
                // When disabled or disconnected: collectLatest suspends here — ZERO CPU usage
            }
        }
    }

    private suspend fun runSingleMeasurement(metricType: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        
        // Use Mutex instead of spin-waiting
        measurementMutex.withLock {
            if (_connectionState.value != ConnectionState.CONNECTED) return
            startMeasurement(metricType)
            // Wait for measurement to complete (max 45 seconds)
            delay(45000)
            if (_measuringType.value == metricType) {
                stopMeasurement(metricType)
                addLog("EVENT", "AUTO_MEASURE", null, null, null, "$metricType auto-timed out")
            }
        }
    }

    fun stopAutoMeasureLoop() {
        autoMeasureJobs.values.forEach { it.cancel() }
        autoMeasureJobs.clear()
    }


    private suspend inline fun <reified T : GoBoultProtocol.ParsedData> syncAndWait(
        cmd: ByteArray,
        timeoutMs: Long,
        crossinline condition: (T) -> Boolean
    ): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
        val result = withTimeoutOrNull(timeoutMs) {
            _parsedDataFlow.first { it is T && condition(it) }
        }
        if (result == null) {
            Log.w(TAG, "Sync timeout for ${T::class.simpleName}")
        }
        return result != null
    }

    private fun sendSyncCommand() {
        scope.launch {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                expectedHrIndex = 0
                addLog("EVENT", "SYNC_START", null, null, null, "Starting full sync sequence...")

                // 1. Sync Time & HR Interval
                val shouldSyncTime = appCache.syncTimeOnConnect.first() 
                if (shouldSyncTime) {
                    writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getSyncTimeCmd())
                    delay(300)
                }
                
                val hrAutoEnabled = appCache.autoMeasureHrEnabled.first()
                val hrInterval = appCache.autoMeasureHrInterval.first()
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getSetHeartRateIntervalCmd(if (hrAutoEnabled) hrInterval else 0))
                delay(300)

                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                // 2. Read Current Summary (steps/calories/distance from FEE1)
                readCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_STEPS_HISTORY)
                delay(400)

                // 3. Today's Sleep (CMD 50)
                syncAndWait<GoBoultProtocol.ParsedData.SleepSummary>(
                    MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_SYNC_SLEEP, ByteArray(0)),
                    timeoutMs = 5000
                ) { it.daysAgo == 0 }
                delay(200)

                // 4. Yesterday's Sleep & Steps
                syncAndWait<GoBoultProtocol.ParsedData.SleepSummary>(
                    MoyoungPacketManager.buildPacket(51, byteArrayOf(3)),
                    timeoutMs = 5000
                ) { it.daysAgo == 1 }
                delay(200)

                syncAndWait<GoBoultProtocol.ParsedData.PastSteps>(
                    MoyoungPacketManager.buildPacket(51, byteArrayOf(1)),
                    timeoutMs = 5000
                ) { it.daysAgo == 1 }
                delay(200)

                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                // 5. Day-before-yesterday Sleep & Steps
                syncAndWait<GoBoultProtocol.ParsedData.SleepSummary>(
                    MoyoungPacketManager.buildPacket(51, byteArrayOf(4)),
                    timeoutMs = 5000
                ) { it.daysAgo == 2 }
                delay(200)

                syncAndWait<GoBoultProtocol.ParsedData.PastSteps>(
                    MoyoungPacketManager.buildPacket(51, byteArrayOf(2)),
                    timeoutMs = 5000
                ) { it.daysAgo == 2 }
                delay(200)

                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                // 6. Hourly Steps
                syncAndWait<GoBoultProtocol.ParsedData.HourlySteps>(
                    GoBoultProtocol.getQueryHourlyStepsCmd(0),
                    timeoutMs = 5000
                ) { it.category == 0 }
                delay(200)

                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                // 7. HR History (auto-chains 0..7)
                for (i in 0..7) {
                    val hrCmd = MoyoungPacketManager.buildPacket(53, byteArrayOf(i.toByte()))
                    expectedHrIndex = i
                    val ok = syncAndWait<GoBoultProtocol.ParsedData.HrHistory>(hrCmd, 3000) { it.index == i }
                    if (!ok) break
                    delay(100)
                }

                // 8. Movement HR (last 3 workout summaries)
                syncAndWait<GoBoultProtocol.ParsedData.HrHistory>(
                    MoyoungPacketManager.buildPacket(55, ByteArray(0)),
                    timeoutMs = 5000
                ) { it.index == 99 }
                delay(200)

                if (_connectionState.value != ConnectionState.CONNECTED) return@launch

                // 9. Stress History (today & yesterday)
                syncAndWait<GoBoultProtocol.ParsedData.StressHistory>(
                    MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_ADVANCED_SETTINGS, byteArrayOf(0x11, 0x03, 0x00)),
                    timeoutMs = 4000
                ) { it.daysAgo == 0 }
                delay(200)

                syncAndWait<GoBoultProtocol.ParsedData.StressHistory>(
                    MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_ADVANCED_SETTINGS, byteArrayOf(0x11, 0x03, 0x01)),
                    timeoutMs = 4000
                ) { it.daysAgo == 1 }
                delay(200)

                // 10. Battery
                bluetoothGatt?.let { tryReadBattery(it) }

                // 11. Weather (fetch real data and push to watch)
                try {
                    val weather = weatherService.fetchWeather()
                    if (weather != null) {
                        appCache.updateWeather(weather.city, weather.currentTemp, weather.moyoungIcon)
                        val p1 = GoBoultProtocol.getWeatherCmd(weather.moyoungIcon, weather.currentTemp, weather.minTemp, weather.maxTemp, weather.city)
                        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, p1)
                        delay(200)
                        val timeStr = java.text.SimpleDateFormat("HH:mm ", java.util.Locale.getDefault()).format(java.util.Date()) + weather.city
                        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getWeatherLocationCmd(timeStr))
                        delay(200)
                        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, 
                            GoBoultProtocol.getWeatherFutureCmd(weather.tomorrowIcon, weather.tomorrowMin, weather.tomorrowMax))
                        addLog("EVENT", "WEATHER_SYNC", null, null, null, "Auto-synced weather: ${weather.city} ${weather.currentTemp}°C")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Weather auto-sync failed: ${e.message}")
                }

                addLog("EVENT", "SYNC_FINISHED", null, null, null, "Full sync sequence complete.")
            } catch (e: Exception) {
                Log.e(TAG, "Sync aborted: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(svc: String, chr: String, data: ByteArray, withResponse: Boolean = false) {
        val g = bluetoothGatt ?: return
        val c = g.getService(UUID.fromString(svc))?.getCharacteristic(UUID.fromString(chr)) ?: return
        val writeType = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT 
                         else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        g.writeCharacteristic(c, data, writeType)
        addLog("OUT", "WRITE", svc, chr, data.joinToString(" ") { "%02X".format(it) }, "Writing ${data.size}B (resp=$withResponse)")
    }

    @SuppressLint("MissingPermission")
    private fun tryReadBattery(gatt: BluetoothGatt) {
        val c = gatt.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
                    ?.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))
        if (c != null) gatt.readCharacteristic(c)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToAllNotifications(gatt: BluetoothGatt) {
        pendingNotifications.clear()
        gatt.services.forEach { s -> s.characteristics.forEach { c -> 
            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) pendingNotifications.add(c) 
        }}
        notificationIndex = 0
        subscribeNext(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        if (notificationIndex >= pendingNotifications.size) {
            // All notifications subscribed, safe to start initial reads and sync writes sequentially
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({ tryReadBattery(gatt) }, 300)
            handler.postDelayed({ startHeartbeat() }, 600)
            handler.postDelayed({ sendSyncCommand() }, 900) // sendSyncCommand already handles weather
            handler.postDelayed({ startPeriodicHealthCommit() }, 1200)
            handler.postDelayed({ startPeriodicWeatherSync() }, 5000) // Start AFTER sync finishes
            return
        }
        val c = pendingNotifications[notificationIndex++]
        gatt.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (d != null) {
            
            @Suppress("DEPRECATION")
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(d)
        }
        else subscribeNext(gatt)
    }

    private fun handleMusicControl(action: String) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        var dispatchedViaSession = false
        when (action) {
            "VOL_UP" -> {
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, 0)
                sendVolumeToWatch(am)
            }
            "VOL_DOWN" -> {
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, 0)
                sendVolumeToWatch(am)
            }
            else -> {
                dispatchedViaSession = com.neubofy.watch.service.MediaListenerService.dispatchMediaAction(action)
                if (!dispatchedViaSession) {
                    val keyCode = when(action) {
                        "PLAY_PAUSE" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        "NEXT" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                        "PREV" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        else -> return
                    }
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
                }
            }
        }
        addLog("EVENT", "MUSIC", null, null, null, "Music control: $action (Via session: $dispatchedViaSession)")
    }

    private fun sendVolumeToWatch(am: android.media.AudioManager) {
        val vol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val level = (16f * vol / max).toInt()
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getVolumeCmd(level))
    }

    // ═══ Public: Music Info (called by MediaSessionService) ═══

    fun sendMusicInfo(artist: String?, track: String?) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        scope.launch {
            try {
                if (!artist.isNullOrBlank()) {
                    writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getMusicArtistCmd(artist))
                    delay(150)
                }
                if (!track.isNullOrBlank()) {
                    writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getMusicTrackCmd(track))
                }
                addLog("EVENT", "MUSIC_INFO", null, null, null, "Sent: $artist - $track")
            } catch (e: Exception) {
                Log.e(TAG, "sendMusicInfo failed", e)
            }
        }
    }

    fun sendMusicState(isPlaying: Boolean) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getMusicStateCmd(isPlaying))
    }

    // ═══ Public: Watch Features ═══


    fun setUserInfo(heightCm: Int, weightKg: Int, age: Int, isMale: Boolean) {
        writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, GoBoultProtocol.getUserInfoCmd(heightCm, weightKg, age, isMale))
        addLog("EVENT", "USER_INFO", null, null, null, "User info: ${heightCm}cm ${weightKg}kg age=$age male=$isMale")
    }

    fun setGoalCalories(calories: Int) {
        scope.launch {
            val cmd = GoBoultProtocol.getSetGoalCalorieCmd(calories)
            writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
            addLog("EVENT", "SETTINGS", null, null, null, "Sent Goal Calories: $calories")
            appCache.setGoalCalories(calories)
        }
    }

    private var lastVoiceAssistantTime = 0L

    private fun triggerVoiceAssistant() {
        scope.launch {
            val enabled = appCache.voiceAssistantEnabled.firstOrNull() ?: false
            if (!enabled) {
                addLog("EVENT", "VOICE", null, null, null, "Voice Assistant ignored (disabled in settings)")
                return@launch
            }

            val now = System.currentTimeMillis()
            if (now - lastVoiceAssistantTime < 1000) {
                addLog("EVENT", "VOICE", null, null, null, "Voice Assistant ignored (debounced)")
                return@launch
            }
            lastVoiceAssistantTime = now
            
            try {
                // Target Google Assistant directly for instant activation
                val i = android.content.Intent(android.content.Intent.ACTION_VOICE_COMMAND).apply { 
                    setPackage("com.google.android.googlequicksearchbox")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                    putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR")
                    putExtra("android.speech.extra.GET_AUDIO", true)
                    
                    // Tell the assistant to use the Bluetooth SCO headset (the watch)
                    putExtra("android.intent.extra.VOICE_PROMPT", true)
                }
                
                // Fallback to generic voice command if Google app is not available
                if (i.resolveActivity(context.packageManager) != null) {
                    context.startActivity(i)
                } else {
                    val fallback = android.content.Intent(android.content.Intent.ACTION_VOICE_COMMAND).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                }
                
                // Add a small delay then attempt to start Bluetooth SCO
                scope.launch {
                    delay(500)
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        if (!audioManager.isBluetoothScoOn) {
                            audioManager.startBluetoothSco()
                            audioManager.isBluetoothScoOn = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start Bluetooth SCO", e)
                    }
                }

                addLog("EVENT", "VOICE", null, null, null, "Voice Assistant triggered with SCO attempt")
            } catch (e: Exception) { 
                Log.e(TAG, "Assistant failed", e) 
                addLog("ERROR", "VOICE", null, null, null, "Assistant failed: ${e.message}")
            }
        }
    }


    private fun toggleVoiceRecording() {
        if (!isRecordingVoice) {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    addLog("EVENT", "VOICE_REC", null, null, null, "Microphone permission required")
                    return
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val displayName = "NF watch $timestamp"
                
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp3")
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RECORDINGS)
                    }
                }
                
                val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (audioUri == null) {
                    addLog("ERROR", "VOICE_REC", null, null, null, "Failed to create MediaStore entry")
                    return
                }

                val pfd = resolver.openFileDescriptor(audioUri, "w")
                if (pfd == null) {
                    addLog("ERROR", "VOICE_REC", null, null, null, "Failed to open file descriptor")
                    return
                }

                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                
                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(pfd.fileDescriptor)
                    prepare()
                    start()
                }
                isRecordingVoice = true
                addLog("EVENT", "VOICE_REC", null, null, null, "Started recording: $displayName (Recordings folder)")
            } catch (e: Exception) {
                Log.e(TAG, "Voice recording start failed", e)
                addLog("ERROR", "VOICE_REC", null, null, null, "Start failed: ${e.message}")
            }
        } else {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecordingVoice = false
                addLog("EVENT", "VOICE_REC", null, null, null, "Stopped voice recording")
            } catch (e: Exception) {
                Log.e(TAG, "Voice recording stop failed", e)
                addLog("ERROR", "VOICE_REC", null, null, null, "Stop failed: ${e.message}")
            }
        }
    }

    private fun handleCameraEvent(action: Int) {
        addLog("EVENT", "CAMERA", null, null, null, "Camera event: action=$action")
        
        if (action == 1) {
            addLog("EVENT", "CAMERA", null, null, null, "Watch entered Camera mode.")
            return
        }
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val buttonAction = appCache.watchButtonAction.firstOrNull() ?: "Flashlight"
                when (buttonAction) {
                    "Flashlight" -> {
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val cameraId = cameraManager.cameraIdList.getOrNull(0) ?: return@launch
                        val newState = !_isTorchOn.value
                        cameraManager.setTorchMode(cameraId, newState)
                        _isTorchOn.value = newState
                        addLog("EVENT", "TORCH", null, null, null, "Torch toggled: ${if (newState) "ON" else "OFF"}")
                    }
                    "Voice Recorder" -> {
                        toggleVoiceRecording()
                    }
                    "Mute/Unmute Phone" -> {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val currentMode = audioManager.ringerMode
                        if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                            try { audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE } catch(e:Exception){}
                            addLog("EVENT", "MUTE", null, null, null, "Phone set to Vibrate Mode")
                        } else {
                            try { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL } catch(e:Exception){}
                            addLog("EVENT", "UNMUTE", null, null, null, "Phone set to Normal Ringer Mode")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watch button trigger failed", e)
            }
        }
    }

    private fun startPhoneRing() {
        try {
            stopPhoneRing() 
            
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            
            // 🛡️ Volume Guardian: Resets volume to 100% every 3 seconds
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            volumeGuardianJob = scope.launch(Dispatchers.Main) {
                while(isActive) {
                    delay(3000)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                    Log.d(TAG, "Volume Guardian: Resetting to MAX")
                }
            }

            // 📳 Vibrator Setup
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 🔦 Torch Hardware
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = try { cameraManager.cameraIdList.getOrNull(0) } catch (e: Exception) { null }

            // 🚨 SOS Pattern Implementation (Sound, Vibration, and Torch)
            sosJob = scope.launch(Dispatchers.Default) {
                // Initialize AudioTrack for piercing 3200Hz Sine wave (Hardware Safe)
                val sampleRate = 44100
                val minSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(minSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                // Launch continuous sound synthesis in a sub-coroutine
                val soundJob = launch {
                    val freq = 3200.0
                    val samples = ShortArray(1024)
                    var ph = 0.0
                    while (isActive) {
                        for (i in samples.indices) {
                            samples[i] = (Math.sin(ph) * (Short.MAX_VALUE * 0.9)).toInt().toShort() // 90% amp for head room
                            ph += 2.0 * Math.PI * freq / sampleRate
                        }
                        audioTrack?.write(samples, 0, samples.size)
                        yield()
                    }
                }

                // SOS Timing: S (...), O (---), S (...)
                val pattern = listOf(
                    150 to true, 150 to false, 150 to true, 150 to false, 150 to true, 300 to false, // S
                    450 to true, 150 to false, 450 to true, 150 to false, 450 to true, 300 to false, // O
                    150 to true, 150 to false, 150 to true, 150 to false, 150 to true, 1000 to false // S
                )

                while (isActive) {
                    for ((duration, isOn) in pattern) {
                        if (!isActive) break
                        
                        if (isOn) {
                            // Vibration pulse
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(duration.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(duration.toLong())
                            }
                            // Torch pulse
                            if (cameraId != null) try { cameraManager.setTorchMode(cameraId, true) } catch (_: Exception) {}
                        }

                        delay(duration.toLong())
                        
                        // Clean Torch state after each pulse
                        if (cameraId != null) try { cameraManager.setTorchMode(cameraId, false) } catch (_: Exception) {}
                    }
                }
            }
            
            addLog("EVENT", "FIND_PHONE", null, null, null, "🆘 EMERGENCY: Full SOS Mode Active (Piercing Sound + Flashlight + Vibration)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SOS ring", e)
        }
    }

    fun stopPhoneRing() {
        try {
            volumeGuardianJob?.cancel()
            volumeGuardianJob = null
            
            sosJob?.cancel()
            sosJob = null
            
            vibrator?.cancel()
            vibrator = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Final Torch Clean
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = try { cameraManager.cameraIdList.getOrNull(0) } catch (e: Exception) { null }
            if (cameraId != null) try { cameraManager.setTorchMode(cameraId, false) } catch (_: Exception) {}

            // Restore original volume if we touched it
            if (originalVolume != -1) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                originalVolume = -1
            }
            
            _findPhoneRinging.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ring", e)
        }
    }


    @SuppressLint("MissingPermission")
    fun readCharacteristic(svc: String, chr: String) {
        val g = bluetoothGatt ?: return
        val c = g.getService(UUID.fromString(svc))?.getCharacteristic(UUID.fromString(chr)) ?: return
        g.readCharacteristic(c)
        addLog("OUT", "READ_REQ", svc, chr, null, "Reading characteristic")
    }

    @SuppressLint("MissingPermission")
    fun readAllReadableCharacteristics() {
        val g = bluetoothGatt ?: return
        val readable = mutableListOf<BluetoothGattCharacteristic>()
        g.services.forEach { s -> s.characteristics.forEach { c -> 
            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) readable.add(c) 
        }}
        readCharacteristicsSequentially(g, readable, 0)
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristicsSequentially(gatt: BluetoothGatt, chars: List<BluetoothGattCharacteristic>, idx: Int) {
        if (idx >= chars.size) return
        gatt.readCharacteristic(chars[idx])
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            readCharacteristicsSequentially(gatt, chars, idx + 1)
        }, 500)
    }

    fun sendNotification(type: Int, title: String, body: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        scope.launch {
            try {
                val cmd = GoBoultProtocol.getMessageNotificationCmd(type, title, body)
                writeCharacteristic(GoBoultProtocol.SERVICE_MOYOUNG_MAIN, GoBoultProtocol.CHAR_WRITE, cmd)
                addLog("OUT", "NOTIFY", null, null, null, "Sent Notification ($type): $title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification", e)
            }
        }
    }

    fun clearLogs() {
        _bleLogs.value = emptyList()
    }

    private fun addLog(dir: String, type: String, svc: String?, chr: String?, hex: String?, desc: String, bytes: ByteArray? = null) {
        val entry = BleLogEntry(timeFormat.format(Date()), dir, type, svc, chr, hex, desc, bytes)
        val list = _bleLogs.value.toMutableList()
        list.add(0, entry)
        _bleLogs.value = list.take(MAX_LOG_ENTRIES)
    }
}
