package dev.drivetrace

import kotlin.math.*

enum class Scenario(val title: String, val subtitle: String) {
    CITY("Город", "Светофоры и плавные разгоны"), HIGHWAY("Трасса", "Крейсерский режим до 120 км/ч"),
    SPORT("Динамичный", "Ускорения и резкое торможение"), FAULT("Неисправность", "Перегрев, падение напряжения и обрыв")
}
data class Frame(val elapsed: Long = 0, val timestamp: Long = 0, val speed: Double? = 0.0, val rpm: Int? = 780, val coolant: Double? = 24.0, val intake: Double? = 22.0, val voltage: Double? = 14.2, val throttle: Double? = 0.0, val distance: Double = 0.0, val latitude: Double = DemoRoute.at(0.0).first, val longitude: Double = DemoRoute.at(0.0).second, val simulated: Boolean = true, val engineLoad: Double? = null, val oilC: Double? = null, val manifoldKpa: Double? = null, val baroKpa: Double? = null, val fuelLh: Double? = null, val stft: Double? = null, val ltft: Double? = null, val timing: Double? = null, val maf: Double? = null, val hasLocation: Boolean = true, val locationAccuracyM: Float? = null, val locationTimestamp: Long? = null)
interface TelemetrySource { fun frame(elapsed: Long, started: Long, scenario: Scenario, distance: Double): Frame }
class SimulatorSource(private val seed: Int = 42, private val deterioration: Double = 0.0): TelemetrySource {
    override fun frame(elapsed: Long, started: Long, scenario: Scenario, distance: Double): Frame {
        val t = elapsed / 1000.0
        fun speedAt(seconds: Double): Double {
            val phase = (seconds + seed % 7) % 80
            val city = when { phase < 8 -> 0.0; phase < 25 -> (phase - 8) * 3.2; phase < 48 -> 51 + 5 * sin(seconds / 3); phase < 60 -> max(0.0, 52 - (phase - 48) * 4.8); else -> 0.0 }
            return when(scenario) { Scenario.CITY -> city; Scenario.HIGHWAY -> min(seconds * 2.0, 106 + 9 * sin(seconds / 12)); Scenario.SPORT -> max(0.0, 60 + 52 * sin(seconds / 7)); Scenario.FAULT -> if(seconds in 45.0..51.0) 0.0 else city }
        }
        val speed = speedAt(t)
        val acceleration = (speed - speedAt(max(0.0, t - 0.25))) / 0.25
        val loss = scenario == Scenario.FAULT && t in 45.0..51.0
        val throttle = if(speed < 1) 0.0 else (12 + speed * 0.18 + max(0.0, acceleration) * 12 + 3 * sin(t / 5)).coerceIn(0.0, 100.0)
        val (latitude, longitude) = DemoRoute.at(distance)
        return Frame(elapsed, started + elapsed, speed, if(loss) null else (780 + speed * 21 + throttle * 12).toInt(), if(loss) null else if(scenario == Scenario.FAULT) min(124.0, 24 + t * 2) else min(91.0 + deterioration * 12, 24 + t * 0.36), if(loss) null else 22 + speed * 0.06, if(loss) null else if(scenario == Scenario.FAULT && t > 30) 11.4 else 14.1 + 0.12 * sin(t / 5), if(loss) null else throttle, distance, latitude, longitude, engineLoad = if(loss) null else (throttle * 0.82 + speed * 0.11).coerceIn(0.0, 100.0),
            oilC = if(loss) null else min(104.0 + deterioration * 9, 24 + t * 0.23),
            manifoldKpa = if(loss) null else 35 + throttle * 1.45,
            baroKpa = if(loss) null else 100.4,
            fuelLh = if(loss) null else if(speed < 1) 0.75 else 1.1 + speed * 0.035 + throttle * 0.08,
            stft = if(loss) null else 1.2 * sin(t / 9) + deterioration * 7,
            ltft = if(loss) null else 1.8 + deterioration * 9,
            timing = if(loss) null else 9 + speed * 0.13 - throttle * 0.08,
            maf = if(loss) null else 3 + speed * 0.19 + throttle * 0.37)
    }
}
fun clockText(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
fun csv(frames: List<Frame>, vin: String? = null): String {
    val header = "timestamp_utc,elapsed_ms,source,simulated,vin,speed_kmh,rpm,coolant_c,intake_c,voltage_v,throttle_pct,distance_km,latitude,longitude,engine_load_pct,oil_c,manifold_kpa,baro_kpa,fuel_lh,stft_pct,ltft_pct,timing_deg,maf_gs,location_accuracy_m,location_timestamp_utc"
    return header + "\n" + frames.joinToString("\n") { f -> listOf(java.time.Instant.ofEpochMilli(f.timestamp), f.elapsed, if(f.simulated) "SIMULATOR" else "ELM327", f.simulated, vin ?: "", f.speed, f.rpm, f.coolant, f.intake, f.voltage, f.throttle, f.distance, f.latitude.takeIf { f.hasLocation }, f.longitude.takeIf { f.hasLocation }, f.engineLoad, f.oilC, f.manifoldKpa, f.baroKpa, f.fuelLh, f.stft, f.ltft, f.timing, f.maf, f.locationAccuracyM.takeIf { f.hasLocation }, f.locationTimestamp?.takeIf { f.hasLocation }?.let(java.time.Instant::ofEpochMilli)).joinToString(",") { it?.toString() ?: "" } }
}
