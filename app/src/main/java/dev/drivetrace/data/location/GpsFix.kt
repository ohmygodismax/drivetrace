package dev.drivetrace.data.location

import dev.drivetrace.model.Frame

/** GPS age uses the same monotonic clock as Android Location, independent of wall-clock changes. */
data class GpsFix(val latitude: Double, val longitude: Double, val accuracyM: Float, val timestamp: Long, val elapsedRealtimeMs: Long) {
    fun usable(nowMs: Long): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 && accuracyM.isFinite() &&
        accuracyM in 0f..100f && nowMs - elapsedRealtimeMs in 0L..10_000L
}

fun Frame.withGps(fix: GpsFix?, nowMs: Long): Frame {
    if(simulated) return this
    val current = fix?.takeIf { it.usable(nowMs) }
    return copy(latitude = current?.latitude ?: 0.0, longitude = current?.longitude ?: 0.0,
        hasLocation = current != null, locationAccuracyM = current?.accuracyM, locationTimestamp = current?.timestamp)
}
