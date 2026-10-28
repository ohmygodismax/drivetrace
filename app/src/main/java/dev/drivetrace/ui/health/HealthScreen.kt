package dev.drivetrace.ui.health

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import dev.drivetrace.ui.trips.scenarioTitle
import dev.drivetrace.model.Frame
import dev.drivetrace.data.local.Trip
import dev.drivetrace.domain.health.healthSignals
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.Eyebrow
import dev.drivetrace.ui.components.Heading
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Amber
import dev.drivetrace.ui.theme.Instruments
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Secondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

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
