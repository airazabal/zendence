package com.alex.zendence

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeditationDao {
    @Query("SELECT * FROM meditations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Meditation>>
    @Query("SELECT * FROM meditations WHERE id = :id")
    suspend fun getById(id: Int): Meditation?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meditation: Meditation): Long
    @Update
    suspend fun update(meditation: Meditation)
    @Delete
    suspend fun delete(meditation: Meditation)
    @Query("DELETE FROM meditations")
    suspend fun deleteAll()

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<Preset>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: Preset)
    @Delete
    suspend fun deletePreset(preset: Preset)
}

@Database(entities = [Meditation::class, Preset::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meditationDao(): MeditationDao
}
