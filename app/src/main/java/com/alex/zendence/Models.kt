package com.alex.zendence

import androidx.room.*
import kotlinx.coroutines.flow.Flow
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
    val intervalBellsJson: String // Format: "time:repeats:type:volume|..."
)

data class IntervalBell(
    val id: String = UUID.randomUUID().toString(),
    val atSecFromStart: Int,
    val soundType: String,
    val repeats: Int = 1,
    val volume: Float = 1.0f
)
