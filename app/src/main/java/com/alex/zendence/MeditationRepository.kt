package com.alex.zendence

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

class MeditationRepository(private val dao: MeditationDao, private val sharedPrefs: SharedPreferences) {

    val history: Flow<List<Meditation>> = dao.getAll()
    val presets: Flow<List<Preset>> = dao.getAllPresets()

    // Reactive flow for all settings changes
    val settingsFlow: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(Unit) }

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
    fun getStartingBellUri() = sharedPrefs.getString("starting_bell_uri", "android.resource://com.alex.zendence/raw/starting_bell")
    fun getIntervalBellsJson() = sharedPrefs.getString("interval_bells", null)
    fun getInitialSilence() = sharedPrefs.getInt("initial_silence_sec", 30)
    fun getMeditationReading() = sharedPrefs.getString("meditation_reading", null)
    fun getBackgroundSoundUri() = sharedPrefs.getString("background_sound_uri", "android.resource://com.alex.zendence/raw/nature_stream")

    fun saveMeditationReading(text: String) {
        sharedPrefs.edit().putString("meditation_reading", text).apply()
    }

    fun saveBackgroundSoundUri(uri: String) {
        sharedPrefs.edit().putString("background_sound_uri", uri).apply()
    }

    fun saveSettings(
        duration: Int,
        volume: Float,
        startBell: Boolean,
        startBellVol: Float,
        startBellUri: String,
        bellsJson: String,
        silenceSec: Int
    ) {
        sharedPrefs.edit()
            .putInt("initial_duration_sec", duration)
            .putFloat("volume", volume)
            .putBoolean("starting_bell_enabled", startBell)
            .putFloat("starting_bell_volume", startBellVol)
            .putString("starting_bell_uri", startBellUri)
            .putString("interval_bells", bellsJson)
            .putInt("initial_silence_sec", silenceSec)
            .apply()
    }
}
