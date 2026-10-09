package com.jaagrit.app.engine

/**
 * Clock interface for time retrieval, allowing deterministic unit testing with simulated frames.
 * Pure Kotlin — zero Android dependencies (AGENTS.md Rule 2).
 */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        /** Default system wall-clock implementation */
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}
