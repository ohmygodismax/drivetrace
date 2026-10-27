package dev.drivetrace

import android.app.Application
import android.os.SystemClock
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Entity data class Trip(@PrimaryKey(autoGenerate = true) val id: Long = 0, val started: Long, val scenario: String, val duration: Long = 0, val distance: Double = 0.0, val maxSpeed: Double = 0.0, val status: String = "recording", val routeOffsetKm: Double? = null)
@Entity(primaryKeys = ["tripId", "elapsed"], foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)])
data class Sample(val tripId: Long, val elapsed: Long, val timestamp: Long, val speed: Double, val rpm: Int?, val coolant: Double?, val intake: Double?, val voltage: Double?, val throttle: Double?, val distance: Double, val latitude: Double, val longitude: Double) {
    fun frame() = Frame(elapsed, timestamp, speed, rpm, coolant, intake, voltage, throttle, distance, latitude, longitude)
    companion object { fun from(id: Long, f: Frame) = Sample(id, f.elapsed, f.timestamp, f.speed, f.rpm, f.coolant, f.intake, f.voltage, f.throttle, f.distance, f.latitude, f.longitude) }
}
@Entity(foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId")])
data class TripEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val elapsed: Long, val title: String)
@Dao interface TripDao {
    @Query("SELECT * FROM Trip ORDER BY started DESC") fun trips(): Flow<List<Trip>>
    @Query("SELECT * FROM Trip WHERE id = :id") suspend fun trip(id: Long): Trip?
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)
    @Insert suspend fun samples(samples: List<Sample>)
    @Insert suspend fun event(event: TripEvent)
    @Query("SELECT * FROM Sample WHERE tripId = :id ORDER BY elapsed") suspend fun samples(id: Long): List<Sample>
    @Query("SELECT * FROM TripEvent WHERE tripId = :id ORDER BY elapsed") suspend fun events(id: Long): List<TripEvent>
    @Query("UPDATE Trip SET status = 'interrupted', duration = COALESCE((SELECT MAX(elapsed) FROM Sample WHERE tripId = Trip.id),0), distance = COALESCE((SELECT MAX(distance) FROM Sample WHERE tripId = Trip.id),0), maxSpeed = COALESCE((SELECT MAX(speed) FROM Sample WHERE tripId = Trip.id),0) WHERE status = 'recording'") suspend fun recover()
    @Query("DELETE FROM Trip WHERE id = :id") suspend fun delete(id: Long)
}
@Database(entities = [Trip::class, Sample::class, TripEvent::class], version = 2, exportSchema = false)
abstract class TraceDatabase: RoomDatabase() { abstract fun dao(): TripDao }
val Application.preferences by preferencesDataStore("settings")
data class LiveState(val connected: Boolean = false, val running: Boolean = false, val frame: Frame = Frame(), val trail: List<Frame> = emptyList(), val scenario: Scenario = Scenario.CITY, val rate: Int = 1, val recording: Boolean = false)
class DriveTraceApplication: Application() {
    lateinit var engine: Engine
    override fun onCreate() { super.onCreate(); engine = Engine(this) }
}
class Engine(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = Room.databaseBuilder(app, TraceDatabase::class.java, "drivetrace.db").addMigrations(object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE Trip ADD COLUMN routeOffsetKm REAL") }
    }).build().dao()
    private val source: TelemetrySource = SimulatorSource(42)
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    val trips = db.trips().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val demoMode = app.preferences.data.map { it[booleanPreferencesKey("demo_mode")] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
    val interval = app.preferences.data.map { it[intPreferencesKey("interval")] ?: 250 }.stateIn(scope, SharingStarted.Eagerly, 250)
    val miles = app.preferences.data.map { it[booleanPreferencesKey("miles")] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
    private var active: Trip? = null
    private var baseElapsed = 0L
    private var baseDistance = 0.0
    private var recordingStarted = 0L
    private var wallStart = System.currentTimeMillis()
    private val buffer = mutableListOf<Sample>()
    private val emitted = mutableSetOf<String>()
    private val ready = scope.async { db.recover() }
    private var busy = false
    init { scope.launch {
        ready.await()
        var last = SystemClock.elapsedRealtime()
        while(isActive) {
            delay(interval.value.toLong())
            val now = SystemClock.elapsedRealtime()
            val dt = (now - last).coerceAtMost(1000); last = now
            val s = mutable.value
            if(!s.connected || !s.running) continue
            val elapsed = s.frame.elapsed + dt * s.rate
            val next = source.frame(elapsed, wallStart, s.scenario, s.frame.distance)
            val distance = s.frame.distance + (s.frame.speed + next.speed) / 2 * dt * s.rate / 3600000.0
            val (latitude, longitude) = DemoRoute.at(distance)
            val f = next.copy(distance = distance, latitude = latitude, longitude = longitude)
            mutable.value = s.copy(frame = f, trail = (s.trail + f).takeLast(200))
            active?.let { trip ->
                val recorded = f.copy(elapsed = f.elapsed - baseElapsed, timestamp = recordingStarted + f.elapsed - baseElapsed, distance = f.distance - baseDistance)
                buffer += Sample.from(trip.id, recorded)
                active = trip.copy(duration = recorded.elapsed, distance = recorded.distance, maxSpeed = maxOf(trip.maxSpeed, f.speed))
                val events = buildList {
                    if((s.frame.speed - f.speed) / (dt * s.rate / 1000.0) > 4.5) add("Резкое торможение")
                    if((f.estimatedLoadPct ?: 0.0) >= 70) add("Высокая нагрузка")
                    if((f.coolant ?: 0.0) > 110) add("Перегрев · симуляция")
                    if(f.rpm == null) add("Потеря соединения · симуляция")
                    if(s.scenario == Scenario.FAULT && f.elapsed >= 60000) add("DTC P0217 · тестовый код")
                }
                events.filter { emitted.add(it) }.forEach { db.event(TripEvent(tripId = trip.id, elapsed = recorded.elapsed, title = it)) }
                if(buffer.size >= 8) flush()
            }
        }
    } }
    private suspend fun flush() { if(buffer.isNotEmpty()) { val batch = buffer.toList(); buffer.clear(); db.samples(batch) } }
    fun demo(enabled: Boolean) { scope.launch { app.preferences.edit { it[booleanPreferencesKey("demo_mode")] = enabled } } }
    fun connect() { if(!demoMode.value) return; wallStart = System.currentTimeMillis(); mutable.value = LiveState(connected = true, running = true) }
    fun toggle() { mutable.value = mutable.value.copy(running = !mutable.value.running) }
    fun rate() { mutable.value = mutable.value.copy(rate = when(state.value.rate) { 1 -> 2; 2 -> 5; else -> 1 }) }
    fun scenario(scenario: Scenario) { if(state.value.recording) return; wallStart = System.currentTimeMillis(); mutable.value = state.value.copy(scenario = scenario, frame = Frame(), trail = emptyList()) }
    fun reset() { if(!state.value.recording) scenario(state.value.scenario) }
    fun record() { if(busy) return; busy = true; scope.launch { try {
        ready.await()
        if(active == null) {
            val started = System.currentTimeMillis()
            val startFrame = state.value.frame
            baseElapsed = startFrame.elapsed; baseDistance = startFrame.distance; recordingStarted = started
            val trip = Trip(started = started, scenario = state.value.scenario.name, routeOffsetKm = baseDistance)
            val id = db.insert(trip)
            active = trip.copy(id = id); emitted.clear()
            buffer += Sample.from(id, startFrame.copy(elapsed = 0, timestamp = started, distance = 0.0))
            mutable.value = state.value.copy(recording = true, running = true)
        } else { finish() }
    } finally { busy = false } } }
    private suspend fun finish() { val trip = active ?: return; active = null; flush(); db.update(trip.copy(status = "complete")); mutable.value = state.value.copy(recording = false) }
    fun disconnect() { scope.launch { finish(); mutable.value = LiveState() } }
    fun settings(interval: Int, miles: Boolean) { scope.launch { app.preferences.edit { it[intPreferencesKey("interval")] = interval; it[booleanPreferencesKey("miles")] = miles } } }
    fun delete(id: Long) { scope.launch { db.delete(id) } }
}
