package com.jaagrit.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class representing a Trip together with its collected AlertSamples and AlertEvents.
 */
data class TripWithDetails(
    @Embedded
    val trip: Trip,

    @Relation(
        parentColumn = "id",
        entityColumn = "tripId"
    )
    val samples: List<AlertSample> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "tripId"
    )
    val events: List<AlertEvent> = emptyList()
)
