package dev.drivetrace.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity data class Vehicle(@PrimaryKey val id: String, val vin: String?, val name: String, val simulated: Boolean, val supported: String, val lastSeen: Long = 0) {
    val metrics: Set<ObdMetric> get() = supported.split(',').mapNotNull { name -> ObdMetric.entries.find { it.name == name } }.toSet()
    val identity: String get() = vin ?: "VIN не задан"
}
fun vehicleId(vin: String, simulated: Boolean) = (if(simulated) "demo:" else "vin:") + vin.trim().uppercase()
fun validVin(vin: String): Boolean { val value = vin.trim().uppercase(); return Regex("[A-HJ-NPR-Z0-9]{17}").matches(value) && value.toSet().size > 1 }
fun encodeMetrics(metrics: Set<ObdMetric>) = metrics.sortedBy { it.pid }.joinToString(",") { it.name }

fun Frame.value(metric: ObdMetric): Double? = when(metric) {
    ObdMetric.LOAD -> engineLoad; ObdMetric.COOLANT -> coolant; ObdMetric.STFT -> stft; ObdMetric.LTFT -> ltft; ObdMetric.MAP -> manifoldKpa; ObdMetric.RPM -> rpm?.toDouble(); ObdMetric.SPEED -> speed
    ObdMetric.TIMING -> timing; ObdMetric.INTAKE -> intake; ObdMetric.MAF -> maf; ObdMetric.THROTTLE -> throttle; ObdMetric.BARO -> baroKpa; ObdMetric.VOLTAGE -> voltage; ObdMetric.OIL -> oilC; ObdMetric.FUEL_RATE -> fuelLh
}
fun Frame.only(metrics: Set<ObdMetric>): Frame = copy(
    speed = speed.takeIf { ObdMetric.SPEED in metrics }, rpm = rpm.takeIf { ObdMetric.RPM in metrics }, coolant = coolant.takeIf { ObdMetric.COOLANT in metrics }, intake = intake.takeIf { ObdMetric.INTAKE in metrics }, voltage = voltage.takeIf { ObdMetric.VOLTAGE in metrics }, throttle = throttle.takeIf { ObdMetric.THROTTLE in metrics },
    engineLoad = engineLoad.takeIf { ObdMetric.LOAD in metrics }, oilC = oilC.takeIf { ObdMetric.OIL in metrics }, manifoldKpa = manifoldKpa.takeIf { ObdMetric.MAP in metrics }, baroKpa = baroKpa.takeIf { ObdMetric.BARO in metrics }, fuelLh = fuelLh.takeIf { ObdMetric.FUEL_RATE in metrics }, stft = stft.takeIf { ObdMetric.STFT in metrics }, ltft = ltft.takeIf { ObdMetric.LTFT in metrics }, timing = timing.takeIf { ObdMetric.TIMING in metrics }, maf = maf.takeIf { ObdMetric.MAF in metrics }
)
