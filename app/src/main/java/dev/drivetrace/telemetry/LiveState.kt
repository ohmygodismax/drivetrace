package dev.drivetrace.telemetry

import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario
import dev.drivetrace.model.Vehicle

data class LiveState(val connected: Boolean = false, val running: Boolean = false, val frame: Frame = Frame(), val trail: List<Frame> = emptyList(), val scenario: Scenario = Scenario.CITY, val rate: Int = 1, val recording: Boolean = false, val vehicle: Vehicle? = null, val error: String? = null)
