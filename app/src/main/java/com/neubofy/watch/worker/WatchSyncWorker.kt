package com.neubofy.watch.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class WatchSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appCache = AppCache(applicationContext)
        val address = appCache.pairedDeviceAddress.first() ?: return Result.success()
        val connectionManager = BleConnectionManager.getInstance(applicationContext)

        Log.d("WatchSyncWorker", "Periodic sync started for $address")

        // 1. Ensure connected
        if (connectionManager.connectionState.value != ConnectionState.CONNECTED) {
            Log.d("WatchSyncWorker", "Not connected, attempting priority reconnect during sync window")
            connectionManager.reconnect(address, isPriority = true)
            // Wait a bit for connection
            kotlinx.coroutines.delay(5000)
        }

        if (connectionManager.connectionState.value == ConnectionState.CONNECTED) {
            // 2. Perform Sync
            connectionManager.syncAllData()
            // 3. Weather update (since we are awake)
            connectionManager.sendWeatherUpdate()
        }

        return Result.success()
    }

    companion object {
        private const val SYNC_TAG = "watch_sync_periodic"

        fun schedule(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WatchSyncWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES,
                15, TimeUnit.MINUTES // Flex period
            )
            .setConstraints(constraints)
            .addTag(SYNC_TAG)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d("WatchSyncWorker", "Scheduled periodic sync every $intervalMinutes mins")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_TAG)
        }
    }
}
