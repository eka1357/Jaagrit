package com.jaagrit.app.data.converters

import androidx.room.TypeConverter
import com.jaagrit.app.engine.Level

/**
 * Room TypeConverters for custom enum and object types.
 */
class RoomConverters {

    @TypeConverter
    fun fromLevel(level: Level?): String? {
        return level?.name
    }

    @TypeConverter
    fun toLevel(value: String?): Level? {
        return value?.let {
            try {
                Level.valueOf(it)
            } catch (_: IllegalArgumentException) {
                Level.L0
            }
        }
    }
}
