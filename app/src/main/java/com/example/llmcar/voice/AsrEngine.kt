package com.example.llmcar.voice

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface AsrEngine {
    val displayName: String
    fun isAvailable(context: Context): Boolean
    fun listenOnce(context: Context): Flow<VoiceEvent>
    fun stop()
}