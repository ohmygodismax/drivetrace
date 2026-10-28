package dev.drivetrace.car

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.PaneTemplate
import androidx.car.app.serialization.Bundleable
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.drivetrace.DriveTraceApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarTelemetryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DriveTraceApplication
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(15000) {
        while (!condition()) delay(50)
    }

    @Test fun carControlsSharePhoneRecordingAndPersistTrip() = runBlocking {
        val engine = app.engine
        val previousDemo = engine.demoMode.first()
        var controller: ScreenController? = null
        var savedId: Long? = null
        try {
            main { engine.disconnect() }
            awaitCondition { !engine.state.value.connected && !engine.state.value.recording && engine.state.value.vehicle == null }
            lateinit var screen: TelemetryCarScreen
            main {
                screen = TelemetryCarScreen(TestCarContext.createCarContext(app), engine)
                controller = ScreenController(screen).moveToState(Lifecycle.State.STARTED)
                val disconnected = screen.onGetTemplate() as PaneTemplate
                assertTrue(disconnected.pane.actions.isEmpty())
                assertEquals("Нет подключения", disconnected.pane.rows.first().title.toString())
                engine.demo(true)
            }
            engine.demoMode.first { it }
            main { engine.connect() }
            awaitCondition { engine.state.value.connected }
            lateinit var start: Action
            main {
                val template = screen.onGetTemplate() as PaneTemplate
                assertEquals(3, template.pane.rows.size)
                assertTrue(template.pane.rows.last().texts.first().toString().contains("Симуляция"))
                start = template.pane.actions.single()
                click(start)
            }
            awaitCondition { engine.state.value.recording }
            main { click(start) } // A double click on the old start action must not stop the trip.
            delay(300)
            assertTrue(engine.state.value.recording)
            lateinit var save: Action
            main {
                save = (screen.onGetTemplate() as PaneTemplate).pane.actions.single()
                assertEquals("Сохранить поездку", save.title.toString())
                // Closing the car screen must not stop the shared engine or recording.
                controller!!.moveToState(Lifecycle.State.DESTROYED)
            }
            delay(500)
            assertTrue(engine.state.value.recording)
            main { click(save) }
            awaitCondition { !engine.state.value.recording && engine.lastSavedTrip.value != null }
            val trip = engine.lastSavedTrip.value!!
            savedId = trip.id
            assertEquals("complete", engine.db.trip(trip.id)!!.status)
            assertTrue(engine.db.samples(trip.id).size >= 2)
            main { click(save) } // A stale save must not start another recording.
            delay(300)
            assertFalse(engine.state.value.recording)
            assertTrue(engine.state.value.connected)
        } finally {
            main {
                controller?.moveToState(Lifecycle.State.DESTROYED)
                engine.disconnect()
                engine.demo(previousDemo)
            }
            awaitCondition { !engine.state.value.recording }
            savedId?.let { engine.db.delete(it) }
        }
    }

    private fun click(action: Action) {
        action.onClickDelegate!!.sendClick(object : OnDoneCallback {
            override fun onFailure(response: Bundleable) {
                throw AssertionError("Car action failed: $response")
            }
        })
    }
}
