package com.jaagrit.app.speech

import android.speech.tts.TextToSpeech
import com.jaagrit.app.audio.PlanBAudioPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceReadinessAndPlanBTest {

    @Test
    fun testOfflineHindiVoiceDetectionLogic() {
        // Valid offline Hindi voice
        assertTrue(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = "hi",
                isNetworkConnectionRequired = false,
                features = emptySet()
            )
        )

        // Valid with ISO-639-2 "hin"
        assertTrue(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = "hin",
                isNetworkConnectionRequired = false,
                features = setOf("male")
            )
        )

        // Invalid: requires network connection
        assertFalse(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = "hi",
                isNetworkConnectionRequired = true,
                features = emptySet()
            )
        )

        // Invalid: not installed on device
        assertFalse(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = "hi",
                isNetworkConnectionRequired = false,
                features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            )
        )

        // Invalid: non-Hindi language
        assertFalse(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = "en",
                isNetworkConnectionRequired = false,
                features = emptySet()
            )
        )

        // Invalid: null language
        assertFalse(
            VoiceReadinessChecker.isOfflineHindiVoice(
                language = null,
                isNetworkConnectionRequired = false,
                features = emptySet()
            )
        )
    }

    @Test
    fun testPlanBAudioPlayerResourceDiscovery() {
        // Case 1: No clips exist in resources
        val noClipsPlayer = PlanBAudioPlayer(context = null, resFinder = { 0 })
        assertFalse("Should report no clips", noClipsPlayer.hasAnyClips())
        assertEquals("Res ID should be 0", 0, noClipsPlayer.getClipResId(1))
        assertFalse("Playback should return false without clips", noClipsPlayer.play(1))

        // Case 2: Only specific clip l3_3 exists
        val clip3Player = PlanBAudioPlayer(context = null, resFinder = { name ->
            if (name == "l3_3") 1003 else 0
        })
        assertTrue("Should detect available clip", clip3Player.hasAnyClips())
        assertEquals("Exact request for l3_3 returns 1003", 1003, clip3Player.getClipResId(3))
        assertEquals("Fallback for l3_1 returns first available clip 1003", 1003, clip3Player.getClipResId(1))

        // Case 3: All clips 1..6 exist
        val allClipsPlayer = PlanBAudioPlayer(context = null, resFinder = { name ->
            when (name) {
                "l3_1" -> 1001
                "l3_2" -> 1002
                "l3_3" -> 1003
                "l3_4" -> 1004
                "l3_5" -> 1005
                "l3_6" -> 1006
                else -> 0
            }
        })
        assertTrue("Should detect available clips", allClipsPlayer.hasAnyClips())
        assertEquals(1001, allClipsPlayer.getClipResId(1))
        assertEquals(1002, allClipsPlayer.getClipResId(2))
        assertEquals(1006, allClipsPlayer.getClipResId(6))
    }
}
