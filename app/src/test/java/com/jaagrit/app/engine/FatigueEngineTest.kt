package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FakeClock for deterministic time advancement in unit tests.
 */
class FakeClock(var currentMs: Long = 100_000L) : Clock {
    override fun nowMs(): Long = currentMs

    fun advance(ms: Long) {
        currentMs += ms
    }
}

/**
 * Unit tests for [FatigueEngine] core behavior (ENG-1, ENG-2, ENG-4, ENG-5).
 * Pure Kotlin — zero Android dependencies.
 */
class FatigueEngineTest {

    private lateinit var clock: FakeClock
    private lateinit var engine: FatigueEngine
    private val baseline = Baseline(
        openEar = 0.28f,
        closedEar = 0.04f,
        threshold = 0.16f,
        mar = 0.02f,
        blinkRate = 16.0f,
        responseLatencyMs = 1500L,
        calibratedAtMs = 50_000L,
        isValid = true
    )

    @Before
    fun setup() {
        clock = FakeClock(100_000L)
        engine = FatigueEngine(
            config = Config.DEFAULT,
            clock = clock,
            baseline = baseline
        )
    }

    private fun frame(
        earAvg: Float = 0.28f,
        pitchDeg: Float = 5.0f,
        faceFound: Boolean = true
    ): FaceFrame {
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

    @Test
    fun testNormalDriving_stateAlertLevelL0() {
        // Driver looks forward with open eyes for 10 seconds
        var output = EngineOutput.INITIAL
        for (i in 0 until 50) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.28f))
        }

        assertEquals(DriverState.NORMAL, output.state)
        assertEquals(Level.L0, output.level)
        assertEquals(100, output.alertness)
        assertTrue("No alert reasons during normal driving", output.reasons.isEmpty())
        assertTrue("No actions triggered", output.actions.isEmpty())
    }

    @Test
    fun testShortBlink_doesNotTriggerAlertOrPenalty() {
        // Drive normally for 2s
        for (i in 0 until 20) {
            clock.advance(100L)
            engine.onFrame(frame(earAvg = 0.28f))
        }

        // 150ms blink (closed eye)
        clock.advance(50L)
        engine.onFrame(frame(earAvg = 0.05f))
        clock.advance(100L)
        engine.onFrame(frame(earAvg = 0.05f))

        // Reopen eyes
        clock.advance(100L)
        val output = engine.onFrame(frame(earAvg = 0.28f))

        assertEquals(DriverState.NORMAL, output.state)
        assertEquals(Level.L0, output.level)
        assertTrue("Alertness stays high after normal blink", output.alertness >= 95)
    }

    @Test
    fun testOneSecondClosure_penalizesScoreButNotCritical() {
        // Drive normally first
        for (i in 0 until 10) {
            clock.advance(100L)
            engine.onFrame(frame(earAvg = 0.28f))
        }

        // Close eyes for 1000ms
        var output = EngineOutput.INITIAL
        for (i in 0 until 10) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.05f))
        }

        // 1.0s closure should lower score due to longestRecentClosure penalty
        assertTrue("Score should decrease after 1s closure", output.alertness < 100)
        // Must NOT force CRITICAL yet (closureConfirmMs is 2.5s)
        assertTrue("1s closure must not be CRITICAL", output.state != DriverState.CRITICAL)
        assertTrue("1s closure level must not be L3", output.level != Level.L3)
    }

    @Test
    fun testThreeSecondClosure_forcesCriticalAndLevelL3() {
        // Drive normally
        engine.onFrame(frame(earAvg = 0.28f))

        // Close eyes continuously for 3.0 seconds (30 x 100ms)
        var output = EngineOutput.INITIAL
        for (i in 0 until 30) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }

        // After 2.5s confirm, must force CRITICAL and Level.L3 regardless of score
        assertEquals(DriverState.CRITICAL, output.state)
        assertEquals(Level.L3, output.level)
        assertTrue("Reasons should list eye closure", output.reasons.any { it.contains("closure", ignoreCase = true) })
    }

    @Test
    fun testFaceGlitchMidClosure_under300ms_preservesClosureTimer() {
        // Close eyes for 1.5 seconds
        for (i in 0 until 15) {
            clock.advance(100L)
            engine.onFrame(frame(earAvg = 0.04f))
        }

        // Brief face loss glitch for 200 ms (under 300 ms tolerance)
        clock.advance(100L)
        val glitchOutput1 = engine.onFrame(frame(faceFound = false))
        assertEquals(DriverState.FACE_LOST, glitchOutput1.state)

        clock.advance(100L)
        val glitchOutput2 = engine.onFrame(frame(faceFound = false))
        assertEquals(DriverState.FACE_LOST, glitchOutput2.state)

        // Face reappears still closed: advance 1.0s more (total closure time > 2.5s)
        var output = EngineOutput.INITIAL
        for (i in 0 until 10) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }

        // Glitch under 300ms preserved closure timer -> successfully confirms CRITICAL!
        assertEquals(DriverState.CRITICAL, output.state)
        assertEquals(Level.L3, output.level)
    }

    @Test
    fun testFaceLostMidClosure_over300ms_abortsClosureTimer() {
        // Close eyes for 1.5 seconds
        for (i in 0 until 15) {
            clock.advance(100L)
            engine.onFrame(frame(earAvg = 0.04f))
        }

        // Face lost for 400 ms (exceeds 300 ms tolerance)
        for (i in 0 until 4) {
            clock.advance(100L)
            engine.onFrame(frame(faceFound = false))
        }

        // Face returns with closed eyes for only 1.2 seconds (starts fresh closure timer)
        var output = EngineOutput.INITIAL
        for (i in 0 until 12) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }

        // 1.2s fresh closure has NOT reached 2.5s -> must NOT be CRITICAL!
        assertTrue("Closure timer was reset; 1.2s closure is not CRITICAL", output.state != DriverState.CRITICAL)
    }

    @Test
    fun testFaceLost_neverEscalatesToAlarms_emitsReminderAt30Seconds() {
        // Initial face-lost frame starts the face lost counter
        engine.onFrame(frame(faceFound = false))

        // Face is lost continuously for 29 seconds
        for (s in 1..29) {
            clock.advance(1000L)
            val output = engine.onFrame(frame(faceFound = false))
            assertEquals(DriverState.FACE_LOST, output.state)
            assertEquals(Level.L0, output.level)
            assertTrue("Never escalates to alarms while face lost", output.level != Level.L3)
            assertTrue("No reminder before 30s", output.actions.isEmpty())
        }

        // At 30 seconds: onTick emits Action.Speak reminder
        clock.advance(1000L)
        val tickOutput = engine.onTick()
        assertEquals(DriverState.FACE_LOST, tickOutput.state)
        assertEquals(Level.L0, tickOutput.level)
        assertEquals(1, tickOutput.actions.size)
        val action = tickOutput.actions.first()
        assertTrue("Action must be Speak", action is Action.Speak)
        val speak = action as Action.Speak
        assertTrue("Reminder text must mention camera/phone", speak.text.contains("कैमरे") || speak.text.contains("फोन"))
        assertFalse("Reminder must not be urgent alarm", speak.urgent)
    }

    @Test
    fun testDriveTimePenaltyRamp() {
        // 1 Hour: 0 penalty
        engine.resetDrive(startTimeMs = 0L)
        clock.currentMs = 3_600_000L // 1 hour
        var output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals("0 penalty at 1h", 100, output.alertness)

        // 2 Hours: 0 penalty boundary
        clock.currentMs = 7_200_000L // 2 hours
        output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals("0 penalty at 2h boundary", 100, output.alertness)

        // 4 Hours: Half ramp (7.5 pts penalty)
        clock.currentMs = 14_400_000L // 4 hours
        // Feed frames to let EMA converge
        for (i in 0 until 30) {
            output = engine.onFrame(frame(earAvg = 0.28f))
        }
        assertTrue("Score around 92-93 after 4h", output.alertness in 91..94)

        // 6 Hours: Max 15 pts penalty
        clock.currentMs = 21_600_000L // 6 hours
        for (i in 0 until 40) {
            output = engine.onFrame(frame(earAvg = 0.28f))
        }
        assertEquals("15 pts penalty at 6h (score 85)", 85, output.alertness)

        // 8 Hours: Capped at 15 pts penalty
        clock.currentMs = 28_800_000L // 8 hours
        for (i in 0 until 40) {
            output = engine.onFrame(frame(earAvg = 0.28f))
        }
        assertEquals("Penalty remains capped at 15 pts (score 85)", 85, output.alertness)
    }

    @Test
    fun testHeadDroopPenalty_signalIsolated() {
        // Drive normally
        engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 0.0f))

        // Nod head down (> 15 deg) sustained for 2.0 seconds (> 1.5s sustain)
        var output = EngineOutput.INITIAL
        for (i in 0 until 20) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20.0f))
        }

        assertTrue("Head droop penalty lowers score", output.alertness < 100)
        assertTrue("Reasons include head droop", output.reasons.any { it.contains("droop", ignoreCase = true) })
    }

    @Test
    fun testPerclosPenalty_signalIsolated() {
        // Synthesize high PERCLOS (eyes closed 30% of frames over rolling window)
        var output = EngineOutput.INITIAL
        for (i in 0 until 100) {
            clock.advance(100L)
            val ear = if (i % 3 == 0) 0.05f else 0.28f
            output = engine.onFrame(frame(earAvg = ear))
        }

        assertTrue("High PERCLOS lowers alertness score", output.alertness <= 75)
        assertTrue("Reasons include PERCLOS", output.reasons.any { it.contains("PERCLOS", ignoreCase = true) })
    }

    @Test
    fun testScoreBandTransitions_andArbitration() {
        // 1. Normal (Score >= 71) -> Level L0, State NORMAL
        engine.resetDrive()
        var output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals(Level.L0, output.level)
        assertEquals(DriverState.NORMAL, output.state)

        // 2. Raw trigger arbitration: PERCLOS >= 0.12 triggers Level L2
        for (i in 0 until 50) {
            clock.advance(100L)
            val ear = if (i % 6 == 0) 0.05f else 0.28f
            output = engine.onFrame(frame(earAvg = ear, pitchDeg = 5f))
        }
        assertTrue("Score reflects fatigue (31..70)", output.alertness in 31..70)
        assertEquals(Level.L2, output.level)
        assertEquals(DriverState.FATIGUED, output.state)

        // 3. Severe fatigue dropping score below 30 -> Level L3, State CRITICAL
        // 2.0s closure (< 2.5s override) adds heavy closure penalty + PERCLOS + head droop
        for (i in 0 until 20) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.05f, pitchDeg = 25f))
        }
        for (i in 0 until 25) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = if (i % 2 == 0) 0.05f else 0.28f, pitchDeg = 25f))
        }
        assertTrue("Score reaches critical band <= 30", output.alertness <= 30)
        assertEquals(Level.L3, output.level)
        assertEquals(DriverState.CRITICAL, output.state)
    }

    @Test
    fun testFatigueEngine_endToEndLadderEscalationAndResponse() {
        // 1. Initial normal drive
        engine.resetDrive()
        var output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals(Level.L0, output.level)
        assertEquals(DriverState.NORMAL, output.state)

        // 2. Continuous closure for 2.5s triggers L3 actions
        var l3TriggerOutput: EngineOutput? = null
        for (i in 0 until 30) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
            if (output.actions.isNotEmpty()) {
                l3TriggerOutput = output
            }
        }
        assertNotNull("Must emit actions on confirming 2.5s closure", l3TriggerOutput)
        assertTrue(l3TriggerOutput!!.actions.contains(Action.ShowRedFlash))
        assertTrue(l3TriggerOutput.actions.contains(Action.Vibrate(VibePattern.URGENT)))
        assertTrue(l3TriggerOutput.actions.any { it is Action.Speak && it.urgent })
        assertEquals(Level.L3, output.level)
        assertEquals(DriverState.CRITICAL, output.state)

        // 3. Driver unresponsive for 10s -> escalates to L4 (Family Clip)
        var l4TriggerOutput: EngineOutput? = null
        for (i in 0 until 100) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
            if (output.actions.any { it is Action.PlayFamilyClip }) {
                l4TriggerOutput = output
            }
        }
        assertNotNull("Must escalate to L4 family clip after 10s", l4TriggerOutput)
        assertEquals(Level.L4, output.level)
        assertEquals(DriverState.CRITICAL, output.state)
        assertTrue(output.reasons.any { it.contains("L4", ignoreCase = true) })

        // 4. Driver responds via UI button (VoiceEvent.ImAwake)
        clock.advance(500L)
        val awakeOutput = engine.onVoice(VoiceEvent.ImAwake)
        assertEquals(Level.L0, awakeOutput.level)
        assertEquals(DriverState.NORMAL, awakeOutput.state)

        // 5. Normal driving resumes
        clock.advance(5000L)
        output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals(Level.L0, output.level)
        assertEquals(DriverState.NORMAL, output.state)
        assertTrue(output.actions.none { it is Action.PlayFamilyClip })
        assertTrue(output.actions.none { it is Action.SendSms })
    }

    @Test
    fun testAudit023_onTickAndOnFrame_returnIdenticalLevelAndState() {
        // Scenario 1: Raw L2 trigger (PERCLOS >= 0.12) while score band might still be L0/Caution
        engine.resetDrive()
        for (i in 0 until 50) {
            clock.advance(100L)
            // 1 out of 6 frames closed -> PERCLOS ~0.16 >= 0.12 (raw L2 trigger)
            val ear = if (i % 6 == 0) 0.05f else 0.28f
            engine.onFrame(frame(earAvg = ear, pitchDeg = 5f))
        }

        val frameOutput = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 5f))
        val tickOutput = engine.onTick()

        assertEquals("onFrame and onTick must return identical level on raw L2", frameOutput.level, tickOutput.level)
        assertEquals("onFrame and onTick must return identical state on raw L2", frameOutput.state, tickOutput.state)
        assertEquals(Level.L2, tickOutput.level)
        assertEquals(DriverState.FATIGUED, tickOutput.state)

        // Scenario 2: Severe closure (>= 2.5s) triggering hard CRITICAL override
        engine.resetDrive()
        var lastClosureFrame = EngineOutput.INITIAL
        for (i in 0 until 26) {
            clock.advance(100L)
            lastClosureFrame = engine.onFrame(frame(earAvg = 0.04f))
        }
        val closureTick = engine.onTick()

        assertEquals("onFrame and onTick must return identical level on closure L3", lastClosureFrame.level, closureTick.level)
        assertEquals("onFrame and onTick must return identical state on closure L3", lastClosureFrame.state, closureTick.state)
        assertEquals(Level.L3, closureTick.level)
        assertEquals(DriverState.CRITICAL, closureTick.state)

        // Scenario 3: Normal open eyes driving (Level L0, NORMAL)
        engine.onVoice(VoiceEvent.ImAwake)
        clock.advance(5000L)
        val normalFrame = engine.onFrame(frame(earAvg = 0.28f))
        val normalTick = engine.onTick()

        assertEquals(normalFrame.level, normalTick.level)
        assertEquals(normalFrame.state, normalTick.state)
        assertEquals(Level.L0, normalTick.level)
        assertEquals(DriverState.NORMAL, normalTick.state)
    }
}


