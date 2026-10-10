package com.jaagrit.app.camera

import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Clock
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.DriverState
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.engine.FakeClock
import com.jaagrit.app.engine.FatigueEngine
import com.jaagrit.app.engine.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying AUDIT-015: stale-frame detection (no frame for > 1s is treated as FACE_LOST and never escalates).
 */
class StaleFrameDetectionTest {

    private lateinit var clock: FakeClock
    private lateinit var engine: FatigueEngine
    private val baseline = Baseline(
        openEar = 0.28f,
        closedEar = 0.04f,
        threshold = 0.16f,
        isValid = true
    )

    @Before
    fun setup() {
        clock = FakeClock(10_000L)
        engine = FatigueEngine(
            config = Config.DEFAULT,
            clock = clock,
            baseline = baseline
        )
    }

    private fun normalFrame(ts: Long = clock.nowMs()): FaceFrame = FaceFrame(
        tsMs = ts,
        faceFound = true,
        earL = 0.28f,
        earR = 0.28f,
        mar = 0.05f,
        pitchDeg = 5f,
        yawDeg = 0f,
        rollDeg = 0f
    )

    private fun closedFrame(ts: Long = clock.nowMs()): FaceFrame = FaceFrame(
        tsMs = ts,
        faceFound = true,
        earL = 0.04f,
        earR = 0.04f,
        mar = 0.05f,
        pitchDeg = 5f,
        yawDeg = 0f,
        rollDeg = 0f
    )

    /**
     * Stale frame logic simulation matching MonitoringPipeline.processTick:
     * If now - lastFrameTimeMs > 1000L, generate synthetic face-lost frame.
     */
    private fun tickWithStaleDetection(lastFrameTimeMs: Long): Pair<DriverState, Level> {
        val now = clock.nowMs()
        val isStale = lastFrameTimeMs > 0L && (now - lastFrameTimeMs > 1000L)
        val output = if (isStale) {
            val staleFrame = FaceFrame(
                tsMs = now,
                faceFound = false,
                earL = 0f,
                earR = 0f,
                mar = 0f,
                pitchDeg = 0f,
                yawDeg = 0f,
                rollDeg = 0f
            )
            engine.onFrame(staleFrame)
        } else {
            engine.onTick()
        }
        return Pair(output.state, output.level)
    }

    @Test
    fun testStaleFrame_noFrameForMoreThan1Second_treatedAsFaceLost() {
        // Frame received at t=10_000
        engine.onFrame(normalFrame())
        val lastFrameMs = 10_000L

        // Advance 500ms (not stale yet)
        clock.advance(500L)
        val (stateUnder1s, levelUnder1s) = tickWithStaleDetection(lastFrameMs)
        assertEquals(DriverState.NORMAL, stateUnder1s)
        assertEquals(Level.L0, levelUnder1s)

        // Advance past 1000ms (1100ms total since last frame)
        clock.advance(600L) // t = 11_100ms
        val (stateOver1s, levelOver1s) = tickWithStaleDetection(lastFrameMs)
        assertEquals("No frame for > 1s must be treated as FACE_LOST", DriverState.FACE_LOST, stateOver1s)
        assertEquals(Level.L0, levelOver1s)
    }

    @Test
    fun testStaleFrame_duringAlert_neverEscalates() {
        // Trigger L3 alert with 2.5s closed eyes
        for (i in 0 until 26) {
            clock.advance(100L)
            engine.onFrame(closedFrame())
        }
        assertEquals(Level.L3, engine.ladder.currentLadderLevel)
        assertTrue(engine.ladder.isAlertActive)
        val lastFrameMs = clock.nowMs()

        // Frames suddenly stop (e.g. app backgrounded or camera occluded).
        // Advance past 1000ms: stale frame detector kicks in
        clock.advance(1100L)
        val (stateStale, levelStale) = tickWithStaleDetection(lastFrameMs)
        assertEquals(DriverState.FACE_LOST, stateStale)
        assertEquals(Level.L0, levelStale)

        // Advance another 15 seconds (past the 10s L4 deadline)
        for (i in 0 until 150) {
            clock.advance(100L)
            tickWithStaleDetection(lastFrameMs)
        }

        // Must never escalate to L4 or L5 while frames are stale/lost
        assertFalse("Must never escalate to L4 while stale", engine.ladder.isL4Active)
        assertFalse("Must never escalate to L5 while stale", engine.ladder.isL5Active)
    }
}
