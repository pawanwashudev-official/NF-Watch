package com.neubofy.watch.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.service.MediaListenerService
import com.neubofy.watch.ui.theme.*
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Layers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    connectionManager: BleConnectionManager,
    appCache: AppCache,
    onNavigateToNotifications: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by connectionManager.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    // Notification Access status check
    var hasNotificationAccess by remember {
        mutableStateOf(MediaListenerService.isNotificationAccessGranted(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = MediaListenerService.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // DataStore-backed settings
    val userHeight by appCache.userHeight.collectAsState(initial = 170)
    val userWeight by appCache.userWeight.collectAsState(initial = 70)
    val userAge by appCache.userAge.collectAsState(initial = 25)
    val userIsMale by appCache.userIsMale.collectAsState(initial = true)
    
    val voiceAssistantEnabled by appCache.voiceAssistantEnabled.collectAsState(initial = false)

    var showUserInfoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ═══ Header ═══
        Text(
            "Watch",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Gold
        )
        Text(
            if (isConnected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) Color(0xFF4CAF50) else TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ═══ Notification Access Card ═══
        if (!hasNotificationAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Gold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Watch Features", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Notification access is required for song info and app alerts", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Gold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ═══ SECTION: App Features ═══
        SectionLabel(title = "App Features")

        // Notification Settings
        FeatureCard(
            icon = Icons.Default.Notifications,
            title = "Notification Manager",
            subtitle = "Choose which apps send alerts to watch",
            accent = Color(0xFF4CAF50),
            onClick = onNavigateToNotifications
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Voice Assistant Toggle
        FeatureCard(
            icon = Icons.Default.Hearing,
            title = "Voice Assistant",
            subtitle = if (voiceAssistantEnabled) "Tap watch button to trigger phone assistant" else "Feature disabled",
            accent = if (voiceAssistantEnabled) Gold else Color.Gray,
            trailing = {
                Switch(
                    checked = voiceAssistantEnabled,
                    onCheckedChange = { scope.launch { appCache.setVoiceAssistantEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Gold,
                        checkedTrackColor = Gold.copy(alpha = 0.4f)
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        FindPhoneSettingsSection(appCache)
        
        Spacer(modifier = Modifier.height(16.dp))

        // ═══ SECTION: User Info ═══
        SectionLabel(title = "User Profile")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUserInfoDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFF00BCD4).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF00BCD4), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Personal Stats", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "${userHeight}cm • ${userWeight}kg • ${userAge}y • ${if (userIsMale) "Male" else "Female"}",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted
                        )
                    }
                }
                Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // ═══ User Info Dialog ═══
    if (showUserInfoDialog) {
        UserInfoDialog(
            height = userHeight,
            weight = userWeight,
            age = userAge,
            isMale = userIsMale,
            onDismiss = { showUserInfoDialog = false },
            onSave = { h, w, a, m ->
                scope.launch {
                    appCache.setUserHeight(h)
                    appCache.setUserWeight(w)
                    appCache.setUserAge(a)
                    appCache.setUserIsMale(m)
                }
                if (isConnected) connectionManager.setUserInfo(h, w, a, m)
                showUserInfoDialog = false
            }
        )
    }
}

@Composable
fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
            }
        }
    }
}


@Composable
fun UserInfoDialog(
    height: Int,
    weight: Int,
    age: Int,
    isMale: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Boolean) -> Unit
) {
    var h by remember { mutableStateOf(height.toString()) }
    var w by remember { mutableStateOf(weight.toString()) }
    var a by remember { mutableStateOf(age.toString()) }
    var m by remember { mutableStateOf(isMale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = h,
                    onValueChange = { h = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = w,
                    onValueChange = { w = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Age") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gender:")
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { m = true }) {
                        RadioButton(selected = m, onClick = { m = true })
                        Text("Male")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { m = false }) {
                        RadioButton(selected = !m, onClick = { m = false })
                        Text("Female")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(h.toIntOrNull() ?: 170, w.toIntOrNull() ?: 70, a.toIntOrNull() ?: 25, m)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun FindPhoneSettingsSection(appCache: AppCache) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val lockEnabled by appCache.findPhoneLock.collectAsState(initial = false)
    val wifiEnabled by appCache.findPhoneWifi.collectAsState(initial = true)
    val btEnabled by appCache.findPhoneBt.collectAsState(initial = true)
    val gpsEnabled by appCache.findPhoneGps.collectAsState(initial = true)
    val sirenEnabled by appCache.findPhoneSiren.collectAsState(initial = true)
    val volumeInterval by appCache.findPhoneVolumeInterval.collectAsState(initial = 3)
    val vibrationPattern by appCache.findPhoneVibration.collectAsState(initial = "SOS")
    val audioUri by appCache.findPhoneAudioUri.collectAsState(initial = null)
    val smsConfigsRaw by appCache.findPhoneSmsConfigs.collectAsState(initial = "[]")

    var secureSettingsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
    }

    var smsPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    val requestSmsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        smsPermissionGranted = isGranted
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                scope.launch { appCache.updateFindPhoneSettings(audioUri = it.toString()) }
            } catch (e: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(title = "Find Phone Settings")

        if (!secureSettingsGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Secure Settings Permission Required", color = AccentRed, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("To turn on Wi-Fi, Bluetooth, or GPS programmatically on modern Android, you must grant WRITE_SECURE_SETTINGS via ADB:", color = TextPrimary, fontSize = 12.sp)
                    Text("adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", color = Gold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = {
                            secureSettingsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("I have granted it. Refresh")
                    }
                }
            }
        }

        // Toggles Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column {
                SettingToggle("Lock Phone", "Instantly lock the device", lockEnabled) { scope.launch { appCache.updateFindPhoneSettings(lock = it) } }
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                SettingToggle("Turn on Wi-Fi", "Requires Secure Settings on Android 10+", wifiEnabled) { scope.launch { appCache.updateFindPhoneSettings(wifi = it) } }
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                SettingToggle("Turn on Bluetooth", "Requires Secure Settings on Android 13+", btEnabled) { scope.launch { appCache.updateFindPhoneSettings(bt = it) } }
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                SettingToggle("Turn on GPS", "Requires Secure Settings", gpsEnabled) { scope.launch { appCache.updateFindPhoneSettings(gps = it) } }
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                SettingToggle("Enable Siren", "Play sound, vibrate, and flash torch", sirenEnabled) { scope.launch { appCache.updateFindPhoneSettings(siren = it) } }

                if (sirenEnabled) {
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Volume Reset Interval", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Resets volume to max every $volumeInterval seconds", color = TextMuted, fontSize = 12.sp)
                        }
                        Slider(
                            value = volumeInterval.toFloat(),
                            onValueChange = { scope.launch { appCache.updateFindPhoneSettings(volumeInterval = it.toInt()) } },
                            valueRange = 1f..15f,
                            steps = 14,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibration Pattern", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(vibrationPattern, color = Gold, fontSize = 14.sp)
                        }
                        IconButton(onClick = {
                            val next = if (vibrationPattern == "SOS") "Continuous" else "SOS"
                            scope.launch { appCache.updateFindPhoneSettings(vibration = next) }
                        }) {
                            Icon(Icons.Default.Vibration, null, tint = AccentTeal)
                        }
                    }
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Audio", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(if (audioUri == null) "Default Hardware Sine Wave" else "Custom Audio Selected", color = if (audioUri == null) TextMuted else Gold, fontSize = 12.sp)
                        }
                        IconButton(onClick = { audioPicker.launch("audio/*") }) {
                            Icon(Icons.Default.AudioFile, null, tint = AccentPurple)
                        }
                        if (audioUri != null) {
                            IconButton(onClick = { scope.launch { appCache.updateFindPhoneSettings(audioUri = "") } }) {
                                Icon(Icons.Default.Clear, null, tint = AccentRed)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SMS Alerts", color = Gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!smsPermissionGranted) {
                TextButton(onClick = { requestSmsPermission.launch(Manifest.permission.SEND_SMS) }) {
                    Text("Grant Permission", color = AccentRed)
                }
            }
        }

        val smsList = try {
            val arr = JSONArray(smsConfigsRaw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getString("phone") to obj.getString("message")
            }
        } catch (e: Exception) { emptyList() }

        var showAddDialog by remember { mutableStateOf(false) }

        if (showAddDialog) {
            var newPhone by remember { mutableStateOf("") }
            var newMsg by remember { mutableStateOf("Alert! My phone might be lost. Location: {location}") }
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add SMS Alert", color = Gold) },
                text = {
                    Column {
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("Phone Number") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newMsg, onValueChange = { newMsg = it }, label = { Text("Message Template") }, modifier = Modifier.height(100.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val currentArr = JSONArray(smsConfigsRaw)
                        val newObj = JSONObject().apply {
                            put("phone", newPhone)
                            put("message", newMsg)
                        }
                        currentArr.put(newObj)
                        scope.launch { appCache.updateFindPhoneSettings(smsConfigs = currentArr.toString()) }
                        showAddDialog = false
                    }) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                smsList.forEachIndexed { index, pair ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pair.first, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(pair.second, color = TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            val currentArr = JSONArray(smsConfigsRaw)
                            val newArr = JSONArray()
                            for (i in 0 until currentArr.length()) {
                                if (i != index) newArr.put(currentArr.getJSONObject(i))
                            }
                            scope.launch { appCache.updateFindPhoneSettings(smsConfigs = newArr.toString()) }
                        }) {
                            Icon(Icons.Default.Delete, null, tint = AccentRed)
                        }
                    }
                    if (index < smsList.size - 1) HorizontalDivider(color = Gold.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                }

                if (smsList.size < 10) {
                    TextButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, tint = Gold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add SMS Configuration", color = Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
        )
    }
}
