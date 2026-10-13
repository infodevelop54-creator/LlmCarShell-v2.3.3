package com.example.llmcar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class LlmCarDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var i: LlmCarDatabase? = null
        fun get(c: Context): LlmCarDatabase = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext,
                LlmCarDatabase::class.java, "llmcar.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { i = it }
        }
    }
}