package dev.drivetrace

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ObdMetric(val pid: Int, val title: String, val unit: String) {
    LOAD(0x04,"Нагрузка ЭБУ","%"), COOLANT(0x05,"Охлаждение","°C"), STFT(0x06,"Краткосрочная коррекция","%"), LTFT(0x07,"Долгосрочная коррекция","%"),
    MAP(0x0B,"Давление во впуске","кПа"), RPM(0x0C,"Обороты","об/мин"), SPEED(0x0D,"Скорость","км/ч"), TIMING(0x0E,"Опережение зажигания","°"), INTAKE(0x0F,"Температура впуска","°C"), MAF(0x10,"Расход воздуха","г/с"), THROTTLE(0x11,"Дроссель","%"), BARO(0x33,"Атмосферное давление","кПа"), VOLTAGE(0x42,"Напряжение ЭБУ","В"), OIL(0x5C,"Температура масла","°C"), FUEL_RATE(0x5E,"Расход топлива","л/ч")
}
@Entity data class Vehicle(@PrimaryKey val id: String, val vin: String?, val name: String, val simulated: Boolean, val supported: String, val lastSeen: Long = 0) {
    val metrics: Set<ObdMetric> get() = supported.split(',').mapNotNull { name -> ObdMetric.entries.find { it.name == name } }.toSet()
    val identity: String get() = vin ?: "VIN не задан"
}
fun vehicleId(vin: String, simulated: Boolean) = (if(simulated) "demo:" else "vin:") + vin.trim().uppercase()
fun validVin(vin: String): Boolean { val value = vin.trim().uppercase(); return Regex("[A-HJ-NPR-Z0-9]{17}").matches(value) && value.toSet().size > 1 }
fun encodeMetrics(metrics: Set<ObdMetric>) = metrics.sortedBy { it.pid }.joinToString(",") { it.name }
val DemoVehicles = listOf(
    Vehicle("demo:DEM00000000000001", "DEM00000000000001", "Volkswagen Tayron 1.4 · 2023", true, encodeMetrics(ObdMetric.entries.toSet())),
    Vehicle("demo:DEM00000000000002", "DEM00000000000002", "Volkswagen Golf · демо", true, encodeMetrics(ObdMetric.entries.toSet() - setOf(ObdMetric.OIL, ObdMetric.FUEL_RATE)))
)
fun Frame.value(metric: ObdMetric): Double? = when(metric) {
    ObdMetric.LOAD -> engineLoad; ObdMetric.COOLANT -> coolant; ObdMetric.STFT -> stft; ObdMetric.LTFT -> ltft; ObdMetric.MAP -> manifoldKpa; ObdMetric.RPM -> rpm?.toDouble(); ObdMetric.SPEED -> speed
    ObdMetric.TIMING -> timing; ObdMetric.INTAKE -> intake; ObdMetric.MAF -> maf; ObdMetric.THROTTLE -> throttle; ObdMetric.BARO -> baroKpa; ObdMetric.VOLTAGE -> voltage; ObdMetric.OIL -> oilC; ObdMetric.FUEL_RATE -> fuelLh
}
fun Frame.only(metrics: Set<ObdMetric>): Frame = copy(
    speed = speed.takeIf { ObdMetric.SPEED in metrics }, rpm = rpm.takeIf { ObdMetric.RPM in metrics }, coolant = coolant.takeIf { ObdMetric.COOLANT in metrics }, intake = intake.takeIf { ObdMetric.INTAKE in metrics }, voltage = voltage.takeIf { ObdMetric.VOLTAGE in metrics }, throttle = throttle.takeIf { ObdMetric.THROTTLE in metrics },
    engineLoad = engineLoad.takeIf { ObdMetric.LOAD in metrics }, oilC = oilC.takeIf { ObdMetric.OIL in metrics }, manifoldKpa = manifoldKpa.takeIf { ObdMetric.MAP in metrics }, baroKpa = baroKpa.takeIf { ObdMetric.BARO in metrics }, fuelLh = fuelLh.takeIf { ObdMetric.FUEL_RATE in metrics }, stft = stft.takeIf { ObdMetric.STFT in metrics }, ltft = ltft.takeIf { ObdMetric.LTFT in metrics }, timing = timing.takeIf { ObdMetric.TIMING in metrics }, maf = maf.takeIf { ObdMetric.MAF in metrics }
)
