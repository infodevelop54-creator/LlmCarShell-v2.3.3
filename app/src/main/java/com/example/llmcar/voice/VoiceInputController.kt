package com.example.llmcar.voice

import android.content.Context
import com.example.llmcar.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VoiceInputController(private val context: Context) {

    private val settings = SettingsRepository(context)

    fun listenOnce(): Flow<VoiceEvent> = flow {
        val engine = AsrEngineFactory.create(settings.asrEngine)
        if (!engine.isAvailable(context)) {
            emit(VoiceEvent.Error(-1, "«${engine.displayName}» недоступен"))
            return@flow
        }
        engine.listenOnce(context).collect { emit(it) }
    }

    fun stop() {
        AsrEngineFactory.create(settings.asrEngine).stop()
    }
}