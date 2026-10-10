package com.jaagrit.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaagrit.app.data.dao.AlertDao
import com.jaagrit.app.data.dao.TripDao
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.engine.Level
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class JaagritDatabaseTest {

    private lateinit var db: JaagritDatabase
    private lateinit var tripDao: TripDao
    private lateinit var alertDao: AlertDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JaagritDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripDao = db.tripDao()
        alertDao = db.alertDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testTripCreationAndClose() = runBlocking {
        val startWallMs = 1700000000000L
        val tripId = tripDao.insertTrip(Trip(startMs = startWallMs))
        assertTrue("Trip ID should be generated > 0", tripId > 0)

        val activeTrip = tripDao.getActiveTrip()
        assertNotNull(activeTrip)
        assertEquals(tripId, activeTrip!!.id)
        assertTrue(activeTrip.isActive)
        assertNull(activeTrip.endMs)

        // Close trip
        val endWallMs = startWallMs + 600000L // 10 minutes drive
        tripDao.closeTrip(
            tripId = tripId,
            endMs = endWallMs,
            avgAlertness = 85.5,
            alertCount = 2,
            criticalCount = 1
        )

        val closedTrip = tripDao.getTripById(tripId)
        assertNotNull(closedTrip)
        assertFalse(closedTrip!!.isActive)
        assertEquals(endWallMs, closedTrip.endMs)
        assertEquals(600000L, closedTrip.durationMs)
        assertEquals(85.5, closedTrip.avgAlertness!!, 0.01)
        assertEquals(2, closedTrip.alertCount)
        assertEquals(1, closedTrip.criticalCount)
    }

    @Test
    fun testAlertSampleInsertionAndAverage() = runBlocking {
        val tripId = tripDao.insertTrip(Trip(startMs = 1700000000000L))

        val samples = listOf(
            AlertSample(tripId = tripId, tsMs = 1700000005000L, alertness = 90),
            AlertSample(tripId = tripId, tsMs = 1700000010000L, alertness = 80),
            AlertSample(tripId = tripId, tsMs = 1700000015000L, alertness = 70)
        )
        alertDao.insertSamples(samples)

        val count = alertDao.getSampleCountForTrip(tripId)
        assertEquals(3, count)

        val avg = alertDao.getAverageAlertnessForTrip(tripId)
        assertNotNull(avg)
        assertEquals(80.0, avg!!, 0.01)

        val retrievedSamples = alertDao.getSamplesForTrip(tripId).first()
        assertEquals(3, retrievedSamples.size)
        assertEquals(90, retrievedSamples[0].alertness)
        assertEquals(80, retrievedSamples[1].alertness)
        assertEquals(70, retrievedSamples[2].alertness)
    }

    @Test
    fun testAlertEventLoggingWithResponseTypes() = runBlocking {
        val tripId = tripDao.insertTrip(Trip(startMs = 1700000000000L))

        // Test logging all response types: imAwake, eyesOpen, voice, dismissed, none, L5_NOT_SENT
        val event1 = AlertEvent(
            tripId = tripId,
            tsMs = 1700000010000L,
            level = Level.L3,
            reason = "Eye closure >= 2.5s",
            durationMs = 4500L,
            response = "imAwake"
        )
        val event2 = AlertEvent(
            tripId = tripId,
            tsMs = 1700000030000L,
            level = Level.L4,
            reason = "Unresponsive to alarm - Family voice",
            durationMs = 12000L,
            response = "eyesOpen"
        )
        val event3 = AlertEvent(
            tripId = tripId,
            tsMs = 1700000050000L,
            level = Level.L5,
            reason = "Emergency SMS unavailable (airplane mode)",
            durationMs = 0L,
            response = "L5_NOT_SENT"
        )

        alertDao.insertAlertEvent(event1)
        alertDao.insertAlertEvent(event2)
        alertDao.insertAlertEvent(event3)

        val events = alertDao.getEventsForTrip(tripId).first()
        assertEquals(3, events.size)

        assertEquals(Level.L3, events[0].level)
        assertEquals("imAwake", events[0].response)
        assertEquals(4500L, events[0].durationMs)

        assertEquals(Level.L4, events[1].level)
        assertEquals("eyesOpen", events[1].response)

        assertEquals(Level.L5, events[2].level)
        assertEquals("L5_NOT_SENT", events[2].response)

        val critCount = alertDao.getCriticalAlertCountForTrip(tripId)
        assertEquals(3, critCount) // L3, L4, L5 are all critical
    }

    @Test
    fun testCascadeDeleteOnTripRemoval() = runBlocking {
        val tripId = tripDao.insertTrip(Trip(startMs = 1700000000000L))

        alertDao.insertSample(AlertSample(tripId = tripId, tsMs = 1700000005000L, alertness = 88))
        alertDao.insertAlertEvent(
            AlertEvent(
                tripId = tripId,
                tsMs = 1700000010000L,
                level = Level.L3,
                reason = "Drowsiness",
                durationMs = 2000L,
                response = "dismissed"
            )
        )

        assertEquals(1, alertDao.getSampleCountForTrip(tripId))
        assertEquals(1, alertDao.getAlertCountForTrip(tripId))

        // Delete trip
        val trip = tripDao.getTripById(tripId)!!
        tripDao.deleteTrip(trip)

        // Verify Cascade deletion
        assertNull(tripDao.getTripById(tripId))
        assertEquals(0, alertDao.getSampleCountForTrip(tripId))
        assertEquals(0, alertDao.getAlertCountForTrip(tripId))
    }

    @Test
    fun testExposedQueriesForM8AndM10() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = now - 3600000L // 1 hour ago (within today)

        val trip1 = Trip(
            startMs = startOfToday,
            endMs = startOfToday + 1800000L,
            avgAlertness = 90.0,
            alertCount = 1,
            criticalCount = 1
        )
        val trip1Id = tripDao.insertTrip(trip1)

        alertDao.insertAlertEvent(
            AlertEvent(
                tripId = trip1Id,
                tsMs = startOfToday + 600000L,
                level = Level.L3,
                reason = "Fatigue",
                durationMs = 3000L,
                response = "imAwake"
            )
        )

        val trip2 = Trip(
            startMs = startOfToday + 2000000L,
            endMs = startOfToday + 2500000L,
            avgAlertness = 80.0,
            alertCount = 2,
            criticalCount = 0
        )
        val trip2Id = tripDao.insertTrip(trip2)

        alertDao.insertAlertEvent(
            AlertEvent(
                tripId = trip2Id,
                tsMs = startOfToday + 2200000L,
                level = Level.L1,
                reason = "Caution",
                durationMs = 1000L,
                response = "voice"
            )
        )

        // 1. Total alert count today
        val alertsToday = tripDao.getAlertCountToday(startOfToday - 1000L)
        assertEquals(3, alertsToday)

        // 2. Last alert time
        val lastAlertOverall = alertDao.getLastAlertTimeOverall()
        assertEquals(startOfToday + 2200000L, lastAlertOverall)

        val lastAlertTrip1 = alertDao.getLastAlertTimeForTrip(trip1Id)
        assertEquals(startOfToday + 600000L, lastAlertTrip1)

        // 3. Average alertness overall
        val overallAvg = tripDao.getOverallAverageAlertness()
        assertNotNull(overallAvg)
        assertEquals(85.0, overallAvg!!, 0.01) // (90 + 80) / 2
    }
}
