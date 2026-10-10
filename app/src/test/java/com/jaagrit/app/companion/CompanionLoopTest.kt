package com.jaagrit.app.companion

import com.jaagrit.app.engine.Action
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.CompanionPromptKind
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.DriverState
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.engine.FakeClock
import com.jaagrit.app.engine.FatigueEngine
import com.jaagrit.app.engine.Level
import com.jaagrit.app.engine.VoiceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Companion loop (M9b1, M9b2: COM-1, COM-2, COM-3, COM-4, COM-6, D2).
 * Pure Kotlin — zero Android dependencies.
 */
class CompanionLoopTest {

    private lateinit var clock: FakeClock
    private lateinit var loop: CompanionLoop
    private val baseline = Baseline(
        openEar = 0.32f,
        closedEar = 0.08f,
        threshold = 0.20f,
        mar = 0.02f,
        blinkRate = 16.0f,
        responseLatencyMs = 2000L,
        calibratedAtMs = 1000L,
        isValid = true
    )

    @Before
    fun setup() {
        clock = FakeClock(1_000_000L)
        loop = CompanionLoop(
            config = Config.DEFAULT,
            clock = clock,
            brain = PhraseBankCompanion(Config.DEFAULT),
            baseline = baseline
        )
    }

    @Test
    fun openerFiresAtL1_onceOnBandEntry() {
        // L0 normal driving: silent
        val out0 = loop.evaluate(activeLevel = Level.L0, isEyesClosed = false, isAlertActive = false)
        assertTrue(out0 is CompanionLoop.CompanionOutcome.Actions)
        assertTrue((out0 as CompanionLoop.CompanionOutcome.Actions).actions.isEmpty())

        // Transition to L1: fires opener
        clock.advance(100L)
        val out1 = loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertTrue(out1 is CompanionLoop.CompanionOutcome.Actions)
        val actions1 = (out1 as CompanionLoop.CompanionOutcome.Actions).actions
        assertTrue("Must speak opener", actions1.any { it is Action.Speak })
        assertEquals(CompanionPromptKind.OPENER, loop.currentPromptKind)
        assertNotNull(loop.currentPromptText)

        // Immediate next tick: does not fire a second prompt
        clock.advance(100L)
        val out2 = loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertTrue((out2 as CompanionLoop.CompanionOutcome.Actions).actions.isEmpty())
    }

    @Test
    fun eyesClosed_suppressesCompanionPrompt() {
        // Eyes closed at L1: must NOT prompt
        val out = loop.evaluate(activeLevel = Level.L1, isEyesClosed = true, isAlertActive = false)
        assertTrue((out as CompanionLoop.CompanionOutcome.Actions).actions.isEmpty())
        assertNull(loop.currentPromptKind)
    }

    @Test
    fun openerIgnoredTwice_triggers5MinuteBackoff() {
        // 1st opener fires at L1
        loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertEquals(CompanionPromptKind.OPENER, loop.currentPromptKind)

        // Let 1st opener timeout expire (speech allowance 5s + reply window 10s = 15s)
        clock.advance(16_000L)
        loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertNull("Prompt cleared on timeout", loop.currentPromptKind)
        assertEquals(1, loop.consecutiveIgnoredOpeners)
        assertFalse(loop.status(clock.nowMs()).backedOff)

        // Wait past 60s cooldown
        clock.advance(60_000L)
        // 2nd opener fires
        loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertEquals(CompanionPromptKind.OPENER, loop.currentPromptKind)

        // Let 2nd opener timeout expire
        clock.advance(16_000L)
        loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertNull(loop.currentPromptKind)

        // Backoff triggered for 5 min!
        assertTrue("Companion must be backed off for 5 minutes", loop.status(clock.nowMs()).backedOff)

        // During backoff (e.g. 2 min later), no prompt fires
        clock.advance(120_000L)
        val outDuringBackoff = loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertTrue((outDuringBackoff as CompanionLoop.CompanionOutcome.Actions).actions.isEmpty())

        // After 5 min total backoff, backoff clears
        clock.advance(181_000L)
        assertFalse(loop.status(clock.nowMs()).backedOff)
    }

    @Test
    fun dismissSilencesCompanionFor2Minutes() {
        // Fire opener
        loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertEquals(CompanionPromptKind.OPENER, loop.currentPromptKind)

        // Driver dismisses
        val dismissActions = loop.onVoice(VoiceEvent.Dismiss)
        assertTrue("Speaks dismiss ack", dismissActions.any { it is Action.Speak })
        assertTrue("Silenced for 2 minutes", loop.status(clock.nowMs()).silenced)
        assertNull(loop.currentPromptKind)

        // 1 minute later: still silenced, no prompt
        clock.advance(60_000L)
        val out1 = loop.evaluate(activeLevel = Level.L1, isEyesClosed = false, isAlertActive = false)
        assertTrue((out1 as CompanionLoop.CompanionOutcome.Actions).actions.isEmpty())

        // 2 minutes later: silence lifts
        clock.advance(61_000L)
        assertFalse(loop.status(clock.nowMs()).silenced)
    }

    @Test
    fun mathQuestionFiresAtL2_withChoicesAndMaxWait() {
        // Transition to L2: fires math question
        val out = loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        assertTrue(out is CompanionLoop.CompanionOutcome.Actions)
        val actions = (out as CompanionLoop.CompanionOutcome.Actions).actions
        val askAction = actions.filterIsInstance<Action.AskQuestion>().firstOrNull()
        assertNotNull("AskQuestion action must be emitted", askAction)
        assertEquals(CompanionPromptKind.MATH, loop.currentPromptKind)
        assertNotNull(loop.currentQuestion)
        assertTrue("Choices should contain at least 2 options", loop.currentQuestion!!.choices.size >= 2)
        assertTrue("Choices must include the correct answer", loop.currentQuestion!!.choices.contains(loop.currentQuestion!!.answer))
        assertTrue("Max wait should be positive", loop.currentQuestion!!.maxWaitMs in 4000L..6000L)
    }

    @Test
    fun mathQuestionTimeout_escalatesToL3() {
        // Question asked at L2
        loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        val maxWait = loop.currentQuestion!!.maxWaitMs
        val allowance = Config.DEFAULT.companionSpeechAllowanceMs

        // Advance past deadline (maxWait + allowance + 100ms)
        clock.advance(maxWait + allowance + 100L)
        val timeoutOutcome = loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        assertTrue("Timeout on L2 math question must escalate to L3", timeoutOutcome is CompanionLoop.CompanionOutcome.EscalateToL3)
        assertNull(loop.currentPromptKind)
    }

    @Test
    fun mathAnswerLatency_slowConfirmsFatigueSignal() {
        loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        val q = loop.currentQuestion!!

        // Baseline is 2000ms. Latency factor is 2.0 -> threshold is 4000ms.
        // Answer with 4500ms (> 2x baseline):
        val voiceActions = loop.onVoice(VoiceEvent.Answer(text = q.answer, latencyMs = 4500L))
        assertTrue(loop.lastAnswerSlow)
        assertTrue(loop.isSlowCognitiveResponseActive(clock.nowMs()))
        assertEquals(true, loop.lastAnswerCorrect)
        assertNull(loop.currentPromptKind)
    }

    @Test
    fun mathAnswerLatency_fastAwaitsGoodAck() {
        loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        val q = loop.currentQuestion!!

        // Answer with 1200ms (< 2x baseline):
        val voiceActions = loop.onVoice(VoiceEvent.Answer(text = q.answer, latencyMs = 1200L))
        assertFalse(loop.lastAnswerSlow)
        assertFalse(loop.isSlowCognitiveResponseActive(clock.nowMs()))
        assertEquals(true, loop.lastAnswerCorrect)
    }

    @Test
    fun wrongAnswer_stillCountsAsResponse_onlyLatencyMatters() {
        loop.evaluate(activeLevel = Level.L2, isEyesClosed = false, isAlertActive = false)
        // Give wrong answer text:
        val voiceActions = loop.onVoice(VoiceEvent.Answer(text = "999", latencyMs = 1500L))
        assertEquals(false, loop.lastAnswerCorrect)
        assertFalse("Latency was normal, so not flagged as slow", loop.lastAnswerSlow)
        assertNull("Question was answered, so cleared", loop.currentPromptKind)
    }

    @Test
    fun endToEnd_engineIntegratesCompanion_dismissThenEyeCloseStillFiresL3() {
        val engine = FatigueEngine(
            config = Config.DEFAULT,
            clock = clock,
            baseline = baseline
        )
        engine.resetDrive(clock.nowMs())

        // Drive enters L1 via PERCLOS / blink or voice:
        engine.onVoice(VoiceEvent.Dismiss)
        assertTrue(engine.companion.status(clock.nowMs()).silenced)

        // While silenced, driver closes eyes for 2.5s -> L3 STILL FIRES!
        var l3Fired = false
        val stepMs = 50L
        for (i in 1..60) {
            clock.advance(stepMs)
            val f = FaceFrame(
                tsMs = clock.nowMs(),
                faceFound = true,
                earL = 0.05f,
                earR = 0.05f,
                mar = 0.02f,
                pitchDeg = 5f,
                yawDeg = 0f,
                rollDeg = 0f
            )
            val out = engine.onFrame(f)
            if (out.actions.any { it is Action.ShowRedFlash }) {
                l3Fired = true
                assertEquals(DriverState.CRITICAL, out.state)
                assertEquals(Level.L3, out.level)
                break
            }
        }
        assertTrue("Dismiss silence must NEVER compromise the L3 safety path", l3Fired)
    }
}
