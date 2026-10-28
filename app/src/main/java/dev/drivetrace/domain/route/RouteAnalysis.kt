package dev.drivetrace.domain.route

import dev.drivetrace.data.local.TripEvent
import dev.drivetrace.model.Frame
import kotlin.math.abs

/** Snap distance boundaries to recorded samples, retaining all stationary samples at a boundary. */
fun routeSelection(frames: List<Frame>, startKm: Double, endKm: Double): IntRange {
    if(frames.isEmpty()) return IntRange.EMPTY
    val start = frames.indexOfFirst { it.distance >= startKm - 0.000001 }.let { if(it < 0) frames.lastIndex else it }
    val end = frames.indexOfLast { it.distance <= endKm + 0.000001 }.coerceAtLeast(0)
    if(start <= end) return start..end
    val nearest = frames.indices.minBy { abs(frames[it].distance - (startKm + endKm) / 2) }
    return nearest..nearest
}

/** Use the latest recorded location only while it is close to the event time. */
fun eventFrameIndex(frames: List<Frame>, event: TripEvent): Int? {
    val index = frames.indexOfLast { it.elapsed <= event.elapsed }
    return index.takeIf { it >= 0 && frames[it].hasLocation && event.elapsed - frames[it].elapsed <= 15_000 }
}

data class RouteSummary(
    val distanceKm: Double, val durationMs: Long, val averageSpeed: Double?,
    val maxSpeed: Double?, val maxRpm: Int?, val maxLoad: Double?
)

fun routeSummary(frames: List<Frame>): RouteSummary {
    var speedIntegral = 0.0
    var measuredMs = 0L
    frames.zipWithNext { a, b ->
        val dt = b.elapsed - a.elapsed
        if(dt in 1..15_000 && a.speed != null && b.speed != null) {
            speedIntegral += (a.speed + b.speed) / 2 * dt
            measuredMs += dt
        }
    }
    return RouteSummary(
        if(frames.isEmpty()) 0.0 else (frames.last().distance - frames.first().distance).coerceAtLeast(0.0),
        if(frames.isEmpty()) 0L else (frames.last().elapsed - frames.first().elapsed).coerceAtLeast(0L),
        if(measuredMs > 0) speedIntegral / measuredMs else null,
        frames.mapNotNull { it.speed }.maxOrNull(), frames.mapNotNull { it.rpm }.maxOrNull(),
        frames.mapNotNull { it.estimatedLoadPct }.maxOrNull()
    )
}
