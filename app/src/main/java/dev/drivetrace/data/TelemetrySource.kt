package dev.drivetrace.data

import dev.drivetrace.model.Frame
import dev.drivetrace.model.Scenario

interface TelemetrySource { fun frame(elapsed: Long, started: Long, scenario: Scenario, distance: Double): Frame }
