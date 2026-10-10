package com.jaagrit.app.companion

import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.Lang
import com.jaagrit.app.engine.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaCompanionTest {

    private val contextHi = CompanionContext(level = Level.L1, lang = Lang.HI)
    private val contextEn = CompanionContext(level = Level.L1, lang = Lang.EN)

    @Test
    fun testValidOpenerAccepted() {
        val gemma = GemmaCompanion(
            generator = { "आप कैसे महसूस कर रहे हैं?" }
        )
        val line = gemma.opener(contextHi)
        assertEquals("आप कैसे महसूस कर रहे हैं?", line)
        assertEquals(1, gemma.telemetry.successfulGenerationCount)
        assertEquals(0, gemma.telemetry.fallbackCount)
    }

    @Test
    fun testMalformedOpenerFallsBackToPhraseBank() {
        val gemma = GemmaCompanion(
            generator = { "http://spam.url <unk>" }
        )
        val line = gemma.opener(contextHi)
        assertTrue(line.isNotBlank())
        assertEquals(0, gemma.telemetry.successfulGenerationCount)
        assertEquals(1, gemma.telemetry.fallbackCount)
    }

    @Test
    fun testSpeechActiveSuppressesGemma() {
        var speechActive = true
        val gemma = GemmaCompanion(
            generator = { "Should not be called" },
            isSpeechActiveProvider = { speechActive }
        )
        val line = gemma.opener(contextEn)
        assertTrue(line.isNotBlank())
        assertEquals(0, gemma.telemetry.successfulGenerationCount)
        assertEquals(1, gemma.telemetry.fallbackCount)
    }

    @Test
    fun testThermalThrottlingSuppressesGemma() {
        val gemma = GemmaCompanion(
            generator = { "Should not run when hot" },
            isThermalThrottlingProvider = { true }
        )
        val line = gemma.opener(contextHi)
        assertTrue(line.isNotBlank())
        assertEquals(1, gemma.telemetry.fallbackCount)
    }

    @Test
    fun testValidMathQuestionParsed() {
        val gemma = GemmaCompanion(
            generator = { "12 + 5 = ? | 17" }
        )
        val q = gemma.question(contextEn)
        assertEquals("12 + 5 = ?", q.prompt)
        assertEquals("17", q.answer)
        assertTrue(q.choices.contains("17"))
        assertEquals(1, gemma.telemetry.successfulGenerationCount)
    }

    @Test
    fun testInvalidMathQuestionFallsBack() {
        val gemma = GemmaCompanion(
            generator = { "Invalid gibberish without separator" }
        )
        val q = gemma.question(contextEn)
        assertNotNull(q.prompt)
        assertNotNull(q.answer)
        assertEquals(1, gemma.telemetry.fallbackCount)
    }

    @Test
    fun testNullGeneratorGracefulFallback() {
        val gemma = GemmaCompanion(generator = null)
        val line = gemma.opener(contextHi)
        assertTrue(line.isNotBlank())
        assertEquals(1, gemma.telemetry.fallbackCount)
    }
}
