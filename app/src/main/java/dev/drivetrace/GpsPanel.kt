package dev.drivetrace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun GpsPanel(engine: Engine) {
    val enabled by engine.gpsEnabled.collectAsStateWithLifecycle()
    val status by engine.gps.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        engine.gpsEnabled(true)
        engine.gps.refresh()
    }
    fun enable() {
        if(engine.gps.hasPrecisePermission()) engine.gpsEnabled(true)
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    CardBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Маршрут по GPS телефона", Modifier.weight(1f), fontSize = 13.sp)
            Switch(enabled, { if(it) enable() else engine.gpsEnabled(false) })
        }
        Text(if(enabled && !engine.gps.hasPrecisePermission()) "Для маршрута разрешите точную геолокацию" else if(enabled) status else "GPS выключен · поездка сохранится без маршрута", fontSize = 12.sp, color = Muted)
        Text("Координаты сохраняются с поездкой. GPS и ELM работают при выключенном экране; отключить их можно в уведомлении.", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        if(enabled) {
            if(!engine.gps.hasPrecisePermission()) {
                TextButton({ enable() }) { Text("Разрешить точную геолокацию", fontSize = 11.sp) }
                TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Разрешения приложения", fontSize = 11.sp) }
            }
            TextButton({ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Настройки геолокации телефона", fontSize = 11.sp) }
        }
    }
}
