package com.alex.zendence

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "meditations")
@Serializable
data class Meditation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val durationMinutes: Int,
    val insight: String? = null
)

@Entity(tableName = "presets")
@Serializable
data class Preset(
    @PrimaryKey val name: String,
    val durationMin: Int,
    val volume: Float,
    val intervalBellsJson: String, // Format: JSON string
    val backgroundSoundUri: String? = null,
    val startingBellUri: String? = null,
    val initialSilenceSec: Int? = null
)

@Serializable
data class IntervalBell(
    val id: String = UUID.randomUUID().toString(),
    val atSecFromStart: Int,
    val soundType: String, // "interval_bell", "starting_bell", or "custom"
    val repeats: Int = 1,
    val volume: Float = 1.0f,
    val soundUri: String? = null
)

@Serializable
data class ZendenceConfig(
    val initialDurationSec: Int,
    val volume: Float,
    val startingBellEnabled: Boolean,
    val startingBellVolume: Float,
    val startingBellUri: String,
    val initialSilenceSec: Int,
    val backgroundSoundUri: String,
    val meditationReading: String,
    val intervalBells: List<IntervalBell>,
    val presets: List<Preset>,
    val history: List<Meditation> = emptyList()
)
