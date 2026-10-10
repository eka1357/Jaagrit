package com.jaagrit.app.engine

import com.jaagrit.app.speech.Phrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test suite verifying all audit findings and ladder decisions from docs/AUDIT_M0_M5.md.
 * Covers: AUDIT-001, AUDIT-004, AUDIT-009, AUDIT-011, AUDIT-012.
 */
class AuditRegressionTest {

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
        // Production-like clock offset: e.g. 86,400,000 ms (~1 day elapsedRealtime)
        clock = FakeClock(currentMs = 86_400_000L)
        engine = FatigueEngine(
            config = Config.DEFAULT,
            clock = clock,
            baseline = baseline
        )
    }

    private fun frame(
        earAvg: Float = 0.28f,
        pitchDeg: Float = 5.0f,
        faceFound: Boolean = true,
        tsMs: Long = clock.nowMs()
    ): FaceFrame {
        return FaceFrame(
            tsMs = tsMs,
            faceFound = faceFound,
            earL = earAvg,
            earR = earAvg,
            mar = 0.02f,
            pitchDeg = pitchDeg,
            yawDeg = 0.0f,
            rollDeg = 0.0f
        )
    }

    // --- AUDIT-001: Monotonic Clock Tests ---

    @Test
    fun testAudit001_L4DrivenOnlyByOnTick_fires10sAfterL3() {
        // 1. Feed closed frames until L3 triggers
        var output = EngineOutput.INITIAL
        while (output.level != Level.L3) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }
        assertEquals(Level.L3, output.level)
        assertTrue(output.isAlertActive)
        val l3Time = clock.nowMs()

        // 2. Face stays present and eyes stay closed, but no more frames arrive;
        // only onTick() is called every 100ms.
        // At 9.9s (99 ticks), L4 must NOT have fired yet.
        for (i in 1..99) {
            clock.advance(100L)
            val tickOutput = engine.onTick()
            assertEquals("L4 must not fire before 10s (tick $i)", Level.L3, tickOutput.level)
            assertTrue("PlayFamilyClip must not fire before 10s", tickOutput.actions.none { it is Action.PlayFamilyClip })
        }

        // 3. Exactly at 10.0s (100th tick = L3 + 10s), L4 must fire!
        clock.advance(100L)
        val l4Output = engine.onTick()
        assertEquals("L4 must fire at exactly L3 + 10s", Level.L4, l4Output.level)
        assertTrue("PlayFamilyClip must fire on L4", l4Output.actions.any { it is Action.PlayFamilyClip })
        assertEquals(l3Time + 10_000L, clock.nowMs())
    }

    @Test
    fun testAudit001_faceLostReminder_viaOnTick_at30s() {
        // Face is lost
        val lostFrameOutput = engine.onFrame(frame(faceFound = false))
        assertEquals(DriverState.FACE_LOST, lostFrameOutput.state)
        val lostStartMs = clock.nowMs()

        // Subsequent onTick() calls for 29.9 seconds (299 ticks of 100ms)
        for (i in 1..299) {
            clock.advance(100L)
            val tickOutput = engine.onTick()
            assertEquals(DriverState.FACE_LOST, tickOutput.state)
            assertTrue("No reminder spoken before 30s at tick $i", tickOutput.actions.none { it is Action.Speak })
        }

        // At 30.0s (tick 300 = lostStartMs + 30_000ms), reminder must fire!
        clock.advance(100L)
        val tick30sOutput = engine.onTick()
        assertEquals(DriverState.FACE_LOST, tick30sOutput.state)
        assertEquals(lostStartMs + 30_000L, clock.nowMs())
        val speakAction = tick30sOutput.actions.filterIsInstance<Action.Speak>().firstOrNull()
        assertNotNull("Face-lost 30s reminder must fire at 30.0s", speakAction)
        assertEquals(Phrases.FACE_LOST_30S, speakAction!!.text)
        assertFalse("Face-lost reminder must not be urgent alarm", speakAction.urgent)
    }

    @Test
    fun testAudit001_driveTimePenalty_withProductionLikeClockValues() {
        // Set drive start at production-like timestamp (e.g. 86_400_000 ms)
        engine.resetDrive(clock.nowMs())

        // Drive for 1 hour: 0 penalty
        clock.advance(3_600_000L)
        var output = engine.onFrame(frame(earAvg = 0.28f))
        assertEquals("0 penalty at 1 hour", 100, output.alertness)

        // Drive for 5 hours total (4 more hours): linear ramp 2h..6h penalty = 15 * (5 - 2)/4 = 11.25 points
        clock.advance(4 * 3_600_000L)
        // Feed frames to let EMA converge
        for (i in 0 until 30) {
            output = engine.onFrame(frame(earAvg = 0.28f))
        }
        // Score should be approximately 100 - 11.25 = ~89
        assertTrue("Alertness should reflect ~11pt drive time penalty (was ${output.alertness})", output.alertness in 87..90)
        assertTrue("Reasons must mention long drive duration", output.reasons.any { it.contains("drive duration", ignoreCase = true) })
    }

    @Test
    fun testAudit001_normalBlinkRate_withProductionLikeClockValues() {
        // Enable blink signal specifically for this test
        val customEngine = FatigueEngine(
            config = Config(blinkSignalEnabled = true),
            clock = clock,
            baseline = baseline // baseline.blinkRate = 16.0
        )
        customEngine.resetDrive(clock.nowMs())

        // Simulate 60 seconds of driving at ~10 FPS with 15 normal blinks (each blink ~150ms).
        // 15 blinks x 2 closed frames = 30 closed frames out of 600 frames total.
        // PERCLOS = 30 / 600 = 0.050 (at 0-penalty threshold), keeping alertness 100.
        var output = EngineOutput.INITIAL
        for (second in 1..60) {
            val isBlinkSecond = second % 4 == 0
            for (f in 0 until 10) {
                clock.advance(100L)
                val ear = if (isBlinkSecond && (f == 1 || f == 2)) 0.04f else 0.28f
                output = customEngine.onFrame(frame(earAvg = ear))
            }
        }

        // Normal 15 blinks/min matching baseline (16.0) should have 0 blink penalty
        assertFalse("Blink rate matching baseline must not be flagged elevated", output.reasons.any { it.contains("Blink rate elevated") })
        assertEquals(100, output.alertness)
    }

    // --- AUDIT-004: Persistent Alarm State Through FACE_LOST ---

    @Test
    fun testAudit004_alarmAndVibration_keepRunningThroughFaceLost_untilAwakeResponse() {
        // 1. Close eyes 3.0s to trigger L3 alert
        var output = EngineOutput.INITIAL
        for (i in 0 until 30) {
            clock.advance(100L)
            output = engine.onFrame(frame(earAvg = 0.04f))
        }
        assertEquals(Level.L3, output.level)
        assertTrue("isAlertActive must be true on L3", output.isAlertActive)

        // 2. Face is lost (slumped head / camera covered / face glitch)
        clock.advance(100L)
        val faceLostOutput = engine.onFrame(frame(faceFound = false))
        assertEquals(DriverState.FACE_LOST, faceLostOutput.state)
        assertTrue("AUDIT-004: isAlertActive MUST remain true through FACE_LOST", faceLostOutput.isAlertActive)

        // 3. Periodic tick during FACE_LOST
        clock.advance(100L)
        val tickOutput = engine.onTick()
        assertEquals(DriverState.FACE_LOST, tickOutput.state)
        assertTrue("AUDIT-004: isAlertActive MUST remain true on ticks during FACE_LOST", tickOutput.isAlertActive)

        // 4. Face returns, eyes still closed
        clock.advance(100L)
        val faceReturnOutput = engine.onFrame(frame(earAvg = 0.04f))
        assertEquals(Level.L3, faceReturnOutput.level)
        assertTrue("isAlertActive remains true", faceReturnOutput.isAlertActive)

        // 5. Driver taps 'I'M AWAKE' -> awake response clears alert
        val awakeOutput = engine.onVoice(VoiceEvent.ImAwake)
        assertFalse("AUDIT-004: Response clears alert active state", awakeOutput.isAlertActive)
        assertEquals(Level.L0, awakeOutput.level)
    }

    // --- AUDIT-009: Head-Droop L3 Eyes-Open Auto-Response Timing ---

    @Test
    fun testAudit009_headDroopL3_persistsAtLeast3sOfEyesOpenAfterAlert() {
        // 1. Head droop sustained for 1.5s with eyes OPEN -> soft prompt
        engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))
        clock.advance(1500L)
        engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))

        // 2. 5.0 seconds of no reply and no pitch restore -> L3 alert fires
        clock.advance(5000L)
        val l3Output = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))
        assertEquals(Level.L3, l3Output.level)
        assertTrue(l3Output.isAlertActive)

        // 3. Next frame at +100ms has eyes OPEN.
        // Before AUDIT-009 fix: auto-response cancelled L3 on this frame (40ms later)!
        // After fix: eyesOpenContinuousStartTimeMs was reset on fireL3, so alert MUST persist!
        clock.advance(100L)
        val nextFrameOutput = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))
        assertEquals("AUDIT-009: Alert must NOT auto-cancel on the next frame", Level.L3, nextFrameOutput.level)
        assertTrue("AUDIT-009: Alert must remain active at 100ms", nextFrameOutput.isAlertActive)

        // 4. Advance 2800ms more (total 2.9s of open eyes post-alert) -> must still be L3!
        clock.advance(2800L)
        val pre3sOutput = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))
        assertEquals("Alert must remain active before 3s post-alert open eyes", Level.L3, pre3sOutput.level)
        assertTrue(pre3sOutput.isAlertActive)

        // 5. Advance 200ms more (total >= 3.0s of continuous open eyes post-alert) -> now auto-response clears it!
        clock.advance(200L)
        val post3sOutput = engine.onFrame(frame(earAvg = 0.28f, pitchDeg = 20f))
        assertEquals("Eyes open >= 3s post-alert clears the alert", Level.L0, post3sOutput.level)
        assertFalse("Alert active cleared", post3sOutput.isAlertActive)
    }

    // --- AUDIT-011: Face Return Countdown Restart ---

    @Test
    fun testAudit011_faceReturnsAfterLoss_restartsL4CountdownFromReturnMoment() {
        // 1. Trigger L3 at T0
        for (i in 0 until 30) {
            clock.advance(100L)
            engine.onFrame(frame(earAvg = 0.04f))
        }
        assertEquals(Level.L3, engine.ladder.currentLadderLevel)

        // 2. Face is lost after 2.0s
        clock.advance(2000L)
        engine.onFrame(frame(faceFound = false))

        // 3. Face is lost for 12.0s (which exceeds the original 10s L4 deadline)
        clock.advance(12_000L)
        engine.onTick()
        assertEquals(Level.L3, engine.ladder.currentLadderLevel)

        // 4. Face returns with eyes open at T = 14s
        val faceReturnFrame = engine.onFrame(frame(faceFound = true, earAvg = 0.28f))
        // Before AUDIT-011 fix: PlayFamilyClip fired immediately on this frame!
        // After fix: countdown restarted, PlayFamilyClip must NOT fire on return!
        assertTrue("AUDIT-011: L4 must NOT fire on the first frame face returns", faceReturnFrame.actions.none { it is Action.PlayFamilyClip })
        assertEquals("Ladder level must still be L3", Level.L3, engine.ladder.currentLadderLevel)

        // 5. Countdown was restarted: L4 deadline is now now + 10s
        assertEquals(clock.nowMs() + 10_000L, engine.ladder.l4EscalateAtMs)

        // Advance 9.9s: no L4
        clock.advance(9900L)
        val preTick = engine.onTick()
        assertTrue(preTick.actions.none { it is Action.PlayFamilyClip })

        // At 10.0s after return: L4 fires
        clock.advance(100L)
        val l4Tick = engine.onTick()
        assertTrue("L4 fires 10s after face returned", l4Tick.actions.any { it is Action.PlayFamilyClip })
    }

    // --- AUDIT-012: Blink signal must not fire from a poorly estimated baseline ---
    // M9a enabled the signal (D12). The AUDIT-012 protection is now the in-drive learning window:
    // no blink penalty or L1 until the first BLINK_BASELINE_LEARN_MS (3 min) refine the baseline (CAL-2).

    @Test
    fun testAudit012_blinkSignalSilentDuringBaselineLearning() {
        assertTrue(Config.DEFAULT.blinkSignalEnabled)
        assertEquals(180_000L, Config.BLINK_BASELINE_LEARN_MS)

        val defaultEngine = FatigueEngine(
            config = Config.DEFAULT,
            clock = clock,
            baseline = baseline
        )
        defaultEngine.resetDrive(clock.nowMs())

        // Feed frequent blinks (e.g. 20 blinks in 52s = ~23/min, > 20% over baseline 16.0)
        // With 25 open frames between blinks so PERCLOS is 1/26 = 3.8% (well below the 5% zero-penalty threshold)
        var output = EngineOutput.INITIAL
        for (blink in 1..20) {
            // Blink: 1 closed frame (100ms)
            clock.advance(100L)
            defaultEngine.onFrame(frame(earAvg = 0.04f))
            // Recovery: 25 open frames (2500ms)
            for (openFrame in 1..25) {
                clock.advance(100L)
                output = defaultEngine.onFrame(frame(earAvg = 0.28f))
            }
        }

        // Still inside the 3-min learning window (52 s elapsed):
        // 1. Raw L1 trigger does not fire on blink rate
        assertEquals("Blink signal learning: state is NORMAL", DriverState.NORMAL, output.state)
        assertEquals("Blink signal learning: level is L0", Level.L0, output.level)
        assertEquals("Baseline not yet learned", null, output.metrics?.blinkBaselinePerMin)
        // 2. Alertness penalty is 0, score is 100
        assertEquals("Alertness score unaffected while learning", 100, output.alertness)
        assertFalse("Reasons list contains no blink alert", output.reasons.any { it.contains("Blink") })
    }
}
