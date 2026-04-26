package com.alex.zendence

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow

class MeditationRepository(private val dao: MeditationDao, private val sharedPrefs: SharedPreferences) {

    val history: Flow<List<Meditation>> = dao.getAll()
    val presets: Flow<List<Preset>> = dao.getAllPresets()

    suspend fun getMeditationById(id: Int) = dao.getById(id)
    suspend fun insertMeditation(meditation: Meditation) = dao.insert(meditation)
    suspend fun updateMeditation(meditation: Meditation) = dao.update(meditation)
    suspend fun deleteMeditation(meditation: Meditation) = dao.delete(meditation)
    suspend fun clearHistory() = dao.deleteAll()

    suspend fun insertPreset(preset: Preset) = dao.insertPreset(preset)
    suspend fun deletePreset(preset: Preset) = dao.deletePreset(preset)

    fun getInitialDuration() = sharedPrefs.getInt("initial_duration_sec", 2700)
    fun getVolume() = sharedPrefs.getFloat("volume", 0.5f)
    fun getStartingBellEnabled() = sharedPrefs.getBoolean("starting_bell_enabled", true)
    fun getStartingBellVolume() = sharedPrefs.getFloat("starting_bell_volume", 0.7f)
    fun getIntervalBellsJson() = sharedPrefs.getString("interval_bells", null)

    fun saveSettings(duration: Int, volume: Float, startBell: Boolean, startBellVol: Float, bellsJson: String) {
        sharedPrefs.edit()
            .putInt("initial_duration_sec", duration)
            .putFloat("volume", volume)
            .putBoolean("starting_bell_enabled", startBell)
            .putFloat("starting_bell_volume", startBellVol)
            .putString("interval_bells", bellsJson)
            .apply()
    }
}
