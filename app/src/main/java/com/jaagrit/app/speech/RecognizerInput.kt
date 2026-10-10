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
 * Standard Android SpeechRecognizer error codes and name mappings.
 */
object SpeechErrorCodes {
    const val ERROR_NETWORK_TIMEOUT = 1
    const val ERROR_NETWORK = 2
    const val ERROR_AUDIO = 3
    const val ERROR_SERVER = 4
    const val ERROR_CLIENT = 5
    const val ERROR_SPEECH_TIMEOUT = 6
    const val ERROR_NO_MATCH = 7
    const val ERROR_RECOGNIZER_BUSY = 8
    const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    const val ERROR_TOO_MANY_REQUESTS = 10
    const val ERROR_SERVER_DISCONNECTED = 11
    const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    const val ERROR_LANGUAGE_UNAVAILABLE = 13
    const val ERROR_CANNOT_CHECK_SUPPORT = 14
    const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD = 15

    fun codeToName(code: Int): String {
        return when (code) {
            ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            ERROR_NETWORK -> "ERROR_NETWORK"
            ERROR_AUDIO -> "ERROR_AUDIO"
            ERROR_SERVER -> "ERROR_SERVER"
            ERROR_CLIENT -> "ERROR_CLIENT"
            ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
            ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
            ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
            ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
            ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
            ERROR_CANNOT_LISTEN_TO_DOWNLOAD -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD"
            else -> "UNKNOWN_ERROR_$code"
        }
    }
}

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
        startListeningInternal(languageCode, onResult, onError, retried = false)
    }

    private fun startListeningInternal(
        languageCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        retried: Boolean
    ) {
        mainHandler.post {
            try {
                cancelInternal()

                val isAvail = isAvailable()
                val isOnDevice = isOnDeviceAvailable()
                val permState = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                // Log SpeechRecognizer state per requirements
                Log.i(tag, "SpeechRecognizer.isRecognitionAvailable=$isAvail, isOnDeviceRecognitionAvailable=$isOnDevice, permissionGranted=$permState, targetLanguage=$languageCode, retried=$retried")

                if (!permState) {
                    Log.w(tag, "SpeechRecognizer: RECORD_AUDIO permission missing")
                    onError("PERM_MISSING")
                    return@post
                }

                if (!isAvail) {
                    Log.w(tag, "SpeechRecognizer: Speech recognition service unavailable on device")
                    onError("NO_ON_DEVICE_RECOGNIZER")
                    return@post
                }

                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isOnDevice) {
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
                        val codeName = SpeechErrorCodes.codeToName(error)
                        Log.w(tag, "SpeechRecognizer onError: code=$error ($codeName)")

                        // If English dialect (e.g. en-IN) failed with language unavailable, retry once with en-US
                        if ((error == SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechErrorCodes.ERROR_LANGUAGE_NOT_SUPPORTED) &&
                            languageCode.startsWith("en", ignoreCase = true) &&
                            !languageCode.equals("en-US", ignoreCase = true) &&
                            !retried
                        ) {
                            Log.i(tag, "SpeechRecognizer: Language $languageCode unavailable, falling back to installed en-US")
                            startListeningInternal("en-US", onResult, onError, retried = true)
                            return
                        }

                        cancelInternal()

                        val errorToken = when (error) {
                            SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechErrorCodes.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANG_PACK_MISSING"
                            SpeechErrorCodes.ERROR_INSUFFICIENT_PERMISSIONS -> "PERM_MISSING"
                            SpeechErrorCodes.ERROR_RECOGNIZER_BUSY -> "BUSY"
                            SpeechErrorCodes.ERROR_SPEECH_TIMEOUT,
                            SpeechErrorCodes.ERROR_NO_MATCH -> "NO_SPEECH"
                            else -> "CODE:$error:$codeName"
                        }
                        onError(errorToken)
                    }

                    override fun onResults(results: Bundle?) {
                        clearTimeout()
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull().orEmpty()
                        Log.i(tag, "SpeechRecognizer results: '$spokenText' (candidates: $matches)")
                        cancelInternal()
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
                        onError("NO_SPEECH")
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, 6000L)

            } catch (e: Exception) {
                Log.e(tag, "Failed to start SpeechRecognizer", e)
                cancelInternal()
                onError("CODE:5:ERROR_CLIENT")
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
}
