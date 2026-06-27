package com.rokid.workouttracker

import android.content.Context
import androidx.room.Room

object RoomBuilder {
    private const val DB_NAME = "workout_tracker.db"

    @Volatile
    private var instance: AppDatabase? = null

    fun create(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DB_NAME
        ).allowMainThreadQueries().build().also { instance = it }
        }
    }
}
