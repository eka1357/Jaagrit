package com.jaagrit.app.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Android [SpeechRecognizer] implementation of [SpeechInput] (M8, VOI-3).
 * Prefers on-device speech recognition via [SpeechRecognizer.createOnDeviceSpeechRecognizer].
 * Enforces a 6-second timeout and clean resource disposal.
 */
class RecognizerInput(
    private val context: Context
) : SpeechInput {

    private val tag = "JAAGRIT"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var timeoutRunnable: Runnable? = null

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun isOnDeviceAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    }

    override fun startListening(
        languageCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            try {
                cancelInternal()

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(tag, "SpeechRecognizer: RECORD_AUDIO permission not granted")
                    onError("RECORD_AUDIO permission not granted")
                    return@post
                }

                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    Log.i(tag, "SpeechRecognizer: Using on-device speech recognizer")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.i(tag, "SpeechRecognizer: Using standard speech recognizer with EXTRA_PREFER_OFFLINE")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer = recognizer

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "SpeechRecognizer ready for speech in $languageCode")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "SpeechRecognizer beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "SpeechRecognizer end of speech")
                    }

                    override fun onError(error: Int) {
                        clearTimeout()
                        isListening = false
                        val errorMsg = mapErrorCode(error)
                        Log.w(tag, "SpeechRecognizer error: $error ($errorMsg)")
                        onError(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        clearTimeout()
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull().orEmpty()
                        Log.i(tag, "SpeechRecognizer results: '$spokenText' (candidates: $matches)")
                        onResult(spokenText)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                isListening = true
                recognizer.startListening(recognizerIntent)

                // 6-second timeout: cancel if no response within 6s
                timeoutRunnable = Runnable {
                    if (isListening) {
                        Log.i(tag, "SpeechRecognizer: 6s listening timeout reached")
                        stopListening()
                        onError("Timeout")
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, 6000L)

            } catch (e: Exception) {
                Log.e(tag, "Failed to start SpeechRecognizer", e)
                isListening = false
                onError(e.message ?: "Failed to start recognition")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            clearTimeout()
            if (isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w(tag, "Error stopping SpeechRecognizer: ${e.message}")
                }
                isListening = false
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            cancelInternal()
        }
    }

    private fun cancelInternal() {
        clearTimeout()
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    override fun destroy() {
        mainHandler.post {
            cancelInternal()
        }
    }

    private fun clearTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Speech recognition error code: $error"
        }
    }
}
