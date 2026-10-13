package com.example.llmcar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmcar.data.HistoryRepository
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.integrations.ObdRepository
import com.example.llmcar.net.ChatMessage
import com.example.llmcar.net.LlmApiClient
import com.example.llmcar.net.StreamChunk
import com.example.llmcar.tools.LlmTools
import com.example.llmcar.tools.ToolExecutor
import com.example.llmcar.ui.components.OrbState
import com.example.llmcar.util.AppSignals
import com.example.llmcar.voice.StreamingTtsSplitter
import com.example.llmcar.voice.TtsController
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val content: String)

private data class ToolCallAccum(
    var id: String = "",
    var name: String = "",
    val args: StringBuilder = StringBuilder()
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val history = HistoryRepository(app)
    private val tts = TtsController(app, settings)
    private val obd = ObdRepository(app)
    private val toolExecutor = ToolExecutor(app, obd)

    val messages = mutableStateListOf<UiMessage>()
    var isSending by mutableStateOf(false); private set
    var orbState by mutableStateOf(OrbState.Idle); private set
    var liveText by mutableStateOf(""); private set
    var errorText by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch { history.ensureMigrated() }
    }

    fun settingsRepo(): SettingsRepository = settings

    fun activePresetName(): String =
        PresetRepository.byId(settings.currentPresetId).displayName

    fun send(text: String) {
        if (text.isBlank() || isSending) return
        viewModelScope.launch { runConversation(text) }
    }

    fun stopTts() {
        tts.stop()
        orbState = if (isSending) OrbState.Thinking else OrbState.Idle
    }

    fun speak(text: String) { if (settings.ttsEnabled) tts.speak(text) }

    private suspend fun runConversation(userText: String) {
        isSending = true
        errorText = null
        messages.add(UiMessage("user", userText))

        // Автовыбор пресета по содержанию запроса. Если сработал —
        // молча переключаем; UI узнает через AppSignals.presetChanged.
        // Текущий пресет "assistant" и "free" не трогаем без явных триггеров.
        val autoPreset = PresetRepository.matchByText(userText)
        if (autoPreset != null && settings.currentPresetId != autoPreset.id) {
            settings.setPresetId(autoPreset.id)
            AppSignals.firePresetChanged()
            android.util.Log.i(
                "ChatVM",
                "Авто-пресет по тексту: ${autoPreset.displayName}"
            )
        }

        val effectivePrompt = settings.effectiveSystemPrompt
        val historyMsgs = mutableListOf<ChatMessage>()
        if (effectivePrompt.isNotBlank())
            historyMsgs.add(ChatMessage("system", effectivePrompt))
        historyMsgs.addAll(messages.map { ChatMessage(it.role, it.content) })

        // useTools = false, потому что llama-server@b4400 не поддерживает
        // OpenAI-параметр "tools" в /v1/chat/completions. При переходе
        // на b5096 можно вернуть true — fallback ниже сам переключится.
        var useTools = false
        var rounds = 0
        var finalAnswer = ""

        while (rounds < 4) {
            rounds++
            val splitter = StreamingTtsSplitter()
            val toolAccums = sortedMapOf<Int, ToolCallAccum>()
            val assistantBuffer = StringBuilder()
            var failure: Throwable? = null
            var firstSentenceSent = false

            orbState = OrbState.Thinking

            LlmApiClient.chatStream(
                historyMsgs,
                if (useTools) LlmTools.definitions else null
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Text -> {
                        assistantBuffer.append(chunk.text)
                        liveText = assistantBuffer.toString()
                        if (orbState != OrbState.Speaking) orbState = OrbState.Speaking
                        val sentences = splitter.feed(chunk.text)
                        if (settings.ttsEnabled && settings.ttsStreamingPhrases) {
                            sentences.forEach { s ->
                                if (firstSentenceSent) tts.speakAppend(s)
                                else { tts.speak(s); firstSentenceSent = true }
                            }
                        }
                    }
                    is StreamChunk.ToolCallDelta -> {
                        val acc = toolAccums.getOrPut(chunk.index) { ToolCallAccum() }
                        chunk.id?.let { acc.id = it }
                        chunk.name?.let { acc.name = it }
                        chunk.argsDelta?.let { acc.args.append(it) }
                    }
                    is StreamChunk.Failure -> failure = chunk.error
                    StreamChunk.Done -> {}
                }
            }

            val failureLocal = failure
            if (failureLocal != null) {
                val msg = failureLocal.message.orEmpty()

                // Fallback на случай, если backend вернёт tools-ограничение.
                if (useTools && msg.contains("Unsupported param: tools", ignoreCase = true)) {
                    android.util.Log.w(
                        "ChatVM",
                        "llama-server не поддерживает tools — повтор без tools"
                    )
                    useTools = false
                    rounds--
                    liveText = ""
                    errorText = null
                    continue
                }

                errorText = msg
                messages.add(UiMessage("assistant", "Ошибка: $msg"))
                break
            }

            val tail = splitter.flush()
            if (settings.ttsEnabled && settings.ttsStreamingPhrases && tail.isNotEmpty()) {
                if (firstSentenceSent) tts.speakAppend(tail) else tts.speak(tail)
            }

            if (toolAccums.isEmpty()) {
                finalAnswer = assistantBuffer.toString()
                messages.add(UiMessage("assistant", finalAnswer))
                if (settings.ttsEnabled && !settings.ttsStreamingPhrases
                    && finalAnswer.isNotBlank()) tts.speak(finalAnswer)
                break
            }

            toolAccums.forEach { (_, tc) ->
                if (tc.name.isBlank()) return@forEach
                val result = toolExecutor.execute(tc.name, tc.args.toString())
                messages.add(UiMessage("assistant", "[${tc.name}] $result"))
                historyMsgs.add(ChatMessage("user", "Результат ${tc.name}: $result"))
            }
        }

        liveText = ""
        if (finalAnswer.isNotBlank()) runCatching { history.save(userText, finalAnswer) }
        if (orbState == OrbState.Thinking || orbState == OrbState.Speaking)
            orbState = OrbState.Idle
        isSending = false
    }

    override fun onCleared() {
        tts.shutdown()
        obd.disconnect()
        try {
            Class.forName("com.example.llmcar.voice.VoskAsrEngine")
                .getMethod("releaseModel")
                .invoke(null)
        } catch (_: Exception) {}
        super.onCleared()
    }
}
