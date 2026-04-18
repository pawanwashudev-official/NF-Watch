package com.neubofy.watch.ui.screens
import com.neubofy.watch.ui.screens.GlassSettingsCard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.ui.theme.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPhoneSettingsScreen(
    appCache: AppCache,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lockEnabled by appCache.findPhoneLock.collectAsState(initial = true)
    val wifiEnabled by appCache.findPhoneWifi.collectAsState(initial = false)
    val btEnabled by appCache.findPhoneBt.collectAsState(initial = false)
    val gpsEnabled by appCache.findPhoneGps.collectAsState(initial = false)
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
            // Need to take persistable URI permission
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                scope.launch { appCache.updateFindPhoneSettings(audioUri = it.toString()) }
            } catch (e: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Phone Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBlack)
            )
        },
        containerColor = SurfaceBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (!secureSettingsGranted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
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
            }

            item { Text("Actions", color = Gold, fontWeight = FontWeight.SemiBold) }

            item {
                GlassSettingsCard {
                    SettingToggle("Lock Phone", "Instantly lock the device", lockEnabled) {
                        scope.launch { appCache.updateFindPhoneSettings(lock = it) }
                    }
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    SettingToggle("Turn on Wi-Fi", "Requires Secure Settings on Android 10+", wifiEnabled) {
                        scope.launch { appCache.updateFindPhoneSettings(wifi = it) }
                    }
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    SettingToggle("Turn on Bluetooth", "Requires Secure Settings on Android 13+", btEnabled) {
                        scope.launch { appCache.updateFindPhoneSettings(bt = it) }
                    }
                    HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                    SettingToggle("Turn on GPS", "Requires Secure Settings", gpsEnabled) {
                        scope.launch { appCache.updateFindPhoneSettings(gps = it) }
                    }
                }
            }

            item { Text("Alert Configuration", color = Gold, fontWeight = FontWeight.SemiBold) }

            item {
                GlassSettingsCard {
                    SettingToggle("Enable Siren", "Play sound, vibrate, and flash torch", sirenEnabled) {
                        scope.launch { appCache.updateFindPhoneSettings(siren = it) }
                    }

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
                                modifier = Modifier.width(120.dp)
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

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SMS Alerts", color = Gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!smsPermissionGranted) {
                        TextButton(onClick = { requestSmsPermission.launch(Manifest.permission.SEND_SMS) }) {
                            Text("Grant Permission", color = AccentRed)
                        }
                    }
                }
            }

            item {
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

                GlassSettingsCard {
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

            item { Spacer(modifier = Modifier.height(30.dp)) }
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
