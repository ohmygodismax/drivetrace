package dev.drivetrace.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivetrace.core.format.clockText
import dev.drivetrace.data.export.csv
import dev.drivetrace.data.local.TripEvent
import dev.drivetrace.domain.route.eventFrameIndex
import dev.drivetrace.domain.route.routeSelection
import dev.drivetrace.model.Frame
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.Vehicle
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.Eyebrow
import dev.drivetrace.ui.components.LoadLegend
import dev.drivetrace.ui.components.LoadReading
import dev.drivetrace.ui.components.MiniValue
import dev.drivetrace.ui.components.RouteMap
import dev.drivetrace.ui.components.Sparkline
import dev.drivetrace.ui.components.TelemetryGrid
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Amber
import dev.drivetrace.ui.theme.Bg
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Panel
import java.time.*
import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun Replay(engine: Engine, id: Long, onBack: () -> Unit) {
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
    var activeEvent by remember(id) { mutableStateOf<TripEvent?>(null) }
    var fullMap by rememberSaveable(id) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = !fullMap) { onBack() }
    var rangeStart by rememberSaveable(id) { mutableFloatStateOf(0f) }
    var rangeEnd by rememberSaveable(id) { mutableFloatStateOf(-1f) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val miles by engine.miles.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if(uri!=null) scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv(frames, replayVehicle?.vin)) } ?: error("Не удалось открыть файл") } }.onSuccess { notice="CSV сохранён" }.onFailure { notice="Не удалось сохранить CSV: ${it.localizedMessage}" } } }
    LaunchedEffect(id) { val trip = engine.db.trip(id); replayVehicle = trip?.let { engine.db.vehicle(it.vehicleId) }; replayMetrics = trip?.supported?.split(',')?.mapNotNull { name -> ObdMetric.entries.find { it.name == name } }?.toSet() ?: emptySet(); routeOffsetKm = trip?.routeOffsetKm; frames=engine.db.samples(id).map{it.frame()};events=engine.db.events(id);loaded=true }
    val duration=frames.lastOrNull()?.elapsed ?: 0L
    val totalDistance = ((frames.lastOrNull()?.distance ?: 0.0) - (frames.firstOrNull()?.distance ?: 0.0)).coerceAtLeast(0.0).toFloat()
    val startDistance = rangeStart.coerceIn(0f,totalDistance)
    val endDistance = if(rangeEnd < 0f) totalDistance else rangeEnd.coerceIn(startDistance, totalDistance)
    val selection = remember(frames, startDistance, endDistance) { routeSelection(frames, (frames.firstOrNull()?.distance ?: 0.0) + startDistance, (frames.firstOrNull()?.distance ?: 0.0) + endDistance) }
    val selectedFrames = remember(frames, selection) { if(selection.isEmpty()) emptyList() else frames.slice(selection) }
    val startTime = selectedFrames.firstOrNull()?.elapsed ?: 0L
    val endTime = selectedFrames.lastOrNull()?.elapsed ?: duration
    val selectedDuration = endTime - startTime
    val selectedEvents = events.filter { it.elapsed in startTime..endTime }
    fun selectRange(start: Float, end: Float) {
        rangeStart = start; rangeEnd = end; playing = false; activeEvent = null
        val selected = routeSelection(frames, frames.first().distance + start, frames.first().distance + end)
        position = frames[selected.first].elapsed
    }
    LaunchedEffect(startTime, endTime, loaded) { if(loaded) position = position.coerceIn(startTime, endTime) }
    LaunchedEffect(playing,rate,loaded,startTime,endTime) { if(!loaded) return@LaunchedEffect; while(playing) { delay(50); position=(position+50*rate).coerceIn(startTime,endTime); if(position>=endTime)playing=false } }
    val index=frames.indexOfLast{it.elapsed<=position}.coerceAtLeast(0)
    val f=frames.getOrNull(index) ?: Frame()
    fun selectEvent(event: TripEvent) { position = event.elapsed.coerceIn(startTime, endTime); playing = false; activeEvent = event }

    if(fullMap && frames.isNotEmpty()) androidx.compose.ui.window.Dialog(
        onDismissRequest = { fullMap = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Анализ маршрута", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    TextButton({ fullMap = false }) { Icon(Icons.Outlined.FullscreenExit, null); Text("Свернуть") }
                }
                RouteMap(frames,index,Modifier.fillMaxWidth().weight(1f),routeOffsetKm,selection,
                    onSelect = { position = frames[it].elapsed; playing = false; activeEvent = null },
                    events = selectedEvents, onEventSelect = ::selectEvent)
                Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("● События на карте · ${selectedEvents.count { eventFrameIndex(frames, it) != null }}", color = Amber, fontSize = 11.sp)
                    activeEvent?.let { EventReading(it) }
                    Text("Показания · ${clockText(f.elapsed)} · %.2f %s".format((f.distance-frames.first().distance)*if(miles)0.621371 else 1.0,if(miles)"ми" else "км"), color = Accent, fontSize = 12.sp)
                    Slider(position.toFloat(), { position=it.toLong();playing=false }, valueRange=startTime.toFloat()..max(startTime+1,endTime).toFloat(), enabled=endTime>startTime)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniValue("СКОРОСТЬ",f.speed?.let { "%.0f".format(it*if(miles)0.621371 else 1.0) } ?: "—",if(miles)"миль/ч" else "км/ч")
                        MiniValue("ОБОРОТЫ",f.rpm?.toString() ?: "—","об/мин")
                        MiniValue("ТЕМПЕРАТУРА",f.coolant?.let { "%.0f°".format(it) } ?: "—","охлаждение")
                    }
                    LoadReading(f)
                    RouteRangeControls(frames,startDistance,endDistance,miles,::selectRange)
                    RouteSummaryCard(selectedFrames,miles)
                    TelemetryGrid(f, replayMetrics)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onBack,modifier=Modifier.size(32.dp)){Icon(Icons.Outlined.ArrowBack,"К поездкам")}; Text("Воспроизведение",fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=8.dp));IconButton({export.launch("DriveTrace-$id.csv")},enabled=frames.isNotEmpty()){Icon(Icons.Outlined.FileDownload,"Экспорт CSV",tint=Accent)} }
        if(!loaded) CircularProgressIndicator(color=Accent)
        else if(frames.isEmpty()) Text("В этой поездке нет сохранённых измерений.",color=Muted)
        else {
            replayVehicle?.let { Text("${it.name} · ${it.identity}", fontSize = 11.sp, color = Muted) }
            if(frames.any { it.hasLocation }) {
                if(!fullMap) RouteMap(frames,index,Modifier.fillMaxWidth().height(300.dp),routeOffsetKm,selection,
                    onSelect = { position = frames[it].elapsed; playing = false; activeEvent = null }, onExpand = { fullMap = true },
                    events = selectedEvents, onEventSelect = ::selectEvent)
            }
            else CardBox(Modifier.fillMaxWidth()) { Text("Маршрут недоступен", fontSize = 15.sp); Text("В этой поездке нет точных координат. Включите GPS и разрешите точную геолокацию перед следующей записью.", fontSize = 12.sp, color = Muted) }
            if(frames.any { it.hasLocation }) Text("● События на карте · ${selectedEvents.count { eventFrameIndex(frames, it) != null }} · нажмите на маркер", color = Amber, fontSize = 11.sp)
            activeEvent?.let { EventReading(it) }
            RouteRangeControls(frames, startDistance, endDistance, miles, ::selectRange)
            RouteSummaryCard(selectedFrames,miles)
            LoadLegend()
            LoadReading(f)
            CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { MiniValue("СКОРОСТЬ",f.speed?.let { "%.0f".format(it*if(miles)0.621371 else 1.0) } ?: "—",if(miles)"миль/ч" else "км/ч");MiniValue("ОБОРОТЫ",f.rpm?.toString() ?: "—","об/мин");MiniValue("ТЕМПЕРАТУРА",f.coolant?.let{"%.0f°".format(it)} ?: "—","охлаждение") } }
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) { Eyebrow("ТЕЛЕМЕТРИЯ");Spacer(Modifier.weight(1f));TextButton({rpm=!rpm},contentPadding=PaddingValues(0.dp)){Text(if(rpm)"Обороты ↔" else "Скорость ↔",fontSize=11.sp)} }
                Sparkline(selectedFrames,Modifier.fillMaxWidth().height(110.dp),if(selectedDuration==0L)0f else (position-startTime).toFloat()/selectedDuration,rpm)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(clockText(position),fontSize=12.sp,color=Accent);Text(clockText(endTime),fontSize=12.sp,color=Muted)}
                Slider(position.toFloat(),{position=it.toLong();playing=false},valueRange=startTime.toFloat()..max(startTime+1,endTime).toFloat(),enabled=endTime>startTime,modifier=Modifier.fillMaxWidth())
                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 10.dp)) { selectedEvents.forEach { event -> val x = size.width * (event.elapsed-startTime) / max(1L,selectedDuration); drawCircle(Amber, 3.dp.toPx(), Offset(x, size.height / 2)) } }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    IconButton({position=(position-10000).coerceAtLeast(startTime)}){Icon(Icons.Outlined.Replay10,"Назад 10 секунд")}
                    FilledIconButton({if(position>=endTime)position=startTime;playing=!playing},Modifier.size(50.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Accent,contentColor=Bg)){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing)"Пауза воспроизведения" else "Воспроизвести")}
                    IconButton({position=(position+10000).coerceAtMost(endTime)}){Icon(Icons.Outlined.Forward10,"Вперёд 10 секунд")}
                    TextButton({rate=when(rate){1->2;2->5;else->1}}){Text("${rate}×")}
                }
            }
            TelemetryGrid(f, replayMetrics)
            Eyebrow("СОБЫТИЯ УЧАСТКА · ${selectedEvents.size}")
            if(selectedEvents.isEmpty()) Text("На участке нет отмеченных событий",fontSize=12.sp,color=Muted)
            selectedEvents.forEach { event -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(event.elapsed<=position)Panel else Bg).clickable{selectEvent(event)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Bolt,null,tint=Amber,modifier=Modifier.size(18.dp));Text(event.title,fontSize=12.sp,modifier=Modifier.weight(1f).padding(start=9.dp));Text(clockText(event.elapsed),fontSize=11.sp,color=Muted) } }
            notice?.let{Text(it,fontSize=12.sp,color=Accent)}
            Text(if(f.simulated) "Синтетические показания · маршрут демонстрационный" else "Показания ELM327 · маршрут по GPS телефона · время измерений различается",fontSize=10.sp,color=Muted,lineHeight=16.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable private fun EventReading(event: TripEvent) {
    CardBox(Modifier.fillMaxWidth()) {
        Text("Событие · ${clockText(event.elapsed)}", color = Amber, fontSize = 11.sp)
        Text(event.title, fontSize = 14.sp)
        Text("Показания соответствуют последнему замеру перед событием.", color = Muted, fontSize = 11.sp)
    }
}
