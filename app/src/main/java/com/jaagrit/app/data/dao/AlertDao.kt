package com.jaagrit.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AlertSample and AlertEvent operations.
 */
@Dao
interface AlertDao {

    // --- AlertSample queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: AlertSample): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<AlertSample>)

    @Query("SELECT * FROM alert_samples WHERE tripId = :tripId ORDER BY tsMs ASC")
    fun getSamplesForTrip(tripId: Long): Flow<List<AlertSample>>

    @Query("SELECT AVG(alertness) FROM alert_samples WHERE tripId = :tripId")
    suspend fun getAverageAlertnessForTrip(tripId: Long): Double?

    @Query("SELECT COUNT(*) FROM alert_samples WHERE tripId = :tripId")
    suspend fun getSampleCountForTrip(tripId: Long): Int

    // --- AlertEvent queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertEvent(event: AlertEvent): Long

    @Query("SELECT * FROM alert_events WHERE tripId = :tripId ORDER BY tsMs ASC")
    fun getEventsForTrip(tripId: Long): Flow<List<AlertEvent>>

    @Query("SELECT * FROM alert_events ORDER BY tsMs DESC")
    fun getAllEvents(): Flow<List<AlertEvent>>

    @Query("SELECT MAX(tsMs) FROM alert_events WHERE tripId = :tripId")
    suspend fun getLastAlertTimeForTrip(tripId: Long): Long?

    @Query("SELECT MAX(tsMs) FROM alert_events")
    suspend fun getLastAlertTimeOverall(): Long?

    @Query("SELECT COUNT(*) FROM alert_events WHERE tsMs >= :startOfDayMs")
    suspend fun getAlertEventsCountToday(startOfDayMs: Long): Int

    @Query("SELECT COUNT(*) FROM alert_events WHERE tripId = :tripId AND level IN ('L3', 'L4', 'L5')")
    suspend fun getCriticalAlertCountForTrip(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM alert_events WHERE tripId = :tripId")
    suspend fun getAlertCountForTrip(tripId: Long): Int
}
