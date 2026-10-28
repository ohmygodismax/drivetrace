package dev.drivetrace.data.local

import androidx.room.*
import dev.drivetrace.model.Vehicle
import kotlinx.coroutines.flow.*

@Dao interface TripDao {
    @Query("SELECT * FROM Trip ORDER BY started DESC") fun allTrips(): Flow<List<Trip>>
    @Query("SELECT * FROM Trip WHERE vehicleId = :vehicle ORDER BY started DESC") fun trips(vehicle: String): Flow<List<Trip>>
    @Query("SELECT * FROM Vehicle ORDER BY lastSeen DESC, name") fun vehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM Vehicle WHERE id = :id") suspend fun vehicle(id: String): Vehicle?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addVehicle(vehicle: Vehicle): Long
    @Update suspend fun updateVehicle(vehicle: Vehicle)
    @Query("SELECT * FROM DiagnosticSnapshot WHERE vehicleId = :vehicle ORDER BY capturedAt DESC LIMIT 10") fun diagnostics(vehicle: String): Flow<List<DiagnosticSnapshot>>
    @Insert suspend fun diagnostic(snapshot: DiagnosticSnapshot)
    @Query("SELECT * FROM Trip WHERE id = :id") suspend fun trip(id: Long): Trip?
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)
    @Insert suspend fun samples(samples: List<Sample>)
    @Insert suspend fun event(event: TripEvent)
    @Query("SELECT * FROM Sample WHERE tripId = :id ORDER BY elapsed") suspend fun samples(id: Long): List<Sample>
    @Query("SELECT * FROM TripEvent WHERE tripId = :id ORDER BY elapsed") suspend fun events(id: Long): List<TripEvent>
    @Query("UPDATE Trip SET status = 'interrupted', duration = COALESCE((SELECT MAX(elapsed) FROM Sample WHERE tripId = Trip.id),0), distance = COALESCE((SELECT MAX(distance) FROM Sample WHERE tripId = Trip.id),0), maxSpeed = COALESCE((SELECT MAX(speed) FROM Sample WHERE tripId = Trip.id),0) WHERE status = 'recording'") suspend fun recover()
    @Query("DELETE FROM Trip WHERE id = :id") suspend fun delete(id: Long)
}
