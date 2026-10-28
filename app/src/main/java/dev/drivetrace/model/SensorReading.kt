package dev.drivetrace.model

/** Monotonic time, independent of wall-clock changes. A null value is a completed request without data. */
data class SensorReading(val value: Double?, val receivedAt: Long) {
    fun isFresh(now: Long): Boolean = value != null && now - receivedAt in 0 until 15000
}
