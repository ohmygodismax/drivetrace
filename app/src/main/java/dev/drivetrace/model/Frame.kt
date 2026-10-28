package dev.drivetrace.model

import dev.drivetrace.data.simulator.DemoRoute

data class Frame(val elapsed: Long = 0, val timestamp: Long = 0, val speed: Double? = 0.0, val rpm: Int? = 780, val coolant: Double? = 24.0, val intake: Double? = 22.0, val voltage: Double? = 14.2, val throttle: Double? = 0.0, val distance: Double = 0.0, val latitude: Double = DemoRoute.at(0.0).first, val longitude: Double = DemoRoute.at(0.0).second, val simulated: Boolean = true, val engineLoad: Double? = null, val oilC: Double? = null, val manifoldKpa: Double? = null, val baroKpa: Double? = null, val fuelLh: Double? = null, val stft: Double? = null, val ltft: Double? = null, val timing: Double? = null, val maf: Double? = null, val hasLocation: Boolean = true, val locationAccuracyM: Float? = null, val locationTimestamp: Long? = null)
