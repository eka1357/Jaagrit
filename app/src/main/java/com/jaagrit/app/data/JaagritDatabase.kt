package com.jaagrit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jaagrit.app.data.converters.RoomConverters
import com.jaagrit.app.data.dao.AlertDao
import com.jaagrit.app.data.dao.TripDao
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip

/**
 * Jaagrit Offline Room Database (ARCHITECTURE.md).
 * Manages Trips, AlertSamples (5 s telemetry), and AlertEvents (graduated ladder alerts).
 *
 * Strictly offline. Never stores face images or audio (Hard Rule 5).
 * Emergency contact stays in DataStore, never in Room.
 */
@Database(
    entities = [
        Trip::class,
        AlertSample::class,
        AlertEvent::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class JaagritDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var INSTANCE: JaagritDatabase? = null

        fun getDatabase(context: Context): JaagritDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JaagritDatabase::class.java,
                    "jaagrit.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
