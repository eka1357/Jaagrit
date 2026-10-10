package com.jaagrit.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Plan B audio player for Level 3 alerts (D14, AUDIT-020).
 * If res/raw/l3_1..l3_6 clips exist, plays one on USAGE_ALARM instead of TTS.
 * Falls back to TTS if clips are missing or playback fails.
 */
class PlanBAudioPlayer(
    private val context: Context? = null,
    private val resFinder: (name: String) -> Int = { name ->
        try {
            context?.resources?.getIdentifier(name, "raw", context.packageName) ?: 0
        } catch (e: Exception) {
            0
        }
    }
) {
    private val tag = "JAAGRIT"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Checks if any Plan B developer clip (l3_1..l3_6) exists in resources.
     */
    fun hasAnyClips(): Boolean {
        return (1..6).any { resFinder("l3_$it") != 0 }
    }

    /**
     * Resolves resource ID for a given phrase index (1..6), falling back to any available l3 clip.
     */
    fun getClipResId(preferredIndex: Int = 1): Int {
        val specific = resFinder("l3_$preferredIndex")
        if (specific != 0) return specific
        for (i in 1..6) {
            val res = resFinder("l3_$i")
            if (res != 0) return res
        }
        return 0
    }

    /**
     * Plays an L3 clip on USAGE_ALARM. Returns true if playback successfully started, false otherwise.
     */
    fun play(preferredIndex: Int = 1, onCompletion: () -> Unit = {}): Boolean {
        stop()
        val resId = getClipResId(preferredIndex)
        if (resId == 0) {
            Log.d(tag, "PlanBAudioPlayer: No L3 audio clip found (resId=0)")
            return false
        }

        val ctx = context ?: run {
            Log.d(tag, "PlanBAudioPlayer: Null context, skipping physical MediaPlayer")
            return false
        }

        return try {
            val player = MediaPlayer.create(ctx, resId) ?: run {
                Log.w(tag, "PlanBAudioPlayer: MediaPlayer.create returned null for resId=$resId")
                return false
            }

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setOnCompletionListener {
                stop()
                onCompletion()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(tag, "PlanBAudioPlayer: playback error ($what, $extra)")
                stop()
                true
            }
            player.start()
            mediaPlayer = player
            Log.i(tag, "PlanBAudioPlayer: Successfully playing L3 audio clip (resId=$resId)")
            true
        } catch (e: Exception) {
            Log.w(tag, "PlanBAudioPlayer: Failed to play clip: ${e.message}")
            stop()
            false
        }
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
            Log.e(tag, "PlanBAudioPlayer: Error stopping: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stop()
    }
}
