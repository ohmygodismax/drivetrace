package dev.drivetrace

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable fun TelemetryGrid(frame: Frame,supported: Set<ObdMetric>) {
    ObdMetric.entries.filter { it !in setOf(ObdMetric.RPM,ObdMetric.SPEED,ObdMetric.LOAD) }.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach { metric ->
                val value=frame.value(metric)
                CardBox(Modifier.weight(1f)) {
                    Text(metric.title,fontSize=11.sp,color=Muted,minLines=2)
                    Text(value?.let { "%.1f".format(it) } ?: "—",fontFamily=Instruments,fontSize=24.sp,modifier=Modifier.padding(top=7.dp))
                    Text(if(metric !in supported)"Не поддерживается" else if(value==null)"Нет свежих данных" else metric.unit,fontSize=10.sp,color=if(metric !in supported)Muted else Secondary,modifier=Modifier.padding(top=4.dp))
                }
            }
            if(row.size==1)Spacer(Modifier.weight(1f))
        }
    }
    CardBox(Modifier.fillMaxWidth()) {
        val pressure=if(frame.manifoldKpa!=null && frame.baroKpa!=null)frame.manifoldKpa-frame.baroKpa else null
        Text("Давление относительно атмосферы",fontSize=12.sp,color=Muted)
        Text(pressure?.let { "%.2f бар".format(it/100) } ?: "—",fontFamily=Instruments,fontSize=22.sp,modifier=Modifier.padding(top=8.dp))
        Text("Впуск минус атмосферное давление. При разрежении значение отрицательное.",fontSize=10.sp,color=Muted,lineHeight=16.sp,modifier=Modifier.padding(top=5.dp))
    }
}
@Composable fun Garage(engine: Engine) {
    val vehicles by engine.vehicles.collectAsStateWithLifecycle()
    val selected by engine.selectedVehicle.collectAsStateWithLifecycle()
    val fleet by engine.fleetTrips.collectAsStateWithLifecycle()
    val live by engine.state.collectAsStateWithLifecycle()
    val interval by engine.interval.collectAsStateWithLifecycle()
    val miles by engine.miles.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf<Vehicle?>(null) }
    var name by remember { mutableStateOf("") }
    var disconnect by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Heading("Гараж","Отдельная история для каждого VIN")
        Text("Выбор здесь меняет журнал и динамику. Приборы и текущая запись относятся к подключённому автомобилю.",fontSize=11.sp,color=Muted,lineHeight=18.sp)
        vehicles.forEach { vehicle ->
            CardBox(Modifier.fillMaxWidth().clickable { engine.selectVehicle(vehicle.id) }) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(if(selected?.id==vehicle.id)Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,null,tint=Accent,modifier=Modifier.size(19.dp));Text(vehicle.name,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f).padding(start=10.dp));IconButton({rename=vehicle;name=vehicle.name},Modifier.size(28.dp)){Icon(Icons.Outlined.Edit,"Переименовать ${vehicle.name}",tint=Muted,modifier=Modifier.size(17.dp))} }
                val alerts = remember(fleet, vehicle.id) { healthSignals(fleet.filter { it.vehicleId == vehicle.id }) }
                if(alerts.isNotEmpty()) Text("${alerts.size} изменения для проверки", fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 8.dp))
                Text(vehicle.identity,fontSize=11.sp,fontFamily=Instruments,color=Muted,modifier=Modifier.padding(top=9.dp))
                Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Badge(if(vehicle.simulated)"Демо" else "ELM327",if(vehicle.simulated)Muted else Secondary);if(live.vehicle?.id==vehicle.id)Text("Подключён",fontSize=11.sp,color=Secondary);if(selected?.id==vehicle.id)Text("Открыт журнал",fontSize=11.sp,color=Accent)}
            }
        }
        selected?.let { car ->
            OutlinedButton({capabilities=true},Modifier.fillMaxWidth()){Text("Параметры автомобиля · ${car.metrics.size}/${ObdMetric.entries.size}",fontSize=12.sp)}
        }
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically){Text("Мили вместо километров",Modifier.weight(1f),fontSize=13.sp);Switch(miles,{engine.settings(interval,it)})}
            Text("Интервал симуляции",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(200,250,500).forEach{v->FilterChip(interval==v,{engine.settings(v,miles)},label={Text("$v мс",fontSize=11.sp)})}}
            Text("У ELM частота зависит от адаптера и числа параметров. Время замеров различается.",fontSize=10.sp,color=Muted,lineHeight=16.sp)
        }
        OutlinedButton({disconnect=true},Modifier.fillMaxWidth()){Icon(Icons.Outlined.LinkOff,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Отключить автомобиль")}
        Text("DriveTrace 0.3 · Bluetooth Classic\nПоездки хранятся локально. Для карты нужен интернет. Фоновая запись работает при подключённом ELM. GPS включается отдельно.",fontSize=10.sp,color=Muted,lineHeight=17.sp)
        val uri=androidx.compose.ui.platform.LocalUriHandler.current
        TextButton({uri.openUri("https://commons.wikimedia.org/wiki/File:2023_FAW-Volkswagen_Tayron_300TSI_(facelift,_front).jpg")},contentPadding=PaddingValues(0.dp)){Text("Фото: User3204 · CC BY-SA 4.0",fontSize=10.sp)}
        Text("Фото Tayron 2023 (300TSI) иллюстрирует кузов. Шрифты IBM Plex: SIL OFL. Карта: OpenFreeMap, OpenMapTiles, © OpenStreetMap.",fontSize=10.sp,color=Muted,lineHeight=16.sp)
    }
    if(rename!=null)AlertDialog(onDismissRequest={rename=null},title={Text("Название автомобиля")},text={OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Название")})},confirmButton={TextButton({rename?.let{engine.renameVehicle(it,name)};rename=null}){Text("Сохранить")}},dismissButton={TextButton({rename=null}){Text("Отмена")}})
    if(disconnect)AlertDialog(onDismissRequest={disconnect=false},title={Text("Отключить автомобиль?")},text={Text(if(live.recording)"Текущая поездка будет сохранена за VIN подключённого автомобиля." else "История и наблюдения останутся в гараже.")},confirmButton={TextButton({engine.disconnect();disconnect=false}){Text("Отключить")}},dismissButton={TextButton({disconnect=false}){Text("Отмена")}})
    if(capabilities)AlertDialog(onDismissRequest={capabilities=false},title={Text("Доступные параметры")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){Text(selected?.identity ?: "",fontSize=11.sp,color=Muted);ObdMetric.entries.forEach{metric->Row(Modifier.fillMaxWidth()){Text(metric.title,Modifier.weight(1f),fontSize=12.sp);Text(if(metric in (selected?.metrics ?: emptySet()))"Есть" else "Нет",fontSize=12.sp,color=if(metric in (selected?.metrics ?: emptySet()))Secondary else Muted)}};Text("Поддержка определяется по ответам ЭБУ при подключении; временные пропуски отображаются отдельно.",fontSize=10.sp,color=Muted)}},confirmButton={TextButton({capabilities=false}){Text("Закрыть")}})
}
@Composable fun HealthScreen(engine: Engine,onOpen: (Long)->Unit) {
    val vehicle by engine.selectedVehicle.collectAsStateWithLifecycle()
    val trips by engine.trips.collectAsStateWithLifecycle()
    val diagnostics by engine.diagnostics.collectAsStateWithLifecycle()
    val busy by engine.historyBusy.collectAsStateWithLifecycle()
    val signals=remember(trips){healthSignals(trips)}
    val warmTrips=trips.filter{it.status=="complete" && it.comparableSamples>=20 && it.duration>=180000}
    var seedConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Heading("Динамика",vehicle?.name ?: "Выберите автомобиль в гараже")
        Text(vehicle?.identity ?: "",fontFamily=Instruments,fontSize=11.sp,color=Muted)
        CardBox(Modifier.fillMaxWidth()) {
            Text(if(signals.isNotEmpty())"Есть изменения для проверки" else if(warmTrips.size<6)"Собираем историю" else "Наблюдения за поездками",fontSize=19.sp,fontWeight=FontWeight.Medium)
            Text("${warmTrips.size} поездок с данными прогретого двигателя",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
            Text(if(warmTrips.size<6)"Для сравнения нужны минимум 6 поездок от 3 минут с близкими условиями. Отсутствие сигнала не означает исправность автомобиля." else "Сравниваем 3 последние поездки с 3 предыдущими при близких нагрузке, длительности и температуре впуска. Это наблюдения, а не прогноз даты поломки.",fontSize=11.sp,color=Muted,lineHeight=18.sp,modifier=Modifier.padding(top=8.dp))
        }
        signals.forEach { signal -> CardBox(Modifier.fillMaxWidth().clickable { signal.tripIds.lastOrNull()?.let(onOpen) }) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Info,null,tint=Amber,modifier=Modifier.size(20.dp));Text(signal.title,fontSize=16.sp,modifier=Modifier.padding(start=9.dp))}
            Text(signal.detail,fontSize=12.sp,color=Muted,lineHeight=20.sp,modifier=Modifier.padding(top=10.dp));Text("Открыть последнюю поездку →",fontSize=11.sp,color=Accent,modifier=Modifier.padding(top=12.dp))
        } }
        if(signals.isEmpty() && warmTrips.size>=6)Text("По доступным правилам устойчивых изменений не найдено. Поездки с разными сценариями и условиями не объединяются.",fontSize=12.sp,color=Muted,lineHeight=19.sp)
        TrendChart("Охлаждение на прогретом двигателе","°C",warmTrips){it.avgCoolant}
        TrendChart("Долгосрочная коррекция смеси","%",warmTrips){it.avgTrim}
        TrendChart("Температура масла","°C",warmTrips){it.avgOil}
        TrendChart("Расход за поездку","л/100 км",trips.filter{it.status=="complete"}){it.fuelPer100}
        Eyebrow("Ошибки и стоп-кадр")
        if(diagnostics.isEmpty())Text("Снимков диагностики пока нет. Реальные ошибки читаются при подключении ELM; тестовый код появится после записи сценария неисправности.",fontSize=12.sp,color=Muted,lineHeight=19.sp)
        diagnostics.take(3).forEach{snapshot->CardBox(Modifier.fillMaxWidth()){
            Text(if(snapshot.codes.isBlank())"Сохранённых кодов не получено" else snapshot.codes.replace(",",", "),fontFamily=Instruments,fontSize=17.sp,color=if(snapshot.codes.isBlank())Secondary else Amber)
            Text(Instant.ofEpochMilli(snapshot.capturedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM · HH:mm")) + if(snapshot.simulated)" · симуляция" else " · ELM327",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
            if(snapshot.freezeFrame.isNotBlank())Text(snapshot.freezeFrame,fontSize=12.sp,color=Muted,lineHeight=19.sp,modifier=Modifier.padding(top=10.dp))
        }}
        if(vehicle?.simulated==true) {
            OutlinedButton({seedConfirm=true},Modifier.fillMaxWidth(),enabled=!busy){Text(if(busy)"Создаём демоисторию…" else "Создать 6 демопоездок",fontSize=12.sp)}
            Text("Добавит к выбранному VIN поездки с нарастающими коррекциями смеси и температурой. Данные помечены как синтетические.",fontSize=10.sp,color=Muted,lineHeight=17.sp)
        }
    }
    if(seedConfirm)AlertDialog(onDismissRequest={seedConfirm=false},title={Text("Создать демоисторию?")},text={Text("Будут добавлены 6 поездок для ${vehicle?.name}. Другие автомобили не затрагиваются.")},confirmButton={TextButton({engine.seedHistory();seedConfirm=false}){Text("Создать")}},dismissButton={TextButton({seedConfirm=false}){Text("Отмена")}})
}
@Composable private fun TrendChart(title: String,unit: String,trips: List<Trip>,extract: (Trip)->Double?) {
    val points=trips.sortedBy{it.started}.mapNotNull{t->extract(t)?.let{t to it}}.takeLast(12)
    CardBox(Modifier.fillMaxWidth()) {
        Text(title,fontSize=13.sp)
        if(points.isEmpty())Text("Пока нет данных",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
        else {
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("%.1f %s".format(points.last().second,unit),fontFamily=Instruments,fontSize=21.sp);Text("${points.size} поездок",fontSize=10.sp,color=Muted)}
            Canvas(Modifier.fillMaxWidth().height(85.dp).padding(top=12.dp)) {
                val low=points.minOf{it.second}-1;val span=max(2.0,points.maxOf{it.second}-low+1)
                val path=Path()
                points.forEachIndexed{i,(_,value)->val x=if(points.size==1)size.width/2 else size.width*i/(points.size-1);val y=size.height*(1-(value-low)/span).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y);drawCircle(Accent,3.dp.toPx(),Offset(x,y))}
                drawPath(path,Accent,style=Stroke(2.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("%.1f %s".format(points.first().second,unit),fontSize=10.sp,color=Muted);Text("%.1f %s".format(points.last().second,unit),fontSize=10.sp,color=Muted)}
        }
    }
}
