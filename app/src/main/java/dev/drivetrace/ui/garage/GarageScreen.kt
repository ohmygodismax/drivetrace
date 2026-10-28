package dev.drivetrace.ui.garage

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivetrace.domain.health.healthSignals
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.Vehicle
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.Badge
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.Heading
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Amber
import dev.drivetrace.ui.theme.Instruments
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Secondary

@Composable fun Garage(engine: Engine) {
    val vehicles by engine.vehicles.collectAsStateWithLifecycle()
    val selected by engine.selectedVehicle.collectAsStateWithLifecycle()
    val fleet by engine.fleetTrips.collectAsStateWithLifecycle()
    val live by engine.state.collectAsStateWithLifecycle()
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
        if(live.connected) OutlinedButton({disconnect=true},Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.LinkOff,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Отключить автомобиль")
        }
    }
    if(rename!=null)AlertDialog(onDismissRequest={rename=null},title={Text("Название автомобиля")},text={OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Название")})},confirmButton={TextButton({rename?.let{engine.renameVehicle(it,name)};rename=null}){Text("Сохранить")}},dismissButton={TextButton({rename=null}){Text("Отмена")}})
    if(disconnect)AlertDialog(onDismissRequest={disconnect=false},title={Text("Отключить автомобиль?")},text={Text(if(live.recording)"Текущая поездка будет сохранена за VIN подключённого автомобиля." else "История и наблюдения останутся в гараже.")},confirmButton={TextButton({engine.disconnect();disconnect=false}){Text("Отключить")}},dismissButton={TextButton({disconnect=false}){Text("Отмена")}})
    if(capabilities)AlertDialog(onDismissRequest={capabilities=false},title={Text("Доступные параметры")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){Text(selected?.identity ?: "",fontSize=11.sp,color=Muted);ObdMetric.entries.forEach{metric->Row(Modifier.fillMaxWidth()){Text(metric.title,Modifier.weight(1f),fontSize=12.sp);Text(if(metric in (selected?.metrics ?: emptySet()))"Есть" else "Нет",fontSize=12.sp,color=if(metric in (selected?.metrics ?: emptySet()))Secondary else Muted)}};Text("Поддержка определяется по ответам ЭБУ при подключении; временные пропуски отображаются отдельно.",fontSize=10.sp,color=Muted)}},confirmButton={TextButton({capabilities=false}){Text("Закрыть")}})
}
