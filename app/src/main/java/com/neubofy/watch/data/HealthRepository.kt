package com.neubofy.watch.data

import com.neubofy.watch.data.db.HealthDao
import com.neubofy.watch.data.db.HealthRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration

/**
 * HealthRepository — writes data DIRECTLY to Health Connect as the primary store.
 * Room DB is used only as a lightweight cache for instant latest-value display.
 * History/graphs are read from Health Connect.
 */
class HealthRepository(
    private val healthDao: HealthDao,
    private val healthConnectManager: HealthConnectManager,
    private val appCache: com.neubofy.watch.data.AppCache
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // --- Cache access (for instant display) ---
    fun getLatestRecord(type: String): Flow<HealthRecord?> = healthDao.getLatestRecordByType(type)
    fun getRecords(type: String): Flow<List<HealthRecord>> = healthDao.getRecordsByType(type)

    /**
     * Write health data: writes DIRECTLY to Health Connect, and caches in Room for quick display.
     * For daily types (sleep, steps, calories, distance) — upsert (one record per day in cache).
     * For point types (heart_rate, spo2, blood_pressure, stress) — insert each reading.
     */
    fun insertHealthData(
        type: String,
        value: Double,
        systolic: Int? = null,
        diastolic: Int? = null,
        timestamp: Long = System.currentTimeMillis(),
        isIncremental: Boolean = false
    ) {
        repositoryScope.launch {
            val time = Instant.ofEpochMilli(timestamp)
            val isDailyType = type in listOf("sleep", "steps", "calories", "distance")
            val zone = ZoneId.systemDefault()

            // ── 0. Lightweight Pre-Check ──
            if (isIncremental) {
                // History sync: cheap dedup using Room (2min window, same type + value)
                val windowStart = timestamp - 120_000
                val windowEnd = timestamp + 120_000
                val existing = healthDao.getRecordByTypeAndDateRange(type, windowStart, windowEnd)
                if (existing != null && existing.value == value && existing.isSyncedToHealthConnect) {
                    return@launch // Already synced this exact reading
                }
            } else {
                if (isDailyType) {
                    val recordDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                    val dayStart = recordDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = recordDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    
                    val existing = healthDao.getRecordByTypeAndDateRange(type, dayStart, dayEnd)
                    if (existing != null && existing.isSyncedToHealthConnect) {
                        if (type == "sleep") {
                            if (value <= existing.value) return@launch
                        } else {
                            if (value <= existing.value) return@launch
                        }
                    }
                } else {
                    // Point data: check 60s window
                    val windowStart = timestamp - 60_000
                    val windowEnd = timestamp + 60_000
                    val existing = healthDao.getRecordByTypeAndDateRange(type, windowStart, windowEnd)
                    if (existing != null && existing.value == value && existing.isSyncedToHealthConnect) {
                        return@launch
                    }
                }
            }

            // ── 1. Write directly to Health Connect ──
            var synced = false
            try {
                when (type) {
                    "heart_rate" -> {
                        if (isIncremental) {
                            synced = healthConnectManager.writeHeartRate(value.toInt(), time, time.plusSeconds(300))
                        } else {
                            synced = healthConnectManager.writeHeartRate(value.toInt(), time)
                        }
                    }
                    "spo2" -> synced = healthConnectManager.writeSpO2(value.toInt(), time)
                    "blood_pressure" -> {
                        if (systolic != null && diastolic != null) {
                            synced = healthConnectManager.writeBloodPressure(systolic, diastolic, time)
                        }
                    }
                    "steps" -> {
                        if (isIncremental) {
                            // Write incremental chunks directly (half-hourly sync from watch). Exact 30 min block to prevent bloat.
                            val clientRecordId = "steps_hist_${time.toEpochMilli()}"
                            synced = healthConnectManager.writeSteps(value.toLong(), time, time.plusSeconds(1800), clientRecordId)
                        } else {
                            val recordDate = time.atZone(zone).toLocalDate()
                            val currentHcSteps = healthConnectManager.readStepsForDay(recordDate)
                            val delta = (value.toLong() - currentHcSteps).coerceAtLeast(0)
                            if (delta > 0) {
                                // Live delta sync. Use a 15-minute block instead of a 1-minute burst so it graphs beautifully in HC.
                                // We use a designated hourly live block ID so that subsequent deltas within the same hour safely overwrite each other 
                                // instead of stacking duplicates!
                                val liveHourId = "steps_live_${time.atZone(zone).withMinute(0).withSecond(0).withNano(0).toEpochSecond()}"
                                synced = healthConnectManager.writeSteps(delta, time.minusSeconds(900), time, liveHourId)
                            } else if (value > 0 && currentHcSteps > 0) {
                                // Already synced the total or more
                                synced = true 
                            }
                        }
                    }
                    "sleep" -> {
                        val totalMins = value.toInt()
                        val deepMins = systolic ?: 0
                        val lightMins = diastolic ?: 0
                        val awakeMins = (totalMins - deepMins - lightMins).coerceAtLeast(0)
                        
                        val stages = mutableListOf<androidx.health.connect.client.records.SleepSessionRecord.Stage>()
                        var stageTime = time.minusSeconds(totalMins.toLong() * 60)
                        
                        if (deepMins > 0) {
                            stages.add(androidx.health.connect.client.records.SleepSessionRecord.Stage(
                                startTime = stageTime,
                                endTime = stageTime.plusSeconds(deepMins.toLong() * 60),
                                stage = androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP
                            ))
                            stageTime = stageTime.plusSeconds(deepMins.toLong() * 60)
                        }
                        if (lightMins > 0) {
                            stages.add(androidx.health.connect.client.records.SleepSessionRecord.Stage(
                                startTime = stageTime,
                                endTime = stageTime.plusSeconds(lightMins.toLong() * 60),
                                stage = androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT
                            ))
                            stageTime = stageTime.plusSeconds(lightMins.toLong() * 60)
                        }
                        if (awakeMins > 0) {
                            stages.add(androidx.health.connect.client.records.SleepSessionRecord.Stage(
                                startTime = stageTime,
                                endTime = stageTime.plusSeconds(awakeMins.toLong() * 60),
                                stage = androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE
                            ))
                        }
                        
                        synced = healthConnectManager.writeSleepSession(
                            startTime = time.minusSeconds(totalMins.toLong() * 60),
                            endTime = time,
                            stages = stages
                        )
                    }
                    "calories" -> {
                        val recordDate = time.atZone(zone).toLocalDate()
                        val currentHcKcal = healthConnectManager.readSumForDay(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class, recordDate)
                        val delta = (value - currentHcKcal).coerceAtLeast(0.0)
                        if (delta >= 1.0) {
                            synced = healthConnectManager.writeCalories(delta, time.minusSeconds(60), time)
                        } else {
                            synced = true
                        }
                    }
                    "distance" -> {
                        val recordDate = time.atZone(zone).toLocalDate()
                        val currentHcMeters = healthConnectManager.readSumForDay(androidx.health.connect.client.records.DistanceRecord::class, recordDate)
                        val delta = (value - currentHcMeters).coerceAtLeast(0.0)
                        if (delta >= 1.0) {
                            synced = healthConnectManager.writeDistance(delta, time.minusSeconds(60), time)
                        } else {
                            synced = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HealthRepo", "Failed to write $type to Health Connect: ${e.message}")
            }

            // ── 2. Cache in Room for instant display ──
            val record = HealthRecord(
                type = type,
                value = value,
                systolic = systolic,
                diastolic = diastolic,
                timestamp = timestamp,
                isSyncedToHealthConnect = synced
            )

            if (isDailyType) {
                val recordDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                val dayStart = recordDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = recordDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                if (type == "sleep") {
                    val existing = healthDao.getRecordByTypeAndDateRange(type, dayStart, dayEnd)
                    if (existing != null) {
                        healthDao.update(existing.copy(
                            value = value,
                            systolic = systolic ?: existing.systolic,
                            diastolic = diastolic ?: existing.diastolic,
                            timestamp = timestamp
                        ))
                    } else {
                        healthDao.insert(record)
                    }
                } else {
                    // steps/calories/distance: replace this day's record
                    healthDao.deleteByTypeAndDateRange(type, dayStart, dayEnd)
                    healthDao.insert(record)
                }
            } else {
                // Point data (HR, SpO2, BP, Stress): Insert each reading
                healthDao.insert(record)
            }
            healthDao.pruneOldRecords(type, 200)
        }
    }

    suspend fun deleteRecord(id: Long) {
        healthDao.deleteById(id)
    }

    suspend fun deleteRecordsByType(type: String) {
        healthDao.deleteByType(type)
    }

    suspend fun deleteHealthRecord(record: HealthRecord, deleteFromHealthConnect: Boolean) {
        healthDao.deleteById(record.id)
        if (deleteFromHealthConnect && healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
            val endTime = Instant.ofEpochMilli(record.timestamp)
            val startTime = when (record.type) {
                "sleep" -> endTime.minusSeconds(record.value.toLong() * 60)
                else -> endTime.minusSeconds(60) // Cover the small insertion window
            }
            healthConnectManager.deleteRecord(record.type, startTime, endTime)
        }
    }

    /**
     * Efficient 2-Way "Smart Sync" for the current day.
     * 1. Deletes local records older than today (strict 1-day lifecycle).
     * 2. Pushes unsynced local records to Health Connect.
     * 3. Pulls new records from Health Connect created since lastSyncTimestamp.
     */
    suspend fun syncTodayFromHealthConnect() {
        if (!healthConnectManager.isAvailable() || !healthConnectManager.hasAllPermissions()) return

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

        // 1. Delete all records older than today (Strict 1-day local lifecycle)
        healthDao.pruneAllRecordsOlderThan(todayStart)

        // 2. Retry pushing unsynced local records to HC (App -> HC)
        val unsynced = healthDao.getUnsyncedRecords()
        unsynced.forEach { record ->
            try {
                var synced = false
                val time = Instant.ofEpochMilli(record.timestamp)
                when (record.type) {
                    "heart_rate" -> synced = healthConnectManager.writeHeartRate(record.value.toInt(), time)
                    "spo2" -> synced = healthConnectManager.writeSpO2(record.value.toInt(), time)
                    "blood_pressure" -> {
                        if (record.systolic != null && record.diastolic != null) {
                            synced = healthConnectManager.writeBloodPressure(record.systolic, record.diastolic, time)
                        }
                    }
                    // For steps/cal/dist, the incremental values were handled at creation.
                    // Doing a raw write here could duplicate if it was a chunk. Best to skip or handle with care.
                }
                if (synced) {
                    healthDao.update(record.copy(isSyncedToHealthConnect = true))
                }
            } catch (e: Exception) {
                Log.e("HealthRepo", "Failed to sync unsynced record ${record.id}: ${e.message}")
            }
        }

        // 3. Smart Pull from HC -> Local DB (only data since last sync)
        val lastSyncMs = appCache.getLastSyncTimestampSynchronous()
        // Never pull data older than todayStart to keep local DB clean
        val queryStart = Instant.ofEpochMilli(maxOf(todayStart, lastSyncMs))

        val types = listOf("heart_rate", "spo2", "blood_pressure", "steps", "calories", "distance", "sleep")
        var anyFailures = false
        for (type in types) {
            try {
                // We use our existing custom read logic but only for the specific time range.
                // readRawHealthConnectHistoryForRange handles generic Record types mapped to our app records.
                val records = readRawHealthConnectHistoryForRange(type, TimeRangeFilter.between(queryStart, now))
                records.forEach { hcRec ->
                    val existing = healthDao.getRecordByTypeAndDateRange(type, hcRec.timestamp, hcRec.timestamp + 1)
                    if (existing == null) {
                        healthDao.insert(hcRec.copy(isSyncedToHealthConnect = true))
                    }
                }
            } catch (e: Exception) {
                Log.e("HealthRepo", "Failed to smart pull $type from HC: ${e.message}")
                anyFailures = true
            }
        }

        if (!anyFailures) {
            appCache.setLastSyncTimestamp(now.toEpochMilli())
        }
    }

    // Read history directly from Health Connect
    fun readHealthConnectHistory(type: String, days: Long = 7, onResult: (List<DisplayRecord>) -> Unit) {
        repositoryScope.launch {
            val records = when (type) {
                "heart_rate" -> {
                    healthConnectManager.readHeartRateHistory(days).flatMap { hr ->
                        hr.samples.map { sample ->
                            DisplayRecord(
                                time = sample.time,
                                value = "${sample.beatsPerMinute} bpm",
                                numericValue = sample.beatsPerMinute.toFloat()
                            )
                        }
                    }
                }
                "steps" -> {
                    healthConnectManager.readStepsHistory(days).map { step ->
                        DisplayRecord(
                            time = step.startTime,
                            value = "${step.count} steps",
                            numericValue = step.count.toFloat()
                        )
                    }
                }
                "spo2" -> {
                    healthConnectManager.readSpO2History(days).map { rec ->
                        DisplayRecord(
                            time = rec.time,
                            value = "${rec.percentage.value.toInt()}%",
                            numericValue = rec.percentage.value.toFloat()
                        )
                    }
                }
                "blood_pressure" -> {
                    healthConnectManager.readBloodPressureHistory(days).map { bp ->
                        val sys = bp.systolic.inMillimetersOfMercury.toInt()
                        val dia = bp.diastolic.inMillimetersOfMercury.toInt()
                        DisplayRecord(
                            time = bp.time,
                            value = "$sys/$dia mmHg",
                            numericValue = sys.toFloat(),
                            numericValue2 = dia.toFloat()
                        )
                    }
                }
                "sleep" -> {
                    healthConnectManager.readSleepHistory(days).map { session ->
                        val totalMins = java.time.Duration.between(session.startTime, session.endTime).toMinutes()
                        val deepMins = session.stages.filter { it.stage == androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP }
                            .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                        DisplayRecord(
                            time = session.endTime,
                            value = "${totalMins / 60}h ${totalMins % 60}m",
                            numericValue = totalMins.toFloat(),
                            numericValue2 = deepMins.toFloat()
                        )
                    }
                }
                "calories" -> {
                    healthConnectManager.readCaloriesHistory(days).map { cal ->
                        DisplayRecord(
                            time = cal.startTime,
                            value = "${cal.energy.inKilocalories.toInt()} kcal",
                            numericValue = cal.energy.inKilocalories.toFloat()
                        )
                    }
                }
                "distance" -> {
                    healthConnectManager.readDistanceHistory(days).map { dist ->
                        DisplayRecord(
                            time = dist.startTime,
                            value = "${String.format("%.2f", dist.distance.inKilometers)} km",
                            numericValue = dist.distance.inKilometers.toFloat()
                        )
                    }
                }
                "stress" -> {
                    // Stress has no Health Connect type, read from Room DB
                    val cutoff = System.currentTimeMillis() - (days * 86400 * 1000)
                    healthDao.getRecordsByType("stress")
                        .first()
                        .filter { it.timestamp >= cutoff && it.value > 0 }
                        .map { rec ->
                            DisplayRecord(
                                time = Instant.ofEpochMilli(rec.timestamp),
                                value = "${rec.value.toInt()}",
                                numericValue = rec.value.toFloat()
                            )
                        }
                }
                else -> emptyList()
            }
            onResult(records.sortedBy { it.time })
        }
    }

    suspend fun readRawHealthConnectHistory(type: String, days: Long = 30): List<com.neubofy.watch.data.db.HealthRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return readRawHealthConnectHistoryForRange(type, TimeRangeFilter.after(start))
    }

    suspend fun readRawHealthConnectHistoryForDay(type: String, date: LocalDate): List<com.neubofy.watch.data.db.HealthRecord> {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readRawHealthConnectHistoryForRange(type, TimeRangeFilter.between(start, end))
    }

    suspend fun readRawHealthConnectHistoryForDateRange(type: String, startDate: LocalDate, endDate: LocalDate): List<com.neubofy.watch.data.db.HealthRecord> {
        val start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readRawHealthConnectHistoryForRange(type, TimeRangeFilter.between(start, end))
    }

    private suspend fun readRawHealthConnectHistoryForRange(type: String, filter: TimeRangeFilter): List<com.neubofy.watch.data.db.HealthRecord> {
        val zone = ZoneId.systemDefault()
        return when (type) {
            "heart_rate" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.HeartRateRecord::class, filter).flatMap { rec ->
                rec.samples.map { sample ->
                    com.neubofy.watch.data.db.HealthRecord(
                        type = type,
                        value = sample.beatsPerMinute.toDouble(),
                        timestamp = sample.time.toEpochMilli(),
                        isSyncedToHealthConnect = true
                    )
                }
            }
            "steps" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.StepsRecord::class, filter).map { rec ->
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = rec.count.toDouble(),
                    timestamp = rec.endTime.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            "calories" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class, filter).map { rec ->
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = rec.energy.inKilocalories,
                    timestamp = rec.endTime.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            "distance" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.DistanceRecord::class, filter).map { rec ->
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = rec.distance.inMeters,
                    timestamp = rec.endTime.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            "spo2" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.OxygenSaturationRecord::class, filter).map { rec ->
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = rec.percentage.value,
                    timestamp = rec.time.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            "blood_pressure" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.BloodPressureRecord::class, filter).map { rec ->
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = 0.0,
                    systolic = rec.systolic.inMillimetersOfMercury.toInt(),
                    diastolic = rec.diastolic.inMillimetersOfMercury.toInt(),
                    timestamp = rec.time.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            "sleep" -> healthConnectManager.readRawRecords(androidx.health.connect.client.records.SleepSessionRecord::class, filter).map { rec ->
                val totalMins = java.time.Duration.between(rec.startTime, rec.endTime).toMinutes().toDouble()
                val deepMins = rec.stages.filter { it.stage == androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                val lightMins = rec.stages.filter { it.stage == androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                
                com.neubofy.watch.data.db.HealthRecord(
                    type = type,
                    value = totalMins,
                    systolic = deepMins,
                    diastolic = lightMins,
                    timestamp = rec.endTime.toEpochMilli(),
                    isSyncedToHealthConnect = true
                )
            }
            else -> emptyList()
        }.sortedByDescending { it.timestamp }
    }
}

// Import needed for HealthDetailScreen compatibility
data class DisplayRecord(
    val time: java.time.Instant,
    val value: String,
    val numericValue: Float = 0f,
    val numericValue2: Float? = null
)
