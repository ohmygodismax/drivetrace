package dev.drivetrace.data.obd

import dev.drivetrace.model.ObdMetric
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ElmDiscoveryTest {
    private class FakeElm: ElmTransport {
        val commands=mutableListOf<String>()
        override suspend fun command(command: String,timeoutMs: Long): String {
            commands+=command
            fun bitmap(base: Int,pids: Set<Int>): String {
                val mask=pids.fold(0L){a,p->a or (1L shl (32-(p-base)))}
                return "41 %02X %08X".format(base,mask)
            }
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
        val transport=FakeElm();val session=ElmSession(transport)
        val identity=session.discover{}
        assertEquals("1D4GP00R55B123456",identity.vin)
        assertTrue(ObdMetric.OIL in identity.metrics)
        assertFalse(ObdMetric.FUEL_RATE in identity.metrics)
        assertEquals(listOf("P0133"),identity.codes)
        assertTrue(identity.freeze.contains("1726.0"))
        assertFalse(transport.commands.any { it=="04" })
        assertTrue(transport.commands.containsAll(listOf("0100","0120","0140","0902")))
    }
}
