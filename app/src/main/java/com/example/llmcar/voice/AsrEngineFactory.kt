package com.example.llmcar.voice

import android.content.Context

object AsrEngineFactory {

    fun create(engineId: String): AsrEngine = when (engineId) {
        "google" -> GoogleOnlineAsrEngine()
        "google_offline" -> GoogleOfflineAsrEngine()
        "vosk" -> VoskAsrEngine()
        else -> GoogleOnlineAsrEngine()
    }

    fun list(context: Context): List<AsrEngineInfo> = listOf(
        AsrEngineInfo("google", "Google SpeechRecognizer (online)", false, true),
        AsrEngineInfo("google_offline", "Google SpeechRecognizer (offline-пакет)", true,
            GoogleOfflineAsrEngine().isAvailable(context)),
        AsrEngineInfo("vosk", "Vosk (offline)", true, VoskAsrEngine().isAvailable(context))
    )
}

data class AsrEngineInfo(
    val id: String,
    val displayName: String,
    val offline: Boolean,
    val available: Boolean
)