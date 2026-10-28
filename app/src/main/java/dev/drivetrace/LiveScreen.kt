package dev.drivetrace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val RecordingColor = Color(0xFFEB9187)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Live(engine: Engine, state: LiveState, onOpenTrip: (Long) -> Unit = {}, onHealth: () -> Unit = {}) {
    val miles by engine.miles.collectAsStateWithLifecycle()
    val fleet by engine.fleetTrips.collectAsStateWithLifecycle()
    val gpsEnabled by engine.gpsEnabled.collectAsStateWithLifecycle()
    val gpsStatus by engine.gps.status.collectAsStateWithLifecycle()
    val saved by engine.lastSavedTrip.collectAsStateWithLifecycle()
    val alerts = remember(fleet, state.vehicle?.id) { healthSignals(fleet.filter { it.vehicleId == state.vehicle?.id }) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var scenarios by remember { mutableStateOf(false) }
    var gpsSettings by remember { mutableStateOf(false) }
    var identity by remember { mutableStateOf(false) }
    val demo = state.vehicle?.simulated == true
    val frame = state.frame
    val supported = state.vehicle?.metrics.orEmpty()
    val unit = if(miles) "ми" else "км"
    val distance = (if(state.recording) engine.recordingDistance else frame.distance) * if(miles) 0.621371 else 1.0
    val elapsed = if(state.recording) engine.recordingElapsed else frame.elapsed
    val gpsLabel = when {
        demo -> "Демо"
        !gpsEnabled -> "Выключен"
        !engine.gps.hasPrecisePermission() -> "Нет доступа"
        gpsStatus.startsWith("GPS ·") -> gpsStatus.substringAfter("точность ")
        gpsStatus.startsWith("Включите") -> "Выключен в Android"
        else -> "Нет сигнала"
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.vehicle?.name ?: "Автомобиль", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton({ identity = true }) { Icon(Icons.Outlined.Info, "Информация об автомобиле", tint = Muted, modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp))) {
                StatusCell(if(demo) "Источник" else "ELM327", when { !state.connected -> "Нет связи"; !state.running -> "Пауза"; frame.rpm == null && frame.speed == null -> "Ожидание"; demo -> "Симулятор"; else -> "Подключён" },
                    if(demo) Icons.Outlined.Science else Icons.Outlined.BluetoothConnected, if(state.running) Secondary else Amber, Modifier.weight(1f))
                StatusCell("GPS", gpsLabel, Icons.Outlined.MyLocation, if(demo || !gpsEnabled) Muted else if(gpsStatus.startsWith("GPS ·")) Secondary else Amber,
                    Modifier.weight(1f), if(demo) null else ({ gpsSettings = true }))
                StatusCell("Запись", if(state.recording) clockText(elapsed) else "Выключена", Icons.Outlined.FiberManualRecord,
                    if(state.recording) RecordingColor else Muted, Modifier.weight(1f))
            }
            state.error?.let { Text(it, color = Amber, fontSize = 13.sp) }
            if((frame.coolant ?: 0.0) > 110 || demo && frame.rpm == null) {
                Text(if(frame.rpm == null) "Нет свежих показаний · тестовый обрыв" else "Высокая температура охлаждающей жидкости${if(demo) " · демо" else ""}",
                    color = Amber, fontSize = 13.sp, modifier = Modifier.background(Panel, RoundedCornerShape(6.dp)).padding(12.dp))
            }
            CardBox(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Скорость", fontSize = 13.sp, color = Muted)
                    Text(if(state.running) "Показания сейчас" else "Поток на паузе", fontSize = 12.sp, color = if(state.running) Secondary else Amber)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(frame.speed?.let { "%.0f".format(it * if(miles) 0.621371 else 1.0) } ?: "—", fontFamily = Instruments,
                            fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-3).sp)
                        Text(if(miles) "миль/ч" else "км/ч", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(frame.rpm?.let { "%,d".format(it).replace(',', ' ') } ?: "—", fontFamily = Instruments, fontSize = 27.sp)
                        Text(if(ObdMetric.RPM !in supported) "Не поддерживается" else if(frame.rpm == null) "Нет данных" else "об/мин", fontSize = 12.sp, color = Muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EssentialMetric("Охлаждение", frame.coolant?.let { "%.0f".format(it) }, "°C", ObdMetric.COOLANT in supported,
                    (frame.coolant ?: 0.0) > 110, Modifier.weight(1f))
                EssentialMetric("Напряжение", frame.voltage?.let { "%.1f".format(it) }, "В", ObdMetric.VOLTAGE in supported,
                    false, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(state.recording) "Текущая поездка" else "С подключения", fontSize = 12.sp, color = if(state.recording) RecordingColor else Muted)
                    Text("%.2f %s".format(distance, unit), fontFamily = Instruments, fontSize = 23.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if(state.recording) "Время записи" else "Время подключения", fontSize = 12.sp, color = Muted)
                    Text(clockText(elapsed), fontFamily = Instruments, fontSize = 23.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
            saved?.takeIf { !state.recording && it.vehicleId == state.vehicle?.id }?.let { trip ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp)).clickable { onOpenTrip(trip.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircleOutline, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("Поездка сохранена", fontSize = 13.sp, color = Secondary)
                        Text("${clockText(trip.duration)} · %.2f %s".format(trip.distance * if(miles) 0.621371 else 1.0, unit), fontSize = 12.sp, color = Muted)
                    }
                    Text("Открыть", fontSize = 12.sp, color = Accent)
                }
            }
            if(alerts.isNotEmpty()) TextButton(onHealth, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.MonitorHeart, null, tint = Amber, modifier = Modifier.size(18.dp))
                Text("Наблюдения по автомобилю · ${alerts.size}", color = Amber, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp)).clickable { expanded = !expanded }
                .semantics { stateDescription = if(expanded) "Развёрнуто" else "Свёрнуто" }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Все показатели", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${supported.size}/${ObdMetric.entries.size}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(end = 8.dp))
                Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            if(expanded) {
                LoadReading(frame)
                TelemetryGrid(frame, supported)
                CardBox(Modifier.fillMaxWidth()) {
                    Text("Скорость за ${clockText((state.trail.lastOrNull()?.elapsed ?: 0) - (state.trail.firstOrNull()?.elapsed ?: 0))}", fontSize = 13.sp, color = Muted)
                    Sparkline(state.trail, Modifier.fillMaxWidth().height(70.dp).padding(top = 12.dp))
                }
            }
            if(demo) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ scenarios = true }, enabled = !state.recording, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Science, null, modifier = Modifier.size(18.dp))
                    Text(state.scenario.title, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(18.dp))
                }
                IconButton(engine::toggle) { Icon(if(state.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if(state.running) "Приостановить симуляцию" else "Продолжить симуляцию") }
                IconButton(engine::reset, enabled = !state.recording) { Icon(Icons.Outlined.RestartAlt, "Сбросить симуляцию") }
                TextButton(engine::rate) { Text("${state.rate}×", fontSize = 14.sp) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().background(Bg).padding(16.dp, 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(engine::record, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = state.connected && state.error == null,
                shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = if(state.recording) Color(0xFF4B2E2D) else Accent, contentColor = if(state.recording) Color(0xFFFFDAD4) else Bg)) {
                Icon(if(state.recording) Icons.Outlined.StopCircle else Icons.Outlined.FiberManualRecord, null, Modifier.size(20.dp))
                Text(if(state.recording) "Сохранить поездку" else "Начать запись поездки", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
            }
            Text(when { state.recording && !demo -> "Запись продолжается при выключенном экране"; state.recording -> "Сохраняем демонстрационную поездку"; saved?.vehicleId == state.vehicle?.id -> "Можно начать новую поездку"; else -> "Показания без записи" }, fontSize = 12.sp, color = Muted)
        }
    }
    if(gpsSettings) ModalBottomSheet(onDismissRequest = { gpsSettings = false }, containerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Маршрут поездки", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            GpsPanel(engine)
            TextButton({ gpsSettings = false }, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
        }
    }
    if(identity) AlertDialog(onDismissRequest = { identity = false }, title = { Text(state.vehicle?.name ?: "Автомобиль") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VIN", color = Muted, fontSize = 13.sp)
            Text(state.vehicle?.identity ?: "Не задан", fontFamily = Instruments, fontSize = 14.sp)
            Text(if(demo) "Демонстрационные показания и маршрут" else "ELM327 · Bluetooth Classic", fontSize = 13.sp, color = Muted)
        } }, confirmButton = { TextButton({ identity = false }) { Text("Закрыть") } })
    if(scenarios) AlertDialog(onDismissRequest = { scenarios = false }, title = { Text("Сценарий поездки") }, text = {
        Column { Scenario.entries.forEach { scenario ->
            Column(Modifier.fillMaxWidth().clickable { engine.scenario(scenario); scenarios = false }.padding(vertical = 12.dp)) {
                Text(scenario.title, fontSize = 15.sp, color = if(scenario == state.scenario) Accent else Ink)
                Text(scenario.subtitle, fontSize = 12.sp, color = Muted)
            }
        } }
    }, confirmButton = { TextButton({ scenarios = false }) { Text("Закрыть") } })
}

@Composable private fun RowScope.StatusCell(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val interaction = if(onClick != null) Modifier.clickable(onClickLabel = "Настроить GPS", onClick = onClick) else Modifier
    Column(modifier.then(interaction).padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(title, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if(onClick != null) Icon(Icons.Outlined.ExpandMore, null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(value, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun EssentialMetric(title: String, value: String?, unit: String, supported: Boolean, warning: Boolean, modifier: Modifier) {
    CardBox(modifier) {
        Text(title, fontSize = 13.sp, color = Muted)
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", fontFamily = Instruments, fontSize = 31.sp, color = if(warning) Amber else Ink)
            if(value != null) Text(unit, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp))
        }
        if(value == null) Text(if(supported) "Нет свежих данных" else "Не поддерживается", fontSize = 12.sp, color = Muted)
    }
}
