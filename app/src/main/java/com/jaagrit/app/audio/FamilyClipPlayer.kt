package com.jaagrit.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.jaagrit.app.speech.Phrases

/**
 * Audio player for L4 family voice clips with automatic TTS fallback (LAD-3, D8).
 * Pure offline audio playback via Android MediaPlayer on res/raw clips.
 */
class FamilyClipPlayer(
    private val context: Context,
    private val onFallbackTts: (phrase: String) -> Unit
) {
    private val tag = "JAAGRIT"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Play pre-recorded family audio clip (res/raw/family_{index}).
     * If the clip is missing, corrupt, or fails to play, speaks PHRASES.md Section 2 #1 via TTS (D8).
     */
    fun play(index: Int = 1) {
        stop()
        try {
            val resName = "family_$index"
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId == 0) {
                Log.w(tag, "Family clip resource $resName not found, falling back to TTS (D8)")
                fallbackToTts()
                return
            }

            val player = MediaPlayer.create(context, resId)
            if (player == null) {
                Log.w(tag, "Failed to initialize MediaPlayer for $resName, falling back to TTS (D8)")
                fallbackToTts()
                return
            }

            mediaPlayer = player.apply {
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer playback error ($what, $extra), falling back to TTS (D8)")
                    stop()
                    fallbackToTts()
                    true
                }
                start()
            }
            Log.i(tag, "FamilyClipPlayer: Successfully started playback for $resName")
        } catch (e: Exception) {
            Log.e(tag, "Exception during family clip playback: ${e.message}", e)
            stop()
            fallbackToTts()
        }
    }

    private fun fallbackToTts() {
        Log.i(tag, "FamilyClipPlayer: Triggering TTS fallback: ${Phrases.L4_FALLBACK}")
        onFallbackTts(Phrases.L4_FALLBACK)
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stop()
    }
}
