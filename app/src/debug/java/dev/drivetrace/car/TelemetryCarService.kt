package dev.drivetrace.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.drivetrace.DriveTraceApplication
import dev.drivetrace.Engine
import dev.drivetrace.clockText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Experimental DHU integration, deliberately excluded from release builds. */
class TelemetryCarService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TelemetryCarScreen(
            carContext, (application as DriveTraceApplication).engine
        )
    }
}

class TelemetryCarScreen(context: CarContext, private val engine: Engine) : Screen(context) {
    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Keep host refreshes bounded independently of the OBD polling interval.
                while (isActive) {
                    invalidate()
                    delay(1000)
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = engine.state.value
        val pane = Pane.Builder()
        if (!state.connected) {
            pane.addRow(Row.Builder().setTitle("Нет подключения")
                .addText("Подключите ELM327 или симулятор в DriveTrace на телефоне").build())
            pane.addRow(Row.Builder().setTitle("Запись недоступна")
                .addText(engine.connectionMessage.value ?: "Ожидание автомобиля").build())
        } else {
            val frame = state.frame
            val miles = engine.miles.value
            pane.addRow(Row.Builder().setTitle(
                "Скорость: ${value(frame.speed?.let { if (miles) it / 1.609344 else it }, 0)} ${if (miles) "миль/ч" else "км/ч"}"
            ).addText("Обороты: ${frame.rpm?.toString() ?: "—"} об/мин").build())
            pane.addRow(Row.Builder().setTitle("ОЖ: ${value(frame.coolant, 0)} °C")
                .addText("Напряжение: ${value(frame.voltage, 1)} В").build())
            val recording = when {
                state.recording -> "Идёт запись · ${clockText(engine.recordingElapsed)}"
                engine.lastSavedTrip.value != null -> "Поездка сохранена"
                else -> "Запись выключена"
            }
            pane.addRow(Row.Builder().setTitle(recording)
                .addText(if (state.vehicle?.simulated == true) "Симуляция · тестовые данные" else "ELM327 · реальные данные").build())
            pane.addAction(Action.Builder()
                .setTitle(if (state.recording) "Сохранить поездку" else "Начать запись")
                .setOnClickListener {
                    // Preserve the displayed action even if the phone changes state meanwhile.
                    // A repeated start must not toggle recording off; a stale save must not start it.
                    if (state.recording) engine.stopRecording()
                    else if (engine.state.value.connected && !engine.state.value.recording) engine.record()
                }.build())
        }
        return PaneTemplate.Builder(pane.build()).setTitle("DriveTrace")
            .setHeaderAction(Action.APP_ICON).build()
    }

    private fun value(number: Double?, decimals: Int): String =
        number?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"
}
