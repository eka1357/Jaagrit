package com.jaagrit.app.speech

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [RecognizerInput] retry lifecycle, on-device offline enforcement,
 * and delayed recovery mechanics.
 */
class RecognizerInputTest {

    private class FakePlatformRecognizer(val id: Int) : PlatformSpeechRecognizer {
        var listener: RecognitionListener? = null
        var lastIntent: Intent? = null
        var isListening = false
        var isDestroyed = false
        var isCancelled = false

        var targetLanguage: String = ""

        override fun setRecognitionListener(listener: RecognitionListener) {
            this.listener = listener
        }

        override fun startListening(recognizerIntent: Intent, targetLanguage: String) {
            this.lastIntent = recognizerIntent
            this.targetLanguage = targetLanguage
            this.isListening = true
        }

        override fun stopListening() {
            this.isListening = false
        }

        override fun cancel() {
            this.isCancelled = true
            this.isListening = false
        }

        override fun destroy() {
            this.isDestroyed = true
            this.isListening = false
        }

        fun emitError(code: Int) {
            listener?.onError(code)
        }

        fun emitResults() {
            listener?.onResults(null)
        }
    }

    private class FakeSpeechRecognizerFactory : SpeechRecognizerFactory {
        var isAvailable = true
        var isOnDeviceAvailable = true
        val createdRecognizers = mutableListOf<FakePlatformRecognizer>()
        private var counter = 0

        override fun isRecognitionAvailable(): Boolean = isAvailable
        override fun isOnDeviceRecognitionAvailable(): Boolean = isOnDeviceAvailable

        override fun createOnDeviceRecognizer(): PlatformSpeechRecognizer {
            val r = FakePlatformRecognizer(++counter)
            createdRecognizers.add(r)
            return r
        }
    }

    private class FakeDelayedScheduler : DelayedScheduler {
        data class ScheduledTask(val id: Int, val delayMs: Long, val action: () -> Unit)
        val pendingTasks = mutableListOf<ScheduledTask>()
        private var idCounter = 0

        override fun post(action: () -> Unit) {
            action()
        }

        override fun postDelayed(delayMillis: Long, action: () -> Unit): Any {
            val task = ScheduledTask(++idCounter, delayMillis, action)
            pendingTasks.add(task)
            return task
        }

        override fun cancel(token: Any) {
            if (token is ScheduledTask) {
                pendingTasks.remove(token)
            }
        }

        fun runNextTask(): Boolean {
            if (pendingTasks.isEmpty()) return false
            val task = pendingTasks.removeAt(0)
            task.action()
            return true
        }
    }

    @Test
    fun testEnglishDialectError13_RetriesOnceWithEnUsAfter300ms_AndSucceeds() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        var emittedSpokenText = "Drive time"

        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { emittedSpokenText }
        )

        var finalResult: String? = null
        var finalError: String? = null

        recognizerInput.startListening(
            languageCode = "en-IN",
            onResult = { finalResult = it },
            onError = { finalError = it }
        )

        // Attempt 1: Recognizer created with en-IN
        assertEquals(1, factory.createdRecognizers.size)
        val firstRecognizer = factory.createdRecognizers[0]
        assertEquals("en-IN", firstRecognizer.targetLanguage)

        // Trigger error 13 (ERROR_LANGUAGE_UNAVAILABLE)
        firstRecognizer.emitError(SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE)

        // Recognizer 1 must be destroyed immediately
        assertTrue("Attempt 1 recognizer must be destroyed", firstRecognizer.isDestroyed)
        assertNull("Final error should not be emitted before retry", finalError)

        // Scheduler must hold a 300ms delayed retry task
        assertEquals(1, scheduler.pendingTasks.size)
        assertEquals(300L, scheduler.pendingTasks[0].delayMs)

        // Advance 300ms
        scheduler.runNextTask()

        // Attempt 2: Fresh recognizer created with fallback en-US
        assertEquals(2, factory.createdRecognizers.size)
        val secondRecognizer = factory.createdRecognizers[1]
        assertEquals("en-US", secondRecognizer.targetLanguage)

        // Recognizer 2 succeeds
        secondRecognizer.emitResults()

        assertEquals("Drive time", finalResult)
        assertNull(finalError)
        assertTrue("Second recognizer destroyed after success", secondRecognizer.isDestroyed)
    }

    @Test
    fun testError11ServerDisconnected_RetriesOnceAfter300ms() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { "Alertness" }
        )

        var finalResult: String? = null
        var finalError: String? = null

        recognizerInput.startListening(
            languageCode = "en-US",
            onResult = { finalResult = it },
            onError = { finalError = it }
        )

        assertEquals(1, factory.createdRecognizers.size)
        val firstRecognizer = factory.createdRecognizers[0]

        // Emit error 11 (ERROR_SERVER_DISCONNECTED)
        firstRecognizer.emitError(SpeechErrorCodes.ERROR_SERVER_DISCONNECTED)

        assertTrue(firstRecognizer.isDestroyed)
        assertNull(finalError)
        assertEquals(1, scheduler.pendingTasks.size)
        assertEquals(300L, scheduler.pendingTasks[0].delayMs)

        // Run delayed retry
        scheduler.runNextTask()

        assertEquals(2, factory.createdRecognizers.size)
        val secondRecognizer = factory.createdRecognizers[1]
        assertEquals("en-US", secondRecognizer.targetLanguage)

        secondRecognizer.emitResults()
        assertEquals("Alertness", finalResult)
        assertNull(finalError)
    }

    @Test
    fun testError11Twice_DoesNotLoop_TerminatesWithBusy() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { "None" }
        )

        var finalResult: String? = null
        var finalError: String? = null

        recognizerInput.startListening(
            languageCode = "en-US",
            onResult = { finalResult = it },
            onError = { finalError = it }
        )

        // Attempt 1 -> error 11
        factory.createdRecognizers[0].emitError(SpeechErrorCodes.ERROR_SERVER_DISCONNECTED)
        // Advance 300ms
        scheduler.runNextTask()

        // Attempt 2 -> error 11 again
        assertEquals(2, factory.createdRecognizers.size)
        factory.createdRecognizers[1].emitError(SpeechErrorCodes.ERROR_SERVER_DISCONNECTED)

        // Must NOT schedule a third attempt (never loop)
        assertEquals(0, scheduler.pendingTasks.size)
        assertEquals(2, factory.createdRecognizers.size)
        assertEquals("BUSY", finalError)
        assertNull(finalResult)
    }

    @Test
    fun testHindiError13_DoesNotRetry_EmitsLangPackMissingImmediately() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { "" }
        )

        var finalResult: String? = null
        var finalError: String? = null

        recognizerInput.startListening(
            languageCode = "hi-IN",
            onResult = { finalResult = it },
            onError = { finalError = it }
        )

        assertEquals(1, factory.createdRecognizers.size)
        factory.createdRecognizers[0].emitError(SpeechErrorCodes.ERROR_LANGUAGE_UNAVAILABLE)

        // Hindi has no English fallback: immediately fails with LANG_PACK_MISSING
        assertEquals(0, scheduler.pendingTasks.size)
        assertEquals("LANG_PACK_MISSING", finalError)
        assertNull(finalResult)
    }

    @Test
    fun testOfflineUnavailable_NeverCallsFactoryOrCloud_EmitsNoOnDeviceRecognizer() {
        val factory = FakeSpeechRecognizerFactory().apply {
            isOnDeviceAvailable = false
        }
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { "" }
        )

        var finalError: String? = null
        recognizerInput.startListening(
            languageCode = "en-US",
            onResult = {},
            onError = { finalError = it }
        )

        assertEquals("NO_ON_DEVICE_RECOGNIZER", finalError)
        assertEquals("No platform recognizer should be created when offline recognition is unavailable", 0, factory.createdRecognizers.size)
    }

    @Test
    fun testPermissionMissing_EmitsPermMissing() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { false },
            resultsExtractor = { "" }
        )

        var finalError: String? = null
        recognizerInput.startListening(
            languageCode = "en-US",
            onResult = {},
            onError = { finalError = it }
        )

        assertEquals("PERM_MISSING", finalError)
        assertEquals(0, factory.createdRecognizers.size)
    }

    @Test
    fun testCancel_IgnoresLateCallbacksFromDestroyedRecognizer() {
        val factory = FakeSpeechRecognizerFactory()
        val scheduler = FakeDelayedScheduler()
        val recognizerInput = RecognizerInput(
            context = null,
            factory = factory,
            scheduler = scheduler,
            permissionChecker = { true },
            resultsExtractor = { "" }
        )

        var finalError: String? = null
        recognizerInput.startListening(
            languageCode = "en-US",
            onResult = {},
            onError = { finalError = it }
        )

        val recognizer = factory.createdRecognizers[0]
        recognizerInput.cancel()

        // Recognizer emits error after cancellation
        recognizer.emitError(SpeechErrorCodes.ERROR_SERVER_DISCONNECTED)

        assertNull("Stray callback from cancelled session must be ignored", finalError)
    }
}
