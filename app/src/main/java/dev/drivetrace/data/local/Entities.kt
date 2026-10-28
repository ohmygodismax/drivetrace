package dev.drivetrace.data.local

import androidx.room.*
import dev.drivetrace.model.Frame

@Entity data class Trip(@PrimaryKey(autoGenerate = true) val id: Long = 0, val started: Long, val scenario: String, val duration: Long = 0, val distance: Double = 0.0, val maxSpeed: Double = 0.0, val supported: String = "", val status: String = "recording", val routeOffsetKm: Double? = null,
    @ColumnInfo(defaultValue = "'legacy'") val vehicleId: String = "legacy", val avgCoolant: Double? = null, val avgOil: Double? = null, val avgTrim: Double? = null, val avgVoltage: Double? = null, val avgLoad: Double? = null, val warmupMs: Long? = null, val fuelPer100: Double? = null, val ambientC: Double? = null, @ColumnInfo(defaultValue = "0") val comparableSamples: Int = 0)
@Entity(primaryKeys = ["tripId", "elapsed"], foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)])
data class Sample(val tripId: Long, val elapsed: Long, val timestamp: Long, val speed: Double, val rpm: Int?, val coolant: Double?, val intake: Double?, val voltage: Double?, val throttle: Double?, val distance: Double, val latitude: Double, val longitude: Double,
    val engineLoad: Double? = null, val oilC: Double? = null, val manifoldKpa: Double? = null, val baroKpa: Double? = null, val fuelLh: Double? = null, val stft: Double? = null, val ltft: Double? = null, val timing: Double? = null, val maf: Double? = null,
    @ColumnInfo(defaultValue = "1") val speedPresent: Boolean = true, @ColumnInfo(defaultValue = "1") val simulated: Boolean = true, @ColumnInfo(defaultValue = "1") val hasLocation: Boolean = true, val locationAccuracyM: Float? = null, val locationTimestamp: Long? = null) {
    fun frame() = Frame(elapsed,timestamp,speed.takeIf { speedPresent },rpm,coolant,intake,voltage,throttle,distance,latitude,longitude,simulated,engineLoad,oilC,manifoldKpa,baroKpa,fuelLh,stft,ltft,timing,maf,hasLocation,locationAccuracyM,locationTimestamp)
    companion object { fun from(id: Long, f: Frame) = Sample(id,f.elapsed,f.timestamp,f.speed ?: 0.0,f.rpm,f.coolant,f.intake,f.voltage,f.throttle,f.distance,f.latitude,f.longitude,f.engineLoad,f.oilC,f.manifoldKpa,f.baroKpa,f.fuelLh,f.stft,f.ltft,f.timing,f.maf,f.speed != null,f.simulated,f.hasLocation,f.locationAccuracyM,f.locationTimestamp) }
}
@Entity(foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId")])
data class TripEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val elapsed: Long, val title: String)
@Entity data class DiagnosticSnapshot(@PrimaryKey(autoGenerate = true) val id: Long = 0, val vehicleId: String, val capturedAt: Long, val codes: String, val freezeFrame: String, val simulated: Boolean)
