package com.jaagrit.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.Executors

/**
 * Readiness states for on-device speech recognition.
 */
enum class SpeechReadiness {
    READY,
    LANG_PACK_MISSING,
    UNAVAILABLE
}

/**
 * Checks whether an offline/on-device SpeechRecognizer is available and has the necessary
 * language pack installed for the current language.
 */
object SpeechReadinessChecker {

    private const val TAG = "JAAGRIT"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    fun checkReadiness(
        context: Context,
        appLanguage: String,
        onResult: (SpeechReadiness) -> Unit
    ) {
        val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val isOnDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

        Log.i(TAG, "SpeechReadinessChecker: isRecognitionAvailable=$isAvailable, isOnDeviceRecognitionAvailable=$isOnDevice, lang=$appLanguage")

        if (!isAvailable || !isOnDevice) {
            onResult(SpeechReadiness.UNAVAILABLE)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mainHandler.post {
                try {
                    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    val targetLang = if (appLanguage == "hi") "hi-IN" else "en-US"
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLang)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }

                    recognizer.checkRecognitionSupport(
                        intent,
                        executor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                val installed = recognitionSupport.installedOnDeviceLanguages
                                Log.i(TAG, "SpeechReadinessChecker: installedOnDeviceLanguages=$installed")

                                val isInstalled = if (appLanguage == "hi") {
                                    installed.any { it.startsWith("hi", ignoreCase = true) }
                                } else {
                                    installed.any { it.startsWith("en", ignoreCase = true) }
                                }

                                mainHandler.post {
                                    try {
                                        recognizer.destroy()
                                    } catch (_: Exception) {}

                                    val readiness = if (isInstalled) {
                                        SpeechReadiness.READY
                                    } else {
                                        SpeechReadiness.LANG_PACK_MISSING
                                    }
                                    Log.i(TAG, "SpeechReadinessChecker result: $readiness for lang=$appLanguage")
                                    onResult(readiness)
                                }
                            }

                            override fun onError(error: Int) {
                                Log.w(TAG, "SpeechReadinessChecker: checkRecognitionSupport onError=$error")
                                mainHandler.post {
                                    try {
                                        recognizer.destroy()
                                    } catch (_: Exception) {}
                                    onResult(SpeechReadiness.LANG_PACK_MISSING)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "SpeechReadinessChecker: Failed to create on-device recognizer", e)
                    onResult(SpeechReadiness.UNAVAILABLE)
                }
            }
        } else {
            // Pre-Tiramisu (API 31-32): on-device available but cannot query installed models
            val readiness = if (appLanguage == "hi") {
                SpeechReadiness.LANG_PACK_MISSING
            } else {
                SpeechReadiness.READY
            }
            onResult(readiness)
        }
    }
}
