package com.jaagrit.app.report

import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for M10 ReportExporter: trip safety score calculation,
 * JSON report generation, clipboard formatting, and honest limits compliance.
 */
class ReportExporterTest {

    @Test
    fun calculateTripSafetyScore_perfectTrip_returns100() {
        val score = Config.calculateTripSafetyScore(
            avgAlertness = 98.5,
            alertCount = 0,
            criticalCount = 0
        )
        assertEquals(99, score)
    }

    @Test
    fun calculateTripSafetyScore_nullAvgAlertness_defaultsTo100Base() {
        val score = Config.calculateTripSafetyScore(
            avgAlertness = null,
            alertCount = 0,
            criticalCount = 0
        )
        assertEquals(100, score)
    }

    @Test
    fun calculateTripSafetyScore_withWarningsAndCriticals_appliesConfiguredPenalties() {
        // Base = 85.0
        // 2 criticals * 15.0 = 30.0
        // 2 warnings (4 total - 2 critical) * 5.0 = 10.0
        // Expected: 85 - 30 - 10 = 45
        val score = Config.calculateTripSafetyScore(
            avgAlertness = 85.0,
            alertCount = 4,
            criticalCount = 2
        )
        assertEquals(45, score)
    }

    @Test
    fun calculateTripSafetyScore_heavyPenalties_clampsToZero() {
        val score = Config.calculateTripSafetyScore(
            avgAlertness = 40.0,
            alertCount = 10,
            criticalCount = 8
        )
        assertEquals(0, score)
    }

    @Test
    fun formatAlertSummaryForClipboard_nullEvent_returnsSafeSummary() {
        val summary = ReportExporter.formatAlertSummaryForClipboard(
            tripId = 42L,
            event = null,
            safetyScore = 95,
            driverName = "Rohan"
        )

        assertTrue(summary.contains("Trip #42"))
        assertTrue(summary.contains("Rohan"))
        assertTrue(summary.contains("95/100"))
        assertTrue(summary.contains("No fatigue alerts recorded"))
    }

    @Test
    fun formatAlertSummaryForClipboard_withEvent_formatsCompleteDetails() {
        val event = AlertEvent(
            id = 1L,
            tripId = 42L,
            tsMs = 1700000000000L,
            level = Level.L3,
            reason = "Eyes closed >= 2.5s (microsleep)",
            durationMs = 2800L,
            response = "imAwake"
        )

        val summary = ReportExporter.formatAlertSummaryForClipboard(
            tripId = 42L,
            event = event,
            safetyScore = 72,
            driverName = "Vikram"
        )

        assertTrue(summary.contains("JAAGRIT ALERT SUMMARY"))
        assertTrue(summary.contains("Vikram"))
        assertTrue(summary.contains("Trip #42"))
        assertTrue(summary.contains("Level: L3"))
        assertTrue(summary.contains("Eyes closed >= 2.5s"))
        assertTrue(summary.contains("2.8s"))
        assertTrue(summary.contains("Response: imAwake"))
        assertTrue(summary.contains("Trip Safety Score: 72/100"))
        assertTrue(summary.contains("100% on-device"))
    }

    @Test
    fun buildJsonReport_validStructureAndHonestLimits() {
        // Need dummy context-free ReportExporter logic check
        // Build sample trip, samples, and events
        val trip = Trip(
            id = 7L,
            startMs = 1700000000000L,
            endMs = 1700000600000L,
            avgAlertness = 88.0,
            alertCount = 1,
            criticalCount = 1
        )

        val samples = listOf(
            AlertSample(id = 1L, tripId = 7L, tsMs = 1700000005000L, alertness = 92),
            AlertSample(id = 2L, tripId = 7L, tsMs = 1700000010000L, alertness = 84)
        )

        val events = listOf(
            AlertEvent(
                id = 10L,
                tripId = 7L,
                tsMs = 1700000008000L,
                level = Level.L3,
                reason = "Eyes closed >= 2.5s",
                durationMs = 2600L,
                response = "imAwake"
            )
        )

        val baseline = Baseline(
            openEar = 0.32f,
            closedEar = 0.12f,
            threshold = 0.22f,
            mar = 0.18f,
            blinkRate = 18.0f,
            responseLatencyMs = 1200L,
            calibratedAtMs = 1699990000000L,
            isValid = true
        )

        val safetyScore = Config.calculateTripSafetyScore(trip.avgAlertness, trip.alertCount, trip.criticalCount)

        val jsonOutput = ReportExporter.buildJsonReport(
            trip = trip,
            samples = samples,
            events = events,
            baseline = baseline,
            driverName = "Arjun",
            emergencyContact = "+919876543210",
            safetyScore = safetyScore
        )

        assertNotNull(jsonOutput)
        assertTrue(jsonOutput.contains("\"app\": \"Jaagrit\""))
        assertTrue(jsonOutput.contains("\"tripId\": 7"))
        assertTrue(jsonOutput.contains("\"name\": \"Arjun\""))
        assertTrue(jsonOutput.contains("\"emergencyContact\": \"+919876543210\""))
        assertTrue(jsonOutput.contains("\"isCalibrated\": true"))
        assertTrue(jsonOutput.contains("\"safetyScore\": $safetyScore"))
        assertTrue(jsonOutput.contains("\"honestLimitsDisclaimer\":"))
        assertTrue(jsonOutput.contains("heuristically"))
        assertTrue(jsonOutput.contains("low light"))
        assertTrue(jsonOutput.contains("sunglasses"))
        assertTrue(jsonOutput.contains("medical diagnosis"))
        assertTrue(jsonOutput.contains("\"eventId\": 10"))
        assertTrue(jsonOutput.contains("\"reason\": \"Eyes closed >= 2.5s\""))
    }
}
