package com.neubofy.watch.data

import android.content.Context
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.*
import java.time.*
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Health Connect manager — writes watch data to Android's Health Connect (OS-level storage).
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnect"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(BloodPressureRecord::class),
        )

        fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
            return PermissionController.createRequestPermissionResultContract()
        }
    }

    private val client: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available", e)
            null
        }
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = client?.permissionController?.getGrantedPermissions() ?: return false
        return PERMISSIONS.all { it in granted }
    }

    suspend fun writeHeartRate(bpm: Int, time: Instant = Instant.now(), endTime: Instant = time.plusSeconds(1)): Boolean {
        val c = client ?: return false
        return try {
            val record = HeartRateRecord(
                startTime = time,
                endTime = endTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(time),
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endTime),
                samples = listOf(
                    HeartRateRecord.Sample(time = time, beatsPerMinute = bpm.toLong())
                ),
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "hr_${time.toEpochMilli()}"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write heart rate", e)
            false
        }
    }

    suspend fun writeSteps(count: Long, startTime: Instant, endTime: Instant, clientRecordId: String? = null): Boolean {
        val c = client ?: return false
        return try {
            val record = StepsRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime),
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endTime),
                count = count,
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = clientRecordId ?: "steps_${startTime.atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).toEpochSecond()}"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write steps", e)
            false
        }
    }

    suspend fun writeBloodPressure(systolic: Int, diastolic: Int, time: Instant = Instant.now()): Boolean {
        val c = client ?: return false
        return try {
            val record = BloodPressureRecord(
                time = time,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(time),
                systolic = Pressure.millimetersOfMercury(systolic.toDouble()),
                diastolic = Pressure.millimetersOfMercury(diastolic.toDouble()),
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "bp_${time.toEpochMilli()}"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write blood pressure", e)
            false
        }
    }

    suspend fun writeSpO2(percentage: Int, time: Instant = Instant.now()): Boolean {
        val c = client ?: return false
        return try {
            val record = OxygenSaturationRecord(
                time = time,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(time),
                percentage = Percentage(percentage.toDouble()),
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "spo2_${time.toEpochMilli()}"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SpO2", e)
            false
        }
    }
    
    suspend fun writeSleepSession(
        startTime: Instant,
        endTime: Instant,
        stages: List<SleepSessionRecord.Stage> = emptyList()
    ): Boolean {
        val c = client ?: return false
        return try {
            val record = SleepSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endTime),
                title = "NF Watch Sleep",
                notes = "Auto-synced from watch",
                stages = stages,
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "sleep_${startTime.toEpochMilli()}"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sleep session", e)
            false
        }
    }

    suspend fun readStepsForDay(date: LocalDate): Long {
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = client?.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            return response?.records?.sumOf { it.count } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps for $date", e)
            return 0L
        }
    }

    suspend fun readSumForDay(recordType: kotlin.reflect.KClass<out Record>, date: LocalDate): Double {
        val c = client ?: return 0.0
        return try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            withTimeoutOrNull(5000L) {
                val response = c.readRecords(
                    ReadRecordsRequest(
                        recordType = recordType,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                when (recordType) {
                    TotalCaloriesBurnedRecord::class -> (response.records as? List<TotalCaloriesBurnedRecord>)?.sumOf { it.energy.inKilocalories } ?: 0.0
                    DistanceRecord::class -> (response.records as? List<DistanceRecord>)?.sumOf { it.distance.inMeters } ?: 0.0
                    else -> 0.0
                }
            } ?: 0.0
        } catch (e: Exception) {
            return 0.0
        }
    }

    suspend fun readTodaySteps(): Long = readStepsForDay(LocalDate.now())

    suspend fun writeCalories(kcal: Double, startTime: Instant, endTime: Instant): Boolean {
        val c = client ?: return false
        return try {
            val hourBlock = startTime.atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).toEpochSecond()
            val record = TotalCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                energy = Energy.kilocalories(kcal),
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "kcal_$hourBlock"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write calories", e)
            false
        }
    }

    suspend fun writeDistance(meters: Double, startTime: Instant, endTime: Instant): Boolean {
        val c = client ?: return false
        return try {
            val hourBlock = startTime.atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).toEpochSecond()
            val record = DistanceRecord(
                startTime = startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                distance = Length.meters(meters),
                metadata = androidx.health.connect.client.records.metadata.Metadata(
                    clientRecordId = "dist_$hourBlock"
                )
            )
            withTimeoutOrNull(5000L) {
                c.insertRecords(listOf(record))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write distance", e)
            false
        }
    }

    suspend fun deleteRecord(type: String, startTime: Instant, endTime: Instant): Boolean {
        val c = client ?: return false
        return try {
            withTimeoutOrNull(5000L) {
                val timeRange = TimeRangeFilter.between(startTime, endTime)
                when (type) {
                    "heart_rate" -> c.deleteRecords(HeartRateRecord::class, timeRange)
                    "blood_pressure" -> c.deleteRecords(BloodPressureRecord::class, timeRange)
                    "spo2" -> c.deleteRecords(OxygenSaturationRecord::class, timeRange)
                    "sleep" -> c.deleteRecords(SleepSessionRecord::class, timeRange)
                    "steps" -> c.deleteRecords(StepsRecord::class, timeRange)
                    "calories" -> c.deleteRecords(TotalCaloriesBurnedRecord::class, timeRange)
                    "distance" -> c.deleteRecords(DistanceRecord::class, timeRange)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete from Health Connect", e)
            false
        }
    }

    suspend fun readTodayLatest(): Map<String, Any?> {
        val now = Instant.now()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val results = mutableMapOf<String, Any?>()

        try {
            withTimeoutOrNull(10000L) {
                // 1. Steps, Calories, Distance (Sums)
                val stepsResp = client?.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(startOfDay, now)))
                results["steps"] = stepsResp?.records?.sumOf { it.count }?.toInt() ?: 0

                val calResp = client?.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, TimeRangeFilter.between(startOfDay, now)))
                results["calories"] = calResp?.records?.sumOf { it.energy.inKilocalories }?.toInt() ?: 0

                val distResp = client?.readRecords(ReadRecordsRequest(DistanceRecord::class, TimeRangeFilter.between(startOfDay, now)))
                results["distance"] = distResp?.records?.sumOf { it.distance.inMeters }?.toInt() ?: 0

                // 2. Latest Heart Rate (last 1 hour)
                val hrResp = client?.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minusSeconds(3600), now)))
                results["heart_rate"] = hrResp?.records?.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toInt()

                // 3. Latest SpO2 (today)
                val spo2Resp = client?.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(startOfDay, now)))
                results["spo2"] = spo2Resp?.records?.lastOrNull()?.percentage?.value?.toInt()

                // 4. Latest BP (today)
                val bpResp = client?.readRecords(ReadRecordsRequest(BloodPressureRecord::class, TimeRangeFilter.between(startOfDay, now)))
                val lastBp = bpResp?.records?.lastOrNull()
                if (lastBp != null) {
                    results["blood_pressure"] = "${lastBp.systolic.inMillimetersOfMercury.toInt()}/${lastBp.diastolic.inMillimetersOfMercury.toInt()}"
                }

                // 5. Latest Sleep (from last 24h session)
                val sleepResp = client?.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minusSeconds(86400), now)))
                val lastSleep = sleepResp?.records?.lastOrNull { it.endTime.isAfter(startOfDay) }
                if (lastSleep != null) {
                    val totalMins = java.time.Duration.between(lastSleep.startTime, lastSleep.endTime).toMinutes().toInt()
                    results["sleep"] = totalMins
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading today's summary", e)
        }

        return results
    }

    suspend fun readHeartRateHistory(days: Long = 7): List<HeartRateRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readStepsHistory(days: Long = 7): List<StepsRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readCaloriesHistory(days: Long = 7): List<TotalCaloriesBurnedRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(TotalCaloriesBurnedRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readDistanceHistory(days: Long = 7): List<DistanceRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(DistanceRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readSleepHistory(days: Long = 7): List<SleepSessionRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readBloodPressureHistory(days: Long = 7): List<BloodPressureRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(BloodPressureRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readSpO2History(days: Long = 7): List<OxygenSaturationRecord> {
        val start = Instant.now().minusSeconds(days * 86400)
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.after(start))
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun <T : Record> readRawRecords(
        recordType: kotlin.reflect.KClass<T>, 
        timeRangeFilter: TimeRangeFilter
    ): List<T> {
        return try {
            withTimeoutOrNull(5000L) {
                client?.readRecords(
                    ReadRecordsRequest(recordType, timeRangeFilter)
                )?.records
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
