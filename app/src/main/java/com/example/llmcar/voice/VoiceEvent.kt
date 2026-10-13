package com.example.llmcar.voice

sealed class VoiceEvent {
    data object ReadyForSpeech : VoiceEvent()
    data class RmsChanged(val rmsDb: Float) : VoiceEvent()
    data class Partial(val text: String) : VoiceEvent()
    data class Final(val text: String) : VoiceEvent()
    data class Error(val code: Int, val message: String) : VoiceEvent()
}