package com.example.llmcar.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSignals {

    private val _ttsSpeaking = MutableStateFlow(false)
    val ttsSpeaking: StateFlow<Boolean> = _ttsSpeaking.asStateFlow()
    fun setTtsSpeaking(v: Boolean) { _ttsSpeaking.value = v }

    private val _micTrigger = MutableStateFlow(0L)
    val micTrigger: StateFlow<Long> = _micTrigger.asStateFlow()
    fun fireMicTrigger() { _micTrigger.value = System.currentTimeMillis() }

    private val _presetChanged = MutableStateFlow(0L)
    val presetChanged: StateFlow<Long> = _presetChanged.asStateFlow()
    fun firePresetChanged() { _presetChanged.value = System.currentTimeMillis() }

    private val _obdLive = MutableStateFlow<Pair<String, Map<String, String>>>(
        "Остановлено" to emptyMap()
    )
    val obdLive: StateFlow<Pair<String, Map<String, String>>> = _obdLive.asStateFlow()
    fun pushObdLive(s: String, d: Map<String, String>) { _obdLive.value = s to d }
}