package com.example.llmcar.integrations

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuBridge {

    fun isAvailable() = runCatching {
        Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * В Shizuku 13.1.5 метод newProcess приватный.
     * Используем рефлексию для доступа к нему.
     */
    private val newProcessMethod by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun runShell(cmd: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val method = newProcessMethod
                ?: return@withContext Result.failure(
                    IllegalStateException("Shizuku.newProcess недоступен в этой версии"))
            val p = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
            p.waitFor()
            Result.success(if (err.isBlank()) out.trim()
                           else "$out\n[stderr]\n$err".trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
