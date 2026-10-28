package dev.drivetrace.telemetry

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dev.drivetrace.DriveTraceApplication
import dev.drivetrace.MainActivity
import dev.drivetrace.R
import dev.drivetrace.core.format.clockText
import dev.drivetrace.ui.components.Action
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/** Keeps the Application-owned engine alive, including when the task is swiped away. */
class TelemetryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine get() = (application as DriveTraceApplication).engine
    private var wakeLock: PowerManager.WakeLock? = null
    private var locationActive = false
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Подключение и запись поездки", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Состояние ELM327, записи поездки и GPS"; setShowBadge(false)
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent == null) { stopSelf(); return START_NOT_STICKY }
        when(intent.action) {
            DISCONNECT -> engine.disconnect()
            SAVE -> if(engine.state.value.connected) engine.stopRecording() else stopSelf()
            else -> {
                try {
                    promote()
                    if(wakeLock == null) {
                        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DriveTrace:telemetry").apply {
                            setReferenceCounted(false)
                            @Suppress("WakelockTimeout")
                            acquire()
                        }
                    }
                    if(!observing) {
                        observing = true
                        scope.launch {
                            combine(engine.state, engine.gpsEnabled, engine.connectionMessage) { state, gps, message -> Triple(state, gps, message) }
                                .collect { (state, gps, _) ->
                                    val track = state.connected && state.running && state.vehicle?.simulated == false && gps && engine.gps.hasPrecisePermission()
                                    if(track && !locationActive) {
                                        // Location type must be active before subscribing to GPS in the background.
                                        if(promote()) { engine.gps.start(); locationActive = true }
                                    } else if(!track && locationActive) {
                                        engine.gps.stop(); locationActive = false
                                    }
                                }
                        }
                        scope.launch {
                            while(isActive) {
                                engine.gps.refresh()
                                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                                delay(2000)
                            }
                        }
                    }
                    intent.getStringExtra(ADDRESS)?.let { engine.openElm(it, intent.getStringExtra(VIN).orEmpty()) }
                } catch(e: Exception) {
                    engine.connectionMessage.value = "Ошибка фоновой службы: ${e.localizedMessage}"
                    engine.disconnect()
                    stopSelf()
                }
            }
        }
        // A killed process must not silently resume or create a new trip.
        return START_NOT_STICKY
    }
    private fun promote(): Boolean {
        val wantsLocation = engine.gpsEnabled.value && engine.gps.hasPrecisePermission()
        var types = if(Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        if(Build.VERSION.SDK_INT >= 29 && wantsLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return try {
            if(Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), types)
            else startForeground(NOTIFICATION, notification())
            true
        } catch(e: SecurityException) {
            // Missing/revoked location permission must not interrupt OBD recording.
            if(Build.VERSION.SDK_INT >= 29 && wantsLocation) {
                startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                false
            } else throw e
        }
    }
    private fun notification(): Notification {
        val state = engine.state.value
        val title = when {
            state.recording -> "Идёт запись поездки"
            state.connected -> "ELM327 подключён · запись выключена"
            else -> engine.connectionMessage.value ?: "Подключение к ELM327"
        }
        val detail = if(state.connected) buildString {
            append(state.vehicle?.name ?: "Автомобиль")
            if(state.recording) append(" · ${clockText(engine.recordingElapsed)}")
            append("\n")
            append(if(engine.gpsEnabled.value) { if(engine.gps.hasPrecisePermission()) engine.gps.status.value else "GPS: нет разрешения на точную геолокацию" } else "GPS выключен · телеметрия без маршрута")
        } else "Соединение с адаптером и проверка параметров ЭБУ"
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(id: Int, name: String) = PendingIntent.getService(this, id, Intent(this, TelemetryService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_telemetry).setContentTitle(title).setContentText(detail.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(detail)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .apply {
                if(Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                if(state.recording) addAction(Notification.Action.Builder(null, "Сохранить поездку", action(1, SAVE)).build())
                addAction(Notification.Action.Builder(null, "Отключить", action(2, DISCONNECT)).build())
            }.build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel(); engine.gps.stop(); locationActive = false
        wakeLock?.let { if(it.isHeld) it.release() }; wakeLock = null
        engine.onServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "telemetry"
        private const val NOTIFICATION = 1001
        private const val ADDRESS = "address"
        private const val VIN = "vin"
        private const val SAVE = "dev.drivetrace.SAVE_TRIP"
        private const val DISCONNECT = "dev.drivetrace.DISCONNECT"
        fun connect(context: Context, address: String, vin: String) {
            context.startForegroundService(Intent(context, TelemetryService::class.java).putExtra(ADDRESS, address).putExtra(VIN, vin))
        }
        fun stop(context: Context) { context.stopService(Intent(context, TelemetryService::class.java)) }
    }
}
