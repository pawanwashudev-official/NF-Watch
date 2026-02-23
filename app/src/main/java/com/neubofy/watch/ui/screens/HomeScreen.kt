package com.neubofy.watch.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionManager: BleConnectionManager,
    appCache: AppCache,
    healthRepository: com.neubofy.watch.data.HealthRepository,
    onNavigateToDetail: (String) -> Unit
) {
    val connectionState by connectionManager.connectionState.collectAsState()
    val batteryLevel by connectionManager.batteryLevel.collectAsState()
    val liveHr by connectionManager.heartRate.collectAsState()
    val liveSteps by connectionManager.steps.collectAsState()
    val liveCals by connectionManager.calories.collectAsState()
    val liveDist by connectionManager.distanceMeters.collectAsState()
    val liveSpO2 by connectionManager.spO2.collectAsState()
    val liveBp by connectionManager.bloodPressure.collectAsState()
    val liveSleep by connectionManager.sleepMinutes.collectAsState()
    val liveStress by connectionManager.stress.collectAsState()
    val findPhoneRinging by connectionManager.findPhoneRinging.collectAsState()

    val cachedBattery by appCache.lastBatteryLevel.collectAsState(initial = -1)
    val cachedHr by appCache.lastHeartRate.collectAsState(initial = 0)
    val cachedSteps by appCache.lastSteps.collectAsState(initial = 0)
    val cachedCals by appCache.lastCalories.collectAsState(initial = 0)
    val cachedDist by appCache.lastDistance.collectAsState(initial = 0)
    val cachedSpO2 by appCache.lastSpO2.collectAsState(initial = 0)
    val cachedBp by appCache.lastBloodPressure.collectAsState(initial = null)
    val cachedSleep by appCache.lastSleepMinutes.collectAsState(initial = 0)
    val cachedStress by appCache.lastStress.collectAsState(initial = 0)
    val pairedName by appCache.pairedDeviceName.collectAsState(initial = null)

    val dbHr by healthRepository.getLatestRecord("heart_rate").collectAsState(initial = null)
    val dbSteps by healthRepository.getLatestRecord("steps").collectAsState(initial = null)
    val dbCals by healthRepository.getLatestRecord("calories").collectAsState(initial = null)
    val dbDist by healthRepository.getLatestRecord("distance").collectAsState(initial = null)
    val dbSpO2 by healthRepository.getLatestRecord("spo2").collectAsState(initial = null)
    val dbBp by healthRepository.getLatestRecord("blood_pressure").collectAsState(initial = null)
    val dbSleep by healthRepository.getLatestRecord("sleep").collectAsState(initial = null)
    val dbStress by healthRepository.getLatestRecord("stress").collectAsState(initial = null)

    // HEALTH CONNECT DATA (Source of Truth)
    val context = androidx.compose.ui.platform.LocalContext.current
    var hcData by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val healthConnectManager = remember { com.neubofy.watch.data.HealthConnectManager(context) }

    fun refreshHealthConnect() {
        scope.launch {
            hcData = healthConnectManager.readTodayLatest()
        }
    }

    // Refresh on resume or periodically
    LaunchedEffect(Unit) {
        refreshHealthConnect()
    }
    
    // Also re-fetch HC data whenever live values change (to reflect the write that just happened)
    LaunchedEffect(liveHr, liveSteps, liveSleep, liveSpO2, liveBp, liveStress) {
        refreshHealthConnect()
    }

    val heartRate = if (liveHr > 0) liveHr 
                   else (hcData["heart_rate"] as? Int ?: dbHr?.value?.toInt() ?: cachedHr)
    
    val steps = if (liveSteps > 0) liveSteps 
                else (hcData["steps"] as? Int ?: dbSteps?.value?.toInt() ?: cachedSteps)
    
    val calories = if (liveCals > 0) liveCals 
                   else (hcData["calories"] as? Int ?: dbCals?.value?.toInt() ?: cachedCals)
    
    val distanceMeters = if (liveDist > 0) liveDist 
                         else (hcData["distance"] as? Int ?: dbDist?.value?.toInt() ?: cachedDist)
    
    val spO2 = if (liveSpO2 > 0) liveSpO2 
               else (hcData["spo2"] as? Int ?: dbSpO2?.value?.toInt() ?: cachedSpO2)
    
    val rawBp = if (liveBp != null) liveBp 
                else (hcData["blood_pressure"] as? String ?: dbBp?.let { "${it.systolic}/${it.diastolic}" } ?: cachedBp)
    val bloodPressure = if (rawBp?.contains("255") == true) null else rawBp
    
    val sleepMinutes = if (liveSleep > 0) liveSleep 
                      else (hcData["sleep"] as? Int ?: dbSleep?.value?.toInt() ?: cachedSleep)
    
    val stress = if (liveStress > 0) liveStress 
                 else (hcData["stress"] as? Int ?: dbStress?.value?.toInt() ?: cachedStress)

    val displayBattery = batteryLevel ?: if (cachedBattery >= 0) cachedBattery else null
    val isConnected = connectionState == ConnectionState.CONNECTED

    val sleepHours = sleepMinutes / 60
    val sleepMins = sleepMinutes % 60
    val sleepText = if (sleepMinutes > 0) "${sleepHours}h ${sleepMins}m" else "--"

    val stressLabel = when {
        stress in 1..25 -> "Relaxed"
        stress in 26..50 -> "Normal"
        stress in 51..75 -> "Medium"
        stress in 76..100 -> "High"
        else -> "Stress"
    }

    // Pulse animation for connection dot
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Pull-to-refresh handler
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            connectionManager.syncAllData()
            kotlinx.coroutines.delay(2000)
            isRefreshing = false
        }
    }

    var hasRequiredPermissions by remember { mutableStateOf(com.neubofy.watch.ble.BleScanner.hasPermissions(context)) }
    
    // Periodically re-check or check on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasRequiredPermissions = com.neubofy.watch.ble.BleScanner.hasPermissions(context)
                refreshHealthConnect()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
        // ═══ Header ═══
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "NF Watch",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Gold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Color(0xFF4CAF50).copy(alpha = glowAlpha)
                                else Color(0xFF666666)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        pairedName ?: "No watch paired",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (displayBattery != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceCard,
                        modifier = Modifier.border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BatteryFull, null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$displayBattery%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═══ Permission Alert ═══
        if (!hasRequiredPermissions) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onNavigateToDetail("permissions_redirect") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Gold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permissions Missing", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Tap to fix and enable watch connection", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Gold)
                }
            }
        }

        // ═══ SOS Find Phone Alert ═══
        AnimatedVisibility(visible = findPhoneRinging, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
            val infiniteSOS = rememberInfiniteTransition(label = "sos")
            val pulseGlow by infiniteSOS.animateFloat(
                initialValue = 0.1f, targetValue = 0.4f,
                animationSpec = infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "pulse"
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = pulseGlow))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth().border(2.dp, AccentRed, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SOS ACTIVE", color = AccentRed, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("USE WATCH TO STOP ALARM", color = AccentRed.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(32.dp))
                }
            }
        }

        // ═══ Connection Card ═══
        if (!isConnected) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WatchOff, null, tint = TextMuted, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Disconnected", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Tap to reconnect", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    IconButton(onClick = { connectionManager.reconnect() }) {
                        Icon(Icons.Default.Refresh, null, tint = Gold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }



        // ═══════════════════════════════════════════════════════
        // ═══ ACTIVITY HERO CARD (from Health page) ═══
        // ═══════════════════════════════════════════════════════
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(220.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { onNavigateToDetail("steps") }
            ) {
                // Background artistic blob
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-20).dp)
                        .size(200.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                        .blur(50.dp)
                )

                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Activity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${if (steps > 0) steps else "--"}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-1.5).sp
                            )
                            Text("STEPS TODAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (steps / 10000f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(90.dp),
                                strokeWidth = 10.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round
                            )
                            Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStatItem(Icons.Default.LocalFireDepartment, if (calories > 0) "$calories" else "--", "kcal", Color(0xFFFF9800))
                        MiniStatItem(Icons.Default.Straighten, if (distanceMeters > 0) "%.1f".format(distanceMeters / 1000f) else "--", "km", Color(0xFF00C853))
                        MiniStatItem(Icons.Default.WaterDrop, if (spO2 > 0) "$spO2%" else "--%", "SpO2", Color(0xFF00B0FF))
                    }
                }
            }
        }

        // ═══ SECTION: Vitals ═══
        SectionLabel("Vitals")

        // ═══ Grid Stats ═══
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToDetail("heart_rate") },
                icon = Icons.Default.Favorite,
                value = if (heartRate > 0) "$heartRate" else "--",
                unit = "bpm",
                label = "Heart Rate",
                accent = Color(0xFFFF4B4B)
            )
            HeroStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToDetail("blood_pressure") },
                icon = Icons.Default.MonitorHeart,
                value = bloodPressure ?: "--",
                unit = "mmHg",
                label = "Blood Pressure",
                accent = Color(0xFF00BFA5)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToDetail("spo2") },
                icon = Icons.Default.WaterDrop,
                value = if (spO2 > 0) "$spO2" else "--",
                unit = "%",
                label = "SpO2",
                accent = Color(0xFF00B0FF)
            )
            HeroStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToDetail("stress") },
                icon = Icons.Default.Psychology,
                value = if (stress > 0) "$stress" else "--",
                unit = "",
                label = stressLabel,
                accent = Color(0xFFE91E63)
            )
        }



        // ═══ SECTION: Sleep Analysis (from Health page) ═══
        SectionLabel("Sleep")

        val derivedDeep = dbSleep?.systolic ?: 0
        val derivedLight = dbSleep?.diastolic ?: 0
        val derivedAwake = (dbSleep?.value?.toInt() ?: 0) - derivedDeep - derivedLight

        SleepAnalysisCard(
            totalMinutes = sleepMinutes,
            deepMinutes = derivedDeep,
            lightMinutes = derivedLight,
            awakeMinutes = derivedAwake,
            onClick = { onNavigateToDetail("sleep") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ═══ Privacy badge ═══
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, tint = Gold.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "100% Offline • No data leaves your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pull to refresh hint
        if (isConnected) {
            Text(
                "↓ Pull down to sync",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
        } // Column
    } // Box
}

// ═══ Section Label ═══
@Composable
fun SectionLabel(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
    }
}

// ═══ Mini Stat Item (for Activity Hero) ═══
@Composable
fun MiniStatItem(icon: ImageVector, value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══ Glassmorphism Card ═══
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .border(1.dp, Gold.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
    ) {
        content()
    }
}

// ═══ Hero Stat Card ═══
@Composable
fun HeroStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    accent: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow")
    val blobOffset by infiniteTransition.animateFloat(
        initialValue = -15f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "blob"
    )

    Surface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
        ) {
            // Animated ambient blob in the top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = blobOffset.dp)
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))
                    )
                    .blur(20.dp)
            )

            // Soft glowing bottom line
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.1f)))
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accent.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    if (unit.isNotEmpty()) {
                        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                
                Column {
                    Text(
                        value,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        label.uppercase(),
                        fontSize = 11.sp,
                        color = accent.copy(alpha = 0.9f),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ═══ Sleep Analysis Card ═══
@Composable
fun SleepAnalysisCard(
    totalMinutes: Int,
    deepMinutes: Int,
    lightMinutes: Int,
    awakeMinutes: Int,
    onClick: () -> Unit
) {
    val totalValid = deepMinutes + lightMinutes + awakeMinutes
    val dMins = if (totalValid > 0) deepMinutes else (totalMinutes * 0.4).toInt()
    val lMins = if (totalValid > 0) lightMinutes else (totalMinutes * 0.55).toInt()
    val aMins = if (totalValid > 0) awakeMinutes else (totalMinutes * 0.05).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color(0xFF651FFF).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bedtime, null, tint = Color(0xFF651FFF), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sleep Analysis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (totalMinutes > 0) "${totalMinutes / 60}h ${totalMinutes % 60}m" else "--",
                        fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (totalMinutes > 0) {
                    // Sleep progress bar
                    Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                        Box(modifier = Modifier.weight((dMins.toFloat() / totalMinutes).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF3F51B5)))
                        Box(modifier = Modifier.weight((lMins.toFloat() / totalMinutes).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF03A9F4)))
                        Box(modifier = Modifier.weight((aMins.toFloat() / totalMinutes).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFFF9800)))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        SleepDot("Deep", "${dMins / 60}h ${dMins % 60}m", Color(0xFF3F51B5))
                        SleepDot("Light", "${lMins / 60}h ${lMins % 60}m", Color(0xFF03A9F4))
                        SleepDot("Awake", "${aMins / 60}h ${aMins % 60}m", Color(0xFFFF9800))
                    }
                } else {
                    Text("No sleep data recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SleepDot(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
