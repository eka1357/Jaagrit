package com.jaagrit.app.engine

import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.sms.SmsNotifier
import com.jaagrit.app.speech.Phrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Milestone M6 Regression Tests (LAD-3, LAD-4, LAD-6, LAD-7, D6, D8, D9).
 * Tests L4 family clip trigger, L5 countdown, SMS template, demo timers, and false alert counter.
 * Pure Kotlin — runs on JVM with FakeClock.
 */
class M6RegressionTest {

    private lateinit var clock: FakeClock
    private val baseline = Baseline(
        openEar = 0.28f,
        closedEar = 0.04f,
        threshold = 0.16f,
        mar = 0.02f,
        blinkRate = 16.0f,
        responseLatencyMs = 1200L,
        calibratedAtMs = 1000L,
        isValid = true
    )

    @Before
    fun setUp() {
        clock = FakeClock(100_000L)
    }

    private fun frame(faceFound: Boolean = true, earAvg: Float = 0.28f, pitchDeg: Float = 0.0f): FaceFrame {
        return FaceFrame(
            tsMs = clock.nowMs(),
            faceFound = faceFound,
            earL = earAvg,
            earR = earAvg,
            mar = 0.02f,
            pitchDeg = pitchDeg,
            yawDeg = 0.0f,
            rollDeg = 0.0f
        )
    }

    // --- LAD-3: L4 Family Clip Trigger ---

    @Test
    fun testL4_familyClipFires10sAfterL3_whenUnresponsive() {
        val engine = FatigueEngine(config = Config(demoTimers = false), clock = clock, baseline = baseline)
        engine.resetDrive(clock.nowMs())

        // 1. Trigger L3 via eye closure (2.5s)
        var output = EngineOutput.INITIAL
        while (output.level != Level.L3) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }
        assertEquals(Level.L3, output.level)
        assertTrue(output.isAlertActive)

        // 2. 9.9 seconds pass (99 ticks), face present, no response -> L4 must not fire yet
        for (i in 1..99) {
            clock.advance(100L)
            val tick = engine.onTick()
            assertEquals("L4 must not fire before 10s at tick $i", Level.L3, tick.level)
            assertTrue("No PlayFamilyClip before 10s", tick.actions.none { it is Action.PlayFamilyClip })
        }

        // 3. Exactly at 10.0s (100th tick), L4 fires!
        clock.advance(100L)
        val l4Output = engine.onTick()
        assertEquals(Level.L4, l4Output.level)
        assertTrue(l4Output.actions.any { it is Action.PlayFamilyClip })
    }

    // --- D8: Family Clip Fallback Text ---

    @Test
    fun testFamilyClipFallbackPhraseMatchesPhrasesSection2() {
        assertEquals("पापा, जल्दी घर आओ। हम इंतज़ार कर रहे हैं।", Phrases.L4_FALLBACK)
    }

    // --- LAD-4: L5 Visible Countdown and SMS Dispatch ---

    @Test
    fun testL5_countdownDecrementsAndSmsFiresAt20s() {
        val engine = FatigueEngine(config = Config(demoTimers = false), clock = clock, baseline = baseline)
        engine.resetDrive(clock.nowMs())

        // 1. Trigger L3
        while (engine.onFrame(frame(earAvg = 0.04f)).level != Level.L3) {
            clock.advance(100L)
        }

        // 2. Advance 10s to trigger L4
        for (i in 1..100) {
            clock.advance(100L)
            engine.onTick()
        }

        // 3. In L4 state, countdown starts at 20s
        val startCountdown = engine.onTick()
        assertEquals(Level.L4, startCountdown.level)
        assertNotNull("L5 countdown must be active", startCountdown.l5CountdownSeconds)
        assertEquals(20, startCountdown.l5CountdownSeconds)

        // 4. Advance 5s (50 ticks) -> countdown shows 15s
        for (i in 1..50) {
            clock.advance(100L)
            engine.onTick()
        }
        val midCountdown = engine.onTick()
        assertEquals(15, midCountdown.l5CountdownSeconds)

        // 5. Advance another 14.8s -> countdown is 0s, L5 has not fired yet
        for (i in 1..148) {
            clock.advance(100L)
            engine.onTick()
        }
        val preL5 = engine.onTick()
        assertEquals(Level.L4, preL5.level)
        assertTrue("SMS not fired before 20s", preL5.actions.none { it is Action.SendSms })

        // 6. At 20.0s after L4 -> L5 fires Action.SendSms!
        clock.advance(200L)
        val l5Output = engine.onTick()
        assertEquals(Level.L5, l5Output.level)
        assertTrue("Action.SendSms must fire on L5", l5Output.actions.any { it is Action.SendSms })
    }

    @Test
    fun testL5_countdownCancelledImmediatelyOnImAwakeResponse() {
        val engine = FatigueEngine(config = Config(demoTimers = false), clock = clock, baseline = baseline)
        engine.resetDrive(clock.nowMs())

        // Trigger L3 and escalate to L4
        while (engine.onFrame(frame(earAvg = 0.04f)).level != Level.L3) {
            clock.advance(100L)
        }
        for (i in 1..100) {
            clock.advance(100L)
            engine.onTick()
        }
        val l4Output = engine.onTick()
        assertEquals(Level.L4, l4Output.level)
        assertNotNull(l4Output.l5CountdownSeconds)

        // Driver taps "I'M AWAKE"
        val responseOutput = engine.onVoice(VoiceEvent.ImAwake)
        assertEquals(Level.L0, responseOutput.level)
        assertFalse(responseOutput.isAlertActive)
        assertNull("Countdown must be cleared on response", responseOutput.l5CountdownSeconds)

        // Advance past former L5 deadline -> SMS must never fire
        for (i in 1..250) {
            clock.advance(100L)
            val tick = engine.onTick()
            assertTrue("SMS must never fire after response", tick.actions.none { it is Action.SendSms })
        }
    }

    // --- PHRASES.md Section 3: SMS Template Formatting ---

    @Test
    fun testSmsTemplateFormatting_withGpsLocation() {
        val message = SmsNotifier.formatSmsMessage(
            driverName = "Ramesh",
            timestamp = "11:45 PM",
            alertCount = 2,
            location = "28.61393, 77.20902"
        )

        val expected = "ALERT: Ramesh may be unresponsive while driving.\n" +
                "Last alert: 11:45 PM\n" +
                "Alerts in this trip: 2\n" +
                "Location: 28.61393, 77.20902\n" +
                "— Sent by Jaagrit (automated safety alert)"

        assertEquals(expected, message)
    }

    @Test
    fun testSmsTemplateFormatting_withLocationUnavailable() {
        val message = SmsNotifier.formatSmsMessage(
            driverName = "Driver",
            timestamp = "03:15 AM",
            alertCount = 1,
            location = "unavailable"
        )

        assertTrue(message.contains("Location: unavailable"))
        assertTrue(message.contains("ALERT: Driver may be unresponsive while driving."))
        assertTrue(message.contains("— Sent by Jaagrit (automated safety alert)"))
    }

    // --- Privacy & Phone Masking ---

    @Test
    fun testPhoneNumberMasking_showsOnlyLastTwoDigits() {
        // Fake test phone numbers per prompt instructions
        assertEquals("******00", SettingsStore.maskPhoneNumber("+91 00000 00000"))
        assertEquals("******10", SettingsStore.maskPhoneNumber("+919999999910"))
        assertEquals("******45", SettingsStore.maskPhoneNumber("+91 88888 88845"))
        assertEquals("Not configured", SettingsStore.maskPhoneNumber(""))
    }

    // --- LAD-7 & D9: DEMO_TIMERS Flag ---

    @Test
    fun testDemoTimers_acceleratesL4To5s_andL5To8s() {
        val demoEngine = FatigueEngine(config = Config(demoTimers = true), clock = clock, baseline = baseline)
        demoEngine.resetDrive(clock.nowMs())

        // 1. Trigger L3
        while (demoEngine.onFrame(frame(earAvg = 0.04f)).level != Level.L3) {
            clock.advance(100L)
        }
        val l3Time = clock.nowMs()

        // 2. In DEMO_TIMERS, L4 fires at 5.0s (50 ticks) instead of 10s
        for (i in 1..49) {
            clock.advance(100L)
            val tick = demoEngine.onTick()
            assertEquals("L4 must not fire before 5s in demo mode", Level.L3, tick.level)
        }
        clock.advance(100L)
        val l4Output = demoEngine.onTick()
        assertEquals("L4 must fire at exactly 5s in demo mode", Level.L4, l4Output.level)
        assertEquals(l3Time + 5_000L, clock.nowMs())

        // 3. In DEMO_TIMERS, L5 countdown starts at 8s instead of 20s
        assertEquals(8, l4Output.l5CountdownSeconds)

        // Advance 7.9s -> L5 not fired yet
        for (i in 1..79) {
            clock.advance(100L)
            demoEngine.onTick()
        }
        // At 8.0s (80th tick after L4), L5 fires!
        clock.advance(100L)
        val l5Output = demoEngine.onTick()
        assertEquals("L5 must fire at 8s after L4 in demo mode", Level.L5, l5Output.level)
        assertTrue(l5Output.actions.any { it is Action.SendSms })
    }

    // --- LAD-6: False Alert Counter and Recalibration Suggestion ---

    @Test
    fun testFalseAlertLimit_suggestsRecalibrationAfterMoreThanThreeDismissalsIn10Min() {
        val engine = FatigueEngine(config = Config(), clock = clock, baseline = baseline)
        engine.resetDrive(clock.nowMs())

        // Simulate 3 dismissed L3 alerts within 5 minutes
        for (alert in 1..3) {
            // Trigger L3
            while (engine.onFrame(frame(earAvg = 0.04f)).level != Level.L3) {
                clock.advance(100L)
            }
            // Dismiss immediately
            val dismissed = engine.onVoice(VoiceEvent.ImAwake)
            assertEquals(alert, dismissed.falseAlertCount)
            assertFalse("<= 3 dismissals does not suggest recalibration", dismissed.suggestRecalibration)

            // Advance 60 seconds of normal driving
            for (openFrame in 1..600) {
                clock.advance(100L)
                engine.onFrame(frame(earAvg = 0.28f))
            }
        }

        // 4th dismissal (more than 3 alerts dismissed within 10 min, LAD-6)
        while (engine.onFrame(frame(earAvg = 0.04f)).level != Level.L3) {
            clock.advance(100L)
        }
        val fourthDismissed = engine.onVoice(VoiceEvent.ImAwake)
        assertEquals(4, fourthDismissed.falseAlertCount)
        assertTrue("More than 3 dismissals suggests recalibration", fourthDismissed.suggestRecalibration)

        // Advance past the 10-minute window (600 seconds) without alerts
        clock.advance(600_100L)
        val normalOutput = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals("Dismissals older than 10m prune out", 0, normalOutput.falseAlertCount)
        assertFalse("Old dismissals no longer suggest recalibration", normalOutput.suggestRecalibration)
    }
}
