package dev.drivetrace.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.drivetrace.data.location.GpsFix
import dev.drivetrace.data.location.withGps
import dev.drivetrace.model.Vehicle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsMigrationTest {
    @Test fun versionThreeHistorySurvivesAndGpsFieldsCanBeSaved() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "gps-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            val schema = instrumentation.context.assets.open("schema-v3.sql").bufferedReader().use { it.readText() }
            schema.split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it) }
            legacy.execSQL("INSERT INTO Vehicle VALUES ('vin:test', '1D4GP00R55B123456', 'Test car', 0, 'SPEED', 0)")
            legacy.execSQL("INSERT INTO Trip (id,started,scenario,duration,distance,maxSpeed,supported,status,vehicleId) VALUES (1,1000,'REAL',0,0,0,'SPEED','recording','vin:test')")
            legacy.execSQL("INSERT INTO Sample (tripId,elapsed,timestamp,speed,distance,latitude,longitude,simulated,hasLocation) VALUES (1,0,1000,50,0,55.75,37.61,0,0)")
            legacy.version = 3
        }
        val database = Room.databaseBuilder(context, TraceDatabase::class.java, name).addMigrations(GPS_MIGRATION).build()
        try {
            val dao = database.dao()
            assertEquals("Test car", dao.vehicle("vin:test")!!.name)
            val old = dao.samples(1).single()
            assertEquals(50.0, old.speed, 0.0); assertNull(old.locationAccuracyM); assertNull(old.locationTimestamp)
            val located = old.frame().copy(elapsed = 1000).withGps(GpsFix(55.76, 37.62, 7f, 2000, 1000), 1000)
            dao.samples(listOf(Sample.from(1, located)))
            assertEquals(located, dao.samples(1).last().frame())
            dao.recover()
            assertEquals("interrupted", dao.trip(1)!!.status)
            assertEquals(1000L, dao.trip(1)!!.duration)
            assertEquals(2, dao.samples(1).size)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
