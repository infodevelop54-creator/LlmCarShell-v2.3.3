package com.example.llmcar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.llmcar.ui.theme.AccentHue
import com.example.llmcar.ui.theme.OverlayStyle
import com.example.llmcar.ui.theme.PillSize
import com.example.llmcar.ui.theme.PresetIconStyle
import kotlin.jvm.JvmName

enum class ActivationMode { VOICE, BUTTON }

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastModelPath by mutableStateOf(prefs.getString(KEY_LAST_MODEL, null)); private set
    var modelTreeUris by mutableStateOf(
        prefs.getStringSet(KEY_TREE_URIS, emptySet())?.toList().orEmpty()); private set

    var activationMode by mutableStateOf(
        ActivationMode.valueOf(prefs.getString(KEY_ACTIVATION, "BUTTON") ?: "BUTTON")); private set
    var floatingButtonEnabled by mutableStateOf(prefs.getBoolean(KEY_FLOATING, false)); private set

    var backgroundListening by mutableStateOf(prefs.getBoolean(KEY_BG_LISTEN, false)); private set
    var wakeWordPhrase by mutableStateOf(
        prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD); private set
    var voiceAutoSend by mutableStateOf(prefs.getBoolean(KEY_AUTOSEND, true)); private set
    var porcupineAccessKey by mutableStateOf(prefs.getString(KEY_PORCUPINE_KEY, null)); private set
    var porcupineKeywordAsset by mutableStateOf(prefs.getString(KEY_PORCUPINE_ASSET, null)); private set
    var porcupineModelAsset by mutableStateOf(prefs.getString(KEY_PORCUPINE_MODEL, null)); private set

    var currentPresetId by mutableStateOf(
        prefs.getString(KEY_PRESET_ID, PresetRepository.defaultId) ?: PresetRepository.defaultId); private set
    var customSystemPrompt by mutableStateOf(prefs.getString(KEY_CUSTOM_PROMPT, null)); private set
    val effectiveSystemPrompt: String
        get() = customSystemPrompt?.takeIf { it.isNotBlank() }
            ?: PresetRepository.byId(currentPresetId).prompt

    var asrEngine by mutableStateOf(prefs.getString(KEY_ASR_ENGINE, "google") ?: "google"); private set

    var ttsEnabled by mutableStateOf(prefs.getBoolean(KEY_TTS_ENABLED, true)); private set
    var ttsEngine by mutableStateOf(prefs.getString(KEY_TTS_ENGINE, null)); private set
    var ttsVoiceName by mutableStateOf(prefs.getString(KEY_TTS_VOICE, null)); private set
    var ttsRate by mutableFloatStateOf(prefs.getFloat(KEY_TTS_RATE, 1.0f)); private set
    var ttsPitch by mutableFloatStateOf(prefs.getFloat(KEY_TTS_PITCH, 1.0f)); private set
    var ttsStreamingPhrases by mutableStateOf(prefs.getBoolean(KEY_TTS_STREAMING, true)); private set

    var contextSize by mutableStateOf(prefs.getInt(KEY_CTX, 1024)); private set
    var kvCacheQuantized by mutableStateOf(prefs.getBoolean(KEY_KV_QUANT, true)); private set
    var mmapEnabled by mutableStateOf(prefs.getBoolean(KEY_MMAP, true)); private set
    var batchSize by mutableStateOf(prefs.getInt(KEY_BATCH, 256)); private set
    var threadCount by mutableStateOf(prefs.getInt(KEY_THREADS, 4)); private set
    var parallelSlots by mutableStateOf(prefs.getInt(KEY_SLOTS, 1)); private set
    var ramProfile by mutableStateOf(prefs.getString(KEY_RAM_PROFILE, "balanced") ?: "balanced"); private set

    var cloudSyncEnabled by mutableStateOf(prefs.getBoolean(KEY_CLOUD_ENABLED, false)); private set
    var cloudSyncEmail by mutableStateOf(prefs.getString(KEY_CLOUD_EMAIL, null)); private set
    var yandexMusicEnabled by mutableStateOf(prefs.getBoolean(KEY_YM_ENABLED, true)); private set

    // --------- Визуальные настройки ---------
    var accentHue by mutableStateOf(
        runCatching { AccentHue.valueOf(prefs.getString(KEY_ACCENT_HUE, "TEAL") ?: "TEAL") }
            .getOrDefault(AccentHue.TEAL)); private set
    var pillSize by mutableStateOf(
        runCatching { PillSize.valueOf(prefs.getString(KEY_PILL_SIZE, "NORMAL") ?: "NORMAL") }
            .getOrDefault(PillSize.NORMAL)); private set
    var presetIconStyle by mutableStateOf(
        runCatching { PresetIconStyle.valueOf(prefs.getString(KEY_PRESET_ICON, "SPARKLES") ?: "SPARKLES") }
            .getOrDefault(PresetIconStyle.SPARKLES)); private set
    var overlayStyle by mutableStateOf(
        runCatching { OverlayStyle.valueOf(prefs.getString(KEY_OVERLAY_STYLE, "GLOW") ?: "GLOW") }
            .getOrDefault(OverlayStyle.GLOW)); private set
    // --------- Позиция плавающей кнопки ---------
    var overlayPosX by mutableStateOf(prefs.getInt(KEY_OVERLAY_X, 40)); private set
    var overlayPosY by mutableStateOf(prefs.getInt(KEY_OVERLAY_Y, 300)); private set

    // --------- Сеттеры ---------

    @JvmName("applyLastModelPath")
    fun setLastModelPath(v: String?) { lastModelPath = v; prefs.edit().putString(KEY_LAST_MODEL, v).apply() }

    @JvmName("applyActivationMode")
    fun setActivationMode(v: ActivationMode) { activationMode = v; prefs.edit().putString(KEY_ACTIVATION, v.name).apply() }

    @JvmName("applyFloatingButtonEnabled")
    fun setFloatingButtonEnabled(v: Boolean) { floatingButtonEnabled = v; prefs.edit().putBoolean(KEY_FLOATING, v).apply() }

    @JvmName("applyBackgroundListening")
    fun setBackgroundListening(v: Boolean) { backgroundListening = v; prefs.edit().putBoolean(KEY_BG_LISTEN, v).apply() }

    @JvmName("applyWakeWordPhrase")
    fun setWakeWordPhrase(v: String) { wakeWordPhrase = v; prefs.edit().putString(KEY_WAKE_WORD, v).apply() }

    @JvmName("applyVoiceAutoSend")
    fun setVoiceAutoSend(v: Boolean) { voiceAutoSend = v; prefs.edit().putBoolean(KEY_AUTOSEND, v).apply() }

    @JvmName("applyPorcupineAccessKey")
    fun setPorcupineAccessKey(v: String?) { porcupineAccessKey = v; prefs.edit().putString(KEY_PORCUPINE_KEY, v).apply() }

    @JvmName("applyPorcupineKeywordAsset")
    fun setPorcupineKeywordAsset(v: String?) { porcupineKeywordAsset = v; prefs.edit().putString(KEY_PORCUPINE_ASSET, v).apply() }

    @JvmName("applyPorcupineModelAsset")
    fun setPorcupineModelAsset(v: String?) { porcupineModelAsset = v; prefs.edit().putString(KEY_PORCUPINE_MODEL, v).apply() }

    fun setPresetId(id: String) { currentPresetId = PresetRepository.byId(id).id; prefs.edit().putString(KEY_PRESET_ID, currentPresetId).apply() }

    @JvmName("applyCustomSystemPrompt")
    fun setCustomSystemPrompt(p: String?) { customSystemPrompt = p?.takeIf { it.isNotBlank() }; prefs.edit().putString(KEY_CUSTOM_PROMPT, customSystemPrompt).apply() }

    fun clearCustomSystemPrompt() { customSystemPrompt = null; prefs.edit().remove(KEY_CUSTOM_PROMPT).apply() }

    @JvmName("applyAsrEngine")
    fun setAsrEngine(id: String) { asrEngine = id; prefs.edit().putString(KEY_ASR_ENGINE, id).apply() }

    @JvmName("applyTtsEnabled")
    fun setTtsEnabled(v: Boolean) { ttsEnabled = v; prefs.edit().putBoolean(KEY_TTS_ENABLED, v).apply() }

    @JvmName("applyTtsEngine")
    fun setTtsEngine(p: String?) { ttsEngine = p; prefs.edit().putString(KEY_TTS_ENGINE, p).apply() }

    @JvmName("applyTtsVoiceName")
    fun setTtsVoiceName(n: String?) { ttsVoiceName = n; prefs.edit().putString(KEY_TTS_VOICE, n).apply() }

    @JvmName("applyTtsRate")
    fun setTtsRate(v: Float) { ttsRate = v.coerceIn(0.5f, 2.0f); prefs.edit().putFloat(KEY_TTS_RATE, ttsRate).apply() }

    @JvmName("applyTtsPitch")
    fun setTtsPitch(v: Float) { ttsPitch = v.coerceIn(0.5f, 2.0f); prefs.edit().putFloat(KEY_TTS_PITCH, ttsPitch).apply() }

    @JvmName("applyTtsStreamingPhrases")
    fun setTtsStreamingPhrases(v: Boolean) { ttsStreamingPhrases = v; prefs.edit().putBoolean(KEY_TTS_STREAMING, v).apply() }

    @JvmName("applyContextSize")
    fun setContextSize(v: Int) { contextSize = v; prefs.edit().putInt(KEY_CTX, v).apply() }

    @JvmName("applyKvCacheQuantized")
    fun setKvCacheQuantized(v: Boolean) { kvCacheQuantized = v; prefs.edit().putBoolean(KEY_KV_QUANT, v).apply() }

    @JvmName("applyMmapEnabled")
    fun setMmapEnabled(v: Boolean) { mmapEnabled = v; prefs.edit().putBoolean(KEY_MMAP, v).apply() }

    @JvmName("applyBatchSize")
    fun setBatchSize(v: Int) { batchSize = v; prefs.edit().putInt(KEY_BATCH, v).apply() }

    @JvmName("applyThreadCount")
    fun setThreadCount(v: Int) { threadCount = v; prefs.edit().putInt(KEY_THREADS, v).apply() }

    @JvmName("applyParallelSlots")
    fun setParallelSlots(v: Int) { parallelSlots = v; prefs.edit().putInt(KEY_SLOTS, v).apply() }

    @JvmName("applyCloudSyncEnabled")
    fun setCloudSyncEnabled(v: Boolean) { cloudSyncEnabled = v; prefs.edit().putBoolean(KEY_CLOUD_ENABLED, v).apply() }

    @JvmName("applyCloudSyncEmail")
    fun setCloudSyncEmail(v: String?) { cloudSyncEmail = v; prefs.edit().putString(KEY_CLOUD_EMAIL, v).apply() }

    @JvmName("applyYandexMusicEnabled")
    fun setYandexMusicEnabled(v: Boolean) { yandexMusicEnabled = v; prefs.edit().putBoolean(KEY_YM_ENABLED, v).apply() }

    // --------- Сеттеры визуальных настроек ---------

    @JvmName("applyAccentHue")
    fun setAccentHue(v: AccentHue) {
        accentHue = v
        prefs.edit().putString(KEY_ACCENT_HUE, v.name).apply()
    }

    @JvmName("applyPillSize")
    fun setPillSize(v: PillSize) {
        pillSize = v
        prefs.edit().putString(KEY_PILL_SIZE, v.name).apply()
    }

    @JvmName("applyPresetIconStyle")
    fun setPresetIconStyle(v: PresetIconStyle) {
        presetIconStyle = v
        prefs.edit().putString(KEY_PRESET_ICON, v.name).apply()
    }

    @JvmName("applyOverlayStyle")
    fun setOverlayStyle(v: OverlayStyle) {
        overlayStyle = v
        prefs.edit().putString(KEY_OVERLAY_STYLE, v.name).apply()
    }

        @JvmName("applyOverlayPosX")
    fun setOverlayPosX(v: Int) {
        overlayPosX = v
        prefs.edit().putInt(KEY_OVERLAY_X, v).apply()
    }

    @JvmName("applyOverlayPosY")
    fun setOverlayPosY(v: Int) {
        overlayPosY = v
        prefs.edit().putInt(KEY_OVERLAY_Y, v).apply()
    }

    fun applyRamProfile(profile: String) {
        ramProfile = profile
        prefs.edit().putString(KEY_RAM_PROFILE, profile).apply()
        when (profile) {
            "low" -> { setContextSize(512); setParallelSlots(1); setBatchSize(128)
                       setKvCacheQuantized(true); setMmapEnabled(true) }
            "performance" -> { setContextSize(2048); setParallelSlots(2); setBatchSize(512)
                               setKvCacheQuantized(false); setMmapEnabled(true) }
            else -> { setContextSize(1024); setParallelSlots(1); setBatchSize(256)
                      setKvCacheQuantized(true); setMmapEnabled(true) }
        }
    }

    fun addModelTreeUri(uri: String) {
        val s = prefs.getStringSet(KEY_TREE_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        s.add(uri); prefs.edit().putStringSet(KEY_TREE_URIS, s).apply(); modelTreeUris = s.toList()
    }

    fun removeModelTreeUri(uri: String) {
        val s = prefs.getStringSet(KEY_TREE_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        s.remove(uri); prefs.edit().putStringSet(KEY_TREE_URIS, s).apply(); modelTreeUris = s.toList()
    }

    companion object {
        private const val PREFS = "llm_settings"
        private const val KEY_LAST_MODEL = "last_model"
        private const val KEY_ACTIVATION = "activation_mode"
        private const val KEY_FLOATING = "floating_enabled"
        private const val KEY_BG_LISTEN = "bg_listen"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_AUTOSEND = "voice_autosend"
        private const val KEY_PORCUPINE_KEY = "porcupine_key"
        private const val KEY_PORCUPINE_ASSET = "porcupine_keyword_asset"
        private const val KEY_PORCUPINE_MODEL = "porcupine_model_asset"
        private const val KEY_PRESET_ID = "preset_id"
        private const val KEY_CUSTOM_PROMPT = "custom_system_prompt"
        private const val KEY_ASR_ENGINE = "asr_engine"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_STREAMING = "tts_streaming"
        private const val KEY_CTX = "ram_ctx"
        private const val KEY_KV_QUANT = "ram_kv_quant"
        private const val KEY_MMAP = "ram_mmap"
        private const val KEY_BATCH = "ram_batch"
        private const val KEY_THREADS = "ram_threads"
        private const val KEY_SLOTS = "ram_slots"
        private const val KEY_RAM_PROFILE = "ram_profile"
        private const val KEY_CLOUD_ENABLED = "cloud_sync_enabled"
        private const val KEY_CLOUD_EMAIL = "cloud_sync_email"
        private const val KEY_YM_ENABLED = "yandex_music_enabled"
        private const val KEY_TREE_URIS = "model_tree_uris"

        // --------- Ключи визуальных настроек ---------
        private const val KEY_ACCENT_HUE = "accent_hue"
        private const val KEY_PILL_SIZE = "pill_size"
        private const val KEY_PRESET_ICON = "preset_icon"
        private const val KEY_OVERLAY_STYLE = "overlay_style"
        // --------- Ключи позиции оверлея ---------
        private const val KEY_OVERLAY_X = "overlay_pos_x"
        private const val KEY_OVERLAY_Y = "overlay_pos_y"

        const val DEFAULT_WAKE_WORD = "привет ассистент"
    }
}
