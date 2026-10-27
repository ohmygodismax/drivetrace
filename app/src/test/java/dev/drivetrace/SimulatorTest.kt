package dev.drivetrace

import org.junit.Assert.*
import org.junit.Test

class SimulatorTest {
    private val simulator = SimulatorSource(42)
    @Test fun `same seed and timeline produce identical frames`() {
        val other = SimulatorSource(42)
        Scenario.entries.forEach { scenario -> (0L..120000L step 250L).forEach { t -> assertEquals(simulator.frame(t,1000,scenario,0.0),other.frame(t,1000,scenario,0.0)) } }
    }
    @Test fun `normal scenarios stay within bounds and rpm tracks movement`() {
        listOf(Scenario.CITY,Scenario.HIGHWAY,Scenario.SPORT).forEach { scenario ->
            (0L..180000L step 250L).map { simulator.frame(it,1000,scenario,0.0) }.forEach {
                assertTrue(it.speed in 0.0..130.0);assertTrue(it.rpm!! in 750..5000);assertTrue(it.throttle!! in 0.0..100.0)
                if(it.speed==0.0)assertEquals(780,it.rpm)
                assertTrue(it.simulated)
            }
        }
    }
    @Test fun `fault has overheat low voltage and explicit unavailable values`() {
        val warm=simulator.frame(60000,0,Scenario.FAULT,0.0)
        assertTrue(warm.coolant!!>110);assertTrue(warm.voltage!!<12)
        val gap=simulator.frame(47000,0,Scenario.FAULT,0.0)
        assertNull(gap.rpm);assertNull(gap.coolant);assertNull(gap.voltage)
    }
    @Test fun `csv contains UTC time units and preserves missing values`() {
        val text=csv(listOf(simulator.frame(47000,0,Scenario.FAULT,0.0)))
        assertTrue(text.startsWith("timestamp_utc,elapsed_ms,source,simulated,speed_kmh"))
        assertTrue(text.contains("1970-01-01T00:00:47Z,47000,SIMULATOR,true,0.0,,,,,,"))
    }
}
