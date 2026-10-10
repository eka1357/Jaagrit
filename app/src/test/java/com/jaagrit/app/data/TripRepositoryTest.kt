package com.jaagrit.app.data

import com.jaagrit.app.data.dao.AlertDao
import com.jaagrit.app.data.dao.TripDao
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.data.model.TripWithDetails
import com.jaagrit.app.data.repository.TripRepository
import com.jaagrit.app.engine.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TripRepositoryTest {

    private lateinit var fakeTripDao: FakeTripDao
    private lateinit var fakeAlertDao: FakeAlertDao
    private lateinit var repository: TripRepository

    @Before
    fun setUp() {
        fakeTripDao = FakeTripDao()
        fakeAlertDao = FakeAlertDao()
        repository = TripRepository(fakeTripDao, fakeAlertDao, Dispatchers.Unconfined)
    }

    @Test
    fun `test create trip and close trip`() = runBlocking {
        val startMs = 1700000000000L
        val tripId = repository.createTrip(startMs)
        assertEquals(1L, tripId)

        val active = repository.getActiveTrip()
        assertNotNull(active)
        assertEquals(startMs, active!!.startMs)
        assertNull(active.endMs)

        // Close trip
        val endMs = startMs + 300000L // 5 mins
        repository.closeTrip(tripId, endMs = endMs, alertCount = 3, criticalCount = 1)

        val closed = repository.getTripById(tripId)
        assertNotNull(closed)
        assertEquals(endMs, closed!!.endMs)
        assertEquals(3, closed.alertCount)
        assertEquals(1, closed.criticalCount)
    }

    @Test
    fun `test flush alert samples and compute average`() = runBlocking {
        val tripId = repository.createTrip(1000L)

        val samples = listOf(
            AlertSample(tripId = tripId, tsMs = 5000L, alertness = 90),
            AlertSample(tripId = tripId, tsMs = 10000L, alertness = 70)
        )
        repository.flushAlertSamples(samples)

        val retrieved = repository.getSamplesForTrip(tripId).first()
        assertEquals(2, retrieved.size)

        val avg = repository.getAverageAlertness(tripId)
        assertNotNull(avg)
        assertEquals(80.0, avg!!, 0.01)
    }

    @Test
    fun `test record alert event and fetch last alert time`() = runBlocking {
        val tripId = repository.createTrip(1000L)

        repository.recordAlertEvent(
            tripId = tripId,
            level = Level.L3,
            reason = "Eye closure >= 2.5s",
            durationMs = 4000L,
            response = "imAwake",
            tsMs = 6000L
        )

        repository.recordAlertEvent(
            tripId = tripId,
            level = Level.L5,
            reason = "Emergency SMS unavailable",
            durationMs = 0L,
            response = "L5_NOT_SENT",
            tsMs = 9000L
        )

        val events = repository.getEventsForTrip(tripId).first()
        assertEquals(2, events.size)
        assertEquals("imAwake", events[0].response)
        assertEquals("L5_NOT_SENT", events[1].response)

        val lastAlert = repository.getLastAlertTime(tripId)
        assertEquals(9000L, lastAlert)
    }

    @Test
    fun `test drive time so far calculation`() = runBlocking {
        val startMs = 10000L
        val tripId = repository.createTrip(startMs)

        // Active trip drive time
        val activeDriveTime = repository.getDriveTimeSoFarMs(tripId, currentWallMs = 25000L)
        assertEquals(15000L, activeDriveTime)

        // Closed trip drive time
        repository.closeTrip(tripId, endMs = 20000L)
        val closedDriveTime = repository.getDriveTimeSoFarMs(tripId, currentWallMs = 50000L)
        assertEquals(10000L, closedDriveTime)
    }

    // --- In-Memory Fakes ---

    private class FakeTripDao : TripDao {
        private val trips = mutableMapOf<Long, Trip>()
        private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())
        private var nextId = 1L

        override suspend fun insertTrip(trip: Trip): Long {
            val id = if (trip.id == 0L) nextId++ else trip.id
            val saved = trip.copy(id = id)
            trips[id] = saved
            tripsFlow.value = trips.values.toList().sortedByDescending { it.startMs }
            return id
        }

        override suspend fun updateTrip(trip: Trip) {
            trips[trip.id] = trip
            tripsFlow.value = trips.values.toList().sortedByDescending { it.startMs }
        }

        override suspend fun closeTrip(
            tripId: Long,
            endMs: Long,
            avgAlertness: Double?,
            alertCount: Int,
            criticalCount: Int
        ): Int {
            val current = trips[tripId] ?: return 0
            val updated = current.copy(
                endMs = endMs,
                avgAlertness = avgAlertness,
                alertCount = alertCount,
                criticalCount = criticalCount
            )
            trips[tripId] = updated
            tripsFlow.value = trips.values.toList().sortedByDescending { it.startMs }
            return 1
        }

        override suspend fun getTripById(tripId: Long): Trip? = trips[tripId]

        override suspend fun getActiveTrip(): Trip? = trips.values.firstOrNull { it.endMs == null }

        override fun getAllTrips(): Flow<List<Trip>> = tripsFlow.asStateFlow()

        override fun getTripWithDetails(tripId: Long): Flow<TripWithDetails?> {
            val t = trips[tripId]
            return MutableStateFlow(t?.let { TripWithDetails(trip = it) })
        }

        override suspend fun getAlertCountToday(startOfDayMs: Long): Int =
            trips.values.filter { it.startMs >= startOfDayMs }.sumOf { it.alertCount }

        override suspend fun getOverallAverageAlertness(): Double? {
            val valid = trips.values.mapNotNull { it.avgAlertness }
            return if (valid.isEmpty()) null else valid.average()
        }

        override suspend fun getTripCount(): Int = trips.size

        override suspend fun deleteTrip(trip: Trip) {
            trips.remove(trip.id)
            tripsFlow.value = trips.values.toList()
        }

        override suspend fun clearAllTrips(): Int {
            val count = trips.size
            trips.clear()
            tripsFlow.value = emptyList()
            return count
        }
    }

    private class FakeAlertDao : AlertDao {
        private val samples = mutableListOf<AlertSample>()
        private val events = mutableListOf<AlertEvent>()
        private val samplesFlow = MutableStateFlow<List<AlertSample>>(emptyList())
        private val eventsFlow = MutableStateFlow<List<AlertEvent>>(emptyList())
        private var nextSampleId = 1L
        private var nextEventId = 1L

        override suspend fun insertSample(sample: AlertSample): Long {
            val id = nextSampleId++
            samples.add(sample.copy(id = id))
            samplesFlow.value = samples.toList()
            return id
        }

        override suspend fun insertSamples(samples: List<AlertSample>) {
            samples.forEach { insertSample(it) }
        }

        override fun getSamplesForTrip(tripId: Long): Flow<List<AlertSample>> {
            return MutableStateFlow(samples.filter { it.tripId == tripId })
        }

        override suspend fun getAverageAlertnessForTrip(tripId: Long): Double? {
            val tripSamples = samples.filter { it.tripId == tripId }
            return if (tripSamples.isEmpty()) null else tripSamples.map { it.alertness }.average()
        }

        override suspend fun getSampleCountForTrip(tripId: Long): Int =
            samples.count { it.tripId == tripId }

        override suspend fun insertAlertEvent(event: AlertEvent): Long {
            val id = nextEventId++
            events.add(event.copy(id = id))
            eventsFlow.value = events.toList()
            return id
        }

        override fun getEventsForTrip(tripId: Long): Flow<List<AlertEvent>> {
            return MutableStateFlow(events.filter { it.tripId == tripId })
        }

        override fun getAllEvents(): Flow<List<AlertEvent>> = eventsFlow.asStateFlow()

        override suspend fun getLastAlertTimeForTrip(tripId: Long): Long? =
            events.filter { it.tripId == tripId }.maxOfOrNull { it.tsMs }

        override suspend fun getLastAlertTimeOverall(): Long? =
            events.maxOfOrNull { it.tsMs }

        override suspend fun getAlertEventsCountToday(startOfDayMs: Long): Int =
            events.count { it.tsMs >= startOfDayMs }

        override suspend fun getCriticalAlertCountForTrip(tripId: Long): Int =
            events.count { it.tripId == tripId && it.level in listOf(Level.L3, Level.L4, Level.L5) }

        override suspend fun getAlertCountForTrip(tripId: Long): Int =
            events.count { it.tripId == tripId }
    }
}
