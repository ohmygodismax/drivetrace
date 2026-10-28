package dev.drivetrace.telemetry

import android.app.Application
import android.os.SystemClock
import androidx.datastore.preferences.core.*
import androidx.room.*
import dev.drivetrace.data.TelemetrySource
import dev.drivetrace.data.local.DiagnosticSnapshot
import dev.drivetrace.data.local.GPS_MIGRATION
import dev.drivetrace.data.local.Sample
import dev.drivetrace.data.local.TraceDatabase
import dev.drivetrace.data.local.Trip
import dev.drivetrace.data.local.TripEvent
import dev.drivetrace.data.location.PhoneLocation
import dev.drivetrace.data.location.withGps
import dev.drivetrace.data.obd.ClassicTransport
import dev.drivetrace.data.obd.ElmSession
import dev.drivetrace.data.obd.ElmTransport
import dev.drivetrace.data.obd.classicSocket
import dev.drivetrace.data.preferences.preferences
import dev.drivetrace.data.simulator.DemoRoute
import dev.drivetrace.data.simulator.DemoVehicles
import dev.drivetrace.data.simulator.SimulatorSource
import dev.drivetrace.domain.health.summarize
import dev.drivetrace.domain.route.estimatedLoadPct
import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario
import dev.drivetrace.model.Vehicle
import dev.drivetrace.model.encodeMetrics
import dev.drivetrace.model.only
import dev.drivetrace.model.validVin
import dev.drivetrace.model.vehicleId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class Engine(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = Room.databaseBuilder(app, TraceDatabase::class.java, "drivetrace.db")
        .addMigrations(GPS_MIGRATION)
        .fallbackToDestructiveMigration(dropAllTables = true).build().dao()
    val gps = PhoneLocation(app)
    val gpsEnabled = app.preferences.data.map { it[booleanPreferencesKey("gps_enabled")] ?: false }.stateIn(scope,SharingStarted.Eagerly,false)
    fun gpsEnabled(enabled: Boolean) { scope.launch { app.preferences.edit { it[booleanPreferencesKey("gps_enabled")]=enabled } } }
    private var source: TelemetrySource = SimulatorSource(42)
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    val fleetTrips = db.allTrips().stateIn(scope,SharingStarted.Eagerly,emptyList())
    val vehicles = db.vehicles().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val selectedVehicle = combine(vehicles, app.preferences.data) { cars,prefs -> cars.find { it.id == prefs[stringPreferencesKey("vehicle")] } ?: cars.firstOrNull { it.id != "legacy" } }.stateIn(scope,SharingStarted.Eagerly,null)
    val trips = selectedVehicle.flatMapLatest { db.trips(it?.id ?: "") }.stateIn(scope,SharingStarted.Eagerly,emptyList())
    val diagnostics = selectedVehicle.flatMapLatest { db.diagnostics(it?.id ?: "") }.stateIn(scope,SharingStarted.Eagerly,emptyList())
    val demoMode = app.preferences.data.map { it[booleanPreferencesKey("demo_mode")] ?: false }.stateIn(scope,SharingStarted.Eagerly,false)
    val demoCar = app.preferences.data.map { it[stringPreferencesKey("demo_car")] ?: DemoVehicles.first().id }.stateIn(scope,SharingStarted.Eagerly,DemoVehicles.first().id)
    val interval = app.preferences.data.map { it[intPreferencesKey("interval")] ?: 250 }.stateIn(scope,SharingStarted.Eagerly,250)
    val miles = app.preferences.data.map { it[booleanPreferencesKey("miles")] ?: false }.stateIn(scope,SharingStarted.Eagerly,false)
    val connectionMessage = MutableStateFlow<String?>(null)
    val historyBusy = MutableStateFlow(false)
    private val savedTrip = MutableStateFlow<Trip?>(null)
    val lastSavedTrip = savedTrip.asStateFlow()
    private var active: Trip? = null
    private var baseElapsed = 0L; private var baseDistance = 0.0; private var recordingStarted = 0L
    private var wallStart = System.currentTimeMillis()
    private val buffer = mutableListOf<Sample>(); private val emitted = mutableSetOf<String>()
    private val recordingMutex = Mutex()
    private var closing = false
    private var busy = false
    private var sessionGeneration = 0L
    private var elm: ElmSession? = null
    private var connectingSocket: android.bluetooth.BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val ready = scope.async { db.recover(); DemoVehicles.forEach { db.addVehicle(it) } }
    init { scope.launch {
        ready.await(); var last = SystemClock.elapsedRealtime()
        while(isActive) {
            delay(interval.value.toLong())
            val s = state.value
            val generation = sessionGeneration
            if(!s.connected || !s.running) { last = SystemClock.elapsedRealtime(); continue }
            try {
                val now = SystemClock.elapsedRealtime()
                var elapsed = s.frame.elapsed + (now-last).coerceAtMost(1000)*s.rate
                val f: Frame
                if(s.vehicle?.simulated != false) {
                    val dt = (now-last).coerceAtMost(1000)*s.rate
                    val next = source.frame(elapsed,wallStart,s.scenario,s.frame.distance).only(s.vehicle?.metrics ?: emptySet())
                    val distance = s.frame.distance + ((s.frame.speed ?: 0.0)+(next.speed ?: 0.0))/2*dt/3600000.0
                    val point = DemoRoute.at(distance)
                    f = next.copy(distance = distance,latitude = point.first,longitude = point.second)
                    last = now
                } else {
                    val next = (elm ?: error("Соединение закрыто")).poll(elapsed,wallStart,s.frame.distance)
                    val finished = SystemClock.elapsedRealtime(); val dt = finished-last
                    elapsed = s.frame.elapsed+dt
                    val distance = if(s.frame.speed != null && next.speed != null) s.frame.distance+(s.frame.speed+next.speed)/2*dt/3600000.0 else s.frame.distance
                    f = next.copy(elapsed = elapsed,timestamp = wallStart+elapsed,distance = distance).withGps(gps.currentFix(),finished); last = finished
                }
                // A disconnect while awaiting I/O must not resurrect the session.
                if(generation != sessionGeneration || !state.value.connected || state.value.vehicle?.id != s.vehicle?.id) continue
                mutable.value = state.value.copy(frame = f,trail = (state.value.trail+f).takeLast(200))
                recordingMutex.withLock { active?.takeIf { f.elapsed > baseElapsed }?.let { trip ->
                    val recorded = f.copy(elapsed = f.elapsed-baseElapsed,timestamp = recordingStarted+f.elapsed-baseElapsed,distance = (f.distance-baseDistance).coerceAtLeast(0.0))
                    buffer += Sample.from(trip.id,recorded)
                    active = trip.copy(duration = recorded.elapsed,distance = recorded.distance,maxSpeed = maxOf(trip.maxSpeed,f.speed ?: 0.0))
                    val events = buildList {
                        if(s.frame.speed != null && f.speed != null && (s.frame.speed-f.speed)/((f.elapsed-s.frame.elapsed).coerceAtLeast(1)/1000.0)>4.5) add("Резкое торможение")
                        if((f.estimatedLoadPct ?: 0.0)>=70) add("Высокая нагрузка")
                        if((f.coolant ?: 0.0)>110) add("Высокая температура ОЖ")
                        if(f.simulated && f.rpm == null) add("Потеря соединения · симуляция")
                        if(f.simulated && s.scenario == Scenario.FAULT && f.elapsed>=60000) add("DTC P0217 · тестовый код")
                    }
                    events.filter { emitted.add(it) }.forEach { db.event(TripEvent(tripId = trip.id,elapsed = recorded.elapsed,title = it)) }
                    if(buffer.size>=8) flush()
                } }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                if(generation == sessionGeneration && state.value.connected) {
                    connectionMessage.value = "Ошибка: поток остановлен — ${e.localizedMessage}"
                    disconnect()
                }
            }
        }
    } }
    private suspend fun flush() { if(buffer.isNotEmpty()) { val batch=buffer.toList(); db.samples(batch); buffer.clear() } }
    fun demo(enabled: Boolean) { scope.launch { app.preferences.edit { it[booleanPreferencesKey("demo_mode")]=enabled } } }
    fun demoVehicle(id: String) { scope.launch { app.preferences.edit { it[stringPreferencesKey("demo_car")]=id } } }
    fun selectVehicle(id: String) { scope.launch { app.preferences.edit { it[stringPreferencesKey("vehicle")]=id } } }
    fun connect() { if(!demoMode.value) return; scope.launch {
        ready.await(); val vehicle=db.vehicle(demoCar.value) ?: DemoVehicles.first()
        selectVehicle(vehicle.id); db.updateVehicle(vehicle.copy(lastSeen = System.currentTimeMillis()))
        source=SimulatorSource(if(vehicle.id == DemoVehicles.first().id)42 else 84); wallStart=System.currentTimeMillis()
        mutable.value=LiveState(connected=true,running=true,vehicle=vehicle,frame=source.frame(0,wallStart,Scenario.CITY,0.0).only(vehicle.metrics))
    } }
    fun connectElm(address: String,manualVin: String = "") {
        if(closing || connectionJob?.isActive == true || state.value.connected) return
        connectionMessage.value = "Запуск фоновой службы"
        runCatching { TelemetryService.connect(app,address,manualVin) }
            .onFailure { connectionMessage.value = "Ошибка запуска: ${it.localizedMessage}" }
    }
    internal fun openElm(address: String, manualVin: String) = connectTransport(manualVin) {
        val socket=classicSocket(app,address); connectingSocket=socket
        val watchdog=scope.launch { delay(15000); runCatching { socket.close() } }
        try { withContext(Dispatchers.IO) { socket.connect() } } finally { watchdog.cancel() }
        ClassicTransport(socket)
    }
    internal fun connectTransport(manualVin: String = "", open: suspend () -> ElmTransport) {
        if(closing || connectionJob?.isActive == true || state.value.connected) return
        connectionJob=scope.launch {
            var session: ElmSession?=null
            try {
                ready.await(); connectionMessage.value="Соединение с адаптером"
                session=ElmSession(open())
                val identity=session.discover { connectionMessage.value=it }
                val vin=identity.vin ?: manualVin.trim().uppercase().takeIf(::validVin) ?: error("VIN не прочитан. Введите VIN вручную и повторите подключение.")
                val id=vehicleId(vin,false); val existing=db.vehicle(id)
                val vehicle=Vehicle(id,vin,existing?.name ?: "Автомобиль · ${vin.takeLast(4)}",false,encodeMetrics(identity.metrics),System.currentTimeMillis())
                if(existing==null) db.addVehicle(vehicle) else db.updateVehicle(vehicle)
                db.diagnostic(DiagnosticSnapshot(vehicleId=id,capturedAt=System.currentTimeMillis(),codes=identity.codes.joinToString(","),freezeFrame=identity.freeze,simulated=false))
                selectVehicle(id); sessionGeneration++; elm=session; session=null; wallStart=System.currentTimeMillis()
                mutable.value=LiveState(connected=true,running=true,vehicle=vehicle,frame=Frame(speed=null,rpm=null,coolant=null,intake=null,voltage=null,throttle=null,simulated=false,hasLocation=false,latitude=0.0,longitude=0.0))
                connectionMessage.value=null
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                connectionMessage.value="Ошибка: ${e.localizedMessage}"
                TelemetryService.stop(app)
            } finally {
                session?.close(); if(!state.value.connected) connectingSocket?.close(); connectingSocket=null
            }
        }
    }
    fun cancelConnection() { runCatching { connectingSocket?.close() }; connectionJob?.cancel(); connectionMessage.value=null; if(!state.value.connected) TelemetryService.stop(app) }
    fun toggle() { if(state.value.error == null) mutable.value=state.value.copy(running=!state.value.running) }
    fun rate() { if(state.value.vehicle?.simulated == true) mutable.value=state.value.copy(rate=when(state.value.rate){1->2;2->5;else->1}) }
    fun scenario(scenario: Scenario) { if(state.value.recording || state.value.vehicle?.simulated != true) return; wallStart=System.currentTimeMillis(); mutable.value=state.value.copy(scenario=scenario,frame=source.frame(0,wallStart,scenario,0.0).only(state.value.vehicle!!.metrics),trail=emptyList()) }
    fun reset() { scenario(state.value.scenario) }
    fun record() { if(closing || busy || !state.value.connected || state.value.vehicle==null || state.value.error != null) return; busy=true; scope.launch { try {
        ready.await()
        recordingMutex.withLock {
        if(!state.value.connected || closing) return@withLock
        if(active==null) {
            val started=System.currentTimeMillis(); val startFrame=state.value.frame.let { if(it.simulated) it else it.withGps(gps.currentFix(),SystemClock.elapsedRealtime()) }
            baseElapsed=startFrame.elapsed; baseDistance=startFrame.distance; recordingStarted=started
            val trip=Trip(started=started,scenario=if(startFrame.simulated)state.value.scenario.name else "REAL",routeOffsetKm=baseDistance.takeIf { startFrame.simulated },vehicleId=state.value.vehicle!!.id,supported=state.value.vehicle!!.supported)
            val id=db.insert(trip); active=trip.copy(id=id); emitted.clear(); savedTrip.value=null
            buffer+=Sample.from(id,startFrame.copy(elapsed=0,timestamp=started,distance=0.0))
            mutable.value=state.value.copy(recording=true,running=true)
        } else finish()
        }
    } finally { busy=false } } }
    val recordingElapsed: Long get() = if(state.value.recording) (state.value.frame.elapsed-baseElapsed).coerceAtLeast(0) else 0
    val recordingDistance: Double get() = if(state.value.recording) (state.value.frame.distance-baseDistance).coerceAtLeast(0.0) else 0.0
    fun stopRecording() { scope.launch { recordingMutex.withLock { finish() } } }
    private suspend fun finish() {
        val trip=active ?: return; flush()
        val frames=db.samples(trip.id).map { it.frame() }
        val completed=summarize(trip.copy(status="complete"),frames)
        db.update(completed)
        if(trip.scenario==Scenario.FAULT.name) db.diagnostic(DiagnosticSnapshot(vehicleId=trip.vehicleId,capturedAt=System.currentTimeMillis(),codes="P0217",freezeFrame="Демонстрационный стоп-кадр: ОЖ 124 °C; 780 об/мин; нагрузка 3%",simulated=true))
        active=null
        savedTrip.value=completed
        mutable.value=state.value.copy(recording=false)
    }
    fun disconnect(stopService: Boolean = true) {
        if(closing) return
        closing = true; sessionGeneration++
        connectionJob?.cancel(); runCatching { connectingSocket?.close() }
        elm?.close(); elm=null; gps.stop()
        mutable.value=state.value.copy(connected=false,running=false,frame=state.value.frame.withGps(null,SystemClock.elapsedRealtime()))
        scope.launch {
            try {
                recordingMutex.withLock { finish() }
                mutable.value=LiveState()
            } finally {
                if(stopService) TelemetryService.stop(app)
                closing=false
            }
        }
    }
    internal fun onServiceStopped() {
        if(!closing && (state.value.connected || connectionJob?.isActive == true)) disconnect(stopService=false)
    }
    fun settings(interval: Int,miles: Boolean) { scope.launch { app.preferences.edit { it[intPreferencesKey("interval")]=interval; it[booleanPreferencesKey("miles")]=miles } } }
    fun renameVehicle(vehicle: Vehicle,name: String) { scope.launch { val renamed=vehicle.copy(name=name.trim().take(80).ifBlank { vehicle.name }); db.updateVehicle(renamed); if(state.value.vehicle?.id==vehicle.id) mutable.value=state.value.copy(vehicle=renamed) } }
    fun delete(id: Long) { scope.launch { db.delete(id) } }
    fun seedHistory() { val vehicle=selectedVehicle.value ?: return; if(!vehicle.simulated || vehicle.id=="legacy" || historyBusy.value) return; historyBusy.value=true; scope.launch { try {
        repeat(6) { day ->
            val started=System.currentTimeMillis()-(7-day)*86400000L
            val sim=SimulatorSource(42,if(day<3)0.0 else 0.8)
            var distance=0.0; var speed=0.0
            val frames=(0L..480000L step 1000L).map { t ->
                val f=sim.frame(t,started,Scenario.CITY,distance).only(vehicle.metrics)
                distance+=(speed+(f.speed ?: 0.0))/2/3600; speed=f.speed ?: 0.0
                val point=DemoRoute.at(distance); f.copy(distance=distance,latitude=point.first,longitude=point.second)
            }
            val trip=Trip(started=started,scenario=Scenario.CITY.name,duration=480000,distance=distance,status="complete",routeOffsetKm=0.0,vehicleId=vehicle.id,supported=vehicle.supported)
            val id=db.insert(trip); db.samples(frames.map { Sample.from(id,it) }); db.update(summarize(trip.copy(id=id),frames))
        }
    } finally { historyBusy.value=false } } }
}
