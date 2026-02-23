package com.neubofy.watch.ble

import android.util.Log

/**
 * Protocol decoder for GoBoult Drift+ (Moyoung V2 / DaFit based).
 * Implements the FE EA Moyoung packets structure.
 */
object GoBoultProtocol {
    private const val TAG = "GoBoultProtocol"

    // --- Services ---
    const val SERVICE_MOYOUNG_MAIN = "0000feea-0000-1000-8000-00805f9b34fb"
    const val SERVICE_MOYOUNG_STEPS = "0000fee7-0000-1000-8000-00805f9b34fb"

    // --- Characteristics ---
    const val CHAR_WRITE = "0000fee2-0000-1000-8000-00805f9b34fb"
    const val CHAR_NOTIFY_DATA = "0000fee3-0000-1000-8000-00805f9b34fb"
    const val CHAR_NOTIFY_SUMMARY = "0000fec9-0000-1000-8000-00805f9b34fb"
    const val CHAR_NOTIFY_STEPS = "0000fea1-0000-1000-8000-00805f9b34fb"
    const val CHAR_STEPS_HISTORY = "0000fee1-0000-1000-8000-00805f9b34fb" // sometimes readable directly
    const val CHAR_HEART_RATE = "00002a37-0000-1000-8000-00805f9b34fb"
    const val CHAR_BATTERY = "00002a19-0000-1000-8000-00805f9b34fb"

    // Helper functions for common outgoing commands
    fun getSyncTimeCmd(): ByteArray {
        val now = System.currentTimeMillis()
        val localOffset = java.util.TimeZone.getDefault().getOffset(now)
        // System is in local time. GMT+8 watch timestamp expects the local time value to be treated as if it were in GMT+8.
        val timeSeconds = ((now + localOffset - (8 * 3600 * 1000L)) / 1000L).toInt()
        val payload = byteArrayOf(
            (timeSeconds shr 24).toByte(),
            (timeSeconds shr 16).toByte(),
            (timeSeconds shr 8).toByte(),
            (timeSeconds).toByte(),
            8 // Hardcoded GMT+8 timezone for watch internally
        )
        return MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_SYNC_TIME, payload)
    }

    fun getMeasureHeartRateCmd(start: Boolean): ByteArray {
        val payload = byteArrayOf(if (start) 0x00 else -1)
        return MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_TRIGGER_MEASURE_HEARTRATE, payload)
    }

    fun getMeasureSpO2Cmd(start: Boolean): ByteArray {
        val payload = byteArrayOf(if (start) 0x00 else -1)
        return MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN, payload)
    }

    fun getMeasureBloodPressureCmd(start: Boolean): ByteArray {
        val payload = if (start) byteArrayOf(0, 0, 0) else byteArrayOf(-1, -1, -1)
        return MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_TRIGGER_MEASURE_BLOOD_PRESSURE, payload)
    }

    fun getMeasureStressCmd(start: Boolean): ByteArray {
        // 0x11 = start single measurement, 0x12 = stop
        val payload = byteArrayOf(if (start) 0x11 else 0x12)
        return MoyoungPacketManager.buildPacket(MoyoungPacketManager.CMD_ADVANCED_SETTINGS, payload)
    }

    fun getWeatherCmd(icon: Int, temp: Int, min: Int, max: Int, city: String = "City"): ByteArray {
        // Enforce padding to 4 characters for UTF-16BE (so it's exactly 8 bytes)
        val paddedCity = city.padEnd(4, ' ').substring(0, 4)
        val cityBytes = paddedCity.toByteArray(Charsets.UTF_16BE)

        // Add 8 bytes (4 spaces) for lunar_or_festival that GB expects
        val lunarBytes = "    ".toByteArray(Charsets.UTF_16BE)

        val payload = ByteArray(3 + lunarBytes.size + cityBytes.size) // No PM25
        payload[0] = 0.toByte()
        payload[1] = icon.toByte()
        payload[2] = temp.toByte()
        System.arraycopy(lunarBytes, 0, payload, 3, lunarBytes.size) 
        System.arraycopy(cityBytes, 0, payload, 3 + lunarBytes.size, cityBytes.size)

        return MoyoungPacketManager.buildPacket(67.toByte(), payload) // CMD_SET_WEATHER_TODAY
    }

    fun getWeatherFutureCmd(icon: Int, min: Int, max: Int): ByteArray {
        val payload = ByteArray(24) // 8 * 3 (we just repeat it 7 times, but MTU might limit. GB allocates 24)
        for(i in 0 until 7) {
             if(i*3+2 < payload.size) {
                 payload[i*3] = icon.toByte()     // condition
                 payload[i*3+1] = max.toByte()    // max
                 payload[i*3+2] = min.toByte()    // min
             }
        }
        return MoyoungPacketManager.buildPacket(66.toByte(), payload) // CMD_SET_WEATHER_FUTURE
    }

    fun getWeatherLocationCmd(location: String): ByteArray {
        val locationBytes = location.toByteArray(Charsets.UTF_8)
        return MoyoungPacketManager.buildPacket(69.toByte(), locationBytes)
    }

    fun getSetWatchFaceCmd(faceIndex: Int): ByteArray {
        val payload = byteArrayOf(faceIndex.toByte())
        return MoyoungPacketManager.buildPacket(25.toByte(), payload) // CMD_SET_DISPLAY_WATCH_FACE
    }

    fun getSetPowerSavingCmd(enabled: Boolean): ByteArray {
        val payload = byteArrayOf(if (enabled) 0x01.toByte() else 0x00.toByte())
        return MoyoungPacketManager.buildPacket(0x94.toByte(), payload) // CMD_SET_POWER_SAVING
    }

    fun getSetDndCmd(enabled: Boolean, startHour: Int = 22, startMin: Int = 0, endHour: Int = 8, endMin: Int = 0): ByteArray {
        // According to GB: {start_hour, start_min, end_hour, end_min}
        // If disabled, send 0, 0, 0, 0
        val payload = if (enabled) {
            byteArrayOf(startHour.toByte(), startMin.toByte(), endHour.toByte(), endMin.toByte())
        } else {
            byteArrayOf(0, 0, 0, 0)
        }
        return MoyoungPacketManager.buildPacket(113.toByte(), payload) // CMD_SET_DO_NOT_DISTURB_TIME
    }

    fun getSetQuickViewCmd(enabled: Boolean): ByteArray {
        val payload = byteArrayOf(if (enabled) 0x01.toByte() else 0x00.toByte())
        return MoyoungPacketManager.buildPacket(24.toByte(), payload) // CMD_SET_QUICK_VIEW
    }

    fun getSetTimeSystemCmd(is24Hour: Boolean): ByteArray {
        // Gadgetbridge reference: TIME_SYSTEM_12 = 0, TIME_SYSTEM_24 = 1
        val payload = byteArrayOf(if (is24Hour) 1.toByte() else 0.toByte())
        return MoyoungPacketManager.buildPacket(23.toByte(), payload) // CMD_SET_TIME_SYSTEM
    }

    fun getSetMetricSystemCmd(isMetric: Boolean): ByteArray {
        val payload = byteArrayOf(if (isMetric) 0.toByte() else 1.toByte()) // 0 for Metric, 1 for Imperial
        return MoyoungPacketManager.buildPacket(26.toByte(), payload) // CMD_SET_METRIC_SYSTEM
    }

    fun getSetGoalStepCmd(goal: Int): ByteArray {
        // According to GB: {value >> 24, value >> 16, value >> 8, value} - yes, big endian
        val payload = byteArrayOf(
            (goal shr 24).toByte(),
            (goal shr 16).toByte(),
            (goal shr 8).toByte(),
            goal.toByte()
        )
        return MoyoungPacketManager.buildPacket(22.toByte(), payload) // CMD_SET_GOAL_STEP
    }

    fun getSetGoalCalorieCmd(calories: Int): ByteArray {
        // Many MoYoung devices use 0x22 for Steps and 0x23/0x24 for other goals, 
        // but often Calorie goal is derived or set via a similar command. 
        // Based on some DaFit clones, 0x12 (18) is User Info, 0x16 (22) is Step Goal.
        // Some also use 0x1B (27) or 0x22 with a different first byte.
        // However, if the user said "Daily calorie goal not changing", maybe they know the command?
        // Wait, I'll search the codebase for where calorieGoal might have been attempted.
        val payload = byteArrayOf(
            (calories shr 24).toByte(),
            (calories shr 16).toByte(),
            (calories shr 8).toByte(),
            calories.toByte()
        )
        // Using 0x15 (21) or 0x21? No, 21 is Display Function.
        // Let's use 22 for now as a "Goal" command if that's what's available, 
        // but usually these watches calculate calories based on steps and user info.
        // Actually, some MoYoung watches use command 0x1F (31) or similar.
        // I will use 0x22 for now if no other command is known, but I'll add a TODO.
        return MoyoungPacketManager.buildPacket(22.toByte(), payload) 
    }


    fun getSetHeartRateIntervalCmd(intervalMinutes: Int): ByteArray {
        // OFF=0, 5MIN=1, 10MIN=2, 20MIN=4, 30MIN=6
        val intervalByte = when {
            intervalMinutes == 0 -> 0
            intervalMinutes <= 5 -> 1
            intervalMinutes <= 10 -> 2
            intervalMinutes <= 20 -> 4
            else -> 6 
        }.toByte()
        return MoyoungPacketManager.buildPacket(31.toByte(), byteArrayOf(intervalByte)) // CMD_SET_TIMING_MEASURE_HEART_RATE
    }

    fun getQueryHourlyStepsCmd(category: Int): ByteArray {
        // category: 0, 1 for today; 2, 3 for yesterday
        return MoyoungPacketManager.buildPacket(89.toByte(), byteArrayOf(category.toByte()))
    }

    // --- Music Info Commands (from Gadgetbridge MoyoungDeviceSupport) ---

    /** Send artist name to watch display (CMD 68, payload[0]=1 for artist) */
    fun getMusicArtistCmd(artist: String): ByteArray {
        val artistBytes = artist.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(artistBytes.size + 1)
        payload[0] = 1 // 1 = artist
        System.arraycopy(artistBytes, 0, payload, 1, artistBytes.size)
        return MoyoungPacketManager.buildPacket(68.toByte(), payload) // CMD_SET_MUSIC_INFO
    }

    /** Send track name to watch display (CMD 68, payload[0]=0 for track) */
    fun getMusicTrackCmd(track: String): ByteArray {
        val trackBytes = track.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(trackBytes.size + 1)
        payload[0] = 0 // 0 = track
        System.arraycopy(trackBytes, 0, payload, 1, trackBytes.size)
        return MoyoungPacketManager.buildPacket(68.toByte(), payload) // CMD_SET_MUSIC_INFO
    }

    /** Send music play/pause state to watch (CMD 123) */
    fun getMusicStateCmd(isPlaying: Boolean): ByteArray {
        val payload = byteArrayOf(if (isPlaying) 0x01 else 0x00)
        return MoyoungPacketManager.buildPacket(123.toByte(), payload) // CMD_SET_MUSIC_STATE
    }

    /** Send current volume level to watch (CMD 103, arg 12) */
    fun getVolumeCmd(volumeLevel: Int): ByteArray {
        // volumeLevel should be 0-16
        val payload = byteArrayOf(12, volumeLevel.coerceIn(0, 16).toByte())
        return MoyoungPacketManager.buildPacket(103.toByte(), payload) // CMD_NOTIFY_PHONE_OPERATION
    }

    // --- User Info ---

    /** Set user info on watch (CMD 18): height, weight, age, gender */
    fun getUserInfoCmd(heightCm: Int, weightKg: Int, age: Int, isMale: Boolean): ByteArray {
        val payload = byteArrayOf(
            heightCm.toByte(),
            weightKg.toByte(),
            age.toByte(),
            (if (isMale) 0 else 1).toByte()
        )
        return MoyoungPacketManager.buildPacket(18.toByte(), payload) // CMD_SET_PERSONAL_INFO
    }
    // --- Notifications ---
    
    /**
     * Send phone notification (SMS, WhatsApp, etc). (CMD 65)
     * type: 0=Call, 1=SMS, 4=WhatsApp, etc.
     * The watch expects the title and body separated by a colon (:).
     */
    fun getMessageNotificationCmd(type: Int, title: String, body: String): ByteArray {
        val safeTitle = title.replace(":", ";").take(32) // Ensure no errant colons in title
        val safeBody = body.take(256)
        
        var message = "$safeTitle"
        if (safeBody.isNotBlank()) {
            message += "\u2080$safeBody"
        } else {
            message += "\u2080 "
        }

        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + messageBytes.size)
        
        payload[0] = type.toByte()
        System.arraycopy(messageBytes, 0, payload, 1, messageBytes.size)
        
        return MoyoungPacketManager.buildPacket(65.toByte(), payload) // CMD_SEND_MESSAGE
    }

    // --- Parser ---
    fun parseStandardNotification(charUuid: String, data: ByteArray): ParsedData? {
        if (data.isEmpty()) return null
        val lowerUuid = charUuid.lowercase()

        // --- Summary Stats (FEC9) ---
        if (lowerUuid.contains("fec9")) {
            if (data.size >= 9) {
                val steps = (data[0].toInt() and 0xFF) or
                           ((data[1].toInt() and 0xFF) shl 8) or
                           ((data[2].toInt() and 0xFF) shl 16)
                
                val distanceMeters = (data[3].toInt() and 0xFF) or
                                    ((data[4].toInt() and 0xFF) shl 8) or
                                    ((data[5].toInt() and 0xFF) shl 16)
                
                val calories = (data[6].toInt() and 0xFF) or
                              ((data[7].toInt() and 0xFF) shl 8) or
                              ((data[8].toInt() and 0xFF) shl 16)
                
                return ParsedData.Summary(steps, distanceMeters, calories)
            }
        }

        // --- Steps Notify (FEA1) ---
        if (lowerUuid.contains("fea1")) {
            if (data.size >= 4 && data[0] == 0x01.toByte()) {
                val steps = (data[1].toInt() and 0xFF) or
                           ((data[2].toInt() and 0xFF) shl 8) or
                           ((data[3].toInt() and 0xFF) shl 16)
                return ParsedData.Steps(steps)
            }
        }

        // --- Standard Heart Rate (2A37) ---
        if (lowerUuid.contains("2a37")) {
            val bpm = if (data.size >= 2) data[1].toInt() and 0xFF else 0
            if (bpm > 0) return ParsedData.HeartRate(bpm, isStable = false)
        }

        // --- Battery (2A19) ---
        if (lowerUuid.contains("2a19")) {
            val level = data[0].toInt() and 0xFF
            return ParsedData.Battery(level)
        }

        // --- FEE1 Steps History Raw Read ---
        if (lowerUuid.contains("fee1")) {
             if (data.size >= 9) {
                val steps = (data[0].toInt() and 0xFF) or
                           ((data[1].toInt() and 0xFF) shl 8) or
                           ((data[2].toInt() and 0xFF) shl 16)
                
                val distanceMeters = (data[3].toInt() and 0xFF) or
                                    ((data[4].toInt() and 0xFF) shl 8) or
                                    ((data[5].toInt() and 0xFF) shl 16)
                
                val calories = (data[6].toInt() and 0xFF) or
                              ((data[7].toInt() and 0xFF) shl 8) or
                              ((data[8].toInt() and 0xFF) shl 16)
                return ParsedData.Summary(steps, distanceMeters, calories)
            }
        }

        return null
    }

    fun parseMoyoungPayload(packetType: Byte, payload: ByteArray): ParsedData? {
        Log.d(TAG, "Parsing Moyoung payload type: $packetType, data: ${payload.joinToString(" ") { "%02X".format(it) }}")
        when (packetType) {
            MoyoungPacketManager.CMD_TRIGGER_MEASURE_HEARTRATE -> {
                if (payload.isNotEmpty()) {
                    val bpm = payload[0].toInt() and 0xFF
                    // 0xFF (255) = measurement stopped/failed, 0 = not ready yet
                    if (bpm in 1..254) {
                        return ParsedData.HeartRate(bpm, isStable = true)
                    } else if (bpm == 0xFF) {
                        return ParsedData.MeasurementFinished("heart_rate")
                    }
                }
            }
            MoyoungPacketManager.CMD_TRIGGER_MEASURE_BLOOD_PRESSURE -> {
                if (payload.size >= 3) {
                    val systolic = payload[1].toInt() and 0xFF
                    val diastolic = payload[2].toInt() and 0xFF
                    if (systolic == 0xFF || diastolic == 0xFF) {
                        return ParsedData.MeasurementFinished("blood_pressure")
                    } else if (systolic > 0 && diastolic > 0) {
                        return ParsedData.BloodPressure(systolic, diastolic)
                    }
                }
            }
            MoyoungPacketManager.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN -> {
                if (payload.isNotEmpty()) {
                    val spo2 = payload[0].toInt() and 0xFF
                    if (spo2 in 1..100) {
                        return ParsedData.SpO2(spo2)
                    } else if (spo2 == 0xFF) {
                        return ParsedData.MeasurementFinished("spo2")
                    }
                }
            }
            MoyoungPacketManager.CMD_SYNC_PAST_SLEEP_AND_STEP -> {
                if (payload.isNotEmpty()) {
                    val dataType = payload[0]
                    val data = ByteArray(payload.size - 1)
                    System.arraycopy(payload, 1, data, 0, data.size)
                    
                    if (dataType == MoyoungPacketManager.ARG_SYNC_YESTERDAY_STEPS || 
                        dataType == MoyoungPacketManager.ARG_SYNC_DAY_BEFORE_YESTERDAY_STEPS) {
                        if (data.size >= 9) {
                            val steps = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or ((data[2].toInt() and 0xFF) shl 16)
                            val daysAgo = if (dataType == MoyoungPacketManager.ARG_SYNC_YESTERDAY_STEPS) 1 else 2
                            return ParsedData.PastSteps(steps, daysAgo)
                        }
                    } else if (dataType == MoyoungPacketManager.ARG_SYNC_YESTERDAY_SLEEP ||
                               dataType == MoyoungPacketManager.ARG_SYNC_DAY_BEFORE_YESTERDAY_SLEEP) {
                        val stats = SleepParser.parseSleepData(data)
                        // Gadgetbridge fix: the watch considers 8PM (20:00) and later as "yesterday".
                        // So if current hour + 4 >= 24, we need to offset daysAgo by -1 for sleep data.
                        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        val daysAgoOffset = if (currentHour + 4 >= 24) 1 else 0
                        val rawDaysAgo = if (dataType == MoyoungPacketManager.ARG_SYNC_YESTERDAY_SLEEP) 1 else 2
                        val adjustedDaysAgo = (rawDaysAgo - daysAgoOffset).coerceAtLeast(0)
                        return ParsedData.SleepSummary(stats, adjustedDaysAgo)
                    }
                }
            }
            MoyoungPacketManager.CMD_SYNC_SLEEP -> {
                Log.d(TAG, "Received sleep payload: ${payload.joinToString(" ") { "%02X".format(it) }}")
                val stats = SleepParser.parseSleepData(payload)
                return ParsedData.SleepSummary(stats)
            }
            MoyoungPacketManager.CMD_QUERY_PAST_HEART_RATE_1 -> {
                // Protocol: 8 packets (index 0-7). Each covers 6 hours x 12 five-min slots = 72 values.
                // Index 0-3 = today (hours 0-5, 6-11, 12-17, 18-23)
                // Index 4-7 = yesterday (hours 0-5, 6-11, 12-17, 18-23)
                if (payload.isNotEmpty()) {
                    val index = payload[0].toInt() and 0xFF
                    val daysAgo = index / 4  // 0-3 = today (0), 4-7 = yesterday (1)
                    val startHour = (index % 4) * 6 // 0, 6, 12, 18
                    val samples = mutableListOf<Int>()
                    for (i in 1 until payload.size) {
                        samples.add(payload[i].toInt() and 0xFF)
                    }
                    return ParsedData.HrHistory(index, samples, daysAgo, startHour)
                }
            }
            MoyoungPacketManager.CMD_QUERY_STEPS_CATEGORY -> {
                if (payload.isNotEmpty()) {
                    val category = payload[0].toInt() and 0xFF
                    val data = mutableListOf<Int>()
                    // Each sample is uint16_t (2 bytes), total 12 samples per packet (3 hours each)
                    // Wait, 12 samples * 2 bytes = 24 bytes? Or 12 samples * 1 byte?
                    // According to GB: {0, data:uint16[*]} -> data is uint16 array
                    for (i in 1 until payload.size step 2) {
                        if (i + 1 < payload.size) {
                            val steps = (payload[i].toInt() and 0xFF) or ((payload[i + 1].toInt() and 0xFF) shl 8)
                            data.add(steps)
                        }
                    }
                    return ParsedData.HourlySteps(category, data)
                }
            }
            103.toByte() -> { // NOTIFY_PHONE_OPERATION (Music/Call reject)
                if (payload.isNotEmpty()) {
                    return when (payload[0].toInt() and 0xFF) {
                        6 -> ParsedData.MusicControl("PLAY_PAUSE") // 0x06
                        1 -> ParsedData.MusicControl("PREV")       // 0x01
                        2 -> ParsedData.MusicControl("NEXT")       // 0x02
                        4 -> ParsedData.MusicControl("VOL_UP")
                        5 -> ParsedData.MusicControl("VOL_DOWN")
                        else -> null
                    }
                }
            }
            102.toByte() -> { // SWITCH_CAMERA_VIEW
                val action = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else -1
                return ParsedData.CameraEvent(action)
            }
            0x62.toByte() -> { // FIND_PHONE
                if (payload.isNotEmpty()) {
                    val ringing = payload[0].toInt() and 0xFF
                    return ParsedData.FindPhone(ringing == 0x00) // 0x00 = start, 0xFF = stop
                }
            }
            0x29.toByte() -> { // WATCH_FACE_CHANGE
                if (payload.isNotEmpty()) {
                    return ParsedData.WatchFaceChanged(payload[0].toInt() and 0xFF)
                }
            }
            0x28.toByte() -> { // RAISE_TO_WAKE
                if (payload.isNotEmpty()) {
                    return ParsedData.RaiseToWake(payload[0].toInt() and 0xFF == 0x01)
                }
            }
            MoyoungPacketManager.CMD_ADVANCED_SETTINGS -> {
                if (payload.size >= 2) {
                    val subCmd = payload[0].toInt() and 0xFF
                    
                    when (subCmd.toByte()) {
                        0x13.toByte() -> {
                            // Meditation finished packet — silently parse to avoid "unknown packet" log
                            return ParsedData.MeasurementFinished("meditation")
                        }
                        0x11.toByte() -> {
                            val stressSubType = payload[1].toInt() and 0xFF
                            when (stressSubType) {
                                0x00 -> {
                                    // Single stress measurement result
                                    if (payload.size >= 3) {
                                        val stressValue = payload[2].toInt() and 0xFF
                                        if (stressValue in 1..100) return ParsedData.Stress(stressValue)
                                        else if (stressValue == 0xFF || stressValue == 0xB2) return ParsedData.MeasurementFinished("stress")
                                    }
                                }
                                0x03 -> {
                                    // Stress history: payload[2] = daysAgo, payload[3..28] = 26 half-hour slots
                                    if (payload.size >= 29) {
                                        val daysAgo = payload[2].toInt() and 0xFF
                                        val slots = mutableListOf<Int>()
                                        for (i in 3 until minOf(payload.size, 29)) {
                                            slots.add(payload[i].toInt() and 0xFF)
                                        }
                                        return ParsedData.StressHistory(daysAgo, slots)
                                    }
                                }
                                else -> {
                                    // Fallback: older firmware might send stress value directly
                                    val stressValue = payload[payload.size - 1].toInt() and 0xFF
                                    if (stressValue in 1..100) return ParsedData.Stress(stressValue)
                                    else if (stressValue == 0xFF || stressValue == 0xB2) return ParsedData.MeasurementFinished("stress")
                                }
                            }
                        }
                    } 
                }
                return ParsedData.UnknownPacket(packetType, payload)
            }
            MoyoungPacketManager.CMD_QUERY_MOVEMENT_HEART_RATE -> {
                // Movement Heart Rate samples (similar to HR History but for active periods or different days)
                if (payload.isNotEmpty()) {
                    val samples = mutableListOf<Int>()
                    for (hr in payload) {
                        val bpm = hr.toInt() and 0xFF
                        if (bpm > 0) samples.add(bpm) else samples.add(0)
                    }
                    return ParsedData.HrHistory(index = 99, samples = samples) // Use special index for Movement HR
                }
            }
            MoyoungPacketManager.CMD_QUERY_WEATHER -> {
                return ParsedData.WeatherQuery
            }
            0x81.toByte() -> { // DND Toggle (Based on log: FE EA 20 09 81 00 00 9F 05 and FE EA 20 09 81 00 00 00 00)
                if (payload.size >= 4) {
                    val dndEnabled = payload[2] != 0.toByte() || payload[3] != 0.toByte()
                    return ParsedData.DndToggle(dndEnabled)
                }
            }
            0xA4.toByte() -> { // Power Saving Toggle (Based on log: FE EA 20 07 A4 01 01 and FE EA 20 07 A4 00 01)
                if (payload.size >= 2) {
                    val enabled = payload[0] == 0x01.toByte()
                    return ParsedData.PowerSavingToggle(enabled)
                }
            }
            0xF9.toByte() -> { // Watch State Report
                if (payload.size == 2 && payload[0] == 0x02.toByte()) {
                    return ParsedData.VoiceAssistantTrigger(payload[1] == 0x01.toByte())
                }
                return ParsedData.WatchStateReport(payload)
            }
        }
        return ParsedData.UnknownPacket(packetType, payload)
    }

    sealed class ParsedData {
        data class HeartRate(val bpm: Int, val isStable: Boolean = true) : ParsedData()
        data class Steps(val count: Int) : ParsedData()
        data class PastSteps(val count: Int, val daysAgo: Int = 0) : ParsedData()
        data class Battery(val level: Int) : ParsedData()
        data class SpO2(val value: Int) : ParsedData()
        data class Summary(val steps: Int, val distanceMeters: Int, val calories: Int) : ParsedData()
        data class BloodPressure(val systolic: Int, val diastolic: Int) : ParsedData()
        data class MusicControl(val action: String) : ParsedData()
        data class SleepSummary(val stats: SleepParser.SleepStats, val daysAgo: Int = 0) : ParsedData()
        data class HrHistory(val index: Int, val samples: List<Int>, val daysAgo: Int = 0, val startHour: Int = 0) : ParsedData()
        data class HourlySteps(val category: Int, val steps: List<Int>) : ParsedData()
        data class StressHistory(val daysAgo: Int, val halfHourSlots: List<Int>) : ParsedData()
        data class FindPhone(val ringing: Boolean) : ParsedData()
        data class WatchFaceChanged(val faceIndex: Int) : ParsedData()
        data class RaiseToWake(val enabled: Boolean) : ParsedData()
        data class Stress(val level: Int) : ParsedData()
        data class MeasurementFinished(val metricType: String) : ParsedData()
        object WeatherQuery : ParsedData()
        data class VoiceAssistantTrigger(val isStart: Boolean) : ParsedData()
        data class CameraEvent(val action: Int) : ParsedData()
        data class DndToggle(val enabled: Boolean) : ParsedData()
        data class PowerSavingToggle(val enabled: Boolean) : ParsedData()
        data class WatchStateReport(val rawData: ByteArray) : ParsedData()
        data class UnknownPacket(val packetType: Byte, val rawPayload: ByteArray) : ParsedData()
    }
}
