package com.example.llmcar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert suspend fun insert(e: HistoryEntity): Long
    @Query("DELETE FROM history WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM history") suspend fun clear()
    @Query("SELECT COUNT(*) FROM history") suspend fun count(): Int
}