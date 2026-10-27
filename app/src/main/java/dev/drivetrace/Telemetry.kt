package dev.drivetrace

import kotlin.math.*

enum class Scenario(val title: String, val subtitle: String) {
    CITY("Город", "Светофоры и плавные разгоны"), HIGHWAY("Трасса", "Крейсерский режим до 120 км/ч"),
    SPORT("Динамичный", "Ускорения и резкое торможение"), FAULT("Неисправность", "Перегрев, падение напряжения и обрыв")
}
data class Frame(val elapsed: Long = 0, val timestamp: Long = 0, val speed: Double = 0.0, val rpm: Int? = 780, val coolant: Double? = 24.0, val intake: Double? = 22.0, val voltage: Double? = 14.2, val throttle: Double? = 0.0, val distance: Double = 0.0, val latitude: Double = DemoRoute.at(0.0).first, val longitude: Double = DemoRoute.at(0.0).second, val simulated: Boolean = true)
interface TelemetrySource { fun frame(elapsed: Long, started: Long, scenario: Scenario, distance: Double): Frame }
class SimulatorSource(private val seed: Int = 42): TelemetrySource {
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
        return Frame(elapsed, started + elapsed, speed, if(loss) null else (780 + speed * 21 + throttle * 12).toInt(), if(loss) null else if(scenario == Scenario.FAULT) min(124.0, 24 + t * 2) else min(91.0, 24 + t * 0.36), if(loss) null else 22 + speed * 0.06, if(loss) null else if(scenario == Scenario.FAULT && t > 30) 11.4 else 14.1 + 0.12 * sin(t / 5), if(loss) null else throttle, distance, latitude, longitude)
    }
}
fun clockText(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
fun csv(frames: List<Frame>): String = "timestamp_utc,elapsed_ms,source,simulated,speed_kmh,rpm,coolant_c,intake_c,voltage_v,throttle_pct,distance_km,latitude,longitude,estimated_load_pct\n" + frames.joinToString("\n") { f -> listOf(java.time.Instant.ofEpochMilli(f.timestamp), f.elapsed, "SIMULATOR", true, f.speed, f.rpm ?: "", f.coolant ?: "", f.intake ?: "", f.voltage ?: "", f.throttle ?: "", f.distance, f.latitude, f.longitude, f.estimatedLoadPct ?: "").joinToString(",") }
