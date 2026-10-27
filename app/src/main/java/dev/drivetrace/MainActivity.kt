package dev.drivetrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

val Bg = Color(0xFF111316)
val Panel = Color(0xFF191D22)
val Line = Color(0xFF2B323B)
val Accent = Color(0xFF97B7D2)
val Secondary = Color(0xFF94AD9D)
val Muted = Color(0xFF949EAB)
val Ink = Color(0xFFE8EDF2)
val Amber = Color(0xFFDBAC69)
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { MaterialTheme(typography = TraceTypography, colorScheme = darkColorScheme(primary = Accent, onPrimary = Bg, secondary = Secondary, background = Bg, surface = Panel, onSurface = Ink, onBackground = Ink, outline = Line, surfaceVariant = Color(0xFF2B323B), onSurfaceVariant = Muted)) { App((application as DriveTraceApplication).engine) } }
    }
}
@Composable fun App(engine: Engine) {
    val live by engine.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("live") }
    var replayId by rememberSaveable { mutableStateOf<Long?>(null) }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(!live.connected) Connection(engine) { page = "live"; replayId = null; engine.connect() }
            else {
                Row(Modifier.fillMaxWidth().padding(22.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Brand(Modifier.weight(1f)); Badge("SIMULATED", Accent)
                }
                Box(Modifier.weight(1f)) {
                    when {
                        replayId != null -> Replay(engine, replayId!!) { replayId = null }
                        page == "live" -> Live(engine, live)
                        page == "trips" -> Trips(engine) { replayId = it }
                        else -> Settings(engine)
                    }
                }
                if(replayId == null) Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, Line)).padding(vertical = 10.dp)) {
                    listOf(Triple("live", "Приборы", Icons.Outlined.Speed), Triple("trips", "Поездки", Icons.Outlined.Route), Triple("settings", "Настройки", Icons.Outlined.Tune)).forEach { (id, title, icon) ->
                        Column(Modifier.weight(1f).clickable { page = id }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(icon, title, tint = if(page == id) Accent else Muted, modifier = Modifier.size(22.dp)); Text(title, color = if(page == id) Accent else Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DriveTrace", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
        Box(Modifier.width(1.dp).height(16.dp).background(Line))
        Text("бортовой журнал", color = Muted, fontSize = 10.sp)
    }
}
@Composable fun Badge(text: String, color: Color = Secondary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(when(text) { "SIMULATED" -> "Симуляция"; "LIVE" -> "Поток"; "SIMULATOR" -> "Симулятор"; else -> text }, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
@Composable fun Eyebrow(text: String) { Text(text.lowercase().replaceFirstChar { it.titlecase() }, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp) }
@Composable fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp); Text(subtitle, fontSize = 12.sp, color = Muted) } }
@Composable fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.clip(RoundedCornerShape(6.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(6.dp)).padding(16.dp), content = content) }
@Composable fun Action(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.height(50.dp), enabled = enabled, shape = RoundedCornerShape(5.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Connection(engine: Engine, onConnected: () -> Unit) {
    val demo by engine.demoMode.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf(false) }
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var check by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(demo) { stage = 0 }
    LaunchedEffect(stage) {
        if(stage == 1) { delay(1400); stage = if(demo) 2 else 5 }
        if(stage == 3) { check = 0; repeat(3) { delay(550); check = it + 1 }; stage = 4 }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DriveTrace", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton({ options = true }) { Icon(Icons.Outlined.Tune, "Настройки подключения", tint = Muted) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            if(stage < 2 || stage == 5) {
                Icon(Icons.Outlined.BluetoothSearching, null, tint = Accent, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(24.dp))
                Text("Подключить\nавтомобиль", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Text(if(stage == 1) "Поиск адаптера…" else if(stage == 5) "Реальный адаптер пока не поддерживается. Деморежим доступен в настройках подключения." else "Найдите OBD-II адаптер автомобиля", fontSize = 13.sp, lineHeight = 20.sp, color = Muted)
                if(demo) { Spacer(Modifier.height(16.dp)); Badge("Режим симуляции", Secondary) }
                if(stage == 1) { Spacer(Modifier.height(24.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Line) }
            } else {
                Text("Volkswagen Tayron", fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Text("1.4 TSI · 2023", fontFamily = Instruments, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))
                VehiclePhoto(Modifier.fillMaxWidth().height(190.dp))
                Spacer(Modifier.height(20.dp))
                CardBox(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Bluetooth, null, tint = Accent, modifier = Modifier.size(18.dp)); Text("ELM327 · Simulator", fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp)); Badge("Демо", Secondary) }
                    if(stage >= 3) {
                        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Line)
                        listOf("Адаптер подключён", "Профиль загружен", "Телеметрия доступна").forEachIndexed { i, label ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if(i < check) Icons.Outlined.Check else Icons.Outlined.Remove, null, tint = if(i < check) Secondary else Muted, modifier = Modifier.size(16.dp)); Text(label, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 10.dp)) }
                        }
                    }
                }
            }
        }
        Action(when(stage) { 1 -> "Поиск…"; 2 -> "Подключить автомобиль"; 3 -> "Подключение…"; 4 -> "Открыть приборы"; else -> "Найти автомобиль" }, if(stage == 4) Icons.Outlined.ArrowForward else Icons.Outlined.Bluetooth, Modifier.fillMaxWidth(), stage != 1 && stage != 3) { when(stage) { 2 -> stage = 3; 4 -> onConnected(); else -> stage = 1 } }
        Spacer(Modifier.height(12.dp))
    }
    if(options) ModalBottomSheet(onDismissRequest = { options = false }, containerColor = Panel) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Подключение", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Режим симуляции", fontSize = 15.sp); Text("Tayron 1.4 TSI · виртуальный ELM327", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) }
                Switch(demo, { engine.demo(it) })
            }
            Text("Синтетические показания без автомобиля и адаптера.", fontSize = 12.sp, color = Muted)
            TextButton({ options = false }, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable fun VehiclePhoto(modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Box(modifier.clip(RoundedCornerShape(5.dp))) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.tayron), contentDescription = "Volkswagen Tayron 2023, вид спереди", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Text("User3204 · CC BY-SA 4.0", color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f)).clickable { uri.openUri("https://commons.wikimedia.org/wiki/File:2023_FAW-Volkswagen_Tayron_300TSI_(facelift,_front).jpg") }.padding(5.dp, 3.dp))
    }
}

@Composable fun Live(engine: Engine, state: LiveState) {
    val miles by engine.miles.collectAsStateWithLifecycle()
    var scenarios by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Eyebrow("Volkswagen · 2023"); Text("Tayron 1.4 TSI", fontSize = 22.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp)) }; Box(Modifier.size(9.dp).background(Accent, CircleShape)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Outlined.BluetoothConnected, null, tint = Secondary, modifier = Modifier.size(14.dp)); Text("ELM327 / Simulator", fontSize = 10.sp, color = Muted); Text("/", fontSize = 10.sp, color = Line); Text("Демо", fontSize = 10.sp, color = Muted) }
        if(state.frame.rpm == null || (state.frame.coolant ?: 0.0) > 110) CardBox(Modifier.fillMaxWidth()) { Text(if(state.frame.rpm == null) "Нет данных · тестовый обрыв потока" else "Высокая температура · тестовое событие", color = Amber, fontSize = 12.sp) }
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Eyebrow("ТЕКУЩАЯ СКОРОСТЬ"); Spacer(Modifier.weight(1f)); Badge(if(state.running) "LIVE" else "ПАУЗА", if(state.running) Accent else Amber) }
            Gauge(state.frame, miles)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MiniValue("ОБОРОТЫ", state.frame.rpm?.let { "%,d".format(it).replace(',', ' ') } ?: "—", "об/мин"); MiniValue("ДИСТАНЦИЯ", "%.2f".format(state.frame.distance * if(miles) 0.621371 else 1.0), if(miles) "ми" else "км"); MiniValue("ВРЕМЯ", clockText(state.frame.elapsed), "в пути") }
        }
        LoadReading(state.frame)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Охлаждение", state.frame.coolant?.let { "%.0f°".format(it) } ?: "—", Icons.Outlined.DeviceThermostat, if((state.frame.coolant ?: 0.0) > 110) Amber else Secondary, Modifier.weight(1f)); Metric("Напряжение", state.frame.voltage?.let { "%.1f В".format(it) } ?: "—", Icons.Outlined.BatteryChargingFull, Secondary, Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Дроссель", state.frame.throttle?.let { "%.0f %%".format(it) } ?: "—", Icons.Outlined.Tune, Accent, Modifier.weight(1f)); Metric("Впуск", state.frame.intake?.let { "%.0f °C".format(it) } ?: "—", Icons.Outlined.Air, Secondary, Modifier.weight(1f)) }
        CardBox(Modifier.fillMaxWidth()) { Row { Eyebrow("Скорость за последнюю минуту"); Spacer(Modifier.weight(1f)); Text("Скорость", color = Accent, fontSize = 10.sp) }; Sparkline(state.trail, Modifier.fillMaxWidth().height(70.dp).padding(top = 12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("−${clockText((state.trail.lastOrNull()?.elapsed ?: 0) - (state.trail.firstOrNull()?.elapsed ?: 0))}", fontSize = 9.sp, color = Muted); Text("Сейчас", fontSize = 9.sp, color = Muted) } }
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Science, null, tint = Accent, modifier = Modifier.size(18.dp)); Text("Симулятор", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp)); TextButton({ scenarios = true }, enabled = !state.recording, contentPadding = PaddingValues(0.dp)) { Text(state.scenario.title, fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(engine::toggle, Modifier.weight(1f), shape = RoundedCornerShape(5.dp)) { Icon(if(state.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(17.dp)); Text(if(state.running) "Пауза" else "Старт", fontSize = 11.sp) }
                OutlinedButton(engine::reset, enabled = !state.recording, shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(10.dp)) { Icon(Icons.Outlined.RestartAlt, "Сброс", Modifier.size(19.dp)) }
                OutlinedButton(engine::rate, shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(10.dp)) { Text("${state.rate}×") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Column(Modifier.fillMaxWidth().background(Bg).padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Action(if(state.recording) "Завершить и сохранить поездку" else "Начать запись поездки", if(state.recording) Icons.Outlined.StopCircle else Icons.Outlined.FiberManualRecord, Modifier.fillMaxWidth()) { engine.record() }
        Text(if(state.recording) "●  Запись в локальную базу · можно поставить на паузу" else "Симулятор · локальная запись", fontSize = 10.sp, color = if(state.recording) Accent else Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    }
    if(scenarios) AlertDialog(onDismissRequest = { scenarios = false }, title = { Text("Сценарий поездки") }, text = { Column { Scenario.entries.forEach { s -> Column(Modifier.fillMaxWidth().clickable { engine.scenario(s); scenarios = false }.padding(vertical = 13.dp)) { Text(s.title, color = if(s == state.scenario) Accent else Ink, fontSize = 15.sp); Text(s.subtitle, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) } }; Text("Смена сценария сбрасывает текущую симуляцию. Во время записи недоступна.", fontSize = 11.sp, color = Muted) } }, confirmButton = { TextButton({ scenarios = false }) { Text("Закрыть") } })
}
@Composable fun MiniValue(label: String, value: String, unit: String) { Column { Text(label.lowercase().replaceFirstChar { it.titlecase() }, fontSize = 10.sp, color = Muted); Text(value, fontSize = 22.sp, fontFamily = Instruments, modifier = Modifier.padding(top = 5.dp)); Text(unit, fontSize = 9.sp, color = Muted) } }
@Composable fun Metric(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) { CardBox(modifier) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Text(label, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(start = 6.dp)) }; Text(value, fontSize = 25.sp, fontFamily = Instruments, modifier = Modifier.padding(top = 9.dp)) } }
@Composable fun Gauge(frame: Frame, miles: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.0f".format(frame.speed * if(miles) 0.621371 else 1.0), fontFamily = Instruments, fontSize = 76.sp, lineHeight = 84.sp, letterSpacing = (-5).sp)
            Text(if(miles) "миль/ч" else "км/ч", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 14.dp, bottom = 12.dp))
            Spacer(Modifier.weight(1f))

        }
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val value = (frame.speed / 140).toFloat().coerceIn(0f, 1f)
            for(i in 0..69) {
                val x = size.width * i / 70
                drawLine(if(i / 70f < value) Accent else Line, Offset(x, size.height), Offset(x, if(i % 10 == 0) 0f else size.height * 0.5f), 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { listOf(0, 35, 70, 105, 140).forEach { n -> Text(if(miles) "${(n * 0.621371).roundToInt()}" else "$n", fontSize = 9.sp, fontFamily = Instruments, color = Muted) } }
    }
}
@Composable fun Sparkline(frames: List<Frame>, modifier: Modifier, position: Float? = null, rpm: Boolean = false) {
    Canvas(modifier) {
        for(i in 1..3) drawLine(Line,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f)
        if(frames.size < 2) return@Canvas
        val path = Path(); frames.forEachIndexed { i, f -> val x = size.width * (f.elapsed - frames.first().elapsed).toFloat() / max(1L, frames.last().elapsed - frames.first().elapsed); val y = size.height * (1 - (if(rpm) (f.rpm ?: 0) / 5000f else f.speed.toFloat()/140).coerceIn(0f,1f)); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
        val fill = Path().apply { addPath(path); lineTo(size.width,size.height); lineTo(0f,size.height); close() }
        drawPath(fill, (if(rpm) Secondary else Accent).copy(alpha=0.04f))
        drawPath(path,if(rpm) Secondary else Accent,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        position?.let { drawLine(Ink,Offset(size.width*it,0f),Offset(size.width*it,size.height),2f) }
    }
}
@Composable fun Trips(engine: Engine, onOpen: (Long) -> Unit) {
    val trips by engine.trips.collectAsStateWithLifecycle()
    val miles by engine.miles.collectAsStateWithLifecycle()
    var remove by remember { mutableStateOf<Trip?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading("Поездки", "Volkswagen Tayron · локальный журнал")
        CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MiniValue("ПОЕЗДОК", trips.size.toString(), "сохранено"); MiniValue("РАССТОЯНИЕ", "%.1f".format(trips.sumOf { it.distance } * if(miles) 0.621371 else 1.0), if(miles) "миль всего" else "км всего"); MiniValue("ЗА РУЛЁМ", clockText(trips.sumOf { it.duration }), "мин : сек") } }
        Eyebrow("ЛОКАЛЬНАЯ ИСТОРИЯ")
        if(trips.isEmpty()) CardBox(Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Route,null,tint=Accent,modifier=Modifier.size(36.dp)); Text("Нет записанных поездок",fontSize=20.sp,modifier=Modifier.padding(top=16.dp)); Text("Начните запись на экране приборов. После сохранения здесь появятся маршрут, графики и события.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        trips.forEach { trip ->
            CardBox(Modifier.fillMaxWidth().clickable(enabled=trip.status!="recording") { onOpen(trip.id) }) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Route,null,tint=Accent,modifier=Modifier.size(22.dp)); Column(Modifier.weight(1f).padding(start=12.dp)) { Text(Scenario.valueOf(trip.scenario).title+" · Tayron",fontWeight=FontWeight.Medium,fontSize=16.sp); Text(Instant.ofEpochMilli(trip.started).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", java.util.Locale.forLanguageTag("ru"))),color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp)) }; IconButton({ remove=trip }, enabled=trip.status!="recording", modifier=Modifier.size(30.dp)) { Icon(Icons.Outlined.DeleteOutline,"Удалить поездку",tint=Muted,modifier=Modifier.size(19.dp)) } }
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("%.2f %s".format(trip.distance*if(miles)0.621371 else 1.0,if(miles)"ми" else "км"),fontSize=14.sp); Text(clockText(trip.duration),fontSize=14.sp); Badge(when(trip.status) { "recording"->"ЗАПИСЬ";"interrupted"->"ПРЕРВАНА";else->"SIMULATED" },if(trip.status=="interrupted") Amber else Secondary) }
                if(trip.status!="recording") { HorizontalDivider(Modifier.padding(vertical=13.dp),color=Line); Row(verticalAlignment=Alignment.CenterVertically) { Text("Воспроизвести поездку",color=Accent,fontSize=12.sp,modifier=Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward,null,tint=Accent,modifier=Modifier.size(16.dp)) } }
            }
        }
    }
    remove?.let { trip -> AlertDialog(onDismissRequest={remove=null},title={Text("Удалить поездку?")},text={Text("Маршрут, показания и события будут удалены с устройства.")},confirmButton={TextButton({engine.delete(trip.id);remove=null}){Text("Удалить")}},dismissButton={TextButton({remove=null}){Text("Отмена")}}) }
}
@Composable fun Replay(engine: Engine, id: Long, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    var frames by remember(id) { mutableStateOf<List<Frame>>(emptyList()) }
    var events by remember(id) { mutableStateOf<List<TripEvent>>(emptyList()) }
    var routeOffsetKm by remember(id) { mutableStateOf<Double?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var position by rememberSaveable(id) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(id) { mutableStateOf(false) }
    var rate by rememberSaveable(id) { mutableIntStateOf(1) }
    var rpm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val miles by engine.miles.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if(uri!=null) scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv(frames)) } ?: error("Не удалось открыть файл") } }.onSuccess { notice="CSV сохранён" }.onFailure { notice="Не удалось сохранить CSV: ${it.localizedMessage}" } } }
    LaunchedEffect(id) { routeOffsetKm = engine.db.trip(id)?.routeOffsetKm; frames=engine.db.samples(id).map{it.frame()};events=engine.db.events(id);loaded=true }
    val duration=frames.lastOrNull()?.elapsed ?: 0L
    LaunchedEffect(playing,rate,loaded) { if(!loaded) return@LaunchedEffect; while(playing) { delay(50); position=(position+50*rate).coerceAtMost(duration); if(position>=duration)playing=false } }
    val index=frames.indexOfLast{it.elapsed<=position}.coerceAtLeast(0)
    val f=frames.getOrNull(index) ?: Frame()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onBack,modifier=Modifier.size(32.dp)){Icon(Icons.Outlined.ArrowBack,"К поездкам")}; Text("Воспроизведение",fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=8.dp));IconButton({export.launch("DriveTrace-$id.csv")},enabled=frames.isNotEmpty()){Icon(Icons.Outlined.FileDownload,"Экспорт CSV",tint=Accent)} }
        if(!loaded) CircularProgressIndicator(color=Accent)
        else if(frames.isEmpty()) Text("В этой поездке нет сохранённых измерений.",color=Muted)
        else {
            RouteMap(frames,index,Modifier.fillMaxWidth().height(270.dp), routeOffsetKm)
            LoadLegend()
            LoadReading(f)
            CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { MiniValue("СКОРОСТЬ","%.0f".format(f.speed*if(miles)0.621371 else 1.0),if(miles)"миль/ч" else "км/ч");MiniValue("ОБОРОТЫ",f.rpm?.toString() ?: "—","об/мин");MiniValue("ТЕМПЕРАТУРА",f.coolant?.let{"%.0f°".format(it)} ?: "—","охлаждение") } }
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) { Eyebrow("ТЕЛЕМЕТРИЯ");Spacer(Modifier.weight(1f));TextButton({rpm=!rpm},contentPadding=PaddingValues(0.dp)){Text(if(rpm)"Обороты ↔" else "Скорость ↔",fontSize=11.sp)} }
                Sparkline(frames,Modifier.fillMaxWidth().height(110.dp),if(duration==0L)0f else position.toFloat()/duration,rpm)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(clockText(position),fontSize=12.sp,color=Accent);Text(clockText(duration),fontSize=12.sp,color=Muted)}
                Slider(position.toFloat(),{position=it.toLong()},valueRange=0f..max(1L,duration).toFloat(),modifier=Modifier.fillMaxWidth())
                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 10.dp)) { events.forEach { event -> val x = size.width * event.elapsed / max(1L,duration); drawCircle(Amber, 3.dp.toPx(), Offset(x, size.height / 2)) } }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    IconButton({position=(position-10000).coerceAtLeast(0)}){Icon(Icons.Outlined.Replay10,"Назад 10 секунд")}
                    FilledIconButton({if(position>=duration)position=0;playing=!playing},Modifier.size(50.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Accent,contentColor=Bg)){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing)"Пауза воспроизведения" else "Воспроизвести")}
                    IconButton({position=(position+10000).coerceAtMost(duration)}){Icon(Icons.Outlined.Forward10,"Вперёд 10 секунд")}
                    TextButton({rate=when(rate){1->2;2->5;else->1}}){Text("${rate}×")}
                }
            }
            Eyebrow("СОБЫТИЯ · ${events.size}")
            if(events.isEmpty()) Text("Поездка без отмеченных событий",fontSize=12.sp,color=Muted)
            events.forEach { event -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(event.elapsed<=position)Panel else Bg).clickable{position=event.elapsed.coerceAtMost(duration)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Bolt,null,tint=Amber,modifier=Modifier.size(18.dp));Text(event.title,fontSize=12.sp,modifier=Modifier.weight(1f).padding(start=9.dp));Text(clockText(event.elapsed),fontSize=11.sp,color=Muted) } }
            notice?.let{Text(it,fontSize=12.sp,color=Accent)}
            Text("Синтетические показания · маршрут демонстрационный",fontSize=10.sp,color=Muted,lineHeight=16.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable fun Settings(engine: Engine) {
    val interval by engine.interval.collectAsStateWithLifecycle()
    val miles by engine.miles.collectAsStateWithLifecycle()
    val state by engine.state.collectAsStateWithLifecycle()
    var disconnect by remember{mutableStateOf(false)}
    var credits by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Heading("Настройки", "Автомобиль, источник данных и запись")
        CardBox(Modifier.fillMaxWidth()) { VehiclePhoto(Modifier.fillMaxWidth().height(150.dp));Spacer(Modifier.height(16.dp));Text("Volkswagen Tayron",fontSize=21.sp,fontWeight=FontWeight.SemiBold);Text("1.4 TSI · 2023 · Демонстрационный профиль",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp));HorizontalDivider(Modifier.padding(vertical=15.dp),color=Line);Row{Text("Источник данных",fontSize=12.sp,modifier=Modifier.weight(1f));Badge("SIMULATOR",Accent)};Text("ELM327 и реальный Bluetooth будут подключены на следующем этапе.",fontSize=11.sp,color=Muted,lineHeight=18.sp,modifier=Modifier.padding(top=12.dp)) }
        Eyebrow("ПРЕДПОЧТЕНИЯ")
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Мили вместо километров",fontSize=13.sp);Text("Скорость и расстояние",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=4.dp))};Switch(miles,{engine.settings(interval,it)})}
            HorizontalDivider(Modifier.padding(vertical=14.dp),color=Line)
            Text("Интервал измерений",fontSize=13.sp);Text("Частота потока и записи в базу",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp,bottom=12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(200,250,500).forEach{v->FilterChip(interval==v,{engine.settings(v,miles)},label={Text("$v мс",fontSize=11.sp)})}}
        }
        CardBox(Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Lock,null,tint=Secondary,modifier=Modifier.size(22.dp));Text("Хранение данных",fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=12.dp));Text("Поездки хранятся на устройстве. Для подложки карты нужен интернет. Экспорт CSV доступен в плеере.",fontSize=12.sp,color=Muted,lineHeight=20.sp,modifier=Modifier.padding(top=8.dp)) }
        OutlinedButton({disconnect=true},Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(5.dp)){Icon(Icons.Outlined.LinkOff,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Отключить автомобиль")}
        TextButton({ credits = true }, contentPadding = PaddingValues(0.dp)) { Text("О карте, фотографии и шрифтах", fontSize = 12.sp) }
        Text("DriveTrace 0.2 · Android\nСимуляция работает в открытом приложении. Фоновая запись и реальный OBD пока не реализованы.",fontSize=10.sp,color=Muted,lineHeight=17.sp)
    }
    if(credits) {
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        AlertDialog(onDismissRequest = { credits = false }, title = { Text("Источники") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Фото: User3204, CC BY-SA 4.0. Реальный Tayron 2023 (300TSI); используется как иллюстрация кузова. Профиль симулятора — 1.4 TSI. Фотография не изменялась, в интерфейсе кадрируется.", fontSize = 12.sp)
            Text("Карта: MapLibre, OpenFreeMap, © OpenMapTiles, © OpenStreetMap contributors. Дорожный маршрут подготовлен с OSRM; показания симулируются.", fontSize = 12.sp)
            Text("Шрифты: IBM Plex Sans и IBM Plex Mono, SIL Open Font License 1.1.", fontSize = 12.sp)
            TextButton({ uri.openUri("https://creativecommons.org/licenses/by-sa/4.0/") }) { Text("Лицензия фотографии") }
        } }, confirmButton = { TextButton({ credits = false }) { Text("Закрыть") } })
    }
    if(disconnect) AlertDialog(onDismissRequest={disconnect=false},title={Text("Отключить автомобиль?")},text={Text(if(state.recording)"Текущая запись будет завершена и сохранена. Вы вернётесь к подключению." else "Симуляция остановится. Сохранённые поездки останутся на устройстве.")},confirmButton={TextButton({engine.disconnect();disconnect=false}){Text("Отключить")}},dismissButton={TextButton({disconnect=false}){Text("Отмена")}})
}

@Composable fun LoadLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(LoadBand.NORMAL, LoadBand.ELEVATED, LoadBand.HIGH).forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.width(13.dp).height(3.dp).background(Color(android.graphics.Color.parseColor(band.hex)))); Text(band.label, fontSize = 10.sp, color = Muted) }
        }
    }
}

@Composable fun LoadReading(frame: Frame) {
    val load = frame.estimatedLoadPct
    val color = Color(android.graphics.Color.parseColor(LoadBand.of(load).hex))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Расчётная нагрузка", fontSize = 11.sp, color = Muted); Text(load?.let { "%.0f %%".format(it) } ?: "—", fontSize = 12.sp, fontFamily = Instruments, color = color) }
        LinearProgressIndicator(progress = { ((load ?: 0.0) / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp), color = color, trackColor = Line, drawStopIndicator = {})
    }
}
