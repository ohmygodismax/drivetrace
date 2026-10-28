package dev.drivetrace.domain.health

import dev.drivetrace.data.local.Trip
import dev.drivetrace.data.simulator.SimulatorSource
import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario
import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    private fun trip(i: Int,trim: Double=2.0,vehicle: String="a",scenario: String="CITY")=Trip(id=i.toLong(),started=i*86400000L,scenario=scenario,vehicleId=vehicle,status="complete",duration=480000,avgTrim=trim,avgCoolant=91.0,avgLoad=35.0,ambientC=24.0,comparableSamples=100)
    @Test fun `one spike does not create a health alert`() {
        assertTrue(healthSignals((1..6).map{trip(it,if(it==6)12.0 else 2.0)}).isEmpty())
    }
    @Test fun `persistent drift across three comparable trips is explained`() {
        val signals=healthSignals((1..6).map{trip(it,if(it>=4)10.0 else 2.0)})
        assertEquals(1,signals.size);assertEquals(listOf(4L,5L,6L),signals.single().tripIds)
        assertTrue(signals.single().detail.contains("2.0 → 10.0"))
    }
    @Test fun `VINs scenarios loads and missing data never mix`() {
        assertTrue(healthSignals((1..6).map{trip(it,if(it>=4)10.0 else 2.0,if(it>=4)"b" else "a")}).isEmpty())
        assertTrue(healthSignals((1..6).map{trip(it,if(it>=4)10.0 else 2.0,scenario=if(it>=4)"HIGHWAY" else "CITY")}).isEmpty())
        assertTrue(healthSignals((1..6).map{trip(it,if(it>=4)10.0 else 2.0).copy(avgLoad=if(it>=4)80.0 else 35.0)}).isEmpty())
        assertTrue(healthSignals((1..6).map{trip(it).copy(avgTrim=null)}).isEmpty())
    }
    @Test fun `demo history supplies warm comparable data and intended drift`() {
        val trips=(0..5).map{day->
            val source=SimulatorSource(42,if(day<3)0.0 else 0.8)
            val frames=(0L..480000L step 1000L).map{source.frame(it,0,Scenario.CITY,0.0)}
            summarize(trip(day+1),frames)
        }
        assertTrue(trips.all{it.comparableSamples>=20})
        assertEquals(2,healthSignals(trips).size)
    }
    @Test fun `warmup requires cold start and real observed threshold`() {
        assertNull(summarize(trip(1),listOf(Frame(coolant=90.0))).warmupMs)
        assertEquals(1000L,summarize(trip(1),listOf(Frame(coolant=25.0),Frame(elapsed=1000,coolant=80.0))).warmupMs)
        assertNull(summarize(trip(1),listOf(Frame(coolant=null))).avgCoolant)
    }
}
