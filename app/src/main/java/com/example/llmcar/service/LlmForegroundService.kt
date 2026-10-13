package com.example.llmcar.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.R
import com.example.llmcar.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

sealed class LlmStatus {
    data object Idle : LlmStatus()
    data object Loading : LlmStatus()
    data object Ready : LlmStatus()
    data class Error(val message: String) : LlmStatus()
}

class LlmForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proc: Process? = null

    companion object {
        private val _status = MutableStateFlow<LlmStatus>(LlmStatus.Idle)
        val status: StateFlow<LlmStatus> = _status.asStateFlow()
        const val DEFAULT_PORT = 8080
        const val NOTIFICATION_ID = 1001
        const val ACTION_LOAD_MODEL = "com.example.llmcar.LOAD_MODEL"
        const val ACTION_STOP_SERVER = "com.example.llmcar.STOP_SERVER"
        const val EXTRA_MODEL_PATH = "model_path"

        // libomp.so обязателен: libggml-cpu.so линкуется с OpenMP.
        private val REQUIRED_LIBS = listOf(
            "libggml-base.so", "libggml-cpu.so", "libggml.so", "libllama.so",
            "libomp.so"
        )
        private val OPTIONAL_LIBS = listOf("libmtmd.so", "libc++_shared.so")
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notif("Инициализация…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD_MODEL -> intent.getStringExtra(EXTRA_MODEL_PATH)?.let { loadModel(it) }
            ACTION_STOP_SERVER -> stopServer()
        }
        return START_STICKY
    }

    private fun loadModel(ggufPath: String) {
        scope.launch {
            stopInternal()
            _status.value = LlmStatus.Loading
            val model = File(ggufPath)
            if (!model.exists()) {
                _status.value = LlmStatus.Error("Файл модели не найден: $ggufPath")
                return@launch
            }
            try {
                val s = SettingsRepository(applicationContext)
                val binDir = ensureRuntime()
                val server = File(binDir, "llama-server")
                if (!server.exists() || server.length() == 0L) {
                    _status.value = LlmStatus.Error("llama-server отсутствует в APK assets")
                    return@launch
                }
                if (!server.canExecute()) server.setExecutable(true)
                if (!server.canExecute()) {
                    _status.value = LlmStatus.Error("llama-server не имеет права на выполнение")
                    return@launch
                }
                val libDir = File(filesDir, "llm_runtime/lib")

                // ВАЖНО: запускаем через системный линкер, иначе SELinux заблокирует
                // execute на файле из app_data_file (Android 10+).
                val linker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    "/system/bin/linker64" else "/system/bin/linker"

                val cmd = mutableListOf(
                    linker, server.absolutePath,
                    "-m", model.absolutePath,
                    "-c", s.contextSize.toString(),
                    "-np", s.parallelSlots.toString(),
                    "-b", s.batchSize.toString(),
                    "-t", s.threadCount.toString(),
                    "--host", "127.0.0.1",
                    "--port", DEFAULT_PORT.toString()
                )
                if (!s.mmapEnabled) cmd.add("--no-mmap")
                if (s.kvCacheQuantized) {
                    cmd.add("--cache-type-k"); cmd.add("q8_0")
                    cmd.add("--cache-type-v"); cmd.add("q8_0")
                    // V cache quantization requires flash_attn в llama.cpp (b4xxx+).
                    // Без этого флага загрузка падает с ошибкой
                    // "llama_new_context_with_model: V cache quantization requires flash_attn".
                    cmd.add("-fa")
                }

                android.util.Log.i("LlmSvc", "CMD: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd).directory(binDir).redirectErrorStream(true)
                pb.environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
                val p = pb.start()
                proc = p

                // Копим хвост вывода — понадобится для диагностики при таймауте.
                val tail = Collections.synchronizedList(mutableListOf<String>())
                scope.launch {
                    runCatching {
                        p.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                android.util.Log.d("llama-server", line)
                                synchronized(tail) {
                                    tail.add(line)
                                    if (tail.size > 40) tail.removeAt(0)
                                }
                            }
                        }
                    }
                }

                if (waitReady(DEFAULT_PORT, 180_000)) {
                    _status.value = LlmStatus.Ready
                    updateNotif("Готово · ctx=${s.contextSize} · slots=${s.parallelSlots}" +
                        if (s.kvCacheQuantized) " · kv=q8" else "")
                } else {
                    val why = synchronized(tail) { tail.toList() }
                        .joinToString("\n").ifBlank {
                            "процесс жив, но /health не отвечает 180 сек"
                        }
                    android.util.Log.e("LlmSvc", "llama-server не поднялся:\n$why")
                    _status.value = LlmStatus.Error("llama-server: $why")
                    stopInternal()
                }
            } catch (e: Exception) {
                android.util.Log.e("LlmSvc", "loadModel failed", e)
                _status.value = LlmStatus.Error(e.message ?: "?")
                stopInternal()
            }
        }
    }

    /**
     * Распаковка llama-server и .so из assets в filesDir.
     * Проверяет наличие ВСЕХ обязательных .so и пересоздаёт рантайм,
     * если хотя бы один отсутствует или пуст.
     */
    private fun ensureRuntime(): File {
        val rt = File(filesDir, "llm_runtime")
        val bin = File(rt, "bin")
        val lib = File(rt, "lib")
        val srv = File(bin, "llama-server")

        // Диагностика: что вообще есть в assets APK
        runCatching {
            val root = assets.list("")?.toList().orEmpty()
            android.util.Log.i("LlmSvc", "APK assets root: $root")
        }

        val libsOk = REQUIRED_LIBS.all {
            File(lib, it).let { f -> f.exists() && f.length() > 0 }
        }
        if (srv.exists() && srv.length() > 0 && libsOk) {
            if (!srv.canExecute()) srv.setExecutable(true)
            return bin
        }

        bin.mkdirs(); lib.mkdirs()

        // llama-server
        runCatching {
            copyAsset("llama-server", srv)
            srv.setExecutable(true)
            android.util.Log.i("LlmSvc", "unpacked llama-server (${srv.length()} B)")
        }.onFailure {
            android.util.Log.e("LlmSvc", "assets/llama-server: ${it.message}", it)
        }

        // .so — обязательные + опциональные
        (REQUIRED_LIBS + OPTIONAL_LIBS).forEach { name ->
            val dst = File(lib, name)
            try {
                copyAsset(name, dst)
                android.util.Log.i("LlmSvc", "unpacked $name (${dst.length()} B)")
            } catch (e: Exception) {
                val level = if (name in REQUIRED_LIBS) "ERR" else "skip"
                android.util.Log.w("LlmSvc", "$level $name: ${e.message}")
            }
        }
        return bin
    }

    private fun copyAsset(name: String, target: File) {
        assets.open(name).use { i ->
            target.outputStream().use { o -> i.copyTo(o) }
        }
    }

    private suspend fun waitReady(port: Int, timeout: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            // Если процесс уже умер — ждать нет смысла
            val p = proc
            if (p != null && !p.isAlive && p.exitValue() != 0) {
                android.util.Log.e("LlmSvc", "llama-server exited with ${p.exitValue()}")
                return false
            }
            if (checkHealth(port)) return true
            delay(2000)
        }
        return false
    }

    private fun checkHealth(port: Int) = try {
        val c = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 3000; c.requestMethod = "GET"
        val r = c.responseCode; c.disconnect(); r == 200
    } catch (_: Exception) { false }

    private fun stopServer() { scope.launch { stopInternal() } }

    private fun stopInternal() {
        runCatching { proc?.destroy(); proc?.waitFor() }
        proc = null
        _status.value = LlmStatus.Idle
    }

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_ENGINE)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true).build()
    }

    private fun updateNotif(t: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notif(t))
    }

    override fun onDestroy() { stopInternal(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
