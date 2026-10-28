package dev.drivetrace.data.obd

import dev.drivetrace.model.ObdMetric
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ElmDiscoveryTest {
    private class FakeElm: ElmTransport {
        val responses = mutableMapOf<String, String>()
        val commands=mutableListOf<String>()
        override suspend fun command(command: String,timeoutMs: Long): String {
            commands+=command
            fun bitmap(base: Int,pids: Set<Int>): String {
                val mask=pids.fold(0L){a,p->a or (1L shl (32-(p-base)))}
                return "41 %02X %08X".format(base,mask)
            }
            responses[command]?.let { return it }
            return when(command) {
                "0100"->bitmap(0,setOf(4,5,12,13,32))
                "0120"->bitmap(32,setOf(64))
                "0140"->bitmap(64,setOf(0x42,0x5C))
                "0902"->"0: 49 02 01 31 44 34\r1: 47 50 30 30 52 35 35\r2: 42 31 32 33 34 35 36"
                "ATDPN"->"A6"
                "03"->"43 01 01 33"
                "020C00"->"42 0C 00 1A F8"
                "020500"->"42 05 00 82"
                "020D00"->"42 0D 00 32"
                "020400"->"42 04 00 80"
                else->if(command.startsWith("AT"))"OK" else "NO DATA"
            }
        }
        override fun close() = Unit
    }
    @Test fun `discovery reads VIN capabilities and read only diagnostics`() = runBlocking {
        val transport=FakeElm();val session=ElmSession(transport) { 0L }
        val identity=session.discover{}
        assertEquals("1D4GP00R55B123456",identity.vin)
        assertTrue(ObdMetric.OIL in identity.metrics)
        assertFalse(ObdMetric.FUEL_RATE in identity.metrics)
        assertEquals(listOf("P0133"),identity.codes)
        assertTrue(identity.freeze.contains("1726.0"))
        assertEquals(identity.metrics, session.sensorReadings().keys)
        assertTrue(session.sensorReadings().values.all { it.value == null })
        assertFalse(transport.commands.any { it=="04" })
        assertTrue(transport.commands.containsAll(listOf("0100","0120","0140","0902")))
    }
    @Test fun `advertised sensor may not answer and a later missing response clears its value`() = runBlocking {
        val transport = FakeElm()
        transport.responses["010C"] = "41 0C 1A F8"
        transport.responses["015C"] = "41 5C 82"
        var now = 100L
        val session = ElmSession(transport) { now }
        session.discover {}
        assertEquals(1726.0, session.sensorReadings().getValue(ObdMetric.RPM).value!!, .01)
        assertTrue(session.sensorReadings().getValue(ObdMetric.RPM).isFresh(now))
        assertNull(session.sensorReadings().getValue(ObdMetric.COOLANT).value)
        assertFalse(session.sensorReadings().containsKey(ObdMetric.MAF))
        transport.responses["010C"] = "NO DATA"
        now += 1000
        val frame = session.poll(1000, 0, 0.0)
        assertNull(frame.rpm)
        assertNull(session.sensorReadings().getValue(ObdMetric.RPM).value)
        assertNotNull(frame.oilC)
        // Oil temperature is not in the next slow poll: an old cached answer must expire.
        now += 15000
        assertNull(session.poll(16000, 0, 0.0).oilC)
        assertFalse(session.sensorReadings().getValue(ObdMetric.OIL).isFresh(now))
    }

}
