package dev.drivetrace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivetrace.domain.health.healthSignals
import dev.drivetrace.telemetry.Engine
import dev.drivetrace.ui.components.Badge
import dev.drivetrace.ui.components.Brand
import dev.drivetrace.ui.connection.Connection
import dev.drivetrace.ui.garage.Garage
import dev.drivetrace.ui.health.HealthScreen
import dev.drivetrace.ui.live.Live
import dev.drivetrace.ui.navigation.BottomNavigation
import dev.drivetrace.ui.settings.SettingsScreen
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Bg
import dev.drivetrace.ui.trips.Replay
import dev.drivetrace.ui.trips.Trips

@Composable fun App(engine: Engine) {
    val live by engine.state.collectAsStateWithLifecycle()
    val currentTrips by engine.trips.collectAsStateWithLifecycle()
    val signals = remember(currentTrips) { healthSignals(currentTrips) }
    var page by rememberSaveable { mutableStateOf("live") }
    var wasConnected by rememberSaveable { mutableStateOf(live.connected) }
    LaunchedEffect(live.connected) {
        if(live.connected != wasConnected) { page = "live"; wasConnected = live.connected }
    }
    var replayId by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.activity.compose.BackHandler(enabled = replayId == null && page != "live") { page = "live" }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val connectingScreen = !live.connected && page == "live" && replayId == null
            if(!connectingScreen) Row(Modifier.fillMaxWidth().padding(22.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f))
                Badge(if(live.vehicle?.simulated == true) "SIMULATED" else if(live.connected) "ELM327" else "Не подключён", Accent)
            }
            Box(Modifier.weight(1f)) {
                when {
                    replayId != null -> Replay(engine, replayId!!) { replayId = null }
                    connectingScreen -> Connection(engine, onGarage = { page = "garage" }, onSettings = { page = "settings" }) {
                        page = "live"; replayId = null; engine.connect()
                    }
                    page == "live" -> Live(engine, live, onOpenTrip = { replayId = it }, onHealth = {
                        live.vehicle?.let { engine.selectVehicle(it.id) }; page = "health"
                    })
                    page == "trips" -> Trips(engine) { replayId = it }
                    page == "health" -> HealthScreen(engine) { replayId = it }
                    page == "garage" -> Garage(engine)
                    else -> SettingsScreen(engine)
                }
            }
            if(replayId == null) BottomNavigation(page, signals.size) { page = it }
        }
    }
}
