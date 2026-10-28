package dev.drivetrace

import kotlin.math.abs

private fun List<Double>.meanOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
fun summarize(trip: Trip,frames: List<Frame>): Trip {
    val warm=frames.filter { (it.coolant ?: 0.0)>=80 && (it.speed ?: 0.0)>=20 && (it.rpm ?: 0)>600 }
    var liters=0.0; var fuelTime=0L
    frames.zipWithNext().forEach { (a,b) -> if(a.fuelLh!=null && b.fuelLh!=null && b.elapsed-a.elapsed in 1..10000) { liters+=(a.fuelLh+b.fuelLh)/2*(b.elapsed-a.elapsed)/3600000; fuelTime+=b.elapsed-a.elapsed } }
    return trip.copy(
        maxSpeed=frames.mapNotNull { it.speed }.maxOrNull() ?: 0.0,
        avgCoolant=warm.mapNotNull { it.coolant }.meanOrNull(),avgOil=warm.mapNotNull { it.oilC }.meanOrNull(),avgTrim=warm.mapNotNull { it.ltft }.meanOrNull(),avgVoltage=warm.mapNotNull { it.voltage }.meanOrNull(),avgLoad=warm.mapNotNull { it.engineLoad }.meanOrNull(),
        warmupMs=if((frames.firstOrNull()?.coolant ?: 100.0)<50) frames.firstOrNull { (it.coolant ?: 0.0)>=80 }?.elapsed else null,
        fuelPer100=if(trip.distance>=1 && fuelTime>=trip.duration*0.8) liters/trip.distance*100 else null,
        ambientC=frames.take(10).mapNotNull { it.intake }.meanOrNull(),comparableSamples=warm.size
    )
}
data class HealthSignal(val title: String,val detail: String,val tripIds: List<Long>,val caution: Boolean = true)
/** Heuristic observations, evaluated separately for a VIN and comparable scenario/load/ambient. */
fun healthSignals(trips: List<Trip>): List<HealthSignal> {
    val result=mutableListOf<HealthSignal>()
    trips.filter { it.status=="complete" && it.comparableSamples>=20 && it.duration>=180000 }.groupBy { it.vehicleId to it.scenario }.values.forEach { group ->
        val sorted=group.sortedBy { it.started }; if(sorted.size<6) return@forEach
        val last=sorted.takeLast(3); val reference=sorted.dropLast(3).takeLast(3)
        val all=reference+last
        val loads=all.mapNotNull { it.avgLoad }; val ambient=all.mapNotNull { it.ambientC }
        if(loads.size!=6 || ambient.size!=6 || loads.max()-loads.min()>15 || ambient.max()-ambient.min()>10) return@forEach
        val durations=all.map { it.duration }; if(durations.min()<=0 || durations.max().toDouble()/durations.min()>1.3) return@forEach
        fun compare(title: String, unit: String,threshold: Double,extract: (Trip)->Double?,worsens:(Double,Double)->Double,explanation:String) {
            val baseline=reference.mapNotNull(extract); val recent=last.mapNotNull(extract)
            if(baseline.size!=3 || recent.size!=3) return
            val a=baseline.sorted()[1]; val b=recent.sorted()[1]
            if(recent.all { worsens(a,it)>=threshold }) result+=HealthSignal(title,"В трёх последних сопоставимых поездках: %.1f → %.1f %s. %s".format(a,b,unit,explanation),last.map { it.id })
        }
        compare("Коррекция смеси изменилась","%",5.0,{it.avgTrim},{a,b->abs(b)-abs(a)},"Это повод проверить смесь и повторить наблюдение, а не установленная неисправность.")
        compare("Рабочая температура выросла","°C",8.0,{it.avgCoolant},{a,b->b-a},"Сравнение при близких нагрузке и температуре впуска; проверьте условия поездок и охлаждение.")
        compare("Напряжение ниже обычного","В",0.7,{it.avgVoltage},{a,b->a-b},"Режим зарядки и потребители тоже влияют на напряжение.")
    }
    return result
}
