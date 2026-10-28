package dev.drivetrace.domain.route

import dev.drivetrace.data.local.TripEvent
import dev.drivetrace.model.Frame
import org.junit.Assert.*
import org.junit.Test

class RouteAnalysisTest {
    @Test fun `distance range includes stops at both boundaries`() {
        val frames = listOf(0.0, 1.0, 1.0, 2.0, 2.0, 3.0).mapIndexed { i, distance -> Frame(elapsed = i * 1000L, distance = distance) }
        assertEquals(1..4, routeSelection(frames, 1.0, 2.0))
        assertEquals(1..2, routeSelection(frames, 1.0, 1.0))
        assertEquals(1..1, routeSelection(frames, 1.3, 1.4))
        assertTrue(routeSelection(emptyList(), 0.0, 1.0).isEmpty())
    }

    @Test fun `float slider end retains the final sample`() {
        val distance = 11.1234567
        assertEquals(0..1, routeSelection(listOf(Frame(), Frame(distance = distance)), 0.0, distance.toFloat().toDouble()))
    }

    @Test fun `summary weights speed by elapsed time and includes extrema`() {
        val frames = listOf(
            Frame(elapsed = 0, distance = 2.0, speed = 0.0, rpm = 1000, engineLoad = 20.0),
            Frame(elapsed = 1000, distance = 2.1, speed = 20.0, rpm = 3000, engineLoad = 80.0),
            Frame(elapsed = 4000, distance = 2.2, speed = 40.0, rpm = 2000, engineLoad = 40.0)
        )
        val summary = routeSummary(frames)
        assertEquals(25.0, summary.averageSpeed!!, 0.001)
        assertEquals(0.2, summary.distanceKm, 0.001)
        assertEquals(4000L, summary.durationMs)
        assertEquals(40.0, summary.maxSpeed!!, 0.001)
        assertEquals(3000, summary.maxRpm)
        assertEquals(80.0, summary.maxLoad!!, 0.001)
    }

    @Test fun `summary does not bridge missing readings or long gaps`() {
        val summary = routeSummary(listOf(Frame(elapsed = 0, speed = 10.0), Frame(elapsed = 20_000, speed = 90.0), Frame(elapsed = 21_000, speed = null)))
        assertNull(summary.averageSpeed)
        assertNull(routeSummary(emptyList()).maxSpeed)
        assertNull(routeSummary(listOf(Frame(rpm = null, throttle = null))).maxLoad)
    }

    @Test fun `events never borrow a missing or distant GPS fix`() {
        val frames = listOf(Frame(elapsed = 1000), Frame(elapsed = 2000, hasLocation = false), Frame(elapsed = 3000))
        fun event(time: Long) = TripEvent(tripId = 1, elapsed = time, title = "Перегрев")
        assertNull(eventFrameIndex(frames, event(0)))
        assertEquals(0, eventFrameIndex(frames, event(1500)))
        assertNull(eventFrameIndex(frames, event(2500)))
        assertNull(eventFrameIndex(frames, event(19000)))
    }
}
