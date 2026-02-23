package com.neubofy.watch.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import com.neubofy.watch.data.HealthConnectManager
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(
    healthConnectManager: HealthConnectManager,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current

    var bluetoothGranted by remember {
        mutableStateOf(hasBluetoothPermissions(context))
    }
    var healthConnectAvailable by remember {
        mutableStateOf(healthConnectManager.isAvailable())
    }
    var healthConnectGranted by remember { mutableStateOf(false) }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var dndAccessGranted by remember {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        mutableStateOf(nm.isNotificationPolicyAccessGranted)
    }
    var batteryOptimized by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permScope = rememberCoroutineScope()

    // Check Health Connect permissions on launch AND every resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bluetoothGranted = hasBluetoothPermissions(context)
                microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                dndAccessGranted = nm.isNotificationPolicyAccessGranted
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                batteryOptimized = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else true
                notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
                permScope.launch {
                    if (healthConnectAvailable) {
                        healthConnectGranted = healthConnectManager.hasAllPermissions()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (healthConnectAvailable) {
            healthConnectGranted = healthConnectManager.hasAllPermissions()
        }
    }

    // Bluetooth permission launcher
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        bluetoothGranted = permissions.values.all { it }
    }

    // Health Connect permission launcher
    val healthConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        healthConnectGranted = permissions.values.all { it }
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
    }

    // Health Connect specific permission launcher  
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        HealthConnectManager.createPermissionRequestContract()
    ) { granted ->
        healthConnectGranted = granted.containsAll(HealthConnectManager.PERMISSIONS)
    }

    val allGranted = bluetoothGranted && microphoneGranted && dndAccessGranted && batteryOptimized && notificationsGranted && (healthConnectGranted || !healthConnectAvailable)

    Scaffold(
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    Button(
                        onClick = onAllGranted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Continue",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Permissions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "NF Watch needs a few permissions to work.\nPrivate. Secure. Simple.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bluetooth & Location Permission
            PermissionCard(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth & Location",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 
                    "Required to scan and connect to your GoBoult Drift+ watch"
                else 
                    "Location and Bluetooth are required to find nearby devices on your version of Android",
                isGranted = bluetoothGranted,
                onRequest = {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    bluetoothLauncher.launch(permissions)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Health Connect Permission
            PermissionCard(
                icon = Icons.Default.HealthAndSafety,
                title = "Health Connect",
                description = if (healthConnectAvailable)
                    "Store heart rate, steps, sleep in Health Connect (OS-level)"
                else
                    "Health Connect not installed. Install from Play Store for health data storage.",
                isGranted = healthConnectGranted,
                isAvailable = healthConnectAvailable,
                onRequest = {
                    if (healthConnectAvailable) {
                        healthPermissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Microphone Permission (for Voice Recorder)
            PermissionCard(
                icon = Icons.Default.Mic,
                title = "Microphone",
                description = "Required for starting voice recordings from your watch.",
                isGranted = microphoneGranted,
                onRequest = {
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // DND Policy Access (for bi-directional DND sync)
            PermissionCard(
                icon = Icons.Default.DoNotDisturbOn,
                title = "DND Sync Access",
                description = "Required to sync Do Not Disturb mode from watch to phone.",
                isGranted = dndAccessGranted,
                onRequest = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Battery Optimization (for background stability)
            PermissionCard(
                icon = Icons.Default.BatteryChargingFull,
                title = "Background Stability",
                description = "Ignore battery optimizations to keep the watch connected in the background.",
                isGranted = batteryOptimized,
                onRequest = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(12.dp))

                // Notifications Permission
                val notifyLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> notificationsGranted = granted }

                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Required to show the connection status in your notification bar.",
                    isGranted = notificationsGranted,
                    onRequest = {
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }


            Spacer(modifier = Modifier.height(12.dp))

            // Privacy info cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A2E1A)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "What we DON'T request",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF66BB6A),
                            fontSize = 13.sp
                        )
                        Text(
                            "❌ No Internet • ✅ Only Audio on Demand",
                            fontSize = 12.sp,
                            color = Color(0xFF88AA88)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isAvailable: Boolean = true,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onRequest,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isAvailable) "Grant" else "Install")
                }
            }
        }
    }
}

private fun hasBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
