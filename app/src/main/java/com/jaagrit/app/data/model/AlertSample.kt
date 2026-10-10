package com.jaagrit.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Alertness score sample captured every 5 seconds during an active drive (ARCHITECTURE.md).
 *
 * @property id Auto-generated sample primary key
 * @property tripId Foreign key referencing trips.id
 * @property tsMs Wall-clock epoch timestamp (ms) when sample was taken
 * @property alertness Calculated alertness score (0-100)
 */
@Entity(
    tableName = "alert_samples",
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
        Index(value = ["tsMs"])
    ]
)
data class AlertSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val tsMs: Long,
    val alertness: Int
)
