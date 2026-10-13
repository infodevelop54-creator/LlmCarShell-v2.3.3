package com.example.llmcar.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

open class GoogleOnlineAsrEngine : AsrEngine {

    override val displayName = "Google SpeechRecognizer (online)"

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun listenOnce(context: Context): Flow<VoiceEvent> = callbackFlow {
        if (!isAvailable(context)) {
            trySend(VoiceEvent.Error(-1, "SpeechRecognizer недоступен"))
            close(); return@callbackFlow
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(buildListener(this))

        val intent = buildIntent().apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
        }
        runCatching { sr.startListening(intent) }

        awaitClose { destroy() }
    }

    override fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    protected open fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun buildListener(ch: ProducerScope<VoiceEvent>) =
        object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                ch.trySend(VoiceEvent.ReadyForSpeech)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {
                ch.trySend(VoiceEvent.RmsChanged(r))
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) {
                ch.trySend(VoiceEvent.Error(e, mapError(e))); ch.close()
            }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                ch.trySend(VoiceEvent.Final(t)); ch.close()
            }
            override fun onPartialResults(p: Bundle?) {
                val t = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (t.isNotEmpty()) ch.trySend(VoiceEvent.Partial(t))
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        }

    private fun destroy() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun mapError(c: Int) = when (c) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет RECORD_AUDIO"
        SpeechRecognizer.ERROR_NETWORK -> "Сеть недоступна"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сетевой таймаут"
        SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина"
        else -> "Код $c"
    }
}