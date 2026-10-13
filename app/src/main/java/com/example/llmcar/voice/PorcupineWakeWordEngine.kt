package com.example.llmcar.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import android.content.Context

class PorcupineWakeWordEngine(
    private val context: Context,
    private val accessKey: String,
    private val onDetected: () -> Unit
) {

    private var manager: PorcupineManager? = null

    fun start(
        customKeywordAsset: String? = null,
        russianModelAsset: String? = null,
        sensitivity: Float = 0.5f
    ): Result<Unit> = runCatching {
        val b = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setSensitivity(sensitivity)

        if (customKeywordAsset != null) {
            b.setKeywordPath(customKeywordAsset)
        } else {
            b.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
        }
        if (russianModelAsset != null) {
            b.setModelPath(russianModelAsset)
        }

        val m = b.build(context) { onDetected() }
        m.start()
        manager = m
    }

    fun stop() {
        runCatching { manager?.stop() }
        runCatching { manager?.delete() }
        manager = null
    }
}