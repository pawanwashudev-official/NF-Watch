package com.neubofy.watch.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.DisplayRecord
import com.neubofy.watch.data.HealthConnectManager
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ChartType { LINE, BAR }
enum class TimeRange(val days: Long, val label: String) {
    DAY(1, "Day"), WEEK(7, "Week"), MONTH(30, "Month")
}



data class PeriodAggregation(
    val label: String, // "00:00" for hours, "Mon 22" for days
    val sortKey: Long, // timestamp for sorting
    val avg: Float,
    val min: Float,
    val max: Float,
    val avg2: Float? = null,
    val min2: Float? = null,
    val max2: Float? = null,
    val count: Int
)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDetailScreen(
    metricType: String,
    healthConnectManager: HealthConnectManager,
    connectionManager: BleConnectionManager,
    healthRepository: com.neubofy.watch.data.HealthRepository,
    appCache: com.neubofy.watch.data.AppCache,
    onBack: () -> Unit
) {
    var records by remember { mutableStateOf<List<DisplayRecord>>(emptyList()) }
    var rawRecords by remember { mutableStateOf<List<com.neubofy.watch.data.db.HealthRecord>>(emptyList()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTimeRange by remember { mutableStateOf(TimeRange.DAY) }
    var isLoading by remember { mutableStateOf(true) }
    
    var showDeleteDialog by remember { mutableStateOf<com.neubofy.watch.data.db.HealthRecord?>(null) }
    var deleteFromHealthConnect by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isConnected by connectionManager.connectionState.collectAsState()

    // --- Health Connect permission check (on-demand) ---
    var hasHcPermission by remember { mutableStateOf(true) } // assume true initially
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        HealthConnectManager.createPermissionRequestContract()
    ) { granted ->
        hasHcPermission = granted.containsAll(HealthConnectManager.PERMISSIONS)
    }
    LaunchedEffect(Unit) {
        if (healthConnectManager.isAvailable()) {
            hasHcPermission = healthConnectManager.hasAllPermissions()
        }
    }

    val heartRate by connectionManager.heartRate.collectAsState(initial = 0)
    val spO2 by connectionManager.spO2.collectAsState(initial = 0)
    val bloodPressure by connectionManager.bloodPressure.collectAsState(initial = null)
    val stress by connectionManager.stress.collectAsState(initial = 0)
    val steps by connectionManager.steps.collectAsState(initial = 0)
    val calories by connectionManager.calories.collectAsState(initial = 0)
    val distanceMeters by connectionManager.distanceMeters.collectAsState(initial = 0)
    val measuringType by connectionManager.measuringType.collectAsState(initial = null)
    val isMeasuring = measuringType == metricType

    val isMeasureType = metricType in listOf("heart_rate", "spo2", "blood_pressure", "stress")

    val title = when (metricType) {
        "heart_rate" -> "Heart Rate"
        "steps" -> "Steps"
        "calories" -> "Calories"
        "distance" -> "Distance"
        "sleep" -> "Sleep"
        "spo2" -> "Blood Oxygen"
        "blood_pressure" -> "Blood Pressure"
        "stress" -> "Stress"
        else -> "Details"
    }

    val accentColor = when (metricType) {
        "heart_rate" -> Color(0xFFFF6B6B)
        "steps" -> Color(0xFF6C63FF)
        "calories" -> Color(0xFFFFB300)
        "distance" -> Color(0xFF4CAF50)
        "sleep" -> Color(0xFF3F51B5)
        "spo2" -> Color(0xFF00BCD4)
        "blood_pressure" -> Color(0xFF009688)
        "stress" -> Color(0xFF9C27B0)
        else -> Color(0xFF6C63FF)
    }

    val icon = when (metricType) {
        "heart_rate" -> Icons.Default.Favorite
        "steps" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "calories" -> Icons.Default.LocalFireDepartment
        "distance" -> Icons.Default.Straighten
        "sleep" -> Icons.Default.Bedtime
        "spo2" -> Icons.Default.WaterDrop
        "blood_pressure" -> Icons.Default.MonitorHeart
        "stress" -> Icons.Default.Psychology
        else -> Icons.Default.Info
    }

    val unit = when (metricType) {
        "heart_rate" -> "bpm"
        "steps" -> "steps"
        "calories" -> "kcal"
        "distance" -> "km"
        "sleep" -> ""
        "spo2" -> "%"
        "blood_pressure" -> "mmHg"
        "stress" -> ""
        else -> ""
    }

    // Current live value from watch summary
    val liveValue = when (metricType) {
        "heart_rate" -> if (heartRate > 0) "$heartRate" else null
        "steps" -> if (steps > 0) "$steps" else null
        "calories" -> if (calories > 0) "$calories" else null
        "distance" -> if (distanceMeters > 0) "%.1f".format(distanceMeters / 1000.0) else null
        "spo2" -> if (spO2 > 0) "$spO2" else null
        "blood_pressure" -> if (bloodPressure?.contains("255") == true) null else bloodPressure
        "stress" -> if (stress > 0) "$stress" else null
        else -> null
    }

    // Computed value for the main hero card based on selected date
    val displayValue = remember(selectedDate, selectedTimeRange, liveValue, rawRecords, metricType) {
        val isToday = selectedDate == java.time.LocalDate.now() && selectedTimeRange == TimeRange.DAY
        
        if (isToday && liveValue != null) {
            liveValue
        } else if (rawRecords.isEmpty()) {
            "--"
        } else {
            when (metricType) {
                "steps", "calories" -> {
                    val sum = rawRecords.sumOf { it.value }.toLong()
                    if (sum > 0) "$sum" else "0"
                }
                "distance" -> {
                    val sumMeters = rawRecords.sumOf { it.value }
                    if (sumMeters > 0) "%.2f".format(sumMeters / 1000.0) else "0.00"
                }
                "heart_rate", "spo2", "stress" -> {
                    val validValues = rawRecords.map { it.value }.filter { it > 0 }
                    if (validValues.isEmpty()) "--" 
                    else validValues.average().toInt().toString()
                }
                "blood_pressure" -> {
                    if (selectedTimeRange == TimeRange.DAY) {
                        rawRecords.lastOrNull()?.let { 
                            if (it.systolic != null && it.diastolic != null) "${it.systolic}/${it.diastolic}" else "--"
                        } ?: "--"
                    } else {
                        val validSys = rawRecords.mapNotNull { it.systolic }.filter { it > 0 }
                        val validDia = rawRecords.mapNotNull { it.diastolic }.filter { it > 0 }
                        if (validSys.isEmpty() || validDia.isEmpty()) "--"
                        else "${validSys.average().toInt()}/${validDia.average().toInt()}"
                    }
                }
                "sleep" -> {
                    val totalMins = rawRecords.sumOf { it.value }.toInt()
                    if (totalMins > 0) {
                        val hrs = totalMins / 60
                        val mins = totalMins % 60
                        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    } else "--"
                }
                else -> rawRecords.lastOrNull()?.value?.toInt()?.toString() ?: "--"
            }
        }
    }

    val displayLabel = when {
        selectedTimeRange == TimeRange.WEEK -> "Weekly Total/Avg"
        selectedTimeRange == TimeRange.MONTH -> "Monthly Total/Avg"
        selectedDate != java.time.LocalDate.now() -> "Day Summary"
        metricType in listOf("steps", "calories", "distance", "sleep") -> "Today's Total"
        else -> "Latest Reading"
    }

    // Load data from Health Connect (primary source)
    LaunchedEffect(metricType, selectedDate, selectedTimeRange) {
        isLoading = true
        // Chart still shows 30 days for context
        healthRepository.readHealthConnectHistory(metricType, 30) { hcRecords ->
            records = hcRecords
        }
        
        // Logs show only selected date/range (On-Demand)
        if (metricType != "stress") {
            rawRecords = when (selectedTimeRange) {
                TimeRange.DAY -> healthRepository.readRawHealthConnectHistoryForDay(metricType, selectedDate)
                TimeRange.WEEK -> healthRepository.readRawHealthConnectHistoryForDateRange(metricType, selectedDate.minusDays(6), selectedDate)
                TimeRange.MONTH -> healthRepository.readRawHealthConnectHistoryForDateRange(metricType, selectedDate.withDayOfMonth(1), selectedDate.withDayOfMonth(selectedDate.lengthOfMonth()))
            }
        }
        isLoading = false
    }

    // fallback for stress or quick local updates
    val dbRecords by healthRepository.getRecords(metricType).collectAsState(initial = emptyList())
    LaunchedEffect(dbRecords, metricType, selectedDate, selectedTimeRange) {
        if (metricType == "stress") {
            rawRecords = dbRecords.filter { 
                val recDate = java.time.Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                when (selectedTimeRange) {
                    TimeRange.DAY -> recDate == selectedDate
                    TimeRange.WEEK -> !recDate.isBefore(selectedDate.minusDays(6)) && !recDate.isAfter(selectedDate)
                    TimeRange.MONTH -> recDate.month == selectedDate.month && recDate.year == selectedDate.year
                }
            }
        }
    }

    val groupedRecords = remember(rawRecords, selectedTimeRange) {
        rawRecords.groupBy {
            val date = java.time.Instant.ofEpochMilli(it.timestamp)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern(
                when (selectedTimeRange) {
                    TimeRange.DAY -> "HH:mm"
                    TimeRange.WEEK -> "EEE, MMM dd"
                    TimeRange.MONTH -> "MMM dd"
                }
            )
            java.time.Instant.ofEpochMilli(it.timestamp)
                .atZone(java.time.ZoneId.systemDefault()).format(timeFormatter)
        }
    }

    if (showDeleteDialog != null) {
        val recordToDelete = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Data") },
            text = {
                Column {
                    Text("Are you sure you want to delete this $title record?", style = MaterialTheme.typography.bodyLarge)
                    if (recordToDelete.isSyncedToHealthConnect) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteFromHealthConnect,
                                onCheckedChange = { deleteFromHealthConnect = it }
                            )
                            Text("Also un-sync and delete from Health Connect", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This record is local to the App DB only.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            healthRepository.deleteHealthRecord(recordToDelete, deleteFromHealthConnect)
                            // Remove from local memory state without full HC fetch to make it feel snappy
                            val updatedRecords = rawRecords.toMutableList()
                            updatedRecords.remove(recordToDelete)
                            rawRecords = updatedRecords
                            
                            showDeleteDialog = null
                            deleteFromHealthConnect = false
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        scope.launch { connectionManager.syncAllData() }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Data")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Current Value Hero Card
            item {
                CurrentValueCard(
                    value = displayValue,
                    unit = unit,
                    label = displayLabel,
                    icon = icon,
                    accentColor = accentColor,
                    isMeasuring = isMeasuring,
                    canMeasure = isMeasureType && isConnected == ConnectionState.CONNECTED,
                    onStartMeasure = { connectionManager.startMeasurement(metricType) },
                    onStopMeasure = { connectionManager.stopMeasurement(metricType) }
                )
            }

            // --- Health Connect Permission Banner ---
            if (!hasHcPermission && healthConnectManager.isAvailable() && metricType != "stress") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HealthAndSafety, null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Health Connect Access Required", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800), fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Grant Health Connect permissions to view your $title history and enable data storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBBAAAA)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { hcPermissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.Black)
                            ) {
                                Text("Grant Permission", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // --- DAY NAVIGATOR ---
            item {
                val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy") }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { 
                                selectedDate = when (selectedTimeRange) {
                                    TimeRange.DAY -> selectedDate.minusDays(1)
                                    TimeRange.WEEK -> selectedDate.minusWeeks(1)
                                    TimeRange.MONTH -> selectedDate.minusMonths(1)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, null, modifier = Modifier.size(18.dp))
                            }
                            
                            val dateText = when (selectedTimeRange) {
                                TimeRange.DAY -> if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(dateFormatter)
                                TimeRange.WEEK -> "${selectedDate.minusDays(6).format(DateTimeFormatter.ofPattern("MMM dd"))} - ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd"))}"
                                TimeRange.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            }
                            
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { 
                                    selectedDate = when (selectedTimeRange) {
                                        TimeRange.DAY -> selectedDate.plusDays(1)
                                        TimeRange.WEEK -> selectedDate.plusWeeks(1)
                                        TimeRange.MONTH -> selectedDate.plusMonths(1)
                                    }
                                },
                                enabled = selectedDate.isBefore(LocalDate.now())
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Time Range Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TimeRange.entries.forEach { range ->
                                val isSelected = selectedTimeRange == range
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable { selectedTimeRange = range }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        range.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Auto-Monitoring Settings (only for measure types) ──
            if (isMeasureType) {
                item {
                    val scope = rememberCoroutineScope()
                    val isAutoEnabled = when (metricType) {
                        "heart_rate" -> appCache.autoMeasureHrEnabled.collectAsState(initial = false).value
                        "spo2" -> appCache.autoMeasureSpO2Enabled.collectAsState(initial = false).value
                        "blood_pressure" -> appCache.autoMeasureBpEnabled.collectAsState(initial = false).value
                        "stress" -> appCache.autoMeasureStressEnabled.collectAsState(initial = false).value
                        else -> false
                    }
                    val interval = when (metricType) {
                        "heart_rate" -> appCache.autoMeasureHrInterval.collectAsState(initial = 60).value
                        "spo2" -> appCache.autoMeasureSpO2Interval.collectAsState(initial = 60).value
                        "blood_pressure" -> appCache.autoMeasureBpInterval.collectAsState(initial = 60).value
                        "stress" -> appCache.autoMeasureStressInterval.collectAsState(initial = 60).value
                        else -> 60
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(20.dp), tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Auto Monitoring",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Analyze $title throughout the day",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isAutoEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            appCache.setAutoMeasureConfig(metricType = metricType, enabled = checked)
                                            if (metricType == "heart_rate") {
                                                connectionManager.setHeartRateInterval(if (checked) interval else 0)
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                                )
                            }

                            if (isAutoEnabled) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                )
                                Text(
                                    "Frequency Interval",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val intervals = if (metricType == "heart_rate") listOf(5, 10, 20, 30, 60) else listOf(15, 30, 60, 120, 240)
                                    intervals.forEach { mins ->
                                        val isSelected = interval == mins
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surface)
                                                .clickable {
                                                    scope.launch {
                                                        appCache.setAutoMeasureConfig(metricType = metricType, intervalMins = mins)
                                                        if (metricType == "heart_rate") connectionManager.setHeartRateInterval(mins)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${mins}m",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Chart Card overhaul ──
            item {
                // Hourly breakdown for the selected day from top navigator
                // Improved Hourly breakdown: fill empty hours for a better visual graph
                // Aggregation for the selected period (Day/Week/Month)
                val periodAggregations = remember(rawRecords, selectedDate, selectedTimeRange, metricType) {
                    val zone = ZoneId.systemDefault()
                    val grouped = when (selectedTimeRange) {
                        TimeRange.DAY -> {
                            rawRecords.groupBy { rec ->
                                java.time.Instant.ofEpochMilli(rec.timestamp).atZone(zone).hour
                            }
                        }
                        else -> {
                            rawRecords.groupBy { rec ->
                                java.time.Instant.ofEpochMilli(rec.timestamp).atZone(zone).toLocalDate()
                            }
                        }
                    }

                    val items = mutableListOf<PeriodAggregation>()
                    
                    if (selectedTimeRange == TimeRange.DAY) {
                        (0..23).forEach { hour ->
                            val hourRecords = (grouped[hour] as? List<com.neubofy.watch.data.db.HealthRecord>) ?: emptyList()
                            items.add(calculateAggregation(hour.toString(), hour.toLong(), hourRecords, metricType))
                        }
                    } else {
                        val days = selectedTimeRange.days
                        for (i in (days - 1) downTo 0) {
                            val date = selectedDate.minusDays(i)
                            val dateRecords = (grouped[date] as? List<com.neubofy.watch.data.db.HealthRecord>) ?: emptyList()
                            val label = if (selectedTimeRange == TimeRange.WEEK) 
                                date.format(DateTimeFormatter.ofPattern("EEE dd"))
                            else 
                                date.dayOfMonth.toString()
                            
                            items.add(calculateAggregation(label, date.atStartOfDay(zone).toInstant().toEpochMilli(), dateRecords, metricType))
                        }
                    }
                    items
                }

                Card(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (metricType == "steps") "Hourly Steps" else "Hourly Breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (selectedDate == LocalDate.now()) "Today" 
                                else selectedDate.format(DateTimeFormatter.ofPattern("MMM dd")),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                            }
                        } else if (metricType == "sleep") {
                            val validRecords = rawRecords.filter { it.value > 0 }
                            if (validRecords.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No sleep data for this day", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    validRecords.sortedByDescending { it.timestamp }.forEach { record ->
                                        SleepTimelineChart(record)
                                    }
                                }
                            }
                        } else if (periodAggregations.all { it.count == 0 }) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No data for this period", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        } else {
                            val data = periodAggregations
                            when (metricType) {
                                "heart_rate", "steps", "stress", "spo2" -> PeriodAreaChart(data, accentColor)
                                "blood_pressure" -> PeriodRangeChart(data, accentColor)
                                else -> PeriodBarChart(data, accentColor)
                            }
                        }
                    }
                }
            }

            // Samsung Health Style Analytics Highlights
            if (rawRecords.isNotEmpty() && metricType != "sleep" && metricType != "blood_pressure") {
                item {
                    val floatValues = rawRecords.map { it.value.toFloat() }.filter { it > 0 }
                    if (floatValues.isNotEmpty()) {
                        val min = floatValues.minOrNull() ?: 0f
                        val max = floatValues.maxOrNull() ?: 0f
                        val avg = floatValues.average().toFloat()
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Minimum", "%.0f".format(min), unit, accentColor, Modifier.weight(1f))
                            StatCard("Average", "%.0f".format(avg), unit, accentColor, Modifier.weight(1f))
                            StatCard("Maximum", "%.0f".format(max), unit, accentColor, Modifier.weight(1f))
                        }
                    }
                }
            } else if (rawRecords.isNotEmpty() && metricType == "blood_pressure") {
                item {
                    val validRecords = rawRecords.filter { it.systolic != null && it.diastolic != null && it.systolic!! > 0 }
                    if (validRecords.isNotEmpty()) {
                        val avgSys = validRecords.map { it.systolic!! }.average()
                        val avgDia = validRecords.map { it.diastolic!! }.average()
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Avg Systolic", "%.0f".format(avgSys), "mmHg", accentColor, Modifier.weight(1f))
                            StatCard("Avg Diastolic", "%.0f".format(avgDia), "mmHg", accentColor, Modifier.weight(1f))
                        }
                    }
                }
            } else if (rawRecords.isNotEmpty() && metricType == "sleep") {
                item {
                    val latest = rawRecords.maxByOrNull { it.timestamp }
                    if (latest != null) {
                        val deepMins = latest.systolic ?: 0
                        val lightMins = latest.diastolic ?: 0
                        val awakeMins = (latest.value.toInt()) - deepMins - lightMins
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Deep", "${deepMins/60}h ${deepMins%60}m", "", Color(0xFF3F51B5), Modifier.weight(1f))
                            StatCard("Light", "${lightMins/60}h ${lightMins%60}m", "", Color(0xFF03A9F4), Modifier.weight(1f))
                            if (awakeMins > 0) StatCard("Awake", "${awakeMins/60}h ${awakeMins%60}m", "", Color(0xFFFF9800), Modifier.weight(1f))
                        }
                    }
                }
            }

            // List of Records Grouped (by Time for the selected day)
            groupedRecords.forEach { (timeStr, itemsAtTime) ->
                itemsAtTime.forEachIndexed { index, record ->
                    item(key = if (record.id != 0L) record.id else "hc_${record.timestamp}_$index") {
                        HistoryRow(
                            record = record,
                            unit = unit,
                            showDate = selectedTimeRange != TimeRange.DAY,
                            onDeleteClick = {
                                deleteFromHealthConnect = record.isSyncedToHealthConnect
                                showDeleteDialog = record
                            }
                        )
                    }
                }
            }
            
            if (rawRecords.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No records found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentValueCard(
    value: String,
    unit: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    isMeasuring: Boolean,
    canMeasure: Boolean,
    onStartMeasure: () -> Unit,
    onStopMeasure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.isNotEmpty()) {
                    Text(
                        " $unit",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (canMeasure) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = if (isMeasuring) onStopMeasure else onStartMeasure,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMeasuring) MaterialTheme.colorScheme.error else accentColor
                    )
                ) {
                    if (isMeasuring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stop Measuring")
                    } else {
                        Text("Start Measurement")
                    }
                }
            }
        }
    }
}




@Composable
fun HealthChart(records: List<DisplayRecord>, accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)) {
        if (records.size < 2) return@Canvas

        val hasSecondLine = records.any { it.numericValue2 != null }
        
        val max1 = records.maxOf { it.numericValue }.coerceAtLeast(1f)
        val min1 = records.minOf { it.numericValue }
        
        val max2 = if (hasSecondLine) records.maxOf { it.numericValue2 ?: 0f } else 0f
        val min2 = if (hasSecondLine) records.minOf { it.numericValue2 ?: it.numericValue } else 0f

        val maxVal = maxOf(max1, max2)
        val minVal = minOf(min1, min2)
        val range = (maxVal - minVal).coerceAtLeast(10f) * 1.2f // Add 20% vertical padding

        val width = size.width
        val height = size.height

        val stepX = width / (records.size - 1)

        fun getPoints(valueSelector: (DisplayRecord) -> Float): List<Offset> {
            return records.mapIndexed { index, record ->
                Offset(
                    x = index * stepX,
                    y = height - ((valueSelector(record) - minVal) / range) * height
                )
            }
        }

        val points1 = getPoints { it.numericValue }
        val points2 = if (hasSecondLine) getPoints { it.numericValue2 ?: 0f } else emptyList()

        // Draw horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = i * (height / gridLines)
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSmoothLine(points: List<Offset>, color: Color) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val cp1x = (p1.x + p2.x) / 2
                    val cp1y = p1.y
                    val cp2x = (p1.x + p2.x) / 2
                    val cp2y = p2.y
                    cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
            }

            // Area gradient
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                    startY = points.minOf { it.y },
                    endY = height
                )
            )

            // Line
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Data Points
            points.forEach { pt ->
                drawCircle(color = color, radius = 4.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
            }
        }

        if (hasSecondLine && points2.size == points1.size) {
            val diaColor = Color(0xFF03A9F4)
            for (i in points1.indices) {
                val pSys = points1[i]
                val pDia = points2[i]
                drawLine(
                    brush = Brush.verticalGradient(listOf(accentColor, diaColor)),
                    start = pSys,
                    end = pDia,
                    strokeWidth = 20.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = pSys)
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = pDia)
            }
        } else {
            drawSmoothLine(points1, accentColor)
        }
    }
}



@Composable
fun PeriodBarChart(periodAggregations: List<PeriodAggregation>, accentColor: Color) {
    if (periodAggregations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data", color = Color.Gray)
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
        val maxVal = periodAggregations.maxOf { it.max }.coerceAtLeast(1f) * 1.15f
        val barCount = periodAggregations.size
        val gap = 3.dp.toPx()
        val labelAreaHeight = 16.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val barWidth = ((size.width - gap * (barCount - 1).coerceAtLeast(1)) / barCount.coerceAtLeast(1))
            .coerceIn(2.dp.toPx(), 24.dp.toPx())
        val totalBarsWidth = barWidth * barCount + gap * (barCount - 1)
        val startX = (size.width - totalBarsWidth) / 2f

        // Grid lines
        for (i in 0..3) {
            val y = chartHeight * i / 4f
            drawLine(
                color = Color.Gray.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        periodAggregations.forEachIndexed { index, agg ->
            val x = startX + index * (barWidth + gap)
            val barHeight = (agg.avg / maxVal) * chartHeight
            val topY = chartHeight - barHeight

            if (agg.count > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accentColor, accentColor.copy(alpha = 0.3f)),
                        startY = topY,
                        endY = chartHeight
                    ),
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight.coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Labeling
            val labelInterval = when {
                barCount <= 7 -> 1
                barCount <= 24 -> 3
                barCount <= 48 -> 6
                else -> 7
            }
            
            if (index % labelInterval == 0) {
                val labelText = if (barCount == 24) {
                    when {
                        index == 0 -> "12A"
                        index < 12 -> "${index}A"
                        index == 12 -> "12P"
                        else -> "${index-12}P"
                    }
                } else agg.label

                val textLayout = textMeasurer.measure(
                    labelText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 8.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        x + barWidth / 2 - textLayout.size.width / 2,
                        chartHeight + 2.dp.toPx()
                    )
                )
            }
        }
    }
}

@Composable
fun PeriodAreaChart(periodAggregations: List<PeriodAggregation>, accentColor: Color) {
    if (periodAggregations.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
        val maxVal = periodAggregations.maxOf { it.max }.coerceAtLeast(1f) * 1.15f
        val barCount = periodAggregations.size
        val labelAreaHeight = 16.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        
        // Grid lines
        for (i in 0..3) {
            val y = chartHeight * i / 4f
            drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
        }
        
        val points = periodAggregations.mapIndexed { index, agg ->
            val x = (size.width / (barCount - 1).coerceAtLeast(1)) * index
            val y = chartHeight - (agg.avg / maxVal) * chartHeight
            Offset(x, y)
        }
        
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    cubicTo((p1.x + p2.x) / 2, p1.y, (p1.x + p2.x) / 2, p2.y, p2.x, p2.y)
                }
            }
            
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, chartHeight)
                lineTo(points.first().x, chartHeight)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.4f), Color.Transparent),
                    startY = points.minOf { it.y }, endY = chartHeight
                )
            )
            drawPath(path = path, color = accentColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }

        // Labeling
        val labelInterval = when {
            barCount <= 7 -> 1
            barCount <= 24 -> 3
            barCount <= 48 -> 6
            else -> 7
        }
        
        periodAggregations.forEachIndexed { index, agg ->
            if (index % labelInterval == 0) {
                val labelText = if (barCount == 24) {
                    when {
                        index == 0 -> "12A"
                        index < 12 -> "${index}A"
                        index == 12 -> "12P"
                        else -> "${index-12}P"
                    }
                } else agg.label

                val textLayout = textMeasurer.measure(
                    labelText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 8.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        (size.width / (barCount - 1).coerceAtLeast(1)) * index - textLayout.size.width / 2,
                        chartHeight + 2.dp.toPx()
                    )
                )
            }
        }
    }
}




@Composable
fun HistoryRow(
    record: com.neubofy.watch.data.db.HealthRecord,
    unit: String,
    onDeleteClick: () -> Unit,
    showDate: Boolean = false
) {
    val formatter = if (showDate) DateTimeFormatter.ofPattern("MMM dd, HH:mm")
                    else DateTimeFormatter.ofPattern("HH:mm")
    val zonedDateTime = java.time.Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault())
    val valueStr = if (record.type == "blood_pressure") "${record.systolic}/${record.diastolic} $unit"
                   else "${record.value.toInt()} $unit"
    
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    valueStr,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatter.format(zonedDateTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.isSyncedToHealthConnect) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp).padding(end = 12.dp)
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha=0.8f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(modifier=Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(unit, fontSize=10.sp, modifier=Modifier.padding(bottom=2.dp, start=2.dp))
                }
            }
        }
    }
}

@Composable
fun SleepTimelineChart(record: com.neubofy.watch.data.db.HealthRecord) {
    val totalMins = record.value.toInt()
    val deepMins = record.systolic ?: 0
    val lightMins = record.diastolic ?: 0
    val awakeMins = (totalMins - deepMins - lightMins).coerceAtLeast(0)

    // Current logic sets end time of sleep payload in BleWorker...
    // We assume the record timestamp is EndOfSleep
    val endTime = java.time.Instant.ofEpochMilli(record.timestamp).atZone(java.time.ZoneId.systemDefault())
    val startTime = endTime.minusMinutes(totalMins.toLong())

    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Stacked Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                if (deepMins > 0) {
                    Box(modifier = Modifier.weight(deepMins.toFloat()).fillMaxHeight().background(Color(0xFF3F51B5)))
                }
                if (lightMins > 0) {
                    Box(modifier = Modifier.weight(lightMins.toFloat()).fillMaxHeight().background(Color(0xFF03A9F4)))
                }
                if (awakeMins > 0) {
                    Box(modifier = Modifier.weight(awakeMins.toFloat()).fillMaxHeight().background(Color(0xFFFF9800)))
                }
            }
        }
        
        // Time Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fall Asleep: ${startTime.format(timeFormatter)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Wake Up: ${endTime.format(timeFormatter)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SleepLegendDot("Deep", Color(0xFF3F51B5))
            SleepLegendDot("Light", Color(0xFF03A9F4))
            SleepLegendDot("Awake", Color(0xFFFF9800))
        }
    }
}

@Composable
fun SleepLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun PeriodRangeChart(data: List<PeriodAggregation>, accentColor: Color) {
    val textMeasurer = rememberTextMeasurer()
    val validData = data.filter { it.count > 0 }
    
    if (validData.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No readings for this period", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)) {
        val width = size.width
        val height = size.height
        
        val maxSys = data.filter { it.count > 0 }.maxOf { it.max }.coerceAtLeast(140f)
        val minDia = data.filter { it.count > 0 }.mapNotNull { it.min2 }.minOrNull()?.coerceAtMost(60f) ?: 60f
        
        val range = (maxSys - minDia).coerceAtLeast(40f) * 1.3f
        val chartMin = minDia - (range * 0.15f)

        val barCount = data.size
        val stepX = width / (barCount - 1).coerceAtLeast(1)
        val diaColor = Color(0xFF03A9F4)

        // Grid
        for (i in 0..4) {
            val y = height * i / 4f
            drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, y), Offset(width, y), 1f)
        }

        data.forEachIndexed { index, agg ->
            if (agg.count > 0) {
                val x = index * stepX
                val ySys = height - ((agg.avg - chartMin) / range) * height
                val yDia = height - (((agg.avg2 ?: agg.avg) - chartMin) / range) * height
                
                // Vertical capsule for the reading
                drawLine(
                    brush = Brush.verticalGradient(listOf(accentColor, diaColor)),
                    start = Offset(x, ySys),
                    end = Offset(x, yDia),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Dots on ends
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, ySys))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, yDia))
            }

            // Labels
            val labelInterval = when {
                barCount <= 7 -> 1
                barCount <= 24 -> 3
                barCount <= 48 -> 6
                else -> 7
            }
            
            if (index % labelInterval == 0) {
                val labelText = if (barCount == 24) {
                    when {
                        index == 0 -> "12A"
                        index < 12 -> "${index}A"
                        index == 12 -> "12P"
                        else -> "${index-12}P"
                    }
                } else agg.label

                val layout = textMeasurer.measure(labelText, style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = Color.Gray))
                drawText(layout, topLeft = Offset(index * stepX - layout.size.width/2f, height + 4.dp.toPx()))
            }
        }
    }
}

private fun calculateAggregation(label: String, sortKey: Long, records: List<com.neubofy.watch.data.db.HealthRecord>, metricType: String): PeriodAggregation {
    if (metricType == "blood_pressure") {
        val sysValues = records.map { it.systolic?.toFloat() ?: 0f }.filter { it > 0 }
        val diaValues = records.map { it.diastolic?.toFloat() ?: 0f }.filter { it > 0 }
        
        return PeriodAggregation(
            label = label, sortKey = sortKey,
            avg = if (sysValues.isEmpty()) 0f else sysValues.average().toFloat(),
            min = sysValues.minOrNull() ?: 0f,
            max = sysValues.maxOrNull() ?: 0f,
            avg2 = if (diaValues.isEmpty()) 0f else diaValues.average().toFloat(),
            min2 = diaValues.minOrNull() ?: 0f,
            max2 = diaValues.maxOrNull() ?: 0f,
            count = sysValues.size
        )
    } else {
        val values = if (metricType == "steps") {
            records.map { it.value.toFloat() }
        } else {
            records.map { it.value.toFloat() }.filter { it > 0 }
        }
        
        val sum = values.sum()
        val average = if (values.isEmpty()) 0f else values.average().toFloat()
        val maxVal = if (metricType == "steps") sum else (values.maxOrNull() ?: 0f)

        return PeriodAggregation(
            label = label,
            sortKey = sortKey,
            avg = if (metricType == "steps") sum else average,
            min = values.minOrNull() ?: 0f,
            max = maxVal,
            count = values.size
        )
    }
}
