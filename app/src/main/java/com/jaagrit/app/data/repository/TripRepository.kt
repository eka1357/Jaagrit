package com.jaagrit.app.data.repository

import com.jaagrit.app.data.dao.AlertDao
import com.jaagrit.app.data.dao.TripDao
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.data.model.TripWithDetails
import com.jaagrit.app.engine.Level
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repository managing trip recording, 5-second alertness sampling, and alert event persistence.
 *
 * All database operations are dispatched off the main thread (Dispatchers.IO).
 * Timers use SystemClock.elapsedRealtime(); stored database timestamps use wall-clock epoch ms.
 * Emergency contact stays in DataStore, never in Room.
 */
class TripRepository(
    private val tripDao: TripDao,
    private val alertDao: AlertDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    // --- Trip Lifecycle ---

    /**
     * Creates a new driving trip record in Room at Start Drive.
     * Stored timestamp is wall-clock epoch ms (System.currentTimeMillis()).
     */
    suspend fun createTrip(startMs: Long = System.currentTimeMillis()): Long = withContext(ioDispatcher) {
        val trip = Trip(startMs = startMs)
        tripDao.insertTrip(trip)
    }

    /**
     * Closes an active trip on End Drive.
     * Computes average alertness from collected samples if not explicitly provided.
     */
    suspend fun closeTrip(
        tripId: Long,
        endMs: Long = System.currentTimeMillis(),
        alertCount: Int = 0,
        criticalCount: Int = 0
    ) = withContext(ioDispatcher) {
        val avgAlertness = alertDao.getAverageAlertnessForTrip(tripId)
        val computedAlertCount = if (alertCount > 0) alertCount else alertDao.getAlertCountForTrip(tripId)
        val computedCriticalCount = if (criticalCount > 0) criticalCount else alertDao.getCriticalAlertCountForTrip(tripId)

        tripDao.closeTrip(
            tripId = tripId,
            endMs = endMs,
            avgAlertness = avgAlertness,
            alertCount = computedAlertCount,
            criticalCount = computedCriticalCount
        )
    }

    suspend fun getTripById(tripId: Long): Trip? = withContext(ioDispatcher) {
        tripDao.getTripById(tripId)
    }

    suspend fun getActiveTrip(): Trip? = withContext(ioDispatcher) {
        tripDao.getActiveTrip()
    }

    fun getAllTrips(): Flow<List<Trip>> = tripDao.getAllTrips()

    fun getTripWithDetails(tripId: Long): Flow<TripWithDetails?> = tripDao.getTripWithDetails(tripId)

    // --- 5-Second Telemetry Sampling ---

    /**
     * Flushes a batch of AlertSamples collected during the active drive.
     * Flushed every 5 s so a crash or app kill does not lose telemetry.
     */
    suspend fun flushAlertSamples(samples: List<AlertSample>) = withContext(ioDispatcher) {
        if (samples.isEmpty()) return@withContext
        alertDao.insertSamples(samples)
    }

    /**
     * Records a single alertness sample.
     */
    suspend fun recordAlertSample(
        tripId: Long,
        alertness: Int,
        tsMs: Long = System.currentTimeMillis()
    ) = withContext(ioDispatcher) {
        alertDao.insertSample(
            AlertSample(
                tripId = tripId,
                tsMs = tsMs,
                alertness = alertness
            )
        )
    }

    fun getSamplesForTrip(tripId: Long): Flow<List<AlertSample>> = alertDao.getSamplesForTrip(tripId)

    // --- Alert Event Logging ---

    /**
     * Logs an alert event with level, reason, duration, and response type.
     * Response types: none | imAwake | voice | eyesOpen | dismissed | L5_NOT_SENT.
     */
    suspend fun recordAlertEvent(
        tripId: Long,
        level: Level,
        reason: String,
        durationMs: Long,
        response: String,
        tsMs: Long = System.currentTimeMillis()
    ): Long = withContext(ioDispatcher) {
        val event = AlertEvent(
            tripId = tripId,
            tsMs = tsMs,
            level = level,
            reason = reason,
            durationMs = durationMs,
            response = response
        )
        alertDao.insertAlertEvent(event)
    }

    fun getEventsForTrip(tripId: Long): Flow<List<AlertEvent>> = alertDao.getEventsForTrip(tripId)

    // --- Queries for M8 (Voice Answers) and M10 (Dashboard) ---

    /**
     * Returns the drive time so far in milliseconds for a specific trip.
     */
    suspend fun getDriveTimeSoFarMs(
        tripId: Long,
        currentWallMs: Long = System.currentTimeMillis()
    ): Long = withContext(ioDispatcher) {
        val trip = tripDao.getTripById(tripId) ?: return@withContext 0L
        if (trip.endMs != null) {
            trip.durationMs
        } else {
            (currentWallMs - trip.startMs).coerceAtLeast(0L)
        }
    }

    /**
     * Returns the total alert count today (since midnight local time).
     */
    suspend fun getAlertCountToday(
        startOfDayMs: Long = getStartOfTodayMs()
    ): Int = withContext(ioDispatcher) {
        tripDao.getAlertCountToday(startOfDayMs)
    }

    /**
     * Returns the timestamp (wall-clock ms) of the most recent alert event.
     * If tripId is provided, returns the last alert for that trip; otherwise overall.
     */
    suspend fun getLastAlertTime(tripId: Long? = null): Long? = withContext(ioDispatcher) {
        if (tripId != null) {
            alertDao.getLastAlertTimeForTrip(tripId)
        } else {
            alertDao.getLastAlertTimeOverall()
        }
    }

    /**
     * Returns the average alertness score.
     * If tripId is provided, returns the average for that trip; otherwise overall across completed trips.
     */
    suspend fun getAverageAlertness(tripId: Long? = null): Double? = withContext(ioDispatcher) {
        if (tripId != null) {
            alertDao.getAverageAlertnessForTrip(tripId)
        } else {
            tripDao.getOverallAverageAlertness()
        }
    }

    companion object {
        fun getStartOfTodayMs(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
