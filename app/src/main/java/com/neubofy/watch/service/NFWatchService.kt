package com.neubofy.watch.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neubofy.watch.MainActivity
import com.neubofy.watch.R
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat

class NFWatchService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    lateinit var connectionManager: BleConnectionManager
    lateinit var appCache: AppCache

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "Bluetooth turned ON, attempting priority reconnect")
                        // Immediate priority reconnect burst
                        serviceScope.launch {
                            reconnectIfPaired(isPriority = true)
                        }
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d(TAG, "Bluetooth turned OFF")
                        // Inform manager, but let the GATT callback decide if we are truly disconnected
                        connectionManager.onBluetoothOff()
                    }
                }
            }
        }
    }
    
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NFWatchService = this@NFWatchService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        connectionManager = BleConnectionManager.getInstance(this)
        appCache = AppCache(this)
        
        // Ensure repository is connected even in background service
        val database = com.neubofy.watch.data.db.HealthDatabase.getDatabase(this)
        val healthConnectManager = com.neubofy.watch.data.HealthConnectManager(this)
        val healthRepository = com.neubofy.watch.data.HealthRepository(database.healthDao(), healthConnectManager, appCache)
        connectionManager.setRepository(healthRepository)

        // Wire up the media listener so it can send song info to watch
        MediaListenerService.connectionManagerRef = connectionManager
        
        createNotificationChannel()
        val notification = createNotification("Starting service...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "startForeground SecurityException (Android 14+ FGS restriction without active BT connection): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}")
        }

        // Register Bluetooth state listener
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        // Observe connection state to update notification & handle FGS promotion when connected
        serviceScope.launch {
            connectionManager.connectionState.collectLatest { state ->
                val txt = getNotificationText(state)
                updateNotification(txt)
                
                // If we connect, promote to FGS because we now have an active BT connection (satisfying Android 14 requirements)
                if (state == ConnectionState.CONNECTED) {
                    try {
                        val notif = createNotification(txt)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                        } else {
                            startForeground(NOTIFICATION_ID, notif)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Re-promotion to FGS failed: ${e.message}")
                    }
                }
            }
        }

        // Observe steps and calories to update notification with live stats
        serviceScope.launch {
            combine(
                connectionManager.steps,
                connectionManager.calories,
                connectionManager.connectionState
            ) { steps, calories, state ->
                Triple(steps, calories, state)
            }.collectLatest { (steps, calories, state) ->
                val baseText = getNotificationText(state)
                val statsText = if (state == ConnectionState.CONNECTED) {
                    " | $steps Steps | $calories kcal"
                } else ""
                updateNotification(baseText + statsText)
            }
        }


        // Polling natively managed by BleConnectionManager when connected.

        // Auto-reconnect flow
        reconnectIfPaired()
    }

    private fun reconnectIfPaired(isPriority: Boolean = false) {
        serviceScope.launch {
            val address = appCache.pairedDeviceAddress.first()
            if (address != null && connectionManager.connectionState.value == ConnectionState.DISCONNECTED) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    return@launch
                }

                val hasBlePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(this@NFWatchService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(this@NFWatchService, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                }
                
                if (hasBlePermission) {
                    try {
                        Log.d(TAG, "Reconnecting (priority=$isPriority) to $address")
                        connectionManager.reconnect(address, isPriority = isPriority)
                        
                        // If priority, fallback to autoConnect=true after 15 seconds to save battery
                        if (isPriority) {
                            kotlinx.coroutines.delay(15000)
                            if (connectionManager.connectionState.value == ConnectionState.CONNECTING) {
                                Log.d(TAG, "Priority window expired, falling back to passive autoConnect")
                                connectionManager.reconnect(address, isPriority = false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-reconnect failed: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        // Return STICKY to ensure service restarts if killed by system
        return START_STICKY
    }

    private fun getNotificationText(state: ConnectionState): String {
        return when (state) {
            ConnectionState.CONNECTED -> "Watch connected"
            ConnectionState.CONNECTING -> "Connecting to watch..."
            ConnectionState.DISCOVERING_SERVICES -> "Syncing data..."
            ConnectionState.DISCONNECTED -> "Watch disconnected"
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NF Watch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Minimal disturbance
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Watch Connection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.e(TAG, "onTaskRemoved: App swiped away. Re-firing START_STICKY to force keep-alive.")
        // Explicitly restart the service to survive aggressive OS killing
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        try { connectionManager.stopPhoneRing() } catch (e: Exception) {}
        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) {}
        serviceJob.cancel()
        // Intentionally NOT calling unpair/disconnect here.
        // The GATT with autoConnect=true survives service restart via START_STICKY or AlarmManager.
    }

    companion object {
        private const val TAG = "NFWatchService"
        private const val CHANNEL_ID = "NFWatchServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
