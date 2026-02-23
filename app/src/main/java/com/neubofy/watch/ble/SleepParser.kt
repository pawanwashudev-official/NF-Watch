package com.neubofy.watch.ble

object SleepParser {

    enum class SleepStage(val code: Int) {
        DEEP(1),
        LIGHT(2),
        AWAKE(3),
        REM(4),
        UNKNOWN(0);

        companion object {
            fun fromCode(code: Int): SleepStage {
                return values().find { it.code == code } ?: UNKNOWN
            }
        }
    }

    data class SleepBlock(
        val stage: SleepStage,
        val startHour: Int,
        val startMinute: Int
    ) {
        val totalMinutes: Int
            get() = startHour * 60 + startMinute
    }

    data class SleepStats(
        val totalMinutes: Int,
        val deepMinutes: Int,
        val lightMinutes: Int,
        val awakeMinutes: Int,
        val remMinutes: Int,
        val endHour: Int,
        val endMinute: Int
    )

    /**
     * Parses the sleep data payload (chunks of 3 bytes) and calculates the granular sleep durations.
     * @param data Payload bytes consisting of 3-byte sleep blocks
     * @return Detailed object containing minutes per stage
     */
    fun parseSleepData(data: ByteArray): SleepStats {
        val blocks = mutableListOf<SleepBlock>()

        for (i in 0 until data.size step 3) {
            if (i + 2 < data.size) {
                val type = data[i].toInt() and 0xFF
                val hour = data[i + 1].toInt() and 0xFF
                val minute = data[i + 2].toInt() and 0xFF
                
                blocks.add(SleepBlock(SleepStage.fromCode(type), hour, minute))
            }
        }

        if (blocks.isEmpty()) return SleepStats(0, 0, 0, 0, 0, 12, 0)

        var deepMins = 0
        var lightMins = 0
        var awakeMins = 0
        var remMins = 0

        for (i in 0 until blocks.size - 1) {
            val currentBlock = blocks[i]
            val nextBlock = blocks[i + 1]

            var currentMins = currentBlock.totalMinutes
            var nextMins = nextBlock.totalMinutes

            if (nextMins < currentMins) {
                nextMins += 24 * 60
            }

            val duration = nextMins - currentMins

            when (currentBlock.stage) {
                SleepStage.DEEP -> deepMins += duration
                SleepStage.LIGHT -> lightMins += duration
                SleepStage.AWAKE -> awakeMins += duration
                SleepStage.REM -> remMins += duration
                else -> {}
            }
        }

        val totalValidSleep = deepMins + lightMins + awakeMins + remMins
        val lastBlock = blocks.lastOrNull()
        val endHour = lastBlock?.startHour ?: 12
        val endMinute = lastBlock?.startMinute ?: 0
        return SleepStats(totalValidSleep, deepMins, lightMins, awakeMins, remMins, endHour, endMinute)
    }
}
