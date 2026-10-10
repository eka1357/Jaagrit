package com.jaagrit.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.data.model.TripWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Trip operations.
 */
@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Query(
        """
        UPDATE trips 
        SET endMs = :endMs, 
            avgAlertness = :avgAlertness, 
            alertCount = :alertCount, 
            criticalCount = :criticalCount 
        WHERE id = :tripId
        """
    )
    suspend fun closeTrip(
        tripId: Long,
        endMs: Long,
        avgAlertness: Double?,
        alertCount: Int,
        criticalCount: Int
    ): Int

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripById(tripId: Long): Trip?

    @Query("SELECT * FROM trips WHERE endMs IS NULL ORDER BY startMs DESC LIMIT 1")
    suspend fun getActiveTrip(): Trip?

    @Query("SELECT * FROM trips ORDER BY startMs DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun getTripWithDetails(tripId: Long): Flow<TripWithDetails?>

    @Query("SELECT COALESCE(SUM(alertCount), 0) FROM trips WHERE startMs >= :startOfDayMs")
    suspend fun getAlertCountToday(startOfDayMs: Long): Int

    @Query("SELECT AVG(avgAlertness) FROM trips WHERE avgAlertness IS NOT NULL")
    suspend fun getOverallAverageAlertness(): Double?

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getTripCount(): Int

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun clearAllTrips(): Int
}
