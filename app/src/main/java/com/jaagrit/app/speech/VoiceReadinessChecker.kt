package com.jaagrit.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log

/**
 * Detects whether an offline Hindi voice is installed in Android TextToSpeech (AUDIT-020, D14).
 * Checks that the voice target is Hindi, does not require a network connection,
 * and does not contain the KEY_FEATURE_NOT_INSTALLED feature tag.
 */
object VoiceReadinessChecker {

    private const val TAG = "JAAGRIT"

    /**
     * Pure logic evaluation for whether voice attributes qualify as an offline Hindi voice.
     */
    fun isOfflineHindiVoice(
        language: String?,
        isNetworkConnectionRequired: Boolean,
        features: Set<String>?
    ): Boolean {
        val lang = language?.lowercase()
        val isHindi = lang == "hi" || lang == "hin"
        val isOffline = !isNetworkConnectionRequired
        val notInstalled = features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
        return isHindi && isOffline && !notInstalled
    }

    /**
     * Evaluates an Android TTS Voice instance.
     */
    fun isOfflineHindiVoice(voice: Voice): Boolean {
        return isOfflineHindiVoice(
            language = voice.locale?.language,
            isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
            features = voice.features
        )
    }

    /**
     * Checks if a collection of voices contains at least one valid offline Hindi voice.
     */
    fun hasOfflineHindiVoice(voices: Collection<Voice>?): Boolean {
        if (voices.isNullOrEmpty()) return false
        return voices.any { isOfflineHindiVoice(it) }
    }

    /**
     * Asynchronously checks whether an offline Hindi voice is currently installed on the device.
     */
    fun checkOfflineHindiAvailability(context: Context, onResult: (Boolean) -> Unit) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val available = hasOfflineHindiVoice(tts?.voices)
                        Log.i(TAG, "VoiceReadinessChecker: Offline Hindi voice detected: $available")
                        onResult(available)
                    } else {
                        Log.w(TAG, "VoiceReadinessChecker: TextToSpeech init failed with status $status")
                        onResult(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "VoiceReadinessChecker: Error inspecting voices: ${e.message}")
                    onResult(false)
                } finally {
                    try {
                        tts?.shutdown()
                    } catch (e: Exception) {
                        Log.w(TAG, "VoiceReadinessChecker: Error shutting down temporary TTS: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VoiceReadinessChecker: Failed to create TextToSpeech: ${e.message}")
            onResult(false)
        }
    }
}
