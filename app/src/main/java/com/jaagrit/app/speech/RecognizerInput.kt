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
 * Interface wrapping Android platform SpeechRecognizer interactions,
 * enabling deterministic unit testing of the retry and lifecycle logic.
 */
interface PlatformSpeechRecognizer {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(recognizerIntent: Intent, targetLanguage: String = "")
    fun stopListening()
    fun cancel()
    fun destroy()
}

/**
 * Factory for creating [PlatformSpeechRecognizer] instances.
 */
interface SpeechRecognizerFactory {
    fun isRecognitionAvailable(): Boolean
    fun isOnDeviceRecognitionAvailable(): Boolean
    fun createOnDeviceRecognizer(): PlatformSpeechRecognizer
}

/**
 * Abstraction for scheduling delayed actions on a thread/looper.
 */
interface DelayedScheduler {
    fun post(action: () -> Unit)
    fun postDelayed(delayMillis: Long, action: () -> Unit): Any
    fun cancel(token: Any)
}

/**
 * Android handler implementation of [DelayedScheduler].
 */
class HandlerDelayedScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : DelayedScheduler {
    override fun post(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            action()
        } else {
            handler.post(action)
        }
    }

    override fun postDelayed(delayMillis: Long, action: () -> Unit): Any {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return runnable
    }

    override fun cancel(token: Any) {
        if (token is Runnable) {
            handler.removeCallbacks(token)
        }
    }
}

/**
 * Android production implementation of [SpeechRecognizerFactory].
 */
class AndroidSpeechRecognizerFactory(
    private val context: Context
) : SpeechRecognizerFactory {
    override fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun isOnDeviceRecognitionAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

    override fun createOnDeviceRecognizer(): PlatformSpeechRecognizer {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        return object : PlatformSpeechRecognizer {
            override fun setRecognitionListener(listener: RecognitionListener) {
                recognizer.setRecognitionListener(listener)
            }
            override fun startListening(recognizerIntent: Intent, targetLanguage: String) {
                recognizer.startListening(recognizerIntent)
            }
            override fun stopListening() {
                recognizer.stopListening()
            }
            override fun cancel() {
                recognizer.cancel()
            }
            override fun destroy() {
                recognizer.destroy()
            }
        }
    }
}

/**
 * Android on-device [SpeechRecognizer] implementation of [SpeechInput] (M8, VOI-3).
 * Strictly offline: uses [SpeechRecognizer.createOnDeviceSpeechRecognizer] and NEVER falls back
 * to cloud speech recognition.
 *
 * Implements hardened lifecycle and retry mechanics:
 * - One fresh recognizer instance per attempt.
 * - All calls executed on the main thread.
 * - Previous instance destroyed fully with ~300ms pause before next instance creation.
 * - At most one retry per tap (en-IN -> en-US on error 13/12; delayed retry on error 11).
 * - Never loops.
 * - 6-second timeout per attempt.
 */
class RecognizerInput(
    private val context: Context? = null,
    private val factory: SpeechRecognizerFactory = AndroidSpeechRecognizerFactory(context!!),
    private val scheduler: DelayedScheduler = HandlerDelayedScheduler(),
    private val permissionChecker: (String) -> Boolean = { perm ->
        if (context != null) {
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    },
    private val resultsExtractor: (Bundle?) -> String = { bundle ->
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }
) : SpeechInput {

    private val tag = "JAAGRIT"
    private var activeSessionId = 0
    private var currentRecognizer: PlatformSpeechRecognizer? = null
    private var isListening = false
    private var timeoutToken: Any? = null
    private var retryToken: Any? = null

    override fun isAvailable(): Boolean {
        return factory.isRecognitionAvailable()
    }

    override fun isOnDeviceAvailable(): Boolean {
        return factory.isOnDeviceRecognitionAvailable()
    }

    override fun startListening(
        languageCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scheduler.post {
            activeSessionId++
            val sessionId = activeSessionId
            Log.i(tag, "[RecognizerInput] startListening invoked: lang=$languageCode, sessionId=$sessionId, thread=${Thread.currentThread().name}")

            // Cleanly destroy any prior active instance before starting session
            destroyCurrentRecognizer("Start new listening session #$sessionId")

            launchAttempt(
                targetLang = languageCode,
                attempt = 1,
                onResult = onResult,
                onError = onError,
                sessionId = sessionId
            )
        }
    }

    private fun launchAttempt(
        targetLang: String,
        attempt: Int,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        sessionId: Int
    ) {
        if (sessionId != activeSessionId) {
            Log.w(tag, "[RecognizerInput] Abandoning attempt=$attempt for stale sessionId=$sessionId (active=$activeSessionId)")
            return
        }

        val isAvail = factory.isRecognitionAvailable()
        val isOnDevice = factory.isOnDeviceRecognitionAvailable()
        val permGranted = permissionChecker(Manifest.permission.RECORD_AUDIO)

        Log.i(
            tag,
            "[RecognizerInput] Attempt $attempt check: isRecognitionAvailable=$isAvail, isOnDeviceRecognitionAvailable=$isOnDevice, permissionGranted=$permGranted, lang=$targetLang, thread=${Thread.currentThread().name}"
        )

        if (!permGranted) {
            Log.w(tag, "[RecognizerInput] Microphone permission missing (RECORD_AUDIO)")
            onError("PERM_MISSING")
            return
        }

        // Hard rule: Offline on-device recognition only. Never fall back to cloud recognizer.
        if (!isAvail || !isOnDevice) {
            Log.w(tag, "[RecognizerInput] Offline on-device speech recognition unavailable (avail=$isAvail, onDevice=$isOnDevice)")
            onError("NO_ON_DEVICE_RECOGNIZER")
            return
        }

        try {
            Log.i(
                tag,
                "[RecognizerInput] Creating fresh recognizer (type=ON_DEVICE, language=$targetLang, attempt=$attempt, session=$sessionId, thread=${Thread.currentThread().name})"
            )
            val recognizer = factory.createOnDeviceRecognizer()
            currentRecognizer = recognizer

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLang)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLang)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (sessionId != activeSessionId) return
                    Log.d(tag, "[RecognizerInput] onReadyForSpeech: lang=$targetLang, attempt=$attempt, thread=${Thread.currentThread().name}")
                }

                override fun onBeginningOfSpeech() {
                    if (sessionId != activeSessionId) return
                    Log.d(tag, "[RecognizerInput] onBeginningOfSpeech: thread=${Thread.currentThread().name}")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (sessionId != activeSessionId) return
                    Log.d(tag, "[RecognizerInput] onEndOfSpeech: thread=${Thread.currentThread().name}")
                }

                override fun onError(error: Int) {
                    if (sessionId != activeSessionId) {
                        Log.d(tag, "[RecognizerInput] Ignoring onError from superseded session $sessionId (active=$activeSessionId, error=$error)")
                        return
                    }

                    clearTimeout()
                    isListening = false
                    val codeName = SpeechErrorCodes.codeToName(error)
                    Log.w(
                        tag,
                        "[RecognizerInput] onError: code=$error ($codeName), lang=$targetLang, attempt=$attempt, thread=${Thread.currentThread().name}"
                    )

                    val canRetry = (attempt == 1)
                    val isLangUnavailable = (error == SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechErrorCodes.ERROR_LANGUAGE_NOT_SUPPORTED)
                    val isEnglishRetry = isLangUnavailable && targetLang.startsWith("en", ignoreCase = true) && !targetLang.equals("en-US", ignoreCase = true)
                    val isServerDisconnectedRetry = (error == SpeechErrorCodes.ERROR_SERVER_DISCONNECTED)

                    // Case A: en-IN (or non-US en) failed with language unavailable -> retry once with installed en-US after 300ms
                    if (canRetry && isEnglishRetry) {
                        Log.i(
                            tag,
                            "[RecognizerInput] Scheduling retry in 300ms: retryReason=$codeName, nextLanguage=en-US, nextAttempt=2, thread=${Thread.currentThread().name}"
                        )
                        destroyCurrentRecognizer("Teardown failed instance before 300ms retry with en-US")
                        retryToken = scheduler.postDelayed(300L) {
                            launchAttempt(
                                targetLang = "en-US",
                                attempt = 2,
                                onResult = onResult,
                                onError = onError,
                                sessionId = sessionId
                            )
                        }
                        return
                    }

                    // Case B: Server disconnected (code 11) -> wait 300ms and retry once with same language
                    if (canRetry && isServerDisconnectedRetry) {
                        Log.i(
                            tag,
                            "[RecognizerInput] Scheduling retry in 300ms: retryReason=$codeName, nextLanguage=$targetLang, nextAttempt=2, thread=${Thread.currentThread().name}"
                        )
                        destroyCurrentRecognizer("Teardown failed instance before 300ms retry after error 11")
                        retryToken = scheduler.postDelayed(300L) {
                            launchAttempt(
                                targetLang = targetLang,
                                attempt = 2,
                                onResult = onResult,
                                onError = onError,
                                sessionId = sessionId
                            )
                        }
                        return
                    }

                    // Terminal error (no retries left or non-retryable error)
                    Log.w(tag, "[RecognizerInput] Terminal error reached for session #$sessionId: code=$error ($codeName), thread=${Thread.currentThread().name}")
                    destroyCurrentRecognizer("Terminal error teardown")

                    val errorToken = when (error) {
                        SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE,
                        SpeechErrorCodes.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANG_PACK_MISSING"
                        SpeechErrorCodes.ERROR_INSUFFICIENT_PERMISSIONS -> "PERM_MISSING"
                        SpeechErrorCodes.ERROR_RECOGNIZER_BUSY,
                        SpeechErrorCodes.ERROR_SERVER_DISCONNECTED -> "BUSY"
                        SpeechErrorCodes.ERROR_SPEECH_TIMEOUT,
                        SpeechErrorCodes.ERROR_NO_MATCH -> "NO_SPEECH"
                        else -> "CODE:$error:$codeName"
                    }
                    onError(errorToken)
                }

                override fun onResults(results: Bundle?) {
                    if (sessionId != activeSessionId) return
                    clearTimeout()
                    isListening = false
                    val spokenText = resultsExtractor(results)
                    Log.i(tag, "[RecognizerInput] onResults: '$spokenText', thread=${Thread.currentThread().name}")
                    destroyCurrentRecognizer("Teardown on success")
                    onResult(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            isListening = true
            recognizer.startListening(recognizerIntent, targetLanguage = targetLang)

            // 6-second timeout: cancel if no response within 6s
            timeoutToken = scheduler.postDelayed(6000L) {
                if (isListening && sessionId == activeSessionId) {
                    Log.i(tag, "[RecognizerInput] 6s listening timeout reached for session #$sessionId")
                    stopListening()
                    onError("NO_SPEECH")
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "[RecognizerInput] Exception launching recognizer attempt=$attempt", e)
            destroyCurrentRecognizer("Teardown on launch exception")
            onError("CODE:5:ERROR_CLIENT")
        }
    }

    private fun destroyCurrentRecognizer(reason: String) {
        clearTimeout()
        clearRetry()
        val r = currentRecognizer
        currentRecognizer = null
        isListening = false
        if (r != null) {
            Log.i(tag, "[RecognizerInput] Destroying recognizer instance ($reason), thread=${Thread.currentThread().name}")
            try {
                r.cancel()
            } catch (e: Exception) {
                Log.w(tag, "[RecognizerInput] Exception cancelling recognizer: ${e.message}")
            }
            try {
                r.destroy()
            } catch (e: Exception) {
                Log.w(tag, "[RecognizerInput] Exception destroying recognizer: ${e.message}")
            }
        }
    }

    override fun stopListening() {
        scheduler.post {
            clearTimeout()
            if (isListening) {
                try {
                    currentRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w(tag, "[RecognizerInput] Exception stopping recognizer: ${e.message}")
                }
                isListening = false
            }
        }
    }

    override fun cancel() {
        scheduler.post {
            activeSessionId++
            destroyCurrentRecognizer("User cancelled")
        }
    }

    override fun destroy() {
        scheduler.post {
            activeSessionId++
            destroyCurrentRecognizer("Component destroyed")
        }
    }

    private fun clearTimeout() {
        timeoutToken?.let { scheduler.cancel(it) }
        timeoutToken = null
    }

    private fun clearRetry() {
        retryToken?.let { scheduler.cancel(it) }
        retryToken = null
    }
}
