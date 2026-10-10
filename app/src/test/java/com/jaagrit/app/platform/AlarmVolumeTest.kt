package com.jaagrit.app.platform

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for AUDIT-014: set STREAM_ALARM to max while the alarm plays and restore the previous volume afterwards.
 */
class AlarmVolumeTest {

    private class FakeVolumeManager(
        var currentVol: Int = 4,
        val maxVol: Int = 15
    ) : StreamVolumeManager {
        val volumeHistory = mutableListOf<Int>()

        override fun getVolume(streamType: Int): Int = currentVol
        override fun getMaxVolume(streamType: Int): Int = maxVol
        override fun setVolume(streamType: Int, index: Int, flags: Int) {
            currentVol = index
            volumeHistory.add(index)
        }
    }

    @Test
    fun testAlarmSetsMaxVolumeAndRestoresOnStop() {
        val fakeVm = FakeVolumeManager(currentVol = 4, maxVol = 15)
        val generator = AlarmToneGenerator(context = null, volumeManager = fakeVm, audioTrackFactory = { null })

        // Starting alarm sets STREAM_ALARM to max
        generator.startAlarm()
        assertEquals(15, fakeVm.currentVol)
        assertEquals(listOf(15), fakeVm.volumeHistory)

        // Stopping alarm restores previous volume
        generator.stopAlarm()
        assertEquals(4, fakeVm.currentVol)
        assertEquals(listOf(15, 4), fakeVm.volumeHistory)
    }

    @Test
    fun testSubsequentStartAlarmDoesNotOverwriteSavedVolume() {
        val fakeVm = FakeVolumeManager(currentVol = 3, maxVol = 15)
        val generator = AlarmToneGenerator(context = null, volumeManager = fakeVm, audioTrackFactory = { null })

        generator.startAlarm()
        assertEquals(15, fakeVm.currentVol)

        // Calling start again while playing is a no-op
        generator.startAlarm()
        assertEquals(1, fakeVm.volumeHistory.size)

        generator.stopAlarm()
        assertEquals(3, fakeVm.currentVol)
    }

    @Test
    fun testReleaseRestoresVolumeIfPlaying() {
        val fakeVm = FakeVolumeManager(currentVol = 5, maxVol = 15)
        val generator = AlarmToneGenerator(context = null, volumeManager = fakeVm, audioTrackFactory = { null })

        generator.startAlarm()
        assertEquals(15, fakeVm.currentVol)

        generator.release()
        assertEquals(5, fakeVm.currentVol)
    }

    @Test
    fun testStartAlarmAfterReleaseIsNoOp() {
        val fakeVm = FakeVolumeManager(currentVol = 4, maxVol = 15)
        val generator = AlarmToneGenerator(context = null, volumeManager = fakeVm, audioTrackFactory = { null })

        generator.release()
        org.junit.Assert.assertTrue(generator.isReleasedState)

        // Calling startAlarm after release must be a no-op (AUDIT-016)
        generator.startAlarm()
        assertEquals(4, fakeVm.currentVol)
        assertEquals(0, fakeVm.volumeHistory.size)
    }
}
