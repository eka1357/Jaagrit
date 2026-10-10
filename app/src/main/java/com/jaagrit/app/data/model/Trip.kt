package com.jaagrit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a driving session in Jaagrit (ARCHITECTURE.md).
 *
 * @property id Auto-generated trip primary key
 * @property startMs Wall-clock epoch timestamp (ms) when monitoring began
 * @property endMs Wall-clock epoch timestamp (ms) when drive completed (null while active)
 * @property avgAlertness Average alertness score (0-100) calculated from AlertSamples
 * @property alertCount Total alerts triggered during this session
 * @property criticalCount Total critical (L3+) alerts triggered during this session
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val avgAlertness: Double? = null,
    val alertCount: Int = 0,
    val criticalCount: Int = 0
) {
    val durationMs: Long
        get() = if (endMs != null) (endMs - startMs).coerceAtLeast(0L) else 0L

    val isActive: Boolean
        get() = endMs == null
}
