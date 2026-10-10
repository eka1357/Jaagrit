package com.jaagrit.app.companion

import com.jaagrit.app.engine.CompanionQuestion
import com.jaagrit.app.engine.Lang
import com.jaagrit.app.engine.Level

/**
 * Inputs a companion brain may use to pick a line (ARCHITECTURE.md).
 * Pure Kotlin — zero Android dependencies.
 */
data class CompanionContext(
    val level: Level,
    val lang: Lang,
    // COM-6: true only when real past-trip alert history exists near this time of day
    val hasSleepyHistoryNow: Boolean = false
)

/**
 * Source of companion openers and cognitive questions (AGENTS.md Rule 6).
 * Never used on the L3-L5 safety path, which stays hardcoded (Rule 1).
 * [PhraseBankCompanion] works with no LLM; an on-device model can sit behind this interface later.
 */
interface CompanionBrain {
    fun opener(ctx: CompanionContext): String
    fun question(ctx: CompanionContext): CompanionQuestion
}
