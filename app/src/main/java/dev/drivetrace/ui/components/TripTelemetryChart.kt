package dev.drivetrace.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.drivetrace.core.format.clockText
import dev.drivetrace.model.Frame
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.value
import dev.drivetrace.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

@Composable fun TripTelemetryChart(frames: List<Frame>) {
    var metricName by rememberSaveable { mutableStateOf(ObdMetric.SPEED.name) }
    val metric = ObdMetric.valueOf(metricName)
    val ordered = remember(frames) { frames.sortedBy { it.elapsed } }
    val values = remember(ordered, metric) { ordered.mapNotNull { it.value(metric)?.takeIf(Double::isFinite) } }
    var selectedTime by remember(frames, metric) { mutableStateOf<Long?>(null) }
    CardBox(Modifier.fillMaxWidth()) {
        Text("Показатели по времени", fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(ObdMetric.SPEED, ObdMetric.RPM) + ObdMetric.entries.filter { it != ObdMetric.SPEED && it != ObdMetric.RPM }).forEach { option ->
                FilterChip(selected = metric == option, onClick = { metricName = option.name }, label = { Text(option.title, fontSize = 11.sp) })
            }
        }
        Text("${metric.title} · ${metric.unit}", fontSize = 14.sp, color = Accent)
        if(values.isEmpty()) Text("В записи нет данных: ${metric.title.lowercase()}. Выберите другой показатель.", color = Muted, fontSize = 12.sp)
        else {
            val low = values.min(); val high = values.max()
            val margin = max((high - low) * .1, .5)
            val bottom = low - margin
            val span = high - low + 2 * margin
            val start = ordered.first().elapsed; val end = ordered.last().elapsed
            val duration = (end - start).coerceAtLeast(1)
            val selected = selectedTime?.let { time -> ordered.minByOrNull { abs(it.elapsed - time) } }
            Text("Мин %.1f · среднее по замерам %.1f · макс %.1f %s".format(low, values.average(), high, metric.unit), fontSize = 11.sp, color = Muted)
            Text("%.1f %s".format(bottom + span, metric.unit), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
                contentDescription = "${metric.title}: минимум $low, максимум $high ${metric.unit}; от ${clockText(start)} до ${clockText(end)}. Нажмите для просмотра замера."
            }.pointerInput(start, end, metric) {
                detectTapGestures { point -> selectedTime = start + (point.x / size.width.coerceAtLeast(1) * duration).toLong().coerceIn(0, duration) }
            }) {
                repeat(4) { i -> val y = size.height * i / 3; drawLine(Line, Offset(0f, y), Offset(size.width, y)) }
                val path = Path(); var previous: Frame? = null
                ordered.forEach { frame ->
                    val value = frame.value(metric)?.takeIf(Double::isFinite)
                    if(value == null) previous = null
                    else {
                        val x = size.width * (frame.elapsed - start).toFloat() / duration
                        val y = size.height * (1 - (value - bottom) / span).toFloat()
                        if(previous == null || frame.elapsed - previous!!.elapsed > 10000) {
                            path.moveTo(x, y)
                            drawCircle(Accent, 2.dp.toPx(), Offset(x, y))
                        } else path.lineTo(x, y)
                        previous = frame
                    }
                }
                drawPath(path, Accent, style = Stroke(2.dp.toPx()))
                selected?.let { frame ->
                    val x = size.width * (frame.elapsed - start).toFloat() / duration
                    drawLine(Amber, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    frame.value(metric)?.let { value -> drawCircle(Amber, 4.dp.toPx(), Offset(x, size.height * (1 - (value - bottom) / span).toFloat())) }
                }
            }
            Text("%.1f %s".format(bottom, metric.unit), color = Muted, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clockText(start), color = Muted, fontSize = 11.sp)
                Text(clockText(end), color = Muted, fontSize = 11.sp)
            }
            Text(selected?.let { frame -> "${clockText(frame.elapsed)} · " + (frame.value(metric)?.let { "%.1f %s".format(it, metric.unit) } ?: "Нет данных") } ?: "Нажмите на график, чтобы посмотреть замер", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 8.dp))
            Text("Разрывы — отсутствующие замеры или интервал более 10 с. Показатели ЭБУ обновляются с разной частотой.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
        }
    }
}
