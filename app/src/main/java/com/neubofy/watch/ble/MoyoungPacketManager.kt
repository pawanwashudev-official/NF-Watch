package com.neubofy.watch.ble

import android.util.Log

class MoyoungPacketManager {
    private var buffer = ByteArray(0)

    fun append(fragment: ByteArray): List<Pair<Byte, ByteArray>> {
        buffer += fragment
        val packets = mutableListOf<Pair<Byte, ByteArray>>()
        
        while (buffer.size >= 5) {
            if (buffer[0] != 0xFE.toByte() || buffer[1] != 0xEA.toByte()) {
                Log.e("MoyoungPacketManager", "Corrupted header! Clearing buffer. Data: ${buffer.joinToString { "%02X".format(it) }}")
                buffer = ByteArray(0)
                break
            }
            
            val lenH = (buffer[2].toInt() and 0x0F)
            val lenL = buffer[3].toInt() and 0xFF
            val totalLength = (lenH shl 8) or lenL
            
            if (buffer.size >= totalLength) {
                val packetType = buffer[4]
                val payload = ByteArray(totalLength - 5)
                System.arraycopy(buffer, 5, payload, 0, payload.size)
                
                // Remove parsed packet from buffer
                buffer = buffer.drop(totalLength).toByteArray()
                packets.add(Pair(packetType, payload))
            } else {
                break // Wait for more fragments
            }
        }
        
        return packets
    }
    
    companion object {
        fun buildPacket(packetType: Byte, payload: ByteArray): ByteArray {
            val totalLength = payload.size + 5
            val packet = ByteArray(totalLength)
            packet[0] = 0xFE.toByte()
            packet[1] = 0xEA.toByte()
            // Base 16 (0x10) for standard packets
            packet[2] = (16 + (totalLength shr 8)).toByte()
            packet[3] = (totalLength and 0xFF).toByte()
            packet[4] = packetType
            System.arraycopy(payload, 0, packet, 5, payload.size)
            return packet
        }
        
        // Commands
        const val CMD_SYNC_TIME: Byte = 49
        const val CMD_SYNC_SLEEP: Byte = 50
        const val CMD_SYNC_PAST_SLEEP_AND_STEP: Byte = 51
        const val CMD_QUERY_PAST_HEART_RATE_1: Byte = 53 // decimal! 0x35, NOT 0x53
        const val CMD_QUERY_MOVEMENT_HEART_RATE: Byte = 55
        const val CMD_TRIGGER_MEASURE_HEARTRATE: Byte = 109
        const val CMD_TRIGGER_MEASURE_BLOOD_PRESSURE: Byte = 105
        const val CMD_TRIGGER_MEASURE_BLOOD_OXYGEN: Byte = 107
        const val CMD_START_STOP_MEASURE_DYNAMIC_RATE: Byte = 104
        const val CMD_SET_WEATHER: Byte = 0x13
        const val CMD_QUERY_WEATHER: Byte = 0x64
        const val CMD_ADVANCED_SETTINGS: Byte = 0xB9.toByte()
        const val CMD_WATCH_STATE_REPORT: Byte = 0xF9.toByte()
        const val CMD_QUERY_STEPS_CATEGORY: Byte = 89
        const val CMD_SET_ALARM_CLOCK: Byte = 17
        const val CMD_QUERY_ALARM_CLOCK: Byte = 33
        const val CMD_QUERY_DEVICE_VERSION: Byte = 46
        const val CMD_QUERY_DISPLAY_DEVICE_FUNCTION: Byte = 37
        const val CMD_SET_NOTIFICATION_SWITCH: Byte = 33
        
        // Arguments
        const val ARG_SYNC_YESTERDAY_STEPS: Byte = 1
        const val ARG_SYNC_DAY_BEFORE_YESTERDAY_STEPS: Byte = 2
        const val ARG_SYNC_YESTERDAY_SLEEP: Byte = 3
        const val ARG_SYNC_DAY_BEFORE_YESTERDAY_SLEEP: Byte = 4

        fun getCommandName(cmd: Byte): String {
            return when (cmd) {
                CMD_SYNC_TIME -> "SYNC_TIME"
                CMD_SYNC_SLEEP -> "SYNC_SLEEP"
                CMD_SYNC_PAST_SLEEP_AND_STEP -> "SYNC_PAST_DATA"
                CMD_QUERY_PAST_HEART_RATE_1 -> "QUERY_HR_HISTORY_0x53"
                CMD_QUERY_MOVEMENT_HEART_RATE -> "QUERY_MOVEMENT_HR"
                CMD_TRIGGER_MEASURE_HEARTRATE -> "MEASURE_HR"
                CMD_TRIGGER_MEASURE_BLOOD_PRESSURE -> "MEASURE_BP"
                CMD_TRIGGER_MEASURE_BLOOD_OXYGEN -> "MEASURE_SPO2"
                CMD_START_STOP_MEASURE_DYNAMIC_RATE -> "DYNAMIC_HR"
                CMD_SET_WEATHER -> "SET_WEATHER"
                CMD_QUERY_WEATHER -> "QUERY_WEATHER"
                CMD_ADVANCED_SETTINGS -> "ADVANCED_SETTINGS_0xB9"
                103.toByte() -> "PHONE_OPERATION_MUSIC_CALL"
                102.toByte() -> "CAMERA_VIEW"
                0x62.toByte() -> "FIND_PHONE"
                0x29.toByte() -> "WATCH_FACE_CHANGE"
                0x28.toByte() -> "RAISE_TO_WAKE"
                CMD_WATCH_STATE_REPORT -> "WATCH_STATE_REPORT_0xF9"
                CMD_QUERY_STEPS_CATEGORY -> "QUERY_STEPS_HOURLY"
                else -> "UNKNOWN_CMD_0x${"%02X".format(cmd)}"
            }
        }
    }
}
