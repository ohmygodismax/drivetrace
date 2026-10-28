package dev.drivetrace.ui.trips

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivetrace.core.format.clockText
import dev.drivetrace.data.local.Trip
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.Badge
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.Eyebrow
import dev.drivetrace.ui.components.Heading
import dev.drivetrace.ui.components.MiniValue
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Amber
import dev.drivetrace.ui.theme.Line
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Secondary
import java.time.*
import java.time.format.DateTimeFormatter

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
