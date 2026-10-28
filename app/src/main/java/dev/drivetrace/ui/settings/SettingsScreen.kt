package dev.drivetrace.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivetrace.data.simulator.DemoVehicles
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.CardBox
import dev.drivetrace.ui.components.GpsPanel
import dev.drivetrace.ui.components.Heading
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Ink
import dev.drivetrace.ui.theme.Line
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Plex

@Composable fun SettingsScreen(engine: Engine) {
    val miles by engine.miles.collectAsStateWithLifecycle()
    val interval by engine.interval.collectAsStateWithLifecycle()
    val demo by engine.demoMode.collectAsStateWithLifecycle()
    val demoId by engine.demoCar.collectAsStateWithLifecycle()
    val live by engine.state.collectAsStateWithLifecycle()
    val progress by engine.connectionMessage.collectAsStateWithLifecycle()
    val canChangeSource = !live.connected && (progress == null || progress!!.startsWith("Ошибка"))
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF2C3E4D), selectedLabelColor = Ink)
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.3" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Настройки", "Подключение, маршрут и единицы измерения")
        CardBox(Modifier.fillMaxWidth()) {
            SettingsTitle("Единицы измерения", Icons.Outlined.Straighten)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!miles, { engine.settings(interval, false) }, label = { Text("Километры") }, colors = chipColors)
                FilterChip(miles, { engine.settings(interval, true) }, label = { Text("Мили") }, colors = chipColors)
            }
            Text("Меняет скорость и расстояние в приборах и истории.", fontSize = 12.sp, color = Muted)
        }
        CardBox(Modifier.fillMaxWidth()) {
            SettingsTitle("Подключение", Icons.Outlined.Bluetooth)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Режим симуляции", fontSize = 14.sp)
                    Text(if(demo) "Демонстрационный автомобиль" else "Реальный адаптер ELM327", fontSize = 12.sp, color = Muted)
                }
                Switch(demo, { engine.demo(it) }, enabled = canChangeSource, modifier = Modifier.semantics { contentDescription = "Режим симуляции" })
            }
            if(!canChangeSource) Text(if(live.connected) "Для смены источника отключите автомобиль в гараже." else "Источник можно сменить после завершения подключения.", fontSize = 12.sp, color = Muted)
            if(demo) DemoVehicles.forEach { car ->
                Row(Modifier.fillMaxWidth().selectable(selected = demoId == car.id, enabled = canChangeSource, role = Role.RadioButton, onClick = { engine.demoVehicle(car.id) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(demoId == car.id, null, enabled = canChangeSource)
                    Text(car.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            } else {
                Text("Bluetooth Classic. Сначала выполните сопряжение адаптера в настройках Android.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                TextButton({ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Открыть настройки Bluetooth", fontSize = 13.sp) }
            }
        }
        if(!demo) GpsPanel(engine)
        if(demo) CardBox(Modifier.fillMaxWidth()) {
            SettingsTitle("Симуляция", Icons.Outlined.Science)
            Text("Интервал показаний", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 250, 500).forEach { value ->
                    FilterChip(interval == value, { engine.settings(value, miles) }, label = { Text("$value мс", fontSize = 12.sp) }, colors = chipColors)
                }
            }
            Text("Влияет на симулятор. Частота реальных показаний зависит от ELM327 и числа параметров.", fontSize = 12.sp, color = Muted)
        }
        CardBox(Modifier.fillMaxWidth()) {
            SettingsTitle("Запись в фоне", Icons.Outlined.NotificationsNone)
            Text("При подключённом ELM запись и GPS работают при выключенном экране. Сохранить поездку и отключить адаптер можно из уведомления.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
                Text("Настройки уведомлений", fontSize = 13.sp)
            }
        }
        CardBox(Modifier.fillMaxWidth()) {
            SettingsTitle("О приложении", Icons.Outlined.Info)
            Text("DriveTrace $version", fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Text("Поездки хранятся на телефоне. Для загрузки карты нужен интернет.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
            Text("Карта: OpenFreeMap, OpenMapTiles, © OpenStreetMap. Шрифты IBM Plex: SIL OFL.", fontSize = 12.sp, color = Muted)
            TextButton({ uri.openUri("https://commons.wikimedia.org/wiki/File:2023_FAW-Volkswagen_Tayron_300TSI_(facelift,_front).jpg") }, contentPadding = PaddingValues(0.dp)) {
                Text("Фото: User3204 · CC BY-SA 4.0", fontSize = 12.sp)
            }
            Text("Tayron 2023 (300TSI) на фото иллюстрирует кузов демонстрационного автомобиля.", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable private fun SettingsTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
