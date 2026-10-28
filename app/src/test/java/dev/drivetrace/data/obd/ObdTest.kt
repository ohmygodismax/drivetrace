package dev.drivetrace.data.obd

import dev.drivetrace.data.local.Sample
import dev.drivetrace.data.simulator.DemoVehicles
import dev.drivetrace.data.simulator.SimulatorSource
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.Scenario
import dev.drivetrace.model.only
import dev.drivetrace.model.validVin
import dev.drivetrace.model.value
import dev.drivetrace.model.vehicleId
import org.junit.Assert.*
import org.junit.Test

class ObdTest {
    @Test fun `VIN reassembles ELM formatted CAN multiline with echo`() {
        val reply="0902\rSEARCHING...\r014\r0: 49 02 01 31 44 34\r1: 47 50 30 30 52 35 35\r2: 42 31 32 33 34 35 36\r>"
        assertEquals("1D4GP00R55B123456",ObdCodec.vin(reply))
    }
    @Test fun `VIN reassembles raw ISO TP with ECU headers`() {
        val reply="7E8 10 14 49 02 01 31 44 34\r7E8 21 47 50 30 30 52 35 35\r7E8 22 42 31 32 33 34 35 36"
        assertEquals("1D4GP00R55B123456",ObdCodec.vin(reply))
        assertNull(ObdCodec.vin("NO DATA>"))
        assertNull(ObdCodec.vin("49 02 01 49 49 49"))
    }
    @Test fun `PID bitmap handles unsigned bits and blocks`() {
        assertEquals(setOf(1,32),ObdCodec.supported("41 00 80 00 00 01",0))
        assertEquals(setOf(33,64),ObdCodec.supported("41 20 80 00 00 01",32))
        assertTrue(ObdCodec.supported("NO DATA",0).isEmpty())
    }
    @Test fun `standard sensor formulas and unknowns`() {
        assertEquals(1726.0,ObdCodec.value("41 0C 1A F8",ObdMetric.RPM)!!,0.001)
        assertEquals(90.0,ObdCodec.value("41 05 82",ObdMetric.COOLANT)!!,0.001)
        assertEquals(9.375,ObdCodec.value("41 07 8C",ObdMetric.LTFT)!!,0.001)
        assertEquals(0.0,ObdCodec.value("41 0E 80",ObdMetric.TIMING)!!,0.001)
        assertEquals(100.0,ObdCodec.value("41 5C 8C",ObdMetric.OIL)!!,0.001)
        assertEquals(5.0,ObdCodec.value("41 5E 00 64",ObdMetric.FUEL_RATE)!!,0.001)
        assertNull(ObdCodec.value("NO DATA",ObdMetric.OIL))
        assertNull(ObdCodec.value("41 0C 1A",ObdMetric.RPM))
    }
    @Test fun `freeze frame index is not a sensor byte`() {
        assertEquals(1726.0,ObdCodec.value("42 0C 00 1A F8",ObdMetric.RPM,true)!!,0.001)
    }
    @Test fun `DTC decoder reads engine chassis body and network codes`() {
        assertEquals(listOf("P0133","P0217","U0100"),ObdCodec.dtcs("43 01 33 02 17 C1 00 00 00"))
    }
    @Test fun `CAN DTC count is not decoded as a trouble code`() {
        assertEquals(listOf("P0133","U0100"),ObdCodec.dtcs("43 02 01 33 C1 00 00",true))
        assertTrue(ObdCodec.dtcs("43 00",true).isEmpty())
    }
    @Test fun `vehicle identities separate demos from real VINs`() {
        assertNotEquals(vehicleId("1D4GP00R55B123456",true),vehicleId("1D4GP00R55B123456",false))
        assertFalse(validVin("short"));assertFalse(validVin("IIIIIIIIIIIIIIIII"))
        assertTrue(validVin("1D4GP00R55B123456"))
        assertEquals(2,DemoVehicles.map { it.vin }.distinct().size)
    }
    @Test fun `unsupported values are missing not zero`() {
        val frame=SimulatorSource().frame(20000,0,Scenario.CITY,0.0).only(DemoVehicles[1].metrics)
        assertNull(frame.oilC);assertNull(frame.fuelLh);assertNotNull(frame.engineLoad)
        val replay=Sample.from(1,frame.copy(speed=null)).frame()
        assertNull(replay.speed);assertNull(replay.oilC)
    }
}
