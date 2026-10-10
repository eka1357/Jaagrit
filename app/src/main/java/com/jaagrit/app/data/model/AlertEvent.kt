package com.jaagrit.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jaagrit.app.engine.Level

/**
 * Logged alert event during a driving session (ARCHITECTURE.md).
 *
 * @property id Auto-generated event primary key
 * @property tripId Foreign key referencing trips.id
 * @property tsMs Wall-clock epoch timestamp (ms) when alert started or event occurred
 * @property level Highest fatigue intervention level reached (L1 - L5)
 * @property reason Human-readable reason or trigger summary
 * @property durationMs Duration of the alert episode in milliseconds (or 0 for point-in-time events)
 * @property response Driver response type: none | imAwake | voice | eyesOpen | dismissed | L5_NOT_SENT
 */
@Entity(
    tableName = "alert_events",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tsMs"]),
        Index(value = ["level"])
    ]
)
data class AlertEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val tsMs: Long,
    val level: Level,
    val reason: String,
    val durationMs: Long,
    val response: String
)
