package com.neubofy.watch.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "health_records", indices = [Index(value = ["type", "timestamp"])])
data class HealthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "heart_rate", "spo2", "blood_pressure", "stress", "steps", "calories", "distance", "sleep"
    val value: Double,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSyncedToHealthConnect: Boolean = false
)

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HealthRecord)

    @Query("SELECT * FROM health_records WHERE type = :type ORDER BY timestamp DESC LIMIT 500")
    fun getRecordsByType(type: String): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRecordByType(type: String): Flow<HealthRecord?>

    @Query("SELECT * FROM health_records WHERE isSyncedToHealthConnect = 0")
    suspend fun getUnsyncedRecords(): List<HealthRecord>

    @Update
    suspend fun update(record: HealthRecord)

    @Query("DELETE FROM health_records WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query("DELETE FROM health_records WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM health_records WHERE id IN (SELECT id FROM health_records WHERE type = :type ORDER BY timestamp DESC LIMIT -1 OFFSET :keepCount)")
    suspend fun pruneOldRecords(type: String, keepCount: Int)

    @Query("DELETE FROM health_records WHERE timestamp < :cutoffMs")
    suspend fun pruneAllRecordsOlderThan(cutoffMs: Long)

    // Deduplication: get records in a time range for a type
    @Query("SELECT * FROM health_records WHERE type = :type AND timestamp >= :startMs AND timestamp < :endMs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecordByTypeAndDateRange(type: String, startMs: Long, endMs: Long): HealthRecord?

    // Delete records in a time range for a type (for upsert)
    @Query("DELETE FROM health_records WHERE type = :type AND timestamp >= :startMs AND timestamp < :endMs")
    suspend fun deleteByTypeAndDateRange(type: String, startMs: Long, endMs: Long)
}

@Database(entities = [HealthRecord::class], version = 1, exportSchema = false)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getDatabase(context: android.content.Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "nf_watch_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
