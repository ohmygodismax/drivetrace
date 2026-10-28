package dev.drivetrace.data.simulator

import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.Vehicle
import dev.drivetrace.model.encodeMetrics

val DemoVehicles = listOf(
    Vehicle("demo:DEM00000000000001", "DEM00000000000001", "Volkswagen Tayron 1.4 · 2023", true, encodeMetrics(ObdMetric.entries.toSet())),
    Vehicle("demo:DEM00000000000002", "DEM00000000000002", "Volkswagen Golf · демо", true, encodeMetrics(ObdMetric.entries.toSet() - setOf(ObdMetric.OIL, ObdMetric.FUEL_RATE)))
)
