package com.jaagrit.app.speech

/**
 * Interface abstracting speech input so it can be swapped, tested, or cut (AGENTS.md Rule 6).
 * Implementation: [RecognizerInput] using Android SpeechRecognizer.
 */
interface SpeechInput {

    /** Whether any speech recognition service is available on the device. */
    fun isAvailable(): Boolean

    /** Whether offline / on-device speech recognition is available on the device. */
    fun isOnDeviceAvailable(): Boolean

    /**
     * Starts listening for user speech with the specified [languageCode] (e.g. "hi-IN" or "en-IN").
     * Delivers recognized speech text to [onResult] or error message to [onError].
     */
    fun startListening(
        languageCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    )

    /** Stops listening for more input and finalizes recognition. */
    fun stopListening()

    /** Cancels any active recognition without delivering results. */
    fun cancel()

    /** Releases all underlying speech recognizer resources. */
    fun destroy()
}
