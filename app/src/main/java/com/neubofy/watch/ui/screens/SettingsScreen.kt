package com.neubofy.watch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    connectionManager: BleConnectionManager,
    appCache: AppCache,
    onDisconnect: () -> Unit,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val connectionState by connectionManager.connectionState.collectAsState()
    val pairedName by appCache.pairedDeviceName.collectAsState(initial = null)
    val pairedAddress by appCache.pairedDeviceAddress.collectAsState(initial = null)
    val syncTimeOnConnect by appCache.syncTimeOnConnect.collectAsState(initial = true)
    val weatherSyncHours by appCache.weatherSyncHours.collectAsState(initial = 3)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Gold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBlack)
            )
        },
        containerColor = SurfaceBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Watch Info
            SettingSectionTitle("Watch")
            GlassSettingsCard {
                SettingRow(Icons.Default.Watch, "Name", pairedName ?: "Not paired")
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                SettingRow(Icons.Default.Bluetooth, "Address", pairedAddress ?: "—")
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                SettingRow(
                    Icons.Default.Circle, "Status",
                    when (connectionState) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.DISCOVERING_SERVICES -> "Setting up..."
                        ConnectionState.DISCONNECTED -> "Disconnected"
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sync Settings
            SettingSectionTitle("Sync")
            GlassSettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync Time on Connect", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Set watch clock when app connects", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = syncTimeOnConnect,
                        onCheckedChange = { scope.launch { appCache.setSyncTimeOnConnect(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Gold,
                            checkedTrackColor = Gold.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Weather sync interval
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Weather Sync Interval", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Text("Push weather to watch every $weatherSyncHours hours", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 6, 12).forEach { hours ->
                            FilterChip(
                                selected = weatherSyncHours == hours,
                                onClick = { scope.launch { appCache.setWeatherSyncHours(hours) } },
                                label = { Text("${hours}h", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold.copy(alpha = 0.2f),
                                    selectedLabelColor = Gold,
                                    containerColor = SurfaceGlass,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Weather Configuration
            SettingSectionTitle("Weather")
            val weatherCity by appCache.weatherCity.collectAsState(initial = "Fetching...")
            val weatherTemp by appCache.weatherTemp.collectAsState(initial = 0)
            val weatherIcon by appCache.weatherIcon.collectAsState(initial = 0)

            GlassSettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Show current weather info (auto-fetched from Open-Meteo using device location)
                    val weatherEmoji = when (weatherIcon) {
                        0 -> "☁️"; 1 -> "🌫️"; 2 -> "🌥️"; 3 -> "🌧️"; 4 -> "❄️"; 5 -> "☀️"; 6 -> "🌪️"; 7 -> "🌫️"
                        else -> "🌤️"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(weatherEmoji, fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("${weatherTemp}°C", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                            Text(weatherCity, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Weather is automatically fetched using your device's location via Open-Meteo (free, no API key). Updated on every watch connect and when the watch requests it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { connectionManager.sendWeatherUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold.copy(alpha = 0.1f), contentColor = Gold)
                    ) {
                        Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Weather Now")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Automated Monitoring
            SettingSectionTitle("Monitoring & Sleep")
            val autoMeasureHr by appCache.autoMeasureHrEnabled.collectAsState(initial = false)
            val autoMeasureSpO2 by appCache.autoMeasureSpO2Enabled.collectAsState(initial = false)
            val autoMeasureBp by appCache.autoMeasureBpEnabled.collectAsState(initial = false)
            val autoMeasureStress by appCache.autoMeasureStressEnabled.collectAsState(initial = false)
            val autoMeasureHrInterval by appCache.autoMeasureHrInterval.collectAsState(initial = 60)
            val autoMeasureSpO2Interval by appCache.autoMeasureSpO2Interval.collectAsState(initial = 60)
            val autoMeasureBpInterval by appCache.autoMeasureBpInterval.collectAsState(initial = 60)
            val autoMeasureStressInterval by appCache.autoMeasureStressInterval.collectAsState(initial = 60)

            GlassSettingsCard {
                // Heart Rate Auto Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Heart Rate", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Switch(
                        checked = autoMeasureHr,
                        onCheckedChange = { scope.launch { appCache.setAutoMeasureConfig(metricType = "heart_rate", enabled = it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }
                
                // SpO2 Auto Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WaterDrop, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Blood Oxygen", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Switch(
                        checked = autoMeasureSpO2,
                        onCheckedChange = { scope.launch { appCache.setAutoMeasureConfig(metricType = "spo2", enabled = it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                // BP Auto Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MonitorHeart, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Blood Pressure", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Switch(
                        checked = autoMeasureBp,
                        onCheckedChange = { scope.launch { appCache.setAutoMeasureConfig(metricType = "blood_pressure", enabled = it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                // Stress Auto Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Psychology, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Stress Monitor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Switch(
                        checked = autoMeasureStress,
                        onCheckedChange = { scope.launch { appCache.setAutoMeasureConfig(metricType = "stress", enabled = it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Watch Button Action Option
                val watchButtonAction by appCache.watchButtonAction.collectAsState(initial = "Flashlight")
                val actionOptions = listOf("Flashlight", "Voice Recorder", "Mute/Unmute Phone")
                var showActionMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showActionMenu = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch Button Action", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Custom action for camera button", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Text(
                        text = watchButtonAction,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false }
                    ) {
                        actionOptions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action) },
                                onClick = {
                                    scope.launch { appCache.setWatchButtonAction(action) }
                                    showActionMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Watch Specific Settings
            SettingSectionTitle("Watch Settings")
            val watchFaceIndex by appCache.watchFaceIndex.collectAsState(initial = 1)
            val dndEnabled by appCache.dndEnabled.collectAsState(initial = false)
            val powerSavingEnabled by appCache.powerSavingEnabled.collectAsState(initial = false)
            val quickViewEnabled by appCache.quickViewEnabled.collectAsState(initial = true)

            GlassSettingsCard {
                // Watch Face
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Watch Face Index: $watchFaceIndex", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Slider(
                        value = watchFaceIndex.toFloat(),
                        onValueChange = { scope.launch { appCache.setWatchSettings(watchFace = it.toInt()) } },
                        onValueChangeFinished = { connectionManager.setWatchFace(watchFaceIndex) },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Quick View Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Visibility, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Raise to Wake (Quick View)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Turn screen on when raising wrist", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = quickViewEnabled,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(quickView = it) }
                            connectionManager.setQuickView(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }
                
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // DND Sync Toggle
                val syncDndWithPhone by appCache.syncDndWithPhone.collectAsState(initial = false)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sync, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync DND with Phone", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Mirror phone Do Not Disturb mode", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = syncDndWithPhone,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(syncDnd = it) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // DND Toggle with Schedule
                var dndStartHour by remember { mutableIntStateOf(22) }
                var dndStartMin by remember { mutableIntStateOf(0) }
                var dndEndHour by remember { mutableIntStateOf(8) }
                var dndEndMin by remember { mutableIntStateOf(0) }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DoNotDisturbOn, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Do Not Disturb", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("${dndStartHour}:${String.format("%02d", dndStartMin)} – ${dndEndHour}:${String.format("%02d", dndEndMin)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(dnd = it) }
                            connectionManager.setDnd(it, dndStartHour, dndStartMin, dndEndHour, dndEndMin)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                // DND Schedule sliders (only shown when DND is on)
                if (dndEnabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Text("Start: ${dndStartHour}:${String.format("%02d", dndStartMin)}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Slider(
                            value = (dndStartHour * 60 + dndStartMin).toFloat(),
                            onValueChange = { dndStartHour = (it / 60).toInt(); dndStartMin = (it % 60).toInt() },
                            onValueChangeFinished = { connectionManager.setDnd(true, dndStartHour, dndStartMin, dndEndHour, dndEndMin) },
                            valueRange = 0f..1439f,
                            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold.copy(alpha = 0.5f))
                        )
                        Text("End: ${dndEndHour}:${String.format("%02d", dndEndMin)}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Slider(
                            value = (dndEndHour * 60 + dndEndMin).toFloat(),
                            onValueChange = { dndEndHour = (it / 60).toInt(); dndEndMin = (it % 60).toInt() },
                            onValueChangeFinished = { connectionManager.setDnd(true, dndStartHour, dndStartMin, dndEndHour, dndEndMin) },
                            valueRange = 0f..1439f,
                            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold.copy(alpha = 0.5f))
                        )
                    }
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Power Saving Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BatterySaver, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Power Saving Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Conserve watch battery", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = powerSavingEnabled,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(powerSaving = it) }
                            connectionManager.setPowerSaving(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Goals & System
            SettingSectionTitle("Device Preferences")
            val is24Hour by appCache.is24Hour.collectAsState(initial = true)
            val isMetric by appCache.isMetric.collectAsState(initial = true)
            val goalSteps by appCache.goalSteps.collectAsState(initial = 8000)
            
            GlassSettingsCard {
                // Time Format Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("24-Hour Time Format", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text(if (is24Hour) "14:00" else "2:00 PM", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = is24Hour,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(is24HourSys = it) }
                            connectionManager.setTimeFormat(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Metric Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Straighten, null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Metric System", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text(if (isMetric) "Kilometers / Celsius" else "Miles / Fahrenheit", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = isMetric,
                        onCheckedChange = { 
                            scope.launch { appCache.setWatchSettings(isMetricSys = it) }
                            connectionManager.setMetricSystem(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(alpha = 0.3f))
                    )
                }
                
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                
                // Goal Steps Slider
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsWalk, null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Daily Step Goal: $goalSteps", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Slider(
                        value = goalSteps.toFloat(),
                        onValueChange = { scope.launch { appCache.setWatchSettings(goalStepsCount = it.toInt()) } },
                        onValueChangeFinished = { connectionManager.setGoalSteps(goalSteps) },
                        valueRange = 1000f..30000f,
                        steps = 28,
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.06f))

                // Calorie Burn Goal Slider
                val goalCalories by appCache.goalCalories.collectAsState(initial = 500)
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Daily Calorie Goal: $goalCalories kcal", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Slider(
                        value = goalCalories.toFloat(),
                        onValueChange = { scope.launch { appCache.setGoalCalories(it.toInt()) } },
                        onValueChangeFinished = { connectionManager.setGoalCalories(goalCalories) },
                        valueRange = 100f..3000f,
                        steps = 28,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f))
                    )
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // Privacy
            SettingSectionTitle("Privacy")
            GlassSettingsCard {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("100% Offline", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        Text("No internet permission • No cloud sync", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MicOff, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("No Microphone Access", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        Text("Mic permission explicitly blocked", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About
            SettingSectionTitle("About")
            GlassSettingsCard {
                SettingRow(Icons.Default.Info, "App", "NF Watch v1.0.0")
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                SettingRow(Icons.Default.Person, "Developer", "Pawan Washudev")
                HorizontalDivider(color = Gold.copy(alpha = 0.06f))
                SettingRow(Icons.Default.Storage, "Storage", "Health Connect")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Spacer(modifier = Modifier.height(24.dp))

            // Unpair
            if (pairedAddress != null) {
                OutlinedButton(
                    onClick = {
                        connectionManager.unpair()
                        scope.launch { appCache.clearPairedDevice() }
                        // Clear boot flag so service won't auto-start for unpaired device
                        context.getSharedPreferences("nf_watch_boot", android.content.Context.MODE_PRIVATE)
                            .edit().remove("paired_address").apply()
                        onDisconnect()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AccentRed.copy(alpha = 0.4f))
                    )
                ) {
                    Icon(Icons.Default.LinkOff, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unpair Watch", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Gold,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun GlassSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Gold.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = Gold.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
