package dev.drivetrace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Connection(engine: Engine,onGarage: ()->Unit = {},onSettings: ()->Unit = {},onConnected: ()->Unit) {
    val demo by engine.demoMode.collectAsStateWithLifecycle()
    val demoId by engine.demoCar.collectAsStateWithLifecycle()
    val progress by engine.connectionMessage.collectAsStateWithLifecycle()
    val car=DemoVehicles.find { it.id==demoId } ?: DemoVehicles.first()
    val context=LocalContext.current
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var manualVin by rememberSaveable { mutableStateOf("") }
    var adapters by remember { mutableStateOf<List<AdapterChoice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun listAdapters() { runCatching { pairedAdapters(engine.app) }.onSuccess { adapters=it; stage=2; error=if(it.isEmpty())"Нет сопряжённых адаптеров. Добавьте ELM327 в настройках Bluetooth Android." else null }.onFailure { error=it.localizedMessage; stage=0 } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted)listAdapters() else error="Разрешите подключение к Bluetooth-устройствам, чтобы выбрать адаптер." }
    var pendingAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingAddress?.let { engine.connectElm(it,manualVin) }; pendingAddress=null
    }
    fun connectAdapter(address: String) {
        if(Build.VERSION.SDK_INT>=33 && !notificationAsked && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            pendingAddress=address; notificationAsked=true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else engine.connectElm(address,manualVin)
    }
    LaunchedEffect(demo,demoId) { stage=0; error=null }
    LaunchedEffect(stage) { if(demo && stage==1) { delay(900);stage=2 }; if(demo && stage==3) { delay(1000);stage=4 } }
    fun search() {
        error=null
        if(demo) stage=1
        else if(Build.VERSION.SDK_INT>=31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else listAdapters()
    }
    val connecting=progress!=null && !progress!!.startsWith("Ошибка")
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("DriveTrace",fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));IconButton(onGarage){Icon(Icons.Outlined.Garage,"Открыть гараж",tint=Muted)};IconButton(onSettings){Icon(Icons.Outlined.Settings,"Настройки",tint=Muted)} }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
            if(stage<2) {
                Icon(Icons.Outlined.BluetoothSearching,null,tint=Accent,modifier=Modifier.size(44.dp));Spacer(Modifier.height(24.dp))
                Text("Подключить\nавтомобиль",fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Medium)
                Text(if(stage==1)"Поиск адаптера…" else "Найдите OBD-II адаптер автомобиля",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
                if(demo) { Spacer(Modifier.height(16.dp));Badge("Режим симуляции",Secondary) }
            } else if(demo) {
                Text(car.name,fontSize=25.sp,fontWeight=FontWeight.Medium)
                Text("VIN ${car.vin} · демо",fontFamily=Instruments,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
                if(car.id==DemoVehicles.first().id) VehiclePhoto(Modifier.fillMaxWidth().height(180.dp))
                CardBox(Modifier.fillMaxWidth().padding(top=18.dp)) { Text("ELM327 · Simulator",fontSize=15.sp);Text("${car.metrics.size} доступных показателей",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp));if(stage>=3)Text(if(stage==4)"VIN и параметры определены" else "Проверка VIN и параметров…",fontSize=12.sp,color=Secondary,modifier=Modifier.padding(top=12.dp)) }
            } else {
                Text("Сопряжённые адаптеры",fontSize=23.sp,fontWeight=FontWeight.Medium)
                Text("Выберите свой ELM327. Проверим VIN и поддерживаемые показатели.",fontSize=12.sp,color=Muted,lineHeight=19.sp,modifier=Modifier.padding(vertical=14.dp))
                adapters.forEach { adapter -> CardBox(Modifier.fillMaxWidth().padding(bottom=10.dp).clickable(enabled=!connecting){connectAdapter(adapter.address)}) {
                    Text(adapter.name,fontSize=15.sp);Text(adapter.address,fontSize=11.sp,fontFamily=Instruments,color=Muted,modifier=Modifier.padding(top=5.dp))
                } }
                OutlinedTextField(manualVin,{manualVin=it.uppercase().take(17)},label={Text("VIN, если ЭБУ его не отдаёт")},singleLine=true,enabled=!connecting,modifier=Modifier.fillMaxWidth(),supportingText={Text("17 символов. Не определяется по имени адаптера.",fontSize=10.sp)})
            }
            (error ?: progress)?.let { Text(it,color=if(it.startsWith("Ошибка"))Amber else Muted,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(top=16.dp)) }
            if(!demo && !connecting) TextButton({context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}){Text("Настройки Bluetooth Android",fontSize=12.sp)}
            if(connecting || stage==1 || stage==3) { Spacer(Modifier.height(18.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=Accent,trackColor=Line) }
        }
        if(connecting) OutlinedButton({engine.cancelConnection()},Modifier.fillMaxWidth()){Text("Отменить подключение")}
        else Action(when { demo && stage==2->"Подключить автомобиль";demo && stage==4->"Открыть приборы";stage==1||stage==3->"Подключение…";!demo&&stage==2->"Обновить список";else->"Найти автомобиль" },Icons.Outlined.Bluetooth,Modifier.fillMaxWidth(),stage!=1&&stage!=3) { when { demo&&stage==2->stage=3;demo&&stage==4->onConnected();else->search() } }
        Spacer(Modifier.height(12.dp))
    }
}
