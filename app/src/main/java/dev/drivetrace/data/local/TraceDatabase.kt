package dev.drivetrace.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.drivetrace.model.Vehicle

val GPS_MIGRATION = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Sample ADD COLUMN locationAccuracyM REAL")
        db.execSQL("ALTER TABLE Sample ADD COLUMN locationTimestamp INTEGER")
    }
}
@Database(entities = [Vehicle::class, Trip::class, Sample::class, TripEvent::class, DiagnosticSnapshot::class], version = 4, exportSchema = false)
abstract class TraceDatabase: RoomDatabase() { abstract fun dao(): TripDao }
