package com.example.llmcar.data

import android.content.Context
import com.example.llmcar.data.db.HistoryEntity
import com.example.llmcar.data.db.LlmCarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class LegacyEntry(
    val id: Long,
    val timestamp: Long,
    val userText: String,
    val assistantText: String
)

class HistoryRepository(context: Context) {
    private val app = context.applicationContext
    private val dao = LlmCarDatabase.get(app).historyDao()
    private val legacy = File(app.filesDir, "chat_history.json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureMigrated() = withContext(Dispatchers.IO) {
        if (!legacy.exists()) return@withContext
        if (dao.count() > 0) { legacy.delete(); return@withContext }
        runCatching {
            val list = json.decodeFromString<List<LegacyEntry>>(legacy.readText())
            list.reversed().forEach {
                dao.insert(HistoryEntity(
                    timestamp = it.timestamp,
                    userText = it.userText,
                    assistantText = it.assistantText))
            }
            legacy.delete()
        }
    }

    fun observe(): Flow<List<HistoryEntity>> = dao.observeAll()

    suspend fun save(u: String, a: String) = withContext(Dispatchers.IO) {
        dao.insert(HistoryEntity(
            timestamp = System.currentTimeMillis(),
            userText = u,
            assistantText = a))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}