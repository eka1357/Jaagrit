package com.jaagrit.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.jaagrit.app.engine.Lang
import java.util.Locale

/**
 * Text-to-speech engine wrapper using Android TextToSpeech (VOI-1, D14).
 * Prefers Hindi offline voice with English fallback.
 * Urgent alerts (L3) use elevated pitch and speech rate.
 */
class TtsSpeaker(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "JAAGRIT"
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set
    var isHindiAvailable = false
        private set
    var isOfflineHindiAvailable = false
        private set

    // Queue of pending speech items if called before TTS finishes initializing
    private data class PendingUtterance(val text: String, val lang: Lang, val urgent: Boolean)
    private val pendingUtterances = mutableListOf<PendingUtterance>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val offlineHindiVoice = try {
                tts?.voices?.firstOrNull { voice ->
                    VoiceReadinessChecker.isOfflineHindiVoice(voice)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to query TTS voices: ${e.message}")
                null
            }

            if (offlineHindiVoice != null) {
                try {
                    tts?.voice = offlineHindiVoice
                } catch (e: Exception) {
                    Log.w(tag, "Failed to assign offline voice: ${e.message}")
                }
                isHindiAvailable = true
                isOfflineHindiAvailable = true
                Log.i(tag, "TTS initialized with offline Hindi voice: ${offlineHindiVoice.name}")
            } else {
                val hindiLocale = Locale.forLanguageTag("hi-IN")
                val langResult = try {
                    tts?.isLanguageAvailable(hindiLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                } catch (e: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                isHindiAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                isOfflineHindiAvailable = false
                Log.w(tag, "TTS initialized without offline Hindi voice. General Hindi available: $isHindiAvailable")
            }

            val activeLocale = if (isHindiAvailable) Locale.forLanguageTag("hi-IN") else Locale.US
            tts?.language = activeLocale

            // Configure default audio attributes for guidance stream
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            isInitialized = true
            Log.d(tag, "TTS initialized. Offline Hindi: $isOfflineHindiAvailable, General Hindi: $isHindiAvailable")

            // Speak all pending utterances that were queued during initialization
            val queued = ArrayList(pendingUtterances)
            pendingUtterances.clear()
            for (utterance in queued) {
                speak(utterance.text, utterance.lang, utterance.urgent)
            }
        } else {
            Log.e(tag, "TTS initialization failed with status $status")
        }
    }

    /**
     * Speak text with rate and pitch tuned for urgency.
     * Urgent L3 speech uses AudioAttributes.USAGE_ALARM so it is audible over the siren (AUDIT-014).
     */
    fun speak(text: String, lang: Lang = Lang.HI, urgent: Boolean = false) {
        if (!isInitialized) {
            pendingUtterances.add(PendingUtterance(text, lang, urgent))
            return
        }

        val targetLocale = if (lang == Lang.HI && isHindiAvailable) {
            Locale.forLanguageTag("hi-IN")
        } else {
            Locale.US
        }
        tts?.language = targetLocale

        // Urgent speech routes to USAGE_ALARM stream, normal speech to USAGE_ASSISTANCE_NAVIGATION_GUIDANCE (AUDIT-014)
        val audioUsage = if (urgent) {
            AudioAttributes.USAGE_ALARM
        } else {
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts?.setAudioAttributes(audioAttributes)

        if (urgent) {
            // Urgent L3 warnings: higher rate and pitch for rapid alertness (VOI-1)
            tts?.setPitch(1.2f)
            tts?.setSpeechRate(1.25f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "urgent_alert_${System.currentTimeMillis()}")
        } else {
            // Normal prompts (e.g. head droop soft check)
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "prompt_${System.currentTimeMillis()}")
        }
    }

    /** Stop any current speech */
    fun stop() {
        tts?.stop()
    }

    /** Release TTS resources */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
