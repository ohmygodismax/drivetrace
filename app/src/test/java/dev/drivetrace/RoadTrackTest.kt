package dev.drivetrace

import org.junit.Assert.*
import org.junit.Test

class RoadTrackTest {
    @Test fun `sparse samples retain the road bends between them`() {
        val frames = listOf(Frame(distance = 0.0), Frame(distance = 0.7))
        val segment = roadSegments(frames, 0.0).single()
        assertTrue("Road geometry must contain intermediate vertices", segment.points.size > 10)
        assertEquals(DemoRoute.at(0.0), segment.points.first())
        assertEquals(DemoRoute.at(0.7), segment.points.last())
    }
    @Test fun `trip offset preserves the recording start on the same road`() {
        val segment = roadSegments(listOf(Frame(distance = 0.0), Frame(distance = 0.3)), 2.5).single()
        assertEquals(DemoRoute.at(2.5), segment.points.first())
        assertEquals(DemoRoute.at(2.8), segment.points.last())
    }
    @Test fun `load colors include high load and preserve unknowns`() {
        val source = SimulatorSource()
        val bands = (0L..120000L step 250L).map { LoadBand.of(source.frame(it, 0, Scenario.SPORT, 0.0).estimatedLoadPct) }.toSet()
        assertTrue(bands.contains(LoadBand.HIGH)); assertTrue(bands.contains(LoadBand.NORMAL))
        assertNull(Frame(rpm = null).estimatedLoadPct)
        assertEquals(LoadBand.UNKNOWN, LoadBand.of(null))
        assertEquals(LoadBand.ELEVATED, LoadBand.of(45.0))
        assertEquals(LoadBand.HIGH, LoadBand.of(70.0))
    }
}
