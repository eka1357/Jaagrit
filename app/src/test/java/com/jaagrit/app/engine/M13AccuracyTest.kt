package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M13 accuracy pass (D18): reopen confirmation, relative head pitch, yawn L1 signal,
 * and the calibration values that feed them.
 */
class M13AccuracyTest {

    private val baseline = Baseline(
        openEar = 0.28f,
        closedEar = 0.04f,
        threshold = 0.16f,
        mar = 0.02f,
        blinkRate = 16.0f,
        responseLatencyMs = 1500L,
        calibratedAtMs = 1L,
        isValid = true
    )

    private val stepMs = 40L // ~25 FPS, the measured iQOO 15 rate

    private fun newEngine(clock: FakeClock, b: Baseline = baseline) =
        FatigueEngine(config = Config.DEFAULT, clock = clock, baseline = b).also { it.resetDrive(clock.nowMs()) }

    private fun frame(clock: FakeClock, ear: Float = 0.28f, mar: Float = 0.02f, pitch: Float = 0f) = FaceFrame(
        tsMs = clock.nowMs(),
        faceFound = true,
        earL = ear,
        earR = ear,
        mar = mar,
        pitchDeg = pitch,
        yawDeg = 0f,
        rollDeg = 0f
    )

    private fun firesL3(output: EngineOutput) = output.actions.any { it is Action.ShowRedFlash }

    private fun feed(engine: FatigueEngine, clock: FakeClock, durationMs: Long, ear: Float = 0.28f, mar: Float = 0.02f, pitch: Float = 0f): List<EngineOutput> {
        val outputs = mutableListOf<EngineOutput>()
        var elapsed = 0L
        while (elapsed < durationMs) {
            clock.advance(stepMs)
            elapsed += stepMs
            outputs.add(engine.onFrame(frame(clock, ear, mar, pitch)))
        }
        return outputs
    }

    // --- Reopen confirmation ---

    @Test
    fun singleOpenSpikeFrame_doesNotResetMicrosleep() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L)

        val closedStart = clock.nowMs() + stepMs
        val beforeSpike = feed(engine, clock, 1400L, ear = 0.05f)
        assertFalse(beforeSpike.any(::firesL3))

        // One noisy frame reads wide open in the middle of a microsleep
        clock.advance(stepMs)
        assertFalse(firesL3(engine.onFrame(frame(clock, ear = 0.28f))))

        var firedAt: Long? = null
        while (firedAt == null && clock.nowMs() - closedStart < 4000L) {
            clock.advance(stepMs)
            if (firesL3(engine.onFrame(frame(clock, ear = 0.05f)))) firedAt = clock.nowMs() - closedStart
        }
        assertNotNull("one open frame must not reset a 2.5 s closure", firedAt)
        assertTrue(firedAt!! in 2500L..(2500L + stepMs))
    }

    @Test
    fun confirmedReopen_restartsClosureTimer() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L)
        feed(engine, clock, 1400L, ear = 0.05f)
        feed(engine, clock, 200L, ear = 0.28f) // real reopen (> 100 ms)
        val second = feed(engine, clock, 2000L, ear = 0.05f)
        assertFalse("closure restarted after a confirmed reopen", second.any(::firesL3))
    }

    @Test
    fun blink_countedOnceWithDurationPreserved() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L)
        feed(engine, clock, 200L, ear = 0.05f) // 5-frame blink
        val after = feed(engine, clock, 400L)
        // One blink over the 0.2 min minimum rate window = 5 blinks/min
        assertEquals(5.0, after.last().metrics!!.blinkRatePerMin, 0.001)
    }

    @Test
    fun reopenWithJitterNearThreshold_countsOneBlink() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L)
        feed(engine, clock, 160L, ear = 0.05f)
        // Eyelid lifting: EAR wobbles across the 0.16 threshold before settling open.
        // Without hysteresis each 2-frame dip would count as its own 80 ms "blink".
        listOf(0.17f, 0.15f, 0.15f, 0.17f, 0.15f, 0.15f).forEach {
            clock.advance(stepMs); engine.onFrame(frame(clock, ear = it))
        }
        val after = feed(engine, clock, 400L)
        assertEquals(5.0, after.last().metrics!!.blinkRatePerMin, 0.001)
    }

    // --- Relative head pitch ---

    @Test
    fun headDroop_isMeasuredFromCalibratedNeutral() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock, baseline.copy(neutralPitchDeg = 8f))

        // 20° absolute = 12° relative: under the 15° droop threshold
        val looking = feed(engine, clock, 3000L, pitch = 20f)
        assertFalse(looking.any { out -> out.reasons.any { it.contains("droop", ignoreCase = true) } })
        assertFalse(looking.any { out -> out.actions.any { it is Action.Log && it.event == AlertEventType.HEAD_DROOP } })
        assertEquals(12f, looking.last().metrics!!.relPitchDeg, 0.001f)

        // 25° absolute = 17° relative: a real nod
        val nodding = feed(engine, clock, 2000L, pitch = 25f)
        assertTrue(nodding.any { out -> out.reasons.any { it.contains("droop", ignoreCase = true) } })
        assertTrue(nodding.any { out -> out.actions.any { it is Action.Log && it.event == AlertEventType.HEAD_DROOP } })
    }

    // --- Yawn signal ---

    @Test
    fun heldWideMouth_countsOneYawnWithReason() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L)
        feed(engine, clock, 2500L, mar = 0.55f)
        val out = feed(engine, clock, 500L).last()
        assertEquals(1, out.metrics!!.yawnCount)
        assertTrue(out.reasons.any { it == "Yawning (1 in 5 min)" })
        assertEquals(Level.L0, out.level)
    }

    @Test
    fun twoYawns_raiseL1Caution_thenAgeOutAfterWindow() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        repeat(2) {
            feed(engine, clock, 1000L)
            feed(engine, clock, 2000L, mar = 0.55f)
        }
        val out = feed(engine, clock, 500L).last()
        assertEquals(2, out.metrics!!.yawnCount)
        assertEquals(Level.L1, out.level)
        assertEquals(DriverState.CAUTION, out.state)

        clock.advance(Config.YAWN_WINDOW_MS)
        val later = feed(engine, clock, 500L).last()
        assertEquals(0, later.metrics!!.yawnCount)
        assertEquals(Level.L0, later.level)
    }

    @Test
    fun shortMouthOpening_isNotAYawn() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        feed(engine, clock, 1000L, mar = 0.55f) // 1 s < 1.5 s
        val out = feed(engine, clock, 500L).last()
        assertEquals(0, out.metrics!!.yawnCount)
    }

    @Test
    fun loudFastTalking_isNotAYawn() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        var out = EngineOutput.INITIAL
        // ~4 Hz syllables: 120 ms wide open, 120 ms closed, for 30 s
        repeat(750) { i ->
            clock.advance(stepMs)
            out = engine.onFrame(frame(clock, mar = if ((i / 3) % 2 == 0) 0.45f else 0.10f))
        }
        assertEquals(0, out.metrics!!.yawnCount)
    }

    @Test
    fun talkingLevelMar_neverReachesYawnThreshold() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        val out = feed(engine, clock, 5000L, mar = 0.28f).last() // held, but under the 0.30 floor
        assertEquals(0, out.metrics!!.yawnCount)
    }

    // --- Calibration ---

    private fun calFrame(tsMs: Long, ear: Float, mar: Float = 0.02f, pitch: Float = 0f) =
        FaceFrame(tsMs = tsMs, faceFound = true, earL = ear, earR = ear, mar = mar, pitchDeg = pitch, yawDeg = 0f, rollDeg = 0f)

    private fun frames(startMs: Long, count: Int, gen: (Int) -> FaceFrame) =
        (0 until count).map { gen(it) }.let { list -> list.mapIndexed { i, f -> f.copy(tsMs = startMs + i * stepMs) } }

    @Test
    fun calibration_neutralPitchIsMedianOfOpenPhase() {
        val open = frames(0L, 250) { i -> calFrame(0L, 0.28f, pitch = if (i % 10 == 0) 30f else 8f) }
        val closed = frames(10_000L, 75) { calFrame(0L, 0.04f) }
        val b = BaselineCalculator.computeFromFrames(open, 0L, closed, 10_000L)
        assertEquals(8f, b.neutralPitchDeg, 0.001f)
    }

    @Test
    fun calibration_yawnMarIsTopQuartileMedian() {
        val open = frames(0L, 250) { calFrame(0L, 0.28f) }
        val closed = frames(10_000L, 75) { calFrame(0L, 0.04f) }
        // 5 s yawn phase: mostly closed mouth, one ~1.2 s yawn peaking at 0.65
        val yawn = frames(13_000L, 125) { i -> calFrame(0L, 0.28f, mar = if (i in 60..90) 0.65f else 0.03f) }
        val b = BaselineCalculator.computeFromFrames(open, 0L, closed, 10_000L, yawn, 13_000L)
        assertEquals(0.65f, b.yawnMar, 0.001f)
    }

    @Test
    fun calibration_yawnMarFallsBackWhenSkippedOrNoRealYawn() {
        val open = frames(0L, 250) { calFrame(0L, 0.28f) }
        val closed = frames(10_000L, 75) { calFrame(0L, 0.04f) }
        val skipped = BaselineCalculator.computeFromFrames(open, 0L, closed, 10_000L)
        assertEquals(Config.DEFAULT_BASELINE_YAWN_MAR, skipped.yawnMar, 0.001f)

        val weakYawn = frames(13_000L, 125) { calFrame(0L, 0.28f, mar = 0.10f) } // only 0.08 above neutral
        val weak = BaselineCalculator.computeFromFrames(open, 0L, closed, 10_000L, weakYawn, 13_000L)
        assertEquals(Config.DEFAULT_BASELINE_YAWN_MAR, weak.yawnMar, 0.001f)
    }

    @Test
    fun calibration_reactionTimeFramesAreIgnored() {
        val open = frames(0L, 250) { calFrame(0L, 0.28f) }
        // Driver still has eyes open for the first second of the close-eyes phase
        val closed = frames(10_000L, 75) { i -> calFrame(0L, if (i < 25) 0.28f else 0.04f) }
        val b = BaselineCalculator.computeFromFrames(open, 0L, closed, 10_000L)
        assertEquals(0.04f, b.closedEar, 0.001f)
    }
}
