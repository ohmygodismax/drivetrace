package dev.drivetrace.data.simulator

import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class DemoRouteTest {
    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val lat = Math.toRadians(b.first - a.first)
        val lon = Math.toRadians(b.second - a.second)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(lon / 2).pow(2)
        return 6371000 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
    @Test fun `road position follows traveled distance and stays in Moscow`() {
        assertTrue(DemoRoute.lengthKm in 10.0..12.0)
        var previous = DemoRoute.at(0.0)
        for(i in 1..2000) {
            val point = DemoRoute.at(i * 0.005)
            assertTrue(point.first in 55.70..55.80)
            assertTrue(point.second in 37.55..37.70)
            assertTrue("Unphysical jump at $i", meters(previous, point) <= 5.1)
            previous = point
        }
    }
    @Test fun `loop joins continuously and works after multiple laps`() {
        assertTrue(meters(DemoRoute.at(DemoRoute.lengthKm - 0.0001), DemoRoute.at(0.0001)) < 1)
        assertTrue(meters(DemoRoute.at(1.0), DemoRoute.at(DemoRoute.lengthKm * 3 + 1)) < 0.01)
    }
    @Test fun `initial and simulator frames share the road origin`() {
        val initial = Frame()
        val simulated = SimulatorSource().frame(1000, 0, Scenario.CITY, 0.0)
        assertEquals(initial.latitude, simulated.latitude, 0.000001)
        assertEquals(initial.longitude, simulated.longitude, 0.000001)
    }
}
