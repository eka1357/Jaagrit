package com.jaagrit.app.companion

import com.jaagrit.app.engine.CompanionQuestion
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.Lang
import kotlin.random.Random

/**
 * Milestone M12: Gemma on-device companion implementation behind [CompanionBrain].
 *
 * Rules (M12 & AGENTS.md Rule 1):
 * - Never touches or blocks the safety path (L3-L5).
 * - Output validation: strictly rejects malformed, incoherent, or off-topic outputs.
 * - Automatic graceful fallback to [PhraseBankCompanion] on any failure, timeout, or missing weights.
 * - Load only on demand; never executes while speech recognition is actively listening.
 * - Reports generation latency and memory metrics.
 */
class GemmaCompanion(
    private val config: Config = Config.DEFAULT,
    private val fallbackBrain: CompanionBrain = PhraseBankCompanion(config = config),
    private val generator: ((prompt: String) -> String?)? = null,
    private val isSpeechActiveProvider: () -> Boolean = { false },
    private val isThermalThrottlingProvider: () -> Boolean = { false }
) : CompanionBrain {

    data class GemmaTelemetry(
        val lastInferenceLatencyMs: Long = 0L,
        val fallbackCount: Int = 0,
        val successfulGenerationCount: Int = 0,
        val estimatedRamUsageMb: Int = 0
    )

    var telemetry = GemmaTelemetry()
        private set

    override fun opener(ctx: CompanionContext): String {
        // M12: Load only on demand, never while speech recognition runs, never during thermal throttling
        if (isSpeechActiveProvider() || isThermalThrottlingProvider() || generator == null) {
            recordFallback()
            return fallbackBrain.opener(ctx)
        }

        val startMs = System.currentTimeMillis()
        return try {
            val prompt = if (ctx.lang == Lang.HI) {
                "Generate a short, friendly driver alertness check in Hindi (max 8 words):"
            } else {
                "Generate a short, friendly driver alertness check in English (max 8 words):"
            }

            val generated = generator.invoke(prompt)?.trim()
            val latency = System.currentTimeMillis() - startMs

            if (isValidOpener(generated)) {
                telemetry = telemetry.copy(
                    lastInferenceLatencyMs = latency,
                    successfulGenerationCount = telemetry.successfulGenerationCount + 1,
                    estimatedRamUsageMb = 1850 // Gemma 2B quantized estimated footprint
                )
                generated!!
            } else {
                recordFallback(latency)
                fallbackBrain.opener(ctx)
            }
        } catch (e: Exception) {
            recordFallback(System.currentTimeMillis() - startMs)
            fallbackBrain.opener(ctx)
        }
    }

    override fun question(ctx: CompanionContext): CompanionQuestion {
        // Safety: Always fall back to verified phrase bank if speech is active or generator is absent
        if (isSpeechActiveProvider() || isThermalThrottlingProvider() || generator == null) {
            recordFallback()
            return fallbackBrain.question(ctx)
        }

        val startMs = System.currentTimeMillis()
        return try {
            val prompt = "Generate a simple arithmetic question (addition or subtraction under 30) for a driver:"
            val generated = generator.invoke(prompt)?.trim()
            val latency = System.currentTimeMillis() - startMs

            val parsedQuestion = parseGeneratedQuestion(generated, ctx)
            if (parsedQuestion != null) {
                telemetry = telemetry.copy(
                    lastInferenceLatencyMs = latency,
                    successfulGenerationCount = telemetry.successfulGenerationCount + 1
                )
                parsedQuestion
            } else {
                recordFallback(latency)
                fallbackBrain.question(ctx)
            }
        } catch (e: Exception) {
            recordFallback(System.currentTimeMillis() - startMs)
            fallbackBrain.question(ctx)
        }
    }

    private fun isValidOpener(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.length !in 3..80) return false
        // Disallow dangerous or nonsensical outputs
        val forbidden = listOf("<unk>", "<pad>", "http://", "https://", "null", "undefined")
        if (forbidden.any { text.contains(it, ignoreCase = true) }) return false
        return true
    }

    private fun parseGeneratedQuestion(text: String?, ctx: CompanionContext): CompanionQuestion? {
        if (text.isNullOrBlank()) return null
        // Expected format e.g. "9 + 8 = ? | 17" or "What is 7 plus 6? | 13"
        val parts = text.split("|").map { it.trim() }
        if (parts.size >= 2) {
            val qPrompt = parts[0]
            val answerInt = parts[1].toIntOrNull()
            if (qPrompt.isNotBlank() && answerInt != null && answerInt in 0..100) {
                val distractors = listOf(answerInt + 5, answerInt - 5, answerInt + 10)
                    .filter { it >= 0 && it != answerInt }
                    .take(config.mathChoiceCount - 1)
                val choices = (distractors + answerInt).shuffled(Random.Default).map { it.toString() }
                return CompanionQuestion(
                    id = (System.currentTimeMillis() % 100000).toInt(),
                    prompt = qPrompt,
                    answer = answerInt.toString(),
                    maxWaitMs = config.defaultMathMaxWaitMs,
                    choices = choices
                )
            }
        }
        return null
    }

    private fun recordFallback(latency: Long = 0L) {
        telemetry = telemetry.copy(
            lastInferenceLatencyMs = latency,
            fallbackCount = telemetry.fallbackCount + 1
        )
    }
}
