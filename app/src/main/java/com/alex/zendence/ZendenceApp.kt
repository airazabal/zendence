package com.alex.zendence

import android.app.Application
import android.content.Context
import androidx.room.Room

class ZendenceApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "meditation-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository by lazy {
        val sharedPrefs = getSharedPreferences("zendence_prefs", Context.MODE_PRIVATE)
        MeditationRepository(database.meditationDao(), sharedPrefs)
    }
}
