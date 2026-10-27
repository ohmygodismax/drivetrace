package dev.drivetrace

/** A demonstrative load estimate, not a measured OBD PID. Missing data stays unknown. */
val Frame.estimatedLoadPct: Double?
    get() = if(throttle == null || rpm == null) null else (throttle * 0.8 + rpm / 4500.0 * 20).coerceIn(0.0, 100.0)

enum class LoadBand(val label: String, val hex: String) {
    NORMAL("Обычная", "#91B3D0"), ELEVATED("Повышенная", "#DBAC69"), HIGH("Высокая", "#E27B70"), UNKNOWN("Нет данных", "#737E8B");
    companion object {
        fun of(load: Double?): LoadBand = when { load == null -> UNKNOWN; load >= 70 -> HIGH; load >= 45 -> ELEVATED; else -> NORMAL }
    }
}
data class RoadSegment(val points: List<Pair<Double, Double>>, val band: LoadBand)
fun roadSegments(frames: List<Frame>, routeOffsetKm: Double?): List<RoadSegment> = frames.zipWithNext { a, b ->
    val geometry = if(routeOffsetKm != null) DemoRoute.path(routeOffsetKm + a.distance, routeOffsetKm + b.distance)
        else listOf(a.latitude to a.longitude, b.latitude to b.longitude)
    val load = if(a.estimatedLoadPct == null || b.estimatedLoadPct == null) null else maxOf(a.estimatedLoadPct!!, b.estimatedLoadPct!!)
    RoadSegment(geometry, LoadBand.of(load))
}
