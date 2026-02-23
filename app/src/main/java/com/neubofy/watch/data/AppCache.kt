package com.neubofy.watch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nf_watch_cache")

/**
 * Lightweight local cache using DataStore Preferences.
 * Stores settings, last-known watch values, and sync preferences.
 */
class AppCache(private val context: Context) {

    companion object {
        val PAIRED_DEVICE_ADDRESS = stringPreferencesKey("paired_device_address")
        val PAIRED_DEVICE_NAME = stringPreferencesKey("paired_device_name")
        val LAST_BATTERY_LEVEL = intPreferencesKey("last_battery_level")
        val LAST_HEART_RATE = intPreferencesKey("last_heart_rate")
        val LAST_STEPS = intPreferencesKey("last_steps")
        val LAST_CALORIES = intPreferencesKey("last_calories")
        val LAST_DISTANCE = intPreferencesKey("last_distance")
        val LAST_SPO2 = intPreferencesKey("last_spo2")
        val LAST_BLOOD_PRESSURE = stringPreferencesKey("last_blood_pressure")
        val LAST_SLEEP_MINUTES = intPreferencesKey("last_sleep_minutes")
        val LAST_STRESS = intPreferencesKey("last_stress")
        val WEATHER_SYNC_HOURS = intPreferencesKey("weather_sync_hours")
        val SYNC_TIME_ON_CONNECT = booleanPreferencesKey("sync_time_on_connect")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_TEMP = intPreferencesKey("weather_temp")
        val WEATHER_ICON = intPreferencesKey("weather_icon")
        val AUTO_MEASURE_HR_ENABLED = booleanPreferencesKey("auto_measure_hr_enabled")
        val AUTO_MEASURE_SPO2_ENABLED = booleanPreferencesKey("auto_measure_spo2_enabled")
        val AUTO_MEASURE_BP_ENABLED = booleanPreferencesKey("auto_measure_bp_enabled")
        val AUTO_MEASURE_STRESS_ENABLED = booleanPreferencesKey("auto_measure_stress_enabled")
        val AUTO_MEASURE_INTERVAL = intPreferencesKey("auto_measure_interval")
        val AUTO_MEASURE_HR_INTERVAL = intPreferencesKey("auto_measure_hr_interval")
        val AUTO_MEASURE_SPO2_INTERVAL = intPreferencesKey("auto_measure_spo2_interval")
        val AUTO_MEASURE_BP_INTERVAL = intPreferencesKey("auto_measure_bp_interval")
        val AUTO_MEASURE_STRESS_INTERVAL = intPreferencesKey("auto_measure_stress_interval")
        val SLEEP_TRACKING_ENABLED = booleanPreferencesKey("sleep_tracking_enabled")
        val RUN_IN_BACKGROUND = booleanPreferencesKey("run_in_background")
        val WATCH_FACE_INDEX = intPreferencesKey("watch_face_index")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val POWER_SAVING_ENABLED = booleanPreferencesKey("power_saving_enabled")
        val QUICK_VIEW_ENABLED = booleanPreferencesKey("quick_view_enabled")
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val IS_METRIC = booleanPreferencesKey("is_metric")
        val GOAL_STEPS = intPreferencesKey("goal_steps")
        val UI_ACCENT = stringPreferencesKey("ui_accent")
        val SYNC_DND_WITH_PHONE = booleanPreferencesKey("sync_dnd_with_phone")
        val WATCH_BUTTON_ACTION = stringPreferencesKey("watch_button_action")
        val USER_HEIGHT = intPreferencesKey("user_height")
        val USER_WEIGHT = intPreferencesKey("user_weight")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_IS_MALE = booleanPreferencesKey("user_is_male")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val GOAL_CALORIES = intPreferencesKey("goal_calories")
        val VOICE_ASSISTANT_ENABLED = booleanPreferencesKey("voice_assistant_enabled")
        val ALLOWED_NOTIFICATION_PACKAGES = stringSetPreferencesKey("allowed_notification_packages")
    }

    val pairedDeviceAddress: Flow<String?> = context.dataStore.data.map { it[PAIRED_DEVICE_ADDRESS] }
    val pairedDeviceName: Flow<String?> = context.dataStore.data.map { it[PAIRED_DEVICE_NAME] }
    val lastBatteryLevel: Flow<Int> = context.dataStore.data.map { it[LAST_BATTERY_LEVEL] ?: -1 }
    val lastHeartRate: Flow<Int> = context.dataStore.data.map { it[LAST_HEART_RATE] ?: 0 }
    val lastSteps: Flow<Int> = context.dataStore.data.map { it[LAST_STEPS] ?: 0 }
    val lastCalories: Flow<Int> = context.dataStore.data.map { it[LAST_CALORIES] ?: 0 }
    val lastDistance: Flow<Int> = context.dataStore.data.map { it[LAST_DISTANCE] ?: 0 }
    val lastSpO2: Flow<Int> = context.dataStore.data.map { it[LAST_SPO2] ?: 0 }
    val lastBloodPressure: Flow<String?> = context.dataStore.data.map { it[LAST_BLOOD_PRESSURE] }
    val lastSleepMinutes: Flow<Int> = context.dataStore.data.map { it[LAST_SLEEP_MINUTES] ?: 0 }
    val lastStress: Flow<Int> = context.dataStore.data.map { it[LAST_STRESS] ?: 0 }
    val weatherSyncHours: Flow<Int> = context.dataStore.data.map { it[WEATHER_SYNC_HOURS] ?: 3 }
    val syncTimeOnConnect: Flow<Boolean> = context.dataStore.data.map { it[SYNC_TIME_ON_CONNECT] ?: true }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val weatherCity: Flow<String> = context.dataStore.data.map { it[WEATHER_CITY] ?: "London" }
    val weatherTemp: Flow<Int> = context.dataStore.data.map { it[WEATHER_TEMP] ?: 25 }
    val weatherIcon: Flow<Int> = context.dataStore.data.map { it[WEATHER_ICON] ?: 0 }
    val autoMeasureHrEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_MEASURE_HR_ENABLED] ?: false }
    val autoMeasureSpO2Enabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_MEASURE_SPO2_ENABLED] ?: false }
    val autoMeasureBpEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_MEASURE_BP_ENABLED] ?: false }
    val autoMeasureStressEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_MEASURE_STRESS_ENABLED] ?: false }
    
    val autoMeasureHrInterval: Flow<Int> = context.dataStore.data.map { it[AUTO_MEASURE_HR_INTERVAL] ?: 60 }
    val autoMeasureSpO2Interval: Flow<Int> = context.dataStore.data.map { it[AUTO_MEASURE_SPO2_INTERVAL] ?: 60 }
    val autoMeasureBpInterval: Flow<Int> = context.dataStore.data.map { it[AUTO_MEASURE_BP_INTERVAL] ?: 60 }
    val autoMeasureStressInterval: Flow<Int> = context.dataStore.data.map { it[AUTO_MEASURE_STRESS_INTERVAL] ?: 60 }

    val sleepTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SLEEP_TRACKING_ENABLED] ?: true }
    val runInBackground: Flow<Boolean> = context.dataStore.data.map { it[RUN_IN_BACKGROUND] ?: true }
    val watchFaceIndex: Flow<Int> = context.dataStore.data.map { it[WATCH_FACE_INDEX] ?: 1 }
    val dndEnabled: Flow<Boolean> = context.dataStore.data.map { it[DND_ENABLED] ?: false }
    val powerSavingEnabled: Flow<Boolean> = context.dataStore.data.map { it[POWER_SAVING_ENABLED] ?: false }
    val quickViewEnabled: Flow<Boolean> = context.dataStore.data.map { it[QUICK_VIEW_ENABLED] ?: true }
    val is24Hour: Flow<Boolean> = context.dataStore.data.map { it[IS_24_HOUR] ?: true }
    val isMetric: Flow<Boolean> = context.dataStore.data.map { it[IS_METRIC] ?: true }
    val goalSteps: Flow<Int> = context.dataStore.data.map { it[GOAL_STEPS] ?: 8000 }
    val uiAccent: Flow<String> = context.dataStore.data.map { it[UI_ACCENT] ?: "Gold" }
    val syncDndWithPhone: Flow<Boolean> = context.dataStore.data.map { it[SYNC_DND_WITH_PHONE] ?: false }
    val lastSyncTimestamp: Flow<Long> = context.dataStore.data.map { it[LAST_SYNC_TIMESTAMP] ?: 0L }
    val watchButtonAction: Flow<String> = context.dataStore.data.map { it[WATCH_BUTTON_ACTION] ?: "Flashlight" }
    val userHeight: Flow<Int> = context.dataStore.data.map { it[USER_HEIGHT] ?: 170 }
    val userWeight: Flow<Int> = context.dataStore.data.map { it[USER_WEIGHT] ?: 70 }
    val userAge: Flow<Int> = context.dataStore.data.map { it[USER_AGE] ?: 25 }
    val userIsMale: Flow<Boolean> = context.dataStore.data.map { it[USER_IS_MALE] ?: true }
    val goalCalories: Flow<Int> = context.dataStore.data.map { it[GOAL_CALORIES] ?: 500 }
    val voiceAssistantEnabled: Flow<Boolean> = context.dataStore.data.map { it[VOICE_ASSISTANT_ENABLED] ?: false }
    val allowedNotificationPackages: Flow<Set<String>> = context.dataStore.data.map { it[ALLOWED_NOTIFICATION_PACKAGES] ?: emptySet() }
    
    suspend fun setAutoMeasureConfig(
        metricType: String? = null,
        intervalMins: Int? = null,
        enabled: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            when (metricType) {
                "heart_rate" -> {
                    enabled?.let { prefs[AUTO_MEASURE_HR_ENABLED] = it }
                    intervalMins?.let { prefs[AUTO_MEASURE_HR_INTERVAL] = it }
                }
                "spo2" -> {
                    enabled?.let { prefs[AUTO_MEASURE_SPO2_ENABLED] = it }
                    intervalMins?.let { prefs[AUTO_MEASURE_SPO2_INTERVAL] = it }
                }
                "blood_pressure" -> {
                    enabled?.let { prefs[AUTO_MEASURE_BP_ENABLED] = it }
                    intervalMins?.let { prefs[AUTO_MEASURE_BP_INTERVAL] = it }
                }
                "stress" -> {
                    enabled?.let { prefs[AUTO_MEASURE_STRESS_ENABLED] = it }
                    intervalMins?.let { prefs[AUTO_MEASURE_STRESS_INTERVAL] = it }
                }
            }
        }
    }
    
    suspend fun isOnboardingCompleteSynchronous(): Boolean {
        return (context.dataStore.data.map { it[ONBOARDING_COMPLETE] }.firstOrNull() ?: false)
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE] = true }
    }

    suspend fun savePairedDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[PAIRED_DEVICE_ADDRESS] = address
            prefs[PAIRED_DEVICE_NAME] = name
        }
        // Also set lightweight SharedPreferences flag for BootReceiver
        // (BootReceiver runs synchronously and can't use DataStore)
        context.getSharedPreferences("nf_watch_boot", android.content.Context.MODE_PRIVATE)
            .edit().putString("paired_address", address).apply()
    }

    suspend fun updateHealthValues(
        battery: Int? = null,
        heartRate: Int? = null,
        steps: Int? = null,
        calories: Int? = null,
        distance: Int? = null,
        spO2: Int? = null,
        bloodPressure: String? = null,
        sleepMinutes: Int? = null,
        stress: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            battery?.let { prefs[LAST_BATTERY_LEVEL] = it }
            heartRate?.let { if (it > 0) prefs[LAST_HEART_RATE] = it }
            steps?.let { if (it > 0) prefs[LAST_STEPS] = it }
            calories?.let { if (it > 0) prefs[LAST_CALORIES] = it }
            distance?.let { if (it > 0) prefs[LAST_DISTANCE] = it }
            spO2?.let { if (it > 0) prefs[LAST_SPO2] = it }
            bloodPressure?.let { prefs[LAST_BLOOD_PRESSURE] = it }
            sleepMinutes?.let { if (it > 0) prefs[LAST_SLEEP_MINUTES] = it }
            stress?.let { if (it > 0) prefs[LAST_STRESS] = it }
        }
    }

    suspend fun setWeatherSyncHours(hours: Int) {
        context.dataStore.edit { prefs -> prefs[WEATHER_SYNC_HOURS] = hours }
    }

    suspend fun setSyncTimeOnConnect(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SYNC_TIME_ON_CONNECT] = enabled }
    }

    suspend fun updateWeather(city: String, temp: Int, icon: Int) {
        context.dataStore.edit { prefs ->
            prefs[WEATHER_CITY] = city
            prefs[WEATHER_TEMP] = temp
            prefs[WEATHER_ICON] = icon
        }
    }

    suspend fun clearPairedDevice() {
        context.dataStore.edit { prefs ->
            prefs.remove(PAIRED_DEVICE_ADDRESS)
            prefs.remove(PAIRED_DEVICE_NAME)
        }
    }



    suspend fun setSleepTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SLEEP_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun setRunInBackground(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[RUN_IN_BACKGROUND] = enabled }
    }

    suspend fun setWatchButtonAction(action: String) {
        context.dataStore.edit { prefs -> prefs[WATCH_BUTTON_ACTION] = action }
    }

    suspend fun setWatchSettings(
        watchFace: Int? = null,
        dnd: Boolean? = null,
        syncDnd: Boolean? = null,
        powerSaving: Boolean? = null,
        quickView: Boolean? = null,
        is24HourSys: Boolean? = null,
        isMetricSys: Boolean? = null,
        goalStepsCount: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            watchFace?.let { prefs[WATCH_FACE_INDEX] = it }
            dnd?.let { prefs[DND_ENABLED] = it }
            syncDnd?.let { prefs[SYNC_DND_WITH_PHONE] = it }
            powerSaving?.let { prefs[POWER_SAVING_ENABLED] = it }
            quickView?.let { prefs[QUICK_VIEW_ENABLED] = it }
            is24HourSys?.let { prefs[IS_24_HOUR] = it }
            isMetricSys?.let { prefs[IS_METRIC] = it }
            goalStepsCount?.let { prefs[GOAL_STEPS] = it }
        }
    }

    suspend fun setGoalCalories(kcal: Int) {
        context.dataStore.edit { prefs -> prefs[GOAL_CALORIES] = kcal }
    }

    suspend fun setVoiceAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[VOICE_ASSISTANT_ENABLED] = enabled }
    }

    suspend fun setAllowedNotificationPackages(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[ALLOWED_NOTIFICATION_PACKAGES] = packages }
    }

    suspend fun toggleNotificationPackage(pkg: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[ALLOWED_NOTIFICATION_PACKAGES] ?: emptySet()
            if (current.contains(pkg)) {
                prefs[ALLOWED_NOTIFICATION_PACKAGES] = current - pkg
            } else {
                prefs[ALLOWED_NOTIFICATION_PACKAGES] = current + pkg
            }
        }
    }

    suspend fun setUiAccent(accentName: String) {
        context.dataStore.edit { prefs -> prefs[UI_ACCENT] = accentName }
    }

    suspend fun setUserHeight(value: Int) {
        context.dataStore.edit { prefs -> prefs[USER_HEIGHT] = value }
    }
    suspend fun setUserWeight(value: Int) {
        context.dataStore.edit { prefs -> prefs[USER_WEIGHT] = value }
    }
    suspend fun setUserAge(value: Int) {
        context.dataStore.edit { prefs -> prefs[USER_AGE] = value }
    }
    suspend fun setUserIsMale(isMale: Boolean) {
        context.dataStore.edit { it[USER_IS_MALE] = isMale }
    }
    
    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP] = timestamp }
    }
    
    suspend fun getLastSyncTimestampSynchronous(): Long {
        return context.dataStore.data.firstOrNull()?.get(LAST_SYNC_TIMESTAMP) ?: 0L
    }
}
