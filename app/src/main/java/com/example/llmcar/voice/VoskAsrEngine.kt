package com.example.llmcar.voice

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class VoskAsrEngine : AsrEngine {

    override val displayName = "Vosk (offline)"
    private var speechService: SpeechService? = null

    companion object {
        private const val MODEL_DIR = "vosk-model-small-ru-0.22"
        private var model: Model? = null
        private var initialized = false

        init {
            runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        }

        private fun ensureModel(c: Context): Boolean {
            if (initialized && model != null) return true
            return try {
                val target = java.io.File(c.filesDir, MODEL_DIR)
                if (!target.exists()) copyAssetDir(c.assets, MODEL_DIR, target)
                model = Model(target.absolutePath)
                initialized = true; true
            } catch (e: Exception) {
                android.util.Log.e("VoskAsrEngine", "init failed", e)
                initialized = false; false
            }
        }

        private fun copyAssetDir(a: AssetManager, src: String, dst: java.io.File) {
            val children = a.list(src).orEmpty()
            if (children.isEmpty()) {
                dst.parentFile?.mkdirs()
                a.open(src).use { i ->
                    dst.outputStream().use { o -> i.copyTo(o) }
                }
            } else {
                dst.mkdirs()
                children.forEach { copyAssetDir(a, "$src/$it", java.io.File(dst, it)) }
            }
        }

        /** Выгрузка модели из RAM. Вызывать при закрытии приложения. */
        fun releaseModel() {
            runCatching { model?.close() }
            model = null
            initialized = false
        }
    }

    override fun isAvailable(context: Context): Boolean =
        runCatching {
            context.assets.list(MODEL_DIR)?.isNotEmpty() == true
        }.getOrDefault(false)

    override fun listenOnce(context: Context): Flow<VoiceEvent> = callbackFlow {
        if (!isAvailable(context)) {
            trySend(VoiceEvent.Error(-1, "Vosk-модель не найдена в assets/$MODEL_DIR"))
            close(); return@callbackFlow
        }
        if (!ensureModel(context)) {
            trySend(VoiceEvent.Error(-1, "Не удалось загрузить Vosk-модель"))
            close(); return@callbackFlow
        }

        trySend(VoiceEvent.ReadyForSpeech)

        val rec = Recognizer(model, 16000.0f)
        val svc = SpeechService(rec, 16000.0f)
        speechService = svc
        svc.startListening(object : RecognitionListener {
            override fun onPartialResult(h: String?) {
                val t = extract(h)
                if (t.isNotEmpty()) trySend(VoiceEvent.Partial(t))
            }
            override fun onResult(h: String?) {
                trySend(VoiceEvent.Final(extract(h))); close()
            }
            override fun onFinalResult(h: String?) {
                trySend(VoiceEvent.Final(extract(h))); close()
            }
            override fun onError(e: Exception?) {
                trySend(VoiceEvent.Error(-1, e?.message ?: "Vosk error")); close()
            }
            override fun onTimeout() {
                trySend(VoiceEvent.Error(-1, "Таймаут")); close()
            }
        })

        awaitClose {
            runCatching { speechService?.stop() }
            runCatching { speechService?.shutdown() }
            speechService = null
            runCatching { rec.close() }
        }
    }

    override fun stop() { runCatching { speechService?.stop() } }

    private fun extract(json: String?): String {
        if (json == null) return ""
        return Regex("\"(?:text|partial)\"\\s*:\\s*\"([^\"]*)\"")
            .find(json)?.groupValues?.getOrNull(1).orEmpty()
    }
}