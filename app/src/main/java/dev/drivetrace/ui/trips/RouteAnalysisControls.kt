package dev.drivetrace.ui.trips

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.drivetrace.core.format.clockText
import dev.drivetrace.domain.route.routeSelection
import dev.drivetrace.domain.route.routeSummary
import dev.drivetrace.model.Frame
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.MiniValue
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Muted

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
