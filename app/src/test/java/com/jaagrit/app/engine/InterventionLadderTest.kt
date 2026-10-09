package com.jaagrit.app.engine

import com.jaagrit.app.speech.Phrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [InterventionLadder] graduation, timers, cooldowns, and response cancellations.
 * Pure Kotlin — zero Android dependencies (AGENTS.md Rule 2).
 *
 * Requirements: LAD-1, LAD-2, LAD-3, LAD-5, LAD-7; DECISIONS D3, D5, D9, D12.
 */
class InterventionLadderTest {

    private lateinit var clock: FakeClock
    private lateinit var ladder: InterventionLadder

    @Before
    fun setup() {
        clock = FakeClock(100_000L)
        ladder = InterventionLadder(config = Config.DEFAULT, clock = clock)
    }

    @Test
    fun testL3Fires_andResetsOnResponse() {
        // 1. Raw L3 fires when eyes closed >= 2.5 s (face detected)
        val l3Actions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = true,
            closureDurationMs = 2500L,
            pitchDeg = 5f,
            now = clock.nowMs()
        )

        assertEquals(Level.L3, ladder.currentLadderLevel)
        assertTrue(ladder.isL3Active)
        assertFalse(ladder.isL4Active)
        assertNotNull(ladder.l4EscalateAtMs)
        assertEquals(clock.nowMs() + 10_000L, ladder.l4EscalateAtMs)

        assertTrue("Must include ShowRedFlash", l3Actions.contains(Action.ShowRedFlash))
        assertTrue("Must include urgent vibration", l3Actions.contains(Action.Vibrate(VibePattern.URGENT)))
        val speak = l3Actions.filterIsInstance<Action.Speak>().firstOrNull()
        assertNotNull("Must include urgent speech", speak)
        assertTrue(speak!!.urgent)
        assertTrue("Spoken phrase must be in L3_ALERTS", Phrases.L3_ALERTS.contains(speak.text))

        // 2. Response cancels pending L4 and resets ladder level
        clock.advance(1000L)
        val responseActions = ladder.processResponse(clock.nowMs(), "Tapped I'M AWAKE")

        assertEquals(Level.L0, ladder.currentLadderLevel)
        assertFalse(ladder.isL3Active)
        assertNull(ladder.l4EscalateAtMs)
        assertTrue(responseActions.any { it is Action.Log })

        // 3. Advance past original 10s L4 timeout — L4 must NOT fire
        clock.advance(15_000L)
        val tickActions = ladder.onTick(clock.nowMs(), faceFound = true)
        assertTrue("L4 must not fire after response", tickActions.none { it is Action.PlayFamilyClip })
        assertEquals(Level.L0, ladder.currentLadderLevel)
    }

    @Test
    fun testHeadDroop_pitchRecoveryCancelsPendingL3Escalation() {
        // Head drooping (pitch > 15°) for 1.5 s
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 20f, now = clock.nowMs())
        clock.advance(1500L)
        val droopActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = false,
            closureDurationMs = 0L,
            pitchDeg = 20f,
            now = clock.nowMs()
        )

        // Soft prompt emitted ("Sab theek hai? Bol de.")
        val softPrompt = droopActions.filterIsInstance<Action.Speak>().firstOrNull()
        assertNotNull("Must emit soft spoken prompt", softPrompt)
        assertEquals(Phrases.SOFT_PROMPTS.first(), softPrompt!!.text)
        assertFalse(softPrompt.urgent)
        assertNotNull("Pending L3 escalation must be set", ladder.pendingHeadEscalateAtMs)
        assertEquals(clock.nowMs() + 5000L, ladder.pendingHeadEscalateAtMs)

        // Driver recovers head position: pitch returning <= 15° for 1.5 s cancels pending L3 (D3)
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 5f, now = clock.nowMs())
        clock.advance(1500L) // Total 1.5s restored continuously
        val restoreActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = false,
            closureDurationMs = 0L,
            pitchDeg = 5f,
            now = clock.nowMs()
        )

        assertNull("Pending L3 escalation must be cancelled after 1.5s pitch restore", ladder.pendingHeadEscalateAtMs)
        assertTrue(restoreActions.any { it is Action.Log && it.detail.contains("pitch recovered") })

        // Advance past original 5s deadline — L3 never fires
        clock.advance(6000L)
        val tickActions = ladder.onTick(clock.nowMs(), faceFound = true)
        assertFalse("L3 must not fire after pitch recovery", ladder.isL3Active)
        assertEquals(Level.L0, ladder.currentLadderLevel)
        assertTrue(tickActions.none { it is Action.ShowRedFlash })
    }

    @Test
    fun testHeadDroop_noReplyIn5s_escalatesToL3() {
        // Soft prompt fires after 1.5s droop
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 20f, now = clock.nowMs())
        clock.advance(1500L)
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 20f, now = clock.nowMs())
        assertNotNull(ladder.pendingHeadEscalateAtMs)

        // No reply and no pitch recovery for 5.0 seconds
        clock.advance(5000L)
        val escalateActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = false,
            closureDurationMs = 0L,
            pitchDeg = 20f,
            now = clock.nowMs()
        )

        // Must escalate to L3!
        assertEquals(Level.L3, ladder.currentLadderLevel)
        assertTrue(ladder.isL3Active)
        assertTrue(escalateActions.contains(Action.ShowRedFlash))
        assertTrue(escalateActions.contains(Action.Vibrate(VibePattern.URGENT)))
        assertTrue(escalateActions.any { it is Action.Speak && it.urgent })
        assertNotNull("L4 timer must now be scheduled", ladder.l4EscalateAtMs)
    }

    @Test
    fun testL3ToL4Timing_normalAndDemo() {
        // --- Normal Timers (10 s) ---
        val normalLadder = InterventionLadder(config = Config(demoTimers = false), clock = clock)
        normalLadder.fireL3(clock.nowMs(), "Testing L4 timing")

        // 9.9s later — no response, face detected: L4 must NOT fire yet
        clock.advance(9900L)
        val preL4Actions = normalLadder.onFrame(
            faceFound = true,
            isEyesClosed = true,
            closureDurationMs = 9900L,
            pitchDeg = 0f,
            now = clock.nowMs()
        )
        assertFalse("L4 must not fire at 9.9s", normalLadder.isL4Active)
        assertEquals(Level.L3, normalLadder.currentLadderLevel)
        assertTrue(preL4Actions.none { it is Action.PlayFamilyClip })

        // 10.0s later — L4 fires!
        clock.advance(100L)
        val l4Actions = normalLadder.onFrame(
            faceFound = true,
            isEyesClosed = true,
            closureDurationMs = 10_000L,
            pitchDeg = 0f,
            now = clock.nowMs()
        )
        assertTrue("L4 must fire at 10.0s", normalLadder.isL4Active)
        assertEquals(Level.L4, normalLadder.currentLadderLevel)
        assertTrue(l4Actions.contains(Action.PlayFamilyClip(1)))
        assertTrue(l4Actions.contains(Action.Vibrate(VibePattern.URGENT)))
        assertEquals(clock.nowMs() + 20_000L, normalLadder.l5EscalateAtMs)

        // --- Demo Timers (5 s) ---
        val demoClock = FakeClock(200_000L)
        val demoLadder = InterventionLadder(config = Config(demoTimers = true), clock = demoClock)
        demoLadder.fireL3(demoClock.nowMs(), "Testing demo L4")

        demoClock.advance(4900L)
        demoLadder.onTick(demoClock.nowMs(), faceFound = true)
        assertFalse("Demo L4 must not fire at 4.9s", demoLadder.isL4Active)

        demoClock.advance(100L) // 5.0s exactly
        val demoL4Actions = demoLadder.onTick(demoClock.nowMs(), faceFound = true)
        assertTrue("Demo L4 must fire at 5.0s", demoLadder.isL4Active)
        assertEquals(Level.L4, demoLadder.currentLadderLevel)
        assertTrue(demoL4Actions.contains(Action.PlayFamilyClip(1)))
        assertEquals(demoClock.nowMs() + 8000L, demoLadder.l5EscalateAtMs)
    }

    @Test
    fun testL4ToL5Timing_normalAndDemo() {
        // --- Normal Timers (20 s) ---
        ladder.fireL3(clock.nowMs(), "Testing L5 timing")
        clock.advance(10_000L)
        ladder.onFrame(faceFound = true, isEyesClosed = true, closureDurationMs = 10_000L, pitchDeg = 0f, now = clock.nowMs())
        assertTrue(ladder.isL4Active)

        // 19.9s after L4 — L5 must NOT fire yet
        clock.advance(19_900L)
        val preL5 = ladder.onFrame(faceFound = true, isEyesClosed = true, closureDurationMs = 29_900L, pitchDeg = 0f, now = clock.nowMs())
        assertFalse(ladder.isL5Active)
        assertEquals(Level.L4, ladder.currentLadderLevel)
        assertTrue(preL5.none { it is Action.SendSms })

        // 20.0s after L4 — L5 fires!
        clock.advance(100L)
        val l5Actions = ladder.onFrame(faceFound = true, isEyesClosed = true, closureDurationMs = 30_000L, pitchDeg = 0f, now = clock.nowMs())
        assertTrue("L5 must fire 20s after L4", ladder.isL5Active)
        assertEquals(Level.L5, ladder.currentLadderLevel)
        assertTrue(l5Actions.any { it is Action.SendSms })
        assertTrue(l5Actions.contains(Action.Vibrate(VibePattern.URGENT)))

        // --- Demo Timers (8 s) ---
        val demoClock = FakeClock(300_000L)
        val demoLadder = InterventionLadder(config = Config(demoTimers = true), clock = demoClock)
        demoLadder.fireL3(demoClock.nowMs(), "Demo L5")
        demoClock.advance(5000L) // Demo L4 fires at 5s
        demoLadder.onTick(demoClock.nowMs(), faceFound = true)
        assertTrue(demoLadder.isL4Active)

        demoClock.advance(7900L)
        demoLadder.onTick(demoClock.nowMs(), faceFound = true)
        assertFalse("Demo L5 must not fire at 7.9s", demoLadder.isL5Active)

        demoClock.advance(100L) // 8.0s after L4
        val demoL5Actions = demoLadder.onTick(demoClock.nowMs(), faceFound = true)
        assertTrue("Demo L5 must fire at 8.0s after L4", demoLadder.isL5Active)
        assertEquals(Level.L5, demoLadder.currentLadderLevel)
        assertTrue(demoL5Actions.any { it is Action.SendSms })
    }

    @Test
    fun testResponseCancelsL5() {
        ladder.fireL3(clock.nowMs(), "Testing response cancels L5")
        clock.advance(10_000L)
        ladder.onFrame(faceFound = true, isEyesClosed = true, closureDurationMs = 10_000L, pitchDeg = 0f, now = clock.nowMs())
        assertTrue(ladder.isL4Active)
        assertNotNull(ladder.l5EscalateAtMs)

        // Advance 10s into L4, driver responds
        clock.advance(10_000L)
        ladder.processResponse(clock.nowMs(), "Driver voice: 'I am awake'")

        assertFalse(ladder.isL4Active)
        assertFalse(ladder.isL3Active)
        assertNull(ladder.l5EscalateAtMs)
        assertEquals(Level.L0, ladder.currentLadderLevel)

        // Advance past original 20s L5 deadline (total 25s after L4)
        clock.advance(15_000L)
        val tickActions = ladder.onTick(clock.nowMs(), faceFound = true)
        assertFalse("L5 must never fire after response", ladder.isL5Active)
        assertTrue(tickActions.none { it is Action.SendSms })
        assertEquals(Level.L0, ladder.currentLadderLevel)
    }

    @Test
    fun testReclosureRestartsAtL3() {
        // Initial L3 alert
        ladder.fireL3(clock.nowMs(), "Initial closure")
        assertEquals(Level.L3, ladder.currentLadderLevel)

        // Driver responds at +2s
        clock.advance(2000L)
        ladder.processResponse(clock.nowMs(), "Tapped I'M AWAKE")
        assertEquals(Level.L0, ladder.currentLadderLevel)

        // Driver closes eyes again after driving normally for 8s
        clock.advance(8000L)
        // Eyes closed for 2.5s (re-closure confirm)
        clock.advance(2500L)
        val reclosureActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = true,
            closureDurationMs = 2500L,
            pitchDeg = 0f,
            now = clock.nowMs()
        )

        // Must restart at L3 (not L4)!
        assertEquals("Re-closure must restart at L3", Level.L3, ladder.currentLadderLevel)
        assertTrue(ladder.isL3Active)
        assertFalse(ladder.isL4Active)
        assertTrue(reclosureActions.contains(Action.ShowRedFlash))
        assertTrue(reclosureActions.any { it is Action.Speak && it.urgent })
        assertEquals(clock.nowMs() + 10_000L, ladder.l4EscalateAtMs)
    }

    @Test
    fun testL4NeverFiresWhenFaceIsLost() {
        ladder.fireL3(clock.nowMs(), "Face lost test")
        assertEquals(Level.L3, ladder.currentLadderLevel)

        // At +5s face is lost (camera covered or driver turned)
        clock.advance(5000L)
        ladder.onFaceLost(clock.nowMs())

        // Time advances to +15s (5s past L4 timeout) while face is lost
        clock.advance(10_000L)
        val frameActions = ladder.onFrame(
            faceFound = false,
            isEyesClosed = true,
            closureDurationMs = 15_000L,
            pitchDeg = 0f,
            now = clock.nowMs()
        )
        val tickActions = ladder.onTick(clock.nowMs(), faceFound = false)

        assertFalse("L4 must not fire while face is lost", ladder.isL4Active)
        assertTrue(frameActions.none { it is Action.PlayFamilyClip })
        assertTrue(tickActions.none { it is Action.PlayFamilyClip })
    }

    @Test
    fun testCooldownPreventsRepeats() {
        // Initial L3 at T = 100,000
        val firstActions = ladder.fireL3(clock.nowMs(), "First L3")
        assertTrue(firstActions.contains(Action.ShowRedFlash))

        // Without a response, closure repeats at T = 110,000 (10s later, within 30s cooldown)
        clock.advance(10_000L)
        val repeatActions1 = ladder.fireL3(clock.nowMs(), "Repeat within cooldown")
        assertTrue("Cooldown must suppress repeat actions", repeatActions1.isEmpty())

        // Still within cooldown at T = 129,000 (29s later)
        clock.advance(19_000L)
        val repeatActions2 = ladder.fireL3(clock.nowMs(), "Repeat at 29s")
        assertTrue("Cooldown must suppress repeat actions", repeatActions2.isEmpty())

        // Cooldown expires at T = 130,001 (> 30s after first L3)
        clock.advance(1001L)
        val allowedRepeatActions = ladder.fireL3(clock.nowMs(), "Closure after cooldown")
        assertTrue("L3 can fire again after 30s cooldown", allowedRepeatActions.contains(Action.ShowRedFlash))
        assertTrue(allowedRepeatActions.any { it is Action.Speak && it.urgent })
    }

    @Test
    fun testContinuousOpenEyes3s_actsAsAwakeResponse() {
        ladder.fireL3(clock.nowMs(), "Test open eyes response")
        assertTrue(ladder.isL3Active)

        // Initialize open eyes frame at start of open period
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 0f, now = clock.nowMs())

        // Driver opens eyes continuously
        clock.advance(1000L)
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 0f, now = clock.nowMs())
        assertTrue("1s open eyes is not enough", ladder.isL3Active)

        clock.advance(1000L)
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 0f, now = clock.nowMs())
        assertTrue("2s open eyes is not enough", ladder.isL3Active)

        // Reaches 3.0 s open eyes continuously (OPEN_EYES_RESPONSE_MS = 3000)
        clock.advance(1000L)
        val responseActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = false,
            closureDurationMs = 0L,
            pitchDeg = 0f,
            now = clock.nowMs()
        )

        assertFalse("3s open eyes cancels L3", ladder.isL3Active)
        assertEquals(Level.L0, ladder.currentLadderLevel)
        assertNull(ladder.l4EscalateAtMs)
        assertTrue(responseActions.any { it is Action.Log && it.detail.contains("Eyes open >= 3s") })
    }

    @Test
    fun testL3Phrases_randomPickNoImmediateRepeat() {
        // Trigger L3 repeatedly with response resets in between
        val phrasesPicked = mutableListOf<String>()

        for (i in 0 until 10) {
            val actions = ladder.fireL3(clock.nowMs(), "Test pick $i")
            val speak = actions.filterIsInstance<Action.Speak>().first()
            phrasesPicked.add(speak.text)

            // Process response so cooldown doesn't block next trigger
            clock.advance(1000L)
            ladder.processResponse(clock.nowMs(), "Reset")
            clock.advance(1000L)
        }

        // Check no two consecutive phrases are identical
        for (i in 0 until phrasesPicked.size - 1) {
            assertNotEquals(
                "Consecutive L3 phrases must not be identical: ${phrasesPicked[i]} vs ${phrasesPicked[i + 1]}",
                phrasesPicked[i],
                phrasesPicked[i + 1]
            )
        }
    }

    @Test
    fun testIndependentEyeClosure_takesPrecedenceOverHeadDroop() {
        // 1.5s head droop triggers soft prompt
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 20f, now = clock.nowMs())
        clock.advance(1500L)
        ladder.onFrame(faceFound = true, isEyesClosed = false, closureDurationMs = 0L, pitchDeg = 20f, now = clock.nowMs())
        assertNotNull(ladder.pendingHeadEscalateAtMs)

        // While pending head escalation is active, driver closes eyes for 2.5s
        clock.advance(2500L)
        val closureActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = true,
            closureDurationMs = 2500L,
            pitchDeg = 20f,
            now = clock.nowMs()
        )

        // Must directly trigger L3 from eye closure!
        assertEquals(Level.L3, ladder.currentLadderLevel)
        assertTrue(ladder.isL3Active)
        assertTrue(closureActions.contains(Action.ShowRedFlash))

        // Even if pitch recovers now, eye closure L3 is NOT cancelled
        clock.advance(1500L)
        ladder.onFrame(faceFound = true, isEyesClosed = true, closureDurationMs = 4000L, pitchDeg = 0f, now = clock.nowMs())
        assertEquals("Eye-closure L3 must not be cancelled by pitch recovery", Level.L3, ladder.currentLadderLevel)
    }
}
