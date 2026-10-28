package dev.drivetrace.data.location

import dev.drivetrace.data.export.csv
import dev.drivetrace.domain.route.roadSegments
import dev.drivetrace.model.Frame
import org.junit.Assert.*
import org.junit.Test

class GpsFixTest {
    private val fix = GpsFix(55.75, 37.61, 8f, 1_700_000_000_000, 1000)
    @Test fun `only recent accurate valid coordinates are recorded`() {
        assertTrue(fix.usable(1000)); assertTrue(fix.usable(11000))
        assertFalse(fix.usable(11001)); assertFalse(fix.usable(999))
        assertFalse(fix.copy(accuracyM = 101f).usable(1000))
        assertFalse(fix.copy(accuracyM = -1f).usable(1000))
        assertFalse(fix.copy(latitude = Double.NaN).usable(1000))
        assertFalse(fix.copy(longitude = 181.0).usable(1000))
        assertTrue(fix.copy(latitude = 0.0, longitude = 0.0).usable(1000))
    }
    @Test fun `missing or expired GPS clears coordinates without changing OBD readings`() {
        val obd = Frame(speed = 42.0, rpm = 1500, simulated = false, hasLocation = false)
        val located = obd.withGps(fix, 2000)
        assertEquals(55.75, located.latitude, 0.0); assertEquals(8f, located.locationAccuracyM)
        assertEquals(fix.timestamp, located.locationTimestamp)
        val expired = located.withGps(fix, 12000)
        assertFalse(expired.hasLocation); assertNull(expired.locationTimestamp)
        assertEquals(0.0, expired.latitude, 0.0)
        assertEquals(obd.speed, expired.speed); assertEquals(obd.rpm, expired.rpm)
        assertFalse(located.withGps(null, 2000).hasLocation)
    }
    @Test fun `phone GPS never changes the demonstration route`() {
        val demo = Frame()
        assertEquals(demo, demo.withGps(fix, 2000))
    }
    @Test fun `CSV exports the GPS measurement time separately from OBD time`() {
        val frame = Frame(timestamp = 1_700_000_001_000, simulated = false).withGps(fix, 2000)
        val rows = csv(listOf(frame, frame.withGps(null, 12000))).lines().map { it.split(',') }
        assertEquals(rows[0].size, rows[1].size); assertEquals(rows[0].size, rows[2].size)
        assertEquals("55.75", rows[1][12]); assertEquals("8.0", rows[1][23])
        assertEquals("2023-11-14T22:13:20Z", rows[1][24])
        assertEquals("", rows[2][12]); assertEquals("", rows[2][24])
    }
    @Test fun `location gaps and long pauses break the recorded route`() {
        val a = Frame(elapsed = 0, simulated = false).withGps(fix, 2000)
        val gap = a.copy(elapsed = 2000).withGps(null, 3000)
        val b = a.copy(elapsed = 4000, longitude = 37.62)
        val c = b.copy(elapsed = 5000, longitude = 37.63)
        val segments = roadSegments(listOf(a, gap, b, c), null)
        assertTrue(segments[0].points.isEmpty()); assertTrue(segments[1].points.isEmpty())
        assertEquals(2, segments[2].points.size)
        assertTrue(roadSegments(listOf(a, b.copy(elapsed = 16000)), null).single().points.isEmpty())
    }
}
