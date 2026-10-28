package dev.drivetrace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun RouteRangeControls(frames: List<Frame>, start: Float, end: Float, miles: Boolean, onChange: (Float, Float) -> Unit) {
    val first = frames.firstOrNull() ?: return
    val last = frames.last()
    val total = (last.distance - first.distance).coerceAtLeast(0.0).toFloat()
    val selection = routeSelection(frames, first.distance + start, first.distance + end)
    val a = frames[selection.first]
    val b = frames[selection.last]
    val factor = if(miles) 0.621371 else 1.0
    val unit = if(miles) "ми" else "км"
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Участок маршрута", fontSize = 14.sp)
            TextButton(onClick = { onChange(0f, total) }, enabled = start > 0f || end < total, contentPadding = PaddingValues(0.dp)) { Text("Сбросить", fontSize = 12.sp) }
        }
        Text("%.2f–%.2f %s · %.2f %s · %s".format((a.distance - first.distance) * factor, (b.distance - first.distance) * factor, unit, (b.distance - a.distance) * factor, unit, clockText(b.elapsed - a.elapsed)), fontSize = 12.sp, color = Accent)
        if(total > 0f) RangeSlider(
            value = start..end, onValueChange = { onChange(it.start, it.endInclusive) },
            valueRange = 0f..total,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Начало и конец участка по расстоянию" }
        ) else Text("Нет пройденной дистанции. Показания доступны по времени.", fontSize = 12.sp, color = Muted)
        Text("Перемещайте границы участка. Нажмите на трек, чтобы посмотреть показания.", fontSize = 11.sp, color = Muted)
    }
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

@Composable
fun RouteSummaryCard(frames: List<Frame>, miles: Boolean) {
    val summary = androidx.compose.runtime.remember(frames) { routeSummary(frames) }
    val factor = if(miles) 0.621371 else 1.0
    val speedUnit = if(miles) "миль/ч" else "км/ч"
    CardBox(Modifier.fillMaxWidth()) {
        Text("Сводка участка", fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniValue("ДИСТАНЦИЯ", "%.2f".format(summary.distanceKm * factor), if(miles) "ми" else "км")
            MiniValue("ВРЕМЯ", clockText(summary.durationMs), "мин : сек")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniValue("СР. СКОРОСТЬ", summary.averageSpeed?.let { "%.0f".format(it * factor) } ?: "—", speedUnit)
            MiniValue("МАКС. СКОРОСТЬ", summary.maxSpeed?.let { "%.0f".format(it * factor) } ?: "—", speedUnit)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniValue("ПИК ОБОРОТОВ", summary.maxRpm?.toString() ?: "—", "об/мин")
            MiniValue("ПИК НАГРУЗКИ", summary.maxLoad?.let { "%.0f".format(it) } ?: "—", "%")
        }
        Spacer(Modifier.height(8.dp))
        Text("Средняя скорость — по времени доступных замеров. Пропуски более 15 с исключены. Нагрузка — ЭБУ или оценка для симуляции.", fontSize = 10.sp, color = Muted)
    }
}
