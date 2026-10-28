package dev.drivetrace.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.value
import dev.drivetrace.telemetry.LiveState
import dev.drivetrace.ui.theme.*
import kotlinx.coroutines.delay

@Composable fun SensorPanel(state: LiveState) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    val supported = state.vehicle?.metrics.orEmpty()
    val demo = state.vehicle?.simulated == true
    Text("Датчики и данные ЭБУ", fontSize = 22.sp)
    Text(if(demo) "Симуляция · ответы датчиков синтетические" else "Поддержка заявлена ЭБУ; ответ проверяется отдельным запросом.", color = Muted, fontSize = 12.sp)
    Text("Поддерживается ${supported.size} из ${ObdMetric.entries.size}. Все поддерживаемые показатели опрашиваются и сохраняются при записи поездки. Обороты, скорость, нагрузка и дроссель опрашиваются чаще остальных. Ответы старше 15 секунд считаются устаревшими.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    ObdMetric.entries.forEach { metric ->
        val reading = state.sensors[metric]
        val value = if(demo) state.frame.value(metric) else reading?.value
        val fresh = if(demo) value != null else reading?.isFresh(now) == true
        val status = when {
            metric !in supported -> "Не поддерживается"
            !state.connected -> "Нет связи"
            !state.running -> "Опрос на паузе"
            fresh -> if(demo) "Демо · есть данные" else "Отвечает"
            reading == null && !demo -> "Ожидание проверки"
            value != null -> "Устаревший ответ"
            else -> "Нет данных"
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if(fresh) "%.1f %s".format(value, metric.unit) else "—", fontFamily = Instruments, fontSize = 12.sp)
            }
            Text("PID %02X · %s".format(metric.pid, status), color = if(metric !in supported) Muted else if(fresh) Secondary else Amber, fontSize = 11.sp)
            if(metric in supported) Text(when(metric) {
                ObdMetric.COOLANT, ObdMetric.OIL, ObdMetric.LTFT, ObdMetric.VOLTAGE -> "Графики поездки и сравнение прогретых поездок"
                ObdMetric.LOAD, ObdMetric.INTAKE -> "Графики и подбор сопоставимых условий"
                ObdMetric.FUEL_RATE -> "График и расчёт расхода за поездку"
                else -> "Приборы и график поездки"
            }, color = Muted, fontSize = 10.sp)
        }
    }
}
