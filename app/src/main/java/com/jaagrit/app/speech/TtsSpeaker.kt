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
    private var isInitialized = false
    private var isHindiAvailable = false

    // Pending speech item if called before TTS finishes initializing
    private data class PendingUtterance(val text: String, val lang: Lang, val urgent: Boolean)
    private var pendingUtterance: PendingUtterance? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val hindiLocale = Locale("hi", "IN")
            val langResult = tts?.isLanguageAvailable(hindiLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED

            isHindiAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED

            val activeLocale = if (isHindiAvailable) hindiLocale else Locale.US
            tts?.language = activeLocale

            // Configure audio attributes for alarm / guidance stream
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            isInitialized = true
            Log.d(tag, "TTS initialized. Hindi available: $isHindiAvailable, Active: ${activeLocale.language}")

            // Speak pending utterance if one was requested during initialization
            pendingUtterance?.let {
                speak(it.text, it.lang, it.urgent)
                pendingUtterance = null
            }
        } else {
            Log.e(tag, "TTS initialization failed with status $status")
        }
    }

    /**
     * Speak text with rate and pitch tuned for urgency.
     */
    fun speak(text: String, lang: Lang = Lang.HI, urgent: Boolean = false) {
        if (!isInitialized) {
            pendingUtterance = PendingUtterance(text, lang, urgent)
            return
        }

        val targetLocale = if (lang == Lang.HI && isHindiAvailable) {
            Locale("hi", "IN")
        } else {
            Locale.US
        }
        tts?.language = targetLocale

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
