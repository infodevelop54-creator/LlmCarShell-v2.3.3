package com.example.llmcar.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.util.AppSignals
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class TtsController(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentEngine: String? = null
    private var pending: String? = null

    private val seq = AtomicInteger(0)
    private val active = AtomicInteger(0)

    private val onInit = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            applyListener()
            applySettings()
            pending?.let { speak(it) }
            pending = null
        }
    }

    private fun ensureEngine() {
        val desired = settings.ttsEngine
        if (tts != null && currentEngine == desired) return
        runCatching { tts?.shutdown() }
        tts = null; ready = false; currentEngine = desired
        tts = if (desired != null)
            TextToSpeech(context.applicationContext, onInit, desired)
        else
            TextToSpeech(context.applicationContext, onInit)
    }

    private fun applyListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                active.incrementAndGet(); AppSignals.setTtsSpeaking(true)
            }
            override fun onDone(id: String?) { dec() }
            @Deprecated("Deprecated")
            override fun onError(id: String?) { dec() }
            override fun onError(id: String?, e: Int) { dec() }
            override fun onStop(id: String?, i: Boolean) { dec() }
        })
    }

    private fun dec() {
        if (active.decrementAndGet() <= 0) {
            active.set(0); AppSignals.setTtsSpeaking(false)
        }
    }

    private fun applySettings() {
        val t = tts ?: return
        settings.ttsVoiceName?.let { n ->
            val v: Voice? = runCatching {
                t.voices?.firstOrNull { it.name == n }
            }.getOrNull()
            if (v != null) runCatching { t.voice = v }
        } ?: run {
            runCatching { t.language = Locale("ru", "RU") }
        }
        runCatching { t.setSpeechRate(settings.ttsRate) }
        runCatching { t.setPitch(settings.ttsPitch) }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        ensureEngine()
        if (!ready) { pending = text; return }
        applySettings()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "llmcar-${seq.incrementAndGet()}")
    }

    fun speakAppend(text: String) {
        if (text.isBlank()) return
        ensureEngine()
        if (!ready) { pending = text; return }
        applySettings()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "llmcar-${seq.incrementAndGet()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
        active.set(0)
        AppSignals.setTtsSpeaking(false)
    }

    fun shutdown() {
        pending = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null; ready = false; currentEngine = null
        active.set(0); AppSignals.setTtsSpeaking(false)
    }

    fun hasRussianVoice(): Boolean {
        val t = tts ?: return false
        return runCatching {
            val ru = Locale("ru", "RU")
            t.voices?.any { it.locale.language == ru.language } == true
                    || t.isLanguageAvailable(ru) >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
    }

    companion object {
        fun listEnginesAsync(
            c: Context,
            onReady: (List<TextToSpeech.EngineInfo>) -> Unit
        ) {
            val h = arrayOfNulls<TextToSpeech>(1)
            h[0] = TextToSpeech(c.applicationContext) { s ->
                val e = if (s == TextToSpeech.SUCCESS)
                    runCatching { h[0]?.engines.orEmpty() }.getOrDefault(emptyList())
                else emptyList()
                onReady(e)
                runCatching { h[0]?.shutdown() }
                h[0] = null
            }
        }

        fun listVoicesAsync(
            c: Context,
            engine: String?,
            onReady: (List<Voice>) -> Unit
        ) {
            val h = arrayOfNulls<TextToSpeech>(1)
            val l = TextToSpeech.OnInitListener { s ->
                val all = if (s == TextToSpeech.SUCCESS)
                    runCatching { h[0]?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                else emptyList()
                val ru = all.filter { it.locale.language == "rus" || it.locale.language == "ru" }
                onReady(if (ru.isNotEmpty()) ru else all)
                runCatching { h[0]?.shutdown() }
                h[0] = null
            }
            h[0] = if (engine != null) TextToSpeech(c.applicationContext, l, engine)
                   else TextToSpeech(c.applicationContext, l)
        }
    }
}