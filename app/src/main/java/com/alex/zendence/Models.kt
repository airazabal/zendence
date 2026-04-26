package com.alex.zendence

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "meditations")
data class Meditation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val durationMinutes: Int,
    val insight: String? = null
)

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey val name: String,
    val durationMin: Int,
    val volume: Float,
    val intervalBellsJson: String // Format: JSON string
)

@Serializable
data class IntervalBell(
    val id: String = UUID.randomUUID().toString(),
    val atSecFromStart: Int,
    val soundType: String,
    val repeats: Int = 1,
    val volume: Float = 1.0f
)
