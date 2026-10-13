package com.example.llmcar.service

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.*
import androidx.core.app.NotificationCompat
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.R
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.util.AppSignals
import com.example.llmcar.voice.PorcupineWakeWordEngine
import com.example.llmcar.voice.WakeWordMatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

class VoiceActivationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sr: SpeechRecognizer? = null
    private var porcupine: PorcupineWakeWordEngine? = null
    private var running = false
    private var suppress = false
    private var lastTrigger = 0L
    private var lastPreset = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, notif("Инициализация…"))
        scope.launch {
            AppSignals.ttsSpeaking.collect { s ->
                suppress = s
                if (s) stopListening()
                else if (running) handler.postDelayed(
                    { if (running && !suppress) startListening() }, RESUME_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        if (!suppress) startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (suppress) return
        stopListening()
        val s = SettingsRepository(this)
        val key = s.porcupineAccessKey
        if (!key.isNullOrBlank()) {
            val e = PorcupineWakeWordEngine(this, key) {
                handler.post { onWake("porcupine") }
            }
            val r = e.start(s.porcupineKeywordAsset, s.porcupineModelAsset)
            if (r.isSuccess) {
                porcupine = e
                _status.value = "Porcupine (offline)"
                return
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            _status.value = "ASR недоступен"; return
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        sr = rec
        rec.setRecognitionListener(listener)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        runCatching { rec.startListening(i) }.onFailure { restart(1500) }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {
            val phrase = SettingsRepository(this@VoiceActivationService).wakeWordPhrase
            _status.value = getString(R.string.voice_state_listening, phrase)
            updateNotif(getString(R.string.voice_state_listening, phrase))
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(r: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(e: Int) { if (!suppress) restart(500) }
        override fun onResults(r: Bundle?) {
            if (!suppress) {
                handle(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                restart(300)
            }
        }
        override fun onPartialResults(p: Bundle?) {
            if (!suppress) handle(p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        }
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private fun handle(cands: List<String>?) {
        if (cands.isNullOrEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastTrigger < COOLDOWN_MS) return

        if (now - lastPreset > PRESET_COOLDOWN_MS) {
            for (c in cands) {
                val p = PresetRepository.matchByVoice(c, WakeWordMatcher::normalize) ?: continue
                lastPreset = now
                val s = SettingsRepository(this)
                if (s.currentPresetId != p.id) {
                    s.setPresetId(p.id)
                    AppSignals.firePresetChanged()
                    _status.value = "Режим: ${p.displayName}"
                    updateNotif("Режим: ${p.displayName}")
                }
                return
            }
        }

        val phrase = SettingsRepository(this).wakeWordPhrase
        if (phrase.isNotBlank() && cands.any { WakeWordMatcher.matches(it, phrase) })
            onWake("google")
    }

    private fun onWake(src: String) {
        lastTrigger = System.currentTimeMillis()
        _status.value = "Wake-word ($src)"
        updateNotif(getString(R.string.voice_state_detected))
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_AUTO_MIC, true)
        })
        stopListening()
        handler.postDelayed({ if (running && !suppress) startListening() }, 4000)
    }

    private fun restart(ms: Long) {
        handler.postDelayed({ if (running && !suppress) startListening() }, ms)
    }

    private fun stopListening() {
        runCatching { sr?.cancel() }
        runCatching { sr?.destroy() }
        sr = null
        porcupine?.stop()
        porcupine = null
    }

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_VOICE)
            .setContentTitle(getString(R.string.voice_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun updateNotif(t: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(t))
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopListening()
        scope.cancel()
        _status.value = getString(R.string.voice_state_idle)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1003
        private const val COOLDOWN_MS = 3000L
        private const val PRESET_COOLDOWN_MS = 2000L
        private const val RESUME_MS = 800L
        private val _status = MutableStateFlow("Не активно")
        val status: StateFlow<String> = _status.asStateFlow()
    }
}