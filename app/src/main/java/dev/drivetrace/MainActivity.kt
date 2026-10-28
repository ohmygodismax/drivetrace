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
    val currentTrips by engine.trips.collectAsStateWithLifecycle()
    val signals = remember(currentTrips) { healthSignals(currentTrips) }
    var page by rememberSaveable { mutableStateOf("live") }
    var wasConnected by rememberSaveable { mutableStateOf(live.connected) }
    LaunchedEffect(live.connected) {
        if(live.connected != wasConnected) { page = "live"; wasConnected = live.connected }
    }
    var replayId by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.activity.compose.BackHandler(enabled = replayId == null && page != "live") { page = "live" }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val connectingScreen = !live.connected && page == "live" && replayId == null
            if(!connectingScreen) Row(Modifier.fillMaxWidth().padding(22.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f))
                Badge(if(live.vehicle?.simulated == true) "SIMULATED" else if(live.connected) "ELM327" else "Не подключён", Accent)
            }
            Box(Modifier.weight(1f)) {
                when {
                    replayId != null -> Replay(engine, replayId!!) { replayId = null }
                    connectingScreen -> Connection(engine, onGarage = { page = "garage" }, onSettings = { page = "settings" }) {
                        page = "live"; replayId = null; engine.connect()
                    }
                    page == "live" -> Live(engine, live, onOpenTrip = { replayId = it }, onHealth = {
                        live.vehicle?.let { engine.selectVehicle(it.id) }; page = "health"
                    })
                    page == "trips" -> Trips(engine) { replayId = it }
                    page == "health" -> HealthScreen(engine) { replayId = it }
                    page == "garage" -> Garage(engine)
                    else -> SettingsScreen(engine)
                }
            }
            if(replayId == null) BottomNavigation(page, signals.size) { page = it }
        }
    }
}

@Composable fun Brand(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val showTagline = maxWidth >= 240.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DriveTrace", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, maxLines = 1)
            if(showTagline) {
                Box(Modifier.width(1.dp).height(16.dp).background(Line))
                Text("бортовой журнал", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
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

@Composable fun VehiclePhoto(modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Box(modifier.clip(RoundedCornerShape(5.dp))) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.tayron), contentDescription = "Volkswagen Tayron 2023, вид спереди", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Text("User3204 · CC BY-SA 4.0", color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f)).clickable { uri.openUri("https://commons.wikimedia.org/wiki/File:2023_FAW-Volkswagen_Tayron_300TSI_(facelift,_front).jpg") }.padding(5.dp, 3.dp))
    }
}

@Composable fun MiniValue(label: String, value: String, unit: String) { Column { Text(label.lowercase().replaceFirstChar { it.titlecase() }, fontSize = 10.sp, color = Muted); Text(value, fontSize = 22.sp, fontFamily = Instruments, modifier = Modifier.padding(top = 5.dp)); Text(unit, fontSize = 9.sp, color = Muted) } }
@Composable fun Sparkline(frames: List<Frame>, modifier: Modifier, position: Float? = null, rpm: Boolean = false) {
    Canvas(modifier) {
        for(i in 1..3) drawLine(Line,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f)
        if(frames.size < 2) return@Canvas
        val path = Path(); var drawing = false
        frames.forEach { f ->
            val value = if(rpm) f.rpm?.let { it / 5000f } else f.speed?.let { (it / 140).toFloat() }
            if(value == null) drawing = false else {
                val x = size.width * (f.elapsed - frames.first().elapsed).toFloat() / max(1L, frames.last().elapsed - frames.first().elapsed)
                val y = size.height * (1 - value.coerceIn(0f, 1f))
                if(!drawing) path.moveTo(x,y) else path.lineTo(x,y)
                drawing = true
            }
        }
        drawPath(path,if(rpm) Secondary else Accent,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        position?.let { drawLine(Ink,Offset(size.width*it,0f),Offset(size.width*it,size.height),2f) }
    }
}
@Composable fun Trips(engine: Engine, onOpen: (Long) -> Unit) {
    val trips by engine.trips.collectAsStateWithLifecycle()
    val selectedCar by engine.selectedVehicle.collectAsStateWithLifecycle()
    val miles by engine.miles.collectAsStateWithLifecycle()
    var remove by remember { mutableStateOf<Trip?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading("Поездки", selectedCar?.name ?: "Выберите автомобиль в гараже")
        CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MiniValue("ПОЕЗДОК", trips.size.toString(), "сохранено"); MiniValue("РАССТОЯНИЕ", "%.1f".format(trips.sumOf { it.distance } * if(miles) 0.621371 else 1.0), if(miles) "миль всего" else "км всего"); MiniValue("ЗА РУЛЁМ", clockText(trips.sumOf { it.duration }), "мин : сек") } }
        Eyebrow("ЛОКАЛЬНАЯ ИСТОРИЯ")
        if(trips.isEmpty()) CardBox(Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Route,null,tint=Accent,modifier=Modifier.size(36.dp)); Text("Нет записанных поездок",fontSize=20.sp,modifier=Modifier.padding(top=16.dp)); Text("Начните запись на экране приборов. После сохранения здесь появятся маршрут, графики и события.",color=Muted,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(top=8.dp)) }
        trips.forEach { trip ->
            CardBox(Modifier.fillMaxWidth().clickable(enabled=trip.status!="recording") { onOpen(trip.id) }) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Route,null,tint=Accent,modifier=Modifier.size(22.dp)); Column(Modifier.weight(1f).padding(start=12.dp)) { Text(scenarioTitle(trip.scenario),fontWeight=FontWeight.Medium,fontSize=16.sp); Text(Instant.ofEpochMilli(trip.started).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", java.util.Locale.forLanguageTag("ru"))),color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp)) }; IconButton({ remove=trip }, enabled=trip.status!="recording", modifier=Modifier.size(30.dp)) { Icon(Icons.Outlined.DeleteOutline,"Удалить поездку",tint=Muted,modifier=Modifier.size(19.dp)) } }
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("%.2f %s".format(trip.distance*if(miles)0.621371 else 1.0,if(miles)"ми" else "км"),fontSize=14.sp); Text(clockText(trip.duration),fontSize=14.sp); Badge(when(trip.status) { "recording"->"ЗАПИСЬ";"interrupted"->"ПРЕРВАНА";else->if(selectedCar?.simulated==true)"SIMULATED" else "ELM327" },if(trip.status=="interrupted") Amber else Secondary) }
                if(trip.warmupMs != null) Text("Прогрев ОЖ до 80 °C · ${clockText(trip.warmupMs)}", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
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
    var replayMetrics by remember(id) { mutableStateOf<Set<ObdMetric>>(emptySet()) }
    var replayVehicle by remember(id) { mutableStateOf<Vehicle?>(null) }
    var routeOffsetKm by remember(id) { mutableStateOf<Double?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var position by rememberSaveable(id) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(id) { mutableStateOf(false) }
    var rate by rememberSaveable(id) { mutableIntStateOf(1) }
    var rpm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val miles by engine.miles.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if(uri!=null) scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv(frames, replayVehicle?.vin)) } ?: error("Не удалось открыть файл") } }.onSuccess { notice="CSV сохранён" }.onFailure { notice="Не удалось сохранить CSV: ${it.localizedMessage}" } } }
    LaunchedEffect(id) { val trip = engine.db.trip(id); replayVehicle = trip?.let { engine.db.vehicle(it.vehicleId) }; replayMetrics = trip?.supported?.split(',')?.mapNotNull { name -> ObdMetric.entries.find { it.name == name } }?.toSet() ?: emptySet(); routeOffsetKm = trip?.routeOffsetKm; frames=engine.db.samples(id).map{it.frame()};events=engine.db.events(id);loaded=true }
    val duration=frames.lastOrNull()?.elapsed ?: 0L
    LaunchedEffect(playing,rate,loaded) { if(!loaded) return@LaunchedEffect; while(playing) { delay(50); position=(position+50*rate).coerceAtMost(duration); if(position>=duration)playing=false } }
    val index=frames.indexOfLast{it.elapsed<=position}.coerceAtLeast(0)
    val f=frames.getOrNull(index) ?: Frame()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onBack,modifier=Modifier.size(32.dp)){Icon(Icons.Outlined.ArrowBack,"К поездкам")}; Text("Воспроизведение",fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=8.dp));IconButton({export.launch("DriveTrace-$id.csv")},enabled=frames.isNotEmpty()){Icon(Icons.Outlined.FileDownload,"Экспорт CSV",tint=Accent)} }
        if(!loaded) CircularProgressIndicator(color=Accent)
        else if(frames.isEmpty()) Text("В этой поездке нет сохранённых измерений.",color=Muted)
        else {
            replayVehicle?.let { Text("${it.name} · ${it.identity}", fontSize = 11.sp, color = Muted) }
            if(frames.any { it.hasLocation }) RouteMap(frames,index,Modifier.fillMaxWidth().height(270.dp), routeOffsetKm)
            else CardBox(Modifier.fillMaxWidth()) { Text("Маршрут недоступен", fontSize = 15.sp); Text("В этой поездке нет точных координат. Включите GPS и разрешите точную геолокацию перед следующей записью.", fontSize = 12.sp, color = Muted) }
            LoadLegend()
            LoadReading(f)
            CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { MiniValue("СКОРОСТЬ",f.speed?.let { "%.0f".format(it*if(miles)0.621371 else 1.0) } ?: "—",if(miles)"миль/ч" else "км/ч");MiniValue("ОБОРОТЫ",f.rpm?.toString() ?: "—","об/мин");MiniValue("ТЕМПЕРАТУРА",f.coolant?.let{"%.0f°".format(it)} ?: "—","охлаждение") } }
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
            TelemetryGrid(f, replayMetrics)
            Eyebrow("СОБЫТИЯ · ${events.size}")
            if(events.isEmpty()) Text("Поездка без отмеченных событий",fontSize=12.sp,color=Muted)
            events.forEach { event -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(event.elapsed<=position)Panel else Bg).clickable{position=event.elapsed.coerceAtMost(duration)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Bolt,null,tint=Amber,modifier=Modifier.size(18.dp));Text(event.title,fontSize=12.sp,modifier=Modifier.weight(1f).padding(start=9.dp));Text(clockText(event.elapsed),fontSize=11.sp,color=Muted) } }
            notice?.let{Text(it,fontSize=12.sp,color=Accent)}
            Text(if(f.simulated) "Синтетические показания · маршрут демонстрационный" else "Показания ELM327 · маршрут по GPS телефона · время измерений различается",fontSize=10.sp,color=Muted,lineHeight=16.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if(frame.engineLoad != null) "Нагрузка ЭБУ" else "Оценка нагрузки", fontSize = 11.sp, color = Muted); Text(load?.let { "%.0f %%".format(it) } ?: "—", fontSize = 12.sp, fontFamily = Instruments, color = color) }
        LinearProgressIndicator(progress = { ((load ?: 0.0) / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp), color = color, trackColor = Line, drawStopIndicator = {})
    }
}

fun scenarioTitle(value: String): String = Scenario.entries.find { it.name == value }?.title ?: if(value == "REAL") "Поездка ELM327" else value
