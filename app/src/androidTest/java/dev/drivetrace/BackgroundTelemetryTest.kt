package dev.drivetrace

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real foreground service, Room and LocationManager; only the external ELM hardware is faked. */
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class BackgroundTelemetryTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DriveTraceApplication
    private val engine get() = app.engine
    private lateinit var activity: ActivityScenario<MainActivity>
    private lateinit var location: LocationManager
    private lateinit var transport: TestElm

    private class TestElm : ElmTransport {
        var closed = false
        var fail = false
        override suspend fun command(command: String, timeoutMs: Long): String {
            delay(20)
            if(fail) throw java.io.IOException("Test connection lost")
            return when(command) {
                "0100" -> "41 00 08 18 00 00" // coolant, rpm and speed
                "0105" -> "41 05 82"
                "010C" -> "41 0C 1A F8"
                "010D" -> "41 0D 32"
                "0902" -> "49 02 01 31 44 34 47 50 30 30 52 35 35 42 31 32 33 34 35 36"
                "ATDPN" -> "A6"
                "03" -> "43 00"
                else -> if(command.startsWith("AT")) "OK" else "NO DATA"
            }
        }
        override fun close() { closed = true }
    }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
    private fun main(block: () -> Unit) { instrumentation.runOnMainSync(block) }
    private suspend fun awaitCondition(condition: () -> Boolean) {
        try { withTimeout(15000) { while(!condition()) delay(100) } }
        catch(e: TimeoutCancellationException) {
            throw AssertionError("Timed out: connected=${engine.state.value.connected}, recording=${engine.state.value.recording}, gps=${engine.gps.status.value}, message=${engine.connectionMessage.value}", e)
        }
    }
    @Before fun setup() = runBlocking {
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        activity = ActivityScenario.launch(MainActivity::class.java)
        main { engine.disconnect() }
        awaitCondition { !engine.state.value.connected && !serviceRunning() }
        shell("appops set ${app.packageName} android:mock_location allow")
        location = app.getSystemService(LocationManager::class.java)
        @Suppress("DEPRECATION")
        location.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, 1, 1)
        location.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main { engine.gpsEnabled(true) }
        withTimeout(10000) { engine.gpsEnabled.first { it } }
        transport = TestElm()
        main {
            app.startForegroundService(Intent(app, TelemetryService::class.java))
            engine.connectTransport { transport }
        }
        awaitCondition { engine.state.value.connected }
        awaitCondition { engine.gps.status.value.startsWith("Ожидание") || engine.gps.status.value.startsWith("GPS ·") }
        sendFix(37.61)
        awaitCondition { engine.gps.currentFix() != null }
        main { engine.record() }
        awaitCondition { engine.state.value.recording }
    }
    private fun sendFix(longitude: Double) {
        location.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 55.75; this.longitude = longitude; accuracy = 5f
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
    }
    @Suppress("DEPRECATION")
    private fun serviceRunning() = app.getSystemService(ActivityManager::class.java).getRunningServices(30).any { it.service.className == TelemetryService::class.java.name }
    private fun notificationAction(title: String) {
        val notification = app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1001 }.notification
        notification.actions.single { it.title.toString() == title }.actionIntent.send()
    }
    @After fun cleanup() = runBlocking {
        shell("input keyevent 224"); shell("wm dismiss-keyguard")
        main { engine.disconnect() }
        awaitCondition { !serviceRunning() && !engine.state.value.recording }
        shell("appops set ${app.packageName} POST_NOTIFICATION allow")
        runCatching { location.removeTestProvider(LocationManager.GPS_PROVIDER) }
        activity.close()
    }
    @Test fun recordsWithScreenOffAndNotificationActionsSaveAndDisconnect() = runBlocking {
        val start = engine.state.value.frame.elapsed
        shell("input keyevent 3")
        shell("input keyevent 223")
        repeat(5) { delay(1100); sendFix(37.61 + it * 0.001) }
        assertTrue(serviceRunning())
        assertTrue(engine.state.value.frame.elapsed > start + 4000)
        assertTrue(engine.state.value.frame.hasLocation)
        assertEquals(5f, engine.state.value.frame.locationAccuracyM)
        awaitCondition { app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.notification.actions?.any { action -> action.title == "Сохранить поездку" } == true } }
        notificationAction("Сохранить поездку")
        awaitCondition { !engine.state.value.recording }
        assertTrue(engine.state.value.connected)
        val saved = engine.db.allTrips().first().first { it.scenario == "REAL" }
        assertEquals("complete", saved.status)
        val samples = engine.db.samples(saved.id)
        assertTrue(samples.count { it.hasLocation } >= 3)
        assertTrue(samples.any { it.locationTimestamp != null && it.longitude > 37.61 })
        assertTrue(samples.all { !it.simulated })
        main { engine.record() }
        awaitCondition { engine.state.value.recording }
        delay(1000)
        notificationAction("Отключить")
        awaitCondition { !serviceRunning() && !engine.state.value.recording }
        assertFalse(engine.state.value.connected); assertTrue(transport.closed)
        assertNull(engine.gps.currentFix())
        assertEquals("complete", engine.db.allTrips().first().first { it.scenario == "REAL" }.status)
    }
    @Test fun serviceDestructionFlushesTripAndReleasesSession() = runBlocking {
        delay(1200)
        main { TelemetryService.stop(app) }
        awaitCondition { !serviceRunning() && !engine.state.value.recording }
        assertTrue(transport.closed); assertNull(engine.gps.currentFix())
        val trip = engine.db.allTrips().first().first { it.scenario == "REAL" }
        assertEquals("complete", trip.status)
        assertTrue(engine.db.samples(trip.id).isNotEmpty())
    }
    @Test fun togglingGpsDoesNotInterruptObdRecording() = runBlocking {
        awaitCondition { engine.state.value.frame.hasLocation }
        val before = engine.state.value.frame.elapsed
        main { engine.gpsEnabled(false) }
        awaitCondition { !engine.state.value.frame.hasLocation }
        assertTrue(engine.state.value.recording); assertTrue(engine.state.value.frame.elapsed > before)
        assertNotNull(engine.state.value.frame.rpm)
        main { engine.gpsEnabled(true) }
        withTimeout(10000) { engine.gpsEnabled.first { it } }
        awaitCondition { engine.gps.status.value.startsWith("Ожидание") }
        sendFix(37.65)
        awaitCondition { engine.state.value.frame.hasLocation }
        assertEquals(37.65, engine.state.value.frame.longitude, 0.00001)
        assertTrue(engine.state.value.recording)
    }
    @Test fun staleGpsExpiresAndNewFixResumesRoute() = runBlocking {
        awaitCondition { engine.state.value.frame.hasLocation }
        awaitCondition { !engine.state.value.frame.hasLocation }
        assertTrue(engine.state.value.recording)
        assertNotNull(engine.state.value.frame.rpm)
        sendFix(37.66)
        awaitCondition { engine.state.value.frame.hasLocation }
        main { engine.stopRecording() }
        awaitCondition { !engine.state.value.recording }
        val trip = engine.db.allTrips().first().first { it.scenario == "REAL" }
        val frames = engine.db.samples(trip.id).map { it.frame() }
        assertTrue(frames.any { !it.hasLocation })
        assertTrue(roadSegments(frames, null).any { it.points.isEmpty() })
    }
    @Test fun deniedNotificationsDoNotStopBackgroundRecording() = runBlocking {
        shell("appops set ${app.packageName} POST_NOTIFICATION ignore")
        val before = engine.state.value.frame.elapsed
        shell("input keyevent 3"); shell("input keyevent 223")
        delay(2500)
        assertTrue(serviceRunning()); assertTrue(engine.state.value.recording)
        assertTrue(engine.state.value.frame.elapsed > before + 1000)
        shell("appops set ${app.packageName} POST_NOTIFICATION allow")
    }
    @Test fun failedElmPollSavesTripAndStopsService() = runBlocking {
        delay(1200)
        transport.fail = true
        awaitCondition { !serviceRunning() && !engine.state.value.recording }
        assertFalse(engine.state.value.connected); assertTrue(transport.closed)
        assertTrue(engine.connectionMessage.value.orEmpty().contains("Test connection lost"))
        assertEquals("complete", engine.db.allTrips().first().first { it.scenario == "REAL" }.status)
    }
}
