package dev.drivetrace.data.simulator

import dev.drivetrace.data.TelemetrySource
import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario
import kotlin.math.*

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
