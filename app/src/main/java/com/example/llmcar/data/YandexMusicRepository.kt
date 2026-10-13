package com.example.llmcar.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class YandexMusicRepository(context: Context) {

    private val mk = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(context, "ym", mk,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    fun isAuthorized() = !prefs.getString("t", null).isNullOrBlank()
    fun saveToken(t: String) { prefs.edit().putString("t", t).apply() }
    fun clearToken() { prefs.edit().remove("t").apply() }
    private fun token() = prefs.getString("t", null)
        ?: throw IllegalStateException("Не авторизован")

    suspend fun searchTrack(q: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.music.yandex.net/search?text=${Uri.encode(q)}&type=track"
            val req = Request.Builder().url(url)
                .header("Authorization", "OAuth ${token()}").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext Result.failure(Exception("HTTP ${r.code}"))
                val body = r.body?.string().orEmpty()
                val id = Regex("\"id\"\\s*:\\s*\"(\\d+)\"").find(body)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Трек не найден"))
                Result.success(id)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    companion object {
        const val OAUTH_AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=YOUR_CLIENT_ID"
    }
}