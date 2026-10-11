package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EyeStateFilter] reopen hysteresis (D18).
 */
class EyeStateFilterTest {

    // threshold 0.16, gap 0.24 -> reopen level 0.16 + 0.10 * 0.24 = 0.184
    private val baseline = Baseline(openEar = 0.28f, closedEar = 0.04f, threshold = 0.16f)

    private fun newFilter() = EyeStateFilter(Config.DEFAULT, baseline)

    @Test
    fun closesStrictlyBelowThreshold() {
        val filter = newFilter()
        assertFalse(filter.update(0.16f))
        assertTrue(filter.update(0.159f))
    }

    @Test
    fun staysClosedUntilAboveReopenLevel() {
        val filter = newFilter()
        filter.update(0.05f)
        assertTrue("just above threshold is still closed", filter.update(0.17f))
        assertTrue("just under the reopen level is still closed", filter.update(0.183f))
        assertFalse("above the reopen level opens", filter.update(0.19f))
    }

    @Test
    fun earJitterAroundThreshold_doesNotFlicker() {
        val filter = newFilter()
        filter.update(0.15f)
        var toggles = 0
        var last = filter.isClosed
        repeat(100) { i ->
            val closed = filter.update(if (i % 2 == 0) 0.17f else 0.15f)
            if (closed != last) toggles++
            last = closed
        }
        assertEquals("±0.01 jitter around the threshold must not toggle", 0, toggles)
    }

    @Test
    fun resetReturnsToOpen() {
        val filter = newFilter()
        filter.update(0.05f)
        filter.reset()
        assertFalse(filter.isClosed)
        assertFalse(filter.update(0.17f))
    }
}
