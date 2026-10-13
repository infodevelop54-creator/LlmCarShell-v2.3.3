package com.example.llmcar.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.data.CloudSyncRepository
import com.example.llmcar.data.DownloadRepository
import com.example.llmcar.data.DownloadState
import com.example.llmcar.data.HfUrlParser
import com.example.llmcar.data.ModelEntry
import com.example.llmcar.data.ModelRepository
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.integrations.DtcResult
import com.example.llmcar.integrations.MediaController
import com.example.llmcar.integrations.ObdRepository
import com.example.llmcar.integrations.ShizukuBridge
import com.example.llmcar.service.CarAccessibilityService
import com.example.llmcar.service.FloatingButtonService
import com.example.llmcar.service.LlmForegroundService
import com.example.llmcar.service.LlmStatus
import com.example.llmcar.service.VoiceActivationService
import com.example.llmcar.ui.theme.AccentHue
import com.example.llmcar.ui.theme.OverlayStyle
import com.example.llmcar.ui.theme.PillSize
import com.example.llmcar.ui.theme.PresetIconStyle
import com.example.llmcar.ui.theme.VisualTheme
import com.example.llmcar.util.AppSignals
import com.example.llmcar.util.OverlayPermission
import com.example.llmcar.voice.AsrEngineFactory
import com.example.llmcar.voice.TtsController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serviceStatus: LlmStatus,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val modelRepo = remember { ModelRepository(context) }
    val downloadRepo = remember { DownloadRepository(context) }
    val obdRepo = remember { ObdRepository(context) }
    val scope = rememberCoroutineScope()

    // ---------- Состояние: модели ----------
    var models by remember { mutableStateOf<List<ModelEntry>>(emptyList()) }
    var urlInput by remember { mutableStateOf("") }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var copyingModel by remember { mutableStateOf<String?>(null) }

    // ---------- Состояние: TTS ----------
    var engines by remember { mutableStateOf<List<TextToSpeech.EngineInfo>>(emptyList()) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var previewTts by remember { mutableStateOf<TtsController?>(null) }

    // ---------- Состояние: OBD ----------
    var obdMac by remember { mutableStateOf("") }
    var obdStatus by remember { mutableStateOf("Не подключено") }
    var storedDtcs by remember { mutableStateOf<List<DtcResult>>(emptyList()) }
    var pendingDtcs by remember { mutableStateOf<List<DtcResult>>(emptyList()) }
    var permanentDtcs by remember { mutableStateOf<List<DtcResult>>(emptyList()) }
    var dtcReadDone by remember { mutableStateOf(false) }
    var confirmClearDtc by remember { mutableStateOf(false) }

    // ---------- Состояние: персона ----------
    var promptDraft by remember {
        mutableStateOf(
            settingsRepo.customSystemPrompt
                ?: PresetRepository.byId(settingsRepo.currentPresetId).prompt
        )
    }
    var promptEdited by remember { mutableStateOf(false) }

    // ---------- Состояние: медиа-тест ----------
    var mediaVolumePercent by remember { mutableStateOf(MediaController(context).currentVolumePercent()) }

    fun refreshModels() {
        models = modelRepo.scanAll(settingsRepo.modelTreeUris)
    }

    LaunchedEffect(Unit) {
        refreshModels()
        TtsController.listEnginesAsync(context) { engines = it }
    }

    LaunchedEffect(settingsRepo.ttsEngine) {
        TtsController.listVoicesAsync(context, settingsRepo.ttsEngine) { voices = it }
    }

    LaunchedEffect(settingsRepo.currentPresetId) {
        if (!promptEdited) {
            promptDraft = settingsRepo.customSystemPrompt
                ?: PresetRepository.byId(settingsRepo.currentPresetId).prompt
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsRepo.addModelTreeUri(it.toString())
            refreshModels()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewTts?.shutdown()
            obdRepo.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ================================================================
        // Верхняя панель
        // ================================================================
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Настройки", fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.History, null)
            Spacer(Modifier.width(8.dp))
            Text("История запросов")
        }

        Spacer(Modifier.height(20.dp))

        // ================================================================
        // 1. Персона ассистента
        // ================================================================
        SectionTitle("Ассистент (персона)")

        Text(
            "Выберите режим работы. Пресеты также переключаются голосом: " +
                    "«ассистент», «диагност», «навигатор», «медиа», «свободный». " +
                    "Плюс — автоматически по смыслу запроса.",
            fontSize = 12.sp, color = Color(0xFF9090A0)
        )
        Spacer(Modifier.height(10.dp))

        PresetRepository.presets.forEach { preset ->
            val selected = settingsRepo.currentPresetId == preset.id
            Surface(
                color = if (selected) Color(0xFF2D2545) else Color(0xFF252535),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable {
                        settingsRepo.setPresetId(preset.id)
                        AppSignals.firePresetChanged()
                        promptEdited = false
                        promptDraft = settingsRepo.customSystemPrompt
                            ?: PresetRepository.byId(preset.id).prompt
                    }
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            preset.displayName,
                            fontSize = 15.sp,
                            color = Color.White,
                            fontWeight = if (selected) FontWeight.SemiBold
                                         else FontWeight.Normal
                        )
                        Text(
                            "Триггеры: " + preset.voiceTriggers.joinToString(", "),
                            fontSize = 11.sp, color = Color(0xFF9090A0)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = promptDraft,
            onValueChange = {
                promptDraft = it
                promptEdited = true
                // Автосохранение промпта на лету
                settingsRepo.setCustomSystemPrompt(it.ifBlank { null })
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            label = { Text("Системный промпт (сохраняется на лету)") },
            maxLines = 10
        )

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    settingsRepo.setCustomSystemPrompt(promptDraft.ifBlank { null })
                    promptEdited = false
                },
                modifier = Modifier.weight(1f)
            ) { Text("Сохранить") }

            OutlinedButton(
                onClick = {
                    settingsRepo.clearCustomSystemPrompt()
                    promptDraft = PresetRepository.byId(settingsRepo.currentPresetId).prompt
                    promptEdited = false
                },
                modifier = Modifier.weight(1f)
            ) { Text("Сбросить к пресету") }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 1b. Визуальные настройки
        // ================================================================
        SectionTitle("Визуальные настройки")

        Text(
            "Цвет и внешний вид поля ввода, а также стиль голосового оверлея.",
            fontSize = 12.sp, color = Color(0xFF9090A0)
        )
        Spacer(Modifier.height(10.dp))

        // ----- Оттенок -----
        VisualChoiceRow(
            title = "Оттенок интерфейса",
            options = AccentHue.values().toList(),
            selected = settingsRepo.accentHue,
            labelOf = { VisualTheme.label(it) },
            colorOf = { VisualTheme.palette(it).accent },
            onSelected = { settingsRepo.setAccentHue(it) }
        )
        Spacer(Modifier.height(8.dp))

        // ----- Размер пилюли -----
        VisualChoiceRow(
            title = "Размер поля ввода",
            options = PillSize.values().toList(),
            selected = settingsRepo.pillSize,
            labelOf = { VisualTheme.label(it) },
            colorOf = { null },
            onSelected = { settingsRepo.setPillSize(it) }
        )
        Spacer(Modifier.height(8.dp))

        // ----- Иконка пресета -----
        VisualChoiceRow(
            title = "Иконка режима (слева в пилюле)",
            options = PresetIconStyle.values().toList(),
            selected = settingsRepo.presetIconStyle,
            labelOf = { VisualTheme.label(it) },
            colorOf = { null },
            onSelected = { settingsRepo.setPresetIconStyle(it) }
        )
        Spacer(Modifier.height(8.dp))

        // ----- Стиль оверлея -----
        VisualChoiceRow(
            title = "Стиль голосового оверлея",
            options = OverlayStyle.values().toList(),
            selected = settingsRepo.overlayStyle,
            labelOf = { VisualTheme.label(it) },
            colorOf = { null },
            onSelected = { settingsRepo.setOverlayStyle(it) }
        )

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 2. Оптимизация памяти
        // ================================================================
        SectionTitle("Оптимизация памяти")

        Text(
            "Профили: Low — минимум RAM (~1 ГБ), Balanced — компромисс, " +
                    "Performance — качество.",
            fontSize = 12.sp, color = Color(0xFF9090A0)
        )
        Spacer(Modifier.height(10.dp))

        listOf(
            Triple("low", "Low (≤2 ГБ RAM)", "ctx=512 · 1 слот · kv=q8"),
            Triple("balanced", "Balanced (2–4 ГБ RAM)", "ctx=1024 · 1 слот · kv=q8"),
            Triple("performance", "Performance (≥4 ГБ RAM)", "ctx=2048 · 2 слота · kv=fp16")
        ).forEach { (id, label, sub) ->
            val selected = settingsRepo.ramProfile == id
            Surface(
                color = if (selected) Color(0xFF2D2545) else Color(0xFF252535),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { settingsRepo.applyRamProfile(id) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected,
                        onClick = { settingsRepo.applyRamProfile(id) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 14.sp, color = Color.White)
                        Text(sub, fontSize = 11.sp, color = Color(0xFF9090A0))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Тонкая настройка:", fontSize = 13.sp, color = Color(0xFFB0B0C0))
        Spacer(Modifier.height(6.dp))

        RamChoice("Размер контекста (KV cache)", listOf(512, 1024, 2048, 4096),
            settingsRepo.contextSize) { settingsRepo.setContextSize(it) }

        RamChoice("Параллельные слоты", listOf(1, 2, 4),
            settingsRepo.parallelSlots) { settingsRepo.setParallelSlots(it) }

        RamChoice("Batch size", listOf(128, 256, 512, 1024),
            settingsRepo.batchSize) { settingsRepo.setBatchSize(it) }

        RamChoice("CPU потоки", listOf(2, 3, 4, 6, 8),
            settingsRepo.threadCount) { settingsRepo.setThreadCount(it) }

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Квантование KV cache (q8_0)",
                        fontSize = 14.sp, color = Color.White)
                    Text("Экономит ~50% памяти KV-cache",
                        fontSize = 11.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.kvCacheQuantized,
                    onCheckedChange = { settingsRepo.setKvCacheQuantized(it) }
                )
            }
        }

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("mmap весов модели", fontSize = 14.sp, color = Color.White)
                    Text("Ленивая загрузка через page cache",
                        fontSize = 11.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.mmapEnabled,
                    onCheckedChange = { settingsRepo.setMmapEnabled(it) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 3. Управление моделями
        // ================================================================
        SectionTitle("Управление моделями")

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL HuggingFace или user/repo/file.gguf") },
            placeholder = { Text("https://huggingface.co/…/resolve/main/model.gguf") },
            singleLine = true,
            enabled = downloadState !is DownloadState.Running
        )
        Spacer(Modifier.height(8.dp))

        when (val st = downloadState) {
            is DownloadState.Running -> {
                LinearProgressIndicator(
                    progress = { st.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Загрузка: ${st.percent}% (${formatSize(st.bytes)} / ${formatSize(st.total)})",
                    fontSize = 12.sp, color = Color(0xFFB0B0C0)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        downloadRepo.cancel()
                        downloadJob?.cancel()
                        downloadState = DownloadState.Idle
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отменить загрузку") }
            }
            is DownloadState.Done -> {
                Text(
                    "Файл сохранён: ${st.file.name}",
                    fontSize = 13.sp, color = Color(0xFF4CAF50)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        downloadState = DownloadState.Idle
                        refreshModels()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Скачать ещё") }
            }
            is DownloadState.Failed -> {
                Text("Ошибка: ${st.reason}", fontSize = 13.sp, color = Color(0xFFF44336))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { downloadState = DownloadState.Idle },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Повторить") }
            }
            DownloadState.Idle -> {
                Button(
                    onClick = {
                        val parsed = HfUrlParser.parse(urlInput)
                        if (parsed.isFailure) {
                            downloadState = DownloadState.Failed(
                                parsed.exceptionOrNull()?.message ?: "Неверный ввод"
                            )
                            return@Button
                        }
                        val (url, name) = parsed.getOrThrow()
                        downloadJob?.cancel()
                        downloadJob = scope.launch {
                            try {
                                downloadRepo.download(url, name).collect { s ->
                                    downloadState = s
                                    if (s is DownloadState.Done) refreshModels()
                                }
                            } catch (_: CancellationException) {
                            } catch (e: Exception) {
                                downloadState = DownloadState.Failed(e.message ?: "Ошибка")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Скачать") }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Папки моделей", fontSize = 15.sp, color = Color.White,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Модели ищутся в /sdcard/Models, /sdcard/Download и папке приложения. " +
                    "Добавьте любую папку (SD-карта, USB, облако) через SAF.",
            fontSize = 12.sp, color = Color(0xFF9090A0)
        )
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { pickFolder.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить папку…")
        }

        settingsRepo.modelTreeUris.forEach { uri ->
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color(0xFF252535),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SAF-папка", fontSize = 13.sp, color = Color.White)
                        Text(uri, fontSize = 10.sp, color = Color(0xFF9090A0), maxLines = 1)
                    }
                    IconButton(onClick = {
                        settingsRepo.removeModelTreeUri(uri)
                        refreshModels()
                    }) {
                        Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFF44336))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Обнаруженные модели", fontSize = 15.sp, color = Color.White,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        if (models.isEmpty()) {
            Text(
                "Модели не найдены. Скачайте по URL, добавьте папку или " +
                        "разместите .gguf в /sdcard/Models.",
                fontSize = 13.sp, color = Color(0xFF808090)
            )
        } else {
            models.forEach { entry ->
                ModelRow(
                    entry = entry,
                    isActive = serviceStatus is LlmStatus.Ready
                            && settingsRepo.lastModelPath == entry.path,
                    onLoad = {
                        scope.launch {
                            copyingModel = entry.displayName
                            val file = if (entry.isSaf) {
                                val f = modelRepo.materializeSaf(entry)
                                if (f == null) {
                                    copyingModel = null
                                    return@launch
                                }
                                f
                            } else File(entry.path)
                            copyingModel = null

                            val intent = Intent(
                                context, LlmForegroundService::class.java
                            ).apply {
                                action = LlmForegroundService.ACTION_LOAD_MODEL
                                putExtra(
                                    LlmForegroundService.EXTRA_MODEL_PATH,
                                    file.absolutePath
                                )
                            }
                            context.startForegroundService(intent)
                            settingsRepo.setLastModelPath(file.absolutePath)
                            refreshModels()
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        copyingModel?.let {
            Spacer(Modifier.height(6.dp))
            Text("Копирование: $it", fontSize = 12.sp, color = Color(0xFFFFC107))
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 4. Голос и активация
        // ================================================================
        SectionTitle("Голос и активация")

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Постоянное прослушивание в фоне",
                        fontSize = 15.sp, color = Color.White)
                    Text("Слушать микрофон, пока приложение закрыто",
                        fontSize = 12.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.backgroundListening,
                    onCheckedChange = { on ->
                        settingsRepo.setBackgroundListening(on)
                        val intent = Intent(context, VoiceActivationService::class.java)
                        if (on) context.startForegroundService(intent)
                        else context.stopService(intent)
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = settingsRepo.wakeWordPhrase,
            onValueChange = { settingsRepo.setWakeWordPhrase(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ключевая фраза (wake word)") },
            placeholder = { Text("например: привет ассистент") },
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Автоотправка распознанного текста",
                        fontSize = 15.sp, color = Color.White)
                    Text("Отправлять в LLM сразу после финала",
                        fontSize = 12.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.voiceAutoSend,
                    onCheckedChange = { settingsRepo.setVoiceAutoSend(it) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 5. Распознавание речи (ASR)
        // ================================================================
        SectionTitle("Распознавание речи (ASR)")

        Text(
            "Выберите движок для голосового ввода. Offline-движки работают без интернета.",
            fontSize = 12.sp, color = Color(0xFF9090A0)
        )
        Spacer(Modifier.height(10.dp))

        val engineList = remember { AsrEngineFactory.list(context) }
        engineList.forEach { info ->
            val selected = settingsRepo.asrEngine == info.id
            val enabled = info.available
            Surface(
                color = if (selected) Color(0xFF2D2545) else Color(0xFF252535),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(enabled = enabled) {
                        settingsRepo.setAsrEngine(info.id)
                    }
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            if (enabled) settingsRepo.setAsrEngine(info.id)
                        },
                        enabled = enabled
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info.displayName,
                            fontSize = 14.sp,
                            color = if (enabled) Color.White else Color(0xFF606070),
                            fontWeight = if (selected) FontWeight.SemiBold
                                         else FontWeight.Normal
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (info.offline) Color(0xFF4CAF50)
                                                else Color(0xFF2196F3),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    !enabled -> "Недоступен на устройстве"
                                    info.offline -> "Offline"
                                    else -> "Требует интернет"
                                },
                                fontSize = 11.sp,
                                color = if (enabled) Color(0xFF9090A0)
                                        else Color(0xFF606070)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Vosk требует установки модели в assets/. " +
                    "Google offline требует скачанного языкового пакета в настройках Android.",
            fontSize = 11.sp, color = Color(0xFF808090)
        )

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 6. Porcupine
        // ================================================================
        SectionTitle("Porcupine (offline wake-word)")

        OutlinedTextField(
            value = settingsRepo.porcupineAccessKey.orEmpty(),
            onValueChange = {
                settingsRepo.setPorcupineAccessKey(it.ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("AccessKey Picovoice") },
            placeholder = { Text("console.picovoice.ai") },
            singleLine = true
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (settingsRepo.porcupineAccessKey.isNullOrBlank())
                "Google ASR fallback (требует интернет)"
            else "Porcupine активен (offline)",
            fontSize = 12.sp,
            color = if (settingsRepo.porcupineAccessKey.isNullOrBlank())
                Color(0xFFFFC107) else Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = settingsRepo.porcupineKeywordAsset.orEmpty(),
            onValueChange = {
                settingsRepo.setPorcupineKeywordAsset(it.ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Путь к .ppn в assets") },
            placeholder = { Text("porcupine/privet_assisent_ru.ppn") },
            singleLine = true
        )
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = settingsRepo.porcupineModelAsset.orEmpty(),
            onValueChange = {
                settingsRepo.setPorcupineModelAsset(it.ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Путь к .pv (русская модель)") },
            placeholder = { Text("porcupine/porcupine_params_ru.pv") },
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 7. Синтез речи (TTS)
        // ================================================================
        SectionTitle("Синтез речи (TTS)")

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Озвучивать ответы модели",
                        fontSize = 15.sp, color = Color.White)
                    Text("Тумблер также доступен в чате",
                        fontSize = 12.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.ttsEnabled,
                    onCheckedChange = { settingsRepo.setTtsEnabled(it) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Пофразовая озвучка при стриминге",
                        fontSize = 15.sp, color = Color.White)
                    Text("Говорить не дожидаясь конца ответа",
                        fontSize = 12.sp, color = Color(0xFF9090A0))
                }
                Switch(
                    checked = settingsRepo.ttsStreamingPhrases,
                    onCheckedChange = { settingsRepo.setTtsStreamingPhrases(it) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        TtsEnginePicker(
            engines = engines,
            selected = settingsRepo.ttsEngine,
            onSelected = { settingsRepo.setTtsEngine(it) }
        )

        Spacer(Modifier.height(10.dp))

        TtsVoicePicker(
            voices = voices,
            selectedName = settingsRepo.ttsVoiceName,
            onSelected = { settingsRepo.setTtsVoiceName(it) }
        )

        Spacer(Modifier.height(10.dp))

        TtsSlider("Скорость речи", settingsRepo.ttsRate) {
            settingsRepo.setTtsRate(it)
        }
        Spacer(Modifier.height(10.dp))
        TtsSlider("Тон голоса", settingsRepo.ttsPitch) {
            settingsRepo.setTtsPitch(it)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (previewTts == null)
                    previewTts = TtsController(context, settingsRepo)
                previewTts?.speak("Привет! Так будет звучать мой ответ.")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Прослушать пример") }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 8. OBD-II диагностика
        // ================================================================
        SectionTitle("OBD-II диагностика")

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = obdMac,
                    onValueChange = { obdMac = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MAC-адрес адаптера ELM327") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                Text(obdStatus, fontSize = 12.sp, color = Color(0xFF9090A0))
                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                obdStatus = "Подключение…"
                                val r = obdRepo.connect(obdMac)
                                obdStatus = if (r.isSuccess) "Подключено"
                                             else "Ошибка: ${r.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Подключиться") }

                    OutlinedButton(
                        onClick = {
                            obdRepo.disconnect()
                            obdStatus = "Отключено"
                            storedDtcs = emptyList()
                            pendingDtcs = emptyList()
                            permanentDtcs = emptyList()
                            dtcReadDone = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Отключиться") }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        scope.launch {
                            dtcReadDone = false
                            if (!obdRepo.isConnected) {
                                obdStatus = "Сначала подключитесь к адаптеру"
                                return@launch
                            }
                            obdStatus = "Чтение DTC…"
                            storedDtcs = obdRepo.readStoredDtcs()
                                .getOrDefault(emptyList())
                            pendingDtcs = obdRepo.readPendingDtcs()
                                .getOrDefault(emptyList())
                            permanentDtcs = obdRepo.readPermanentDtcs()
                                .getOrDefault(emptyList())
                            dtcReadDone = true
                            obdStatus = "Готово"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Читать коды ошибок (DTC)") }

                if (dtcReadDone) {
                    Spacer(Modifier.height(10.dp))
                    DtcSection("Сохранённые (Service 03)", storedDtcs)
                    Spacer(Modifier.height(8.dp))
                    DtcSection("Ожидающие (Service 07)", pendingDtcs)
                    Spacer(Modifier.height(8.dp))
                    DtcSection("Постоянные (Service 0A)", permanentDtcs)

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { confirmClearDtc = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF44336)
                        )
                    ) { Text("Сбросить ошибки / Check Engine") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 9. Плавающая кнопка
        // ================================================================
        SectionTitle("Плавающая кнопка")

        val overlayGranted = OverlayPermission.isGranted(context)
        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Показывать поверх других приложений",
                        fontSize = 15.sp, color = Color.White)
                    Text(
                        if (overlayGranted) "Разрешение получено"
                        else "Требуется разрешение наложения",
                        fontSize = 12.sp,
                        color = if (overlayGranted) Color(0xFF4CAF50)
                                else Color(0xFFFFC107)
                    )
                }
                Switch(
                    checked = settingsRepo.floatingButtonEnabled && overlayGranted,
                    enabled = overlayGranted,
                    onCheckedChange = { on ->
                        settingsRepo.setFloatingButtonEnabled(on)
                        val intent = Intent(
                            context, FloatingButtonService::class.java
                        )
                        if (on) context.startForegroundService(intent)
                        else context.stopService(intent)
                    }
                )
            }
        }

        if (!overlayGranted) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { OverlayPermission.request(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Разрешить наложение поверх окон") }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 10. Управление приложениями
        // ================================================================
        SectionTitle("Управление приложениями")

        val a11yActive = CarAccessibilityService.isRunning()
        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Расширенное управление (Accessibility Service)",
                    fontSize = 15.sp, color = Color.White)
                Text(
                    if (a11yActive)
                        "Сервис активен — можно добавлять в плейлисты, " +
                                "эмулировать нажатия в Яндекс.Музыке и других приложениях."
                    else
                        "Выключен. Включите в системных настройках Android " +
                                "для продвинутого управления приложениями.",
                    fontSize = 12.sp,
                    color = if (a11yActive) Color(0xFF4CAF50)
                            else Color(0xFFFFC107)
                )
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (a11yActive) "Открыть настройки доступности"
                        else "Включить Accessibility Service"
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Быстрый тест медиа-управления",
                    fontSize = 15.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            MediaController(context).perform(
                                MediaController.Action.PREV
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("⏮") }
                    OutlinedButton(
                        onClick = {
                            MediaController(context).perform(
                                MediaController.Action.TOGGLE
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("⏯") }
                    OutlinedButton(
                        onClick = {
                            MediaController(context).perform(
                                MediaController.Action.NEXT
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("⏭") }
                }

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            MediaController(context).perform(
                                MediaController.Action.VOL_DOWN
                            )
                            mediaVolumePercent =
                                MediaController(context).currentVolumePercent()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("− громкость") }
                    OutlinedButton(
                        onClick = {
                            MediaController(context).perform(
                                MediaController.Action.VOL_UP
                            )
                            mediaVolumePercent =
                                MediaController(context).currentVolumePercent()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("+ громкость") }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Громкость сейчас: $mediaVolumePercent%",
                    fontSize = 12.sp, color = Color(0xFF9090A0)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 11. Облачная синхронизация
        // ================================================================
        SectionTitle("Облачная синхронизация")

        val cloudSigned = !settingsRepo.cloudSyncEmail.isNullOrBlank()
        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Google Drive Backup",
                            fontSize = 15.sp, color = Color.White)
                        Text(
                            if (cloudSigned) "Аккаунт: ${settingsRepo.cloudSyncEmail}"
                            else "Не авторизовано",
                            fontSize = 12.sp,
                            color = if (cloudSigned) Color(0xFF4CAF50)
                                    else Color(0xFFFFC107)
                        )
                    }
                    Switch(
                        checked = settingsRepo.cloudSyncEnabled,
                        enabled = cloudSigned,
                        onCheckedChange = { settingsRepo.setCloudSyncEnabled(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // TODO: Google Sign-In через Activity Result API
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (cloudSigned) "Сменить аккаунт" else "Войти в Google")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    CloudSyncRepository(context).performSyncBlocking()
                                }
                            }
                        },
                        enabled = cloudSigned,
                        modifier = Modifier.weight(1f)
                    ) { Text("Синхр. сейчас") }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                CloudSyncRepository(context).restore()
                            }
                        }
                    },
                    enabled = cloudSigned,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Восстановить из облака") }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 12. Яндекс.Музыка (API)
        // ================================================================
        SectionTitle("Яндекс.Музыка (API)")

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Использовать API (OAuth)",
                            fontSize = 15.sp, color = Color.White)
                        Text("Вместо UI-автоматизации через Accessibility",
                            fontSize = 12.sp, color = Color(0xFF9090A0))
                    }
                    Switch(
                        checked = settingsRepo.yandexMusicEnabled,
                        onCheckedChange = {
                            settingsRepo.setYandexMusicEnabled(it)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                com.example.llmcar.data.YandexMusicRepository
                                    .OAUTH_AUTH_URL
                            )
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Авторизоваться в Яндекс.Музыке") }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================================================================
        // 13. Android Automotive (AAOS)
        // ================================================================
        SectionTitle("Android Automotive (AAOS)")

        Surface(
            color = Color(0xFF252535),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Car App Library активна",
                    fontSize = 15.sp, color = Color.White)
                Text(
                    "Приложение доступно в системном лаунчере AAOS " +
                            "и Android Auto как шаблонное приложение.",
                    fontSize = 12.sp, color = Color(0xFF9090A0)
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ================================================================
    // Диалог подтверждения сброса DTC
    // ================================================================
    if (confirmClearDtc) {
        AlertDialog(
            onDismissRequest = { confirmClearDtc = false },
            title = { Text("Сбросить ошибки?") },
            text = {
                Text(
                    "Будут стёрты все сохранённые DTC и погашена лампа Check Engine. " +
                            "Данные замороженных кадров также будут удалены."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = obdRepo.clearDtcs()
                        if (r.isSuccess) {
                            storedDtcs = emptyList()
                            pendingDtcs = emptyList()
                            obdStatus = "Ошибки сброшены"
                        } else {
                            obdStatus = "Ошибка сброса: ${r.exceptionOrNull()?.message}"
                        }
                        confirmClearDtc = false
                    }
                }) { Text("Сбросить", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDtc = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ====================================================================
// Вспомогательные composable-функции
// ====================================================================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun <T : Number> RamChoice(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { opt ->
                    val isSel = opt == selected
                    Surface(
                        color = if (isSel) Color(0xFF7C4DFF) else Color(0xFF1E1E2E),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { onSelected(opt) }
                    ) {
                        Text(
                            opt.toString(),
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(
                                horizontal = 10.dp, vertical = 6.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка выбора визуального параметра с горизонтальной прокруткой.
 * Если colorOf возвращает не-null — рядом с подписью показывается цветной кружок.
 */
@Composable
private fun <T> VisualChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    colorOf: (T) -> Color?,
    onSelected: (T) -> Unit
) {
    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                options.forEach { opt ->
                    val isSel = opt == selected
                    val swatch = colorOf(opt)
                    Surface(
                        color = if (isSel) Color(0xFF7C4DFF)
                                else Color(0xFF1E1E2E),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { onSelected(opt) }
                    ) {
                        Row(
                            Modifier.padding(
                                horizontal = 10.dp, vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (swatch != null) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .background(swatch, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                labelOf(opt),
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelEntry,
    isActive: Boolean,
    onLoad: () -> Unit
) {
    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, fontSize = 14.sp, color = Color.White)
                Text(
                    (if (entry.isSaf) "SAF · " else "") + formatSize(entry.size),
                    fontSize = 11.sp, color = Color(0xFF9090A0)
                )
            }
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle, "Активна",
                    tint = Color(0xFF4CAF50)
                )
            } else {
                TextButton(onClick = onLoad) {
                    Text("Загрузить", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TtsEnginePicker(
    engines: List<TextToSpeech.EngineInfo>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = engines.firstOrNull { it.name == selected }?.label
        ?: "Системный по умолчанию"

    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("TTS-движок", fontSize = 15.sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Системный по умолчанию") },
                        onClick = {
                            onSelected(null)
                            expanded = false
                        }
                    )
                    engines.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.label) },
                            onClick = {
                                onSelected(e.name)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsVoicePicker(
    voices: List<Voice>,
    selectedName: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = voices.firstOrNull { it.name == selectedName }?.name
        ?: "Автоматически (RU)"

    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Голос", fontSize = 15.sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label, maxLines = 1) }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Автоматически (RU)") },
                        onClick = {
                            onSelected(null)
                            expanded = false
                        }
                    )
                    voices.forEach { v ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${v.name} (${v.locale.displayName})",
                                    maxLines = 1
                                )
                            },
                            onClick = {
                                onSelected(v.name)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsSlider(
    title: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Surface(
        color = Color(0xFF252535),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%.2f×".format(value),
                    fontSize = 13.sp,
                    color = Color(0xFF9090A0)
                )
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0.5f..2.0f
            )
        }
    }
}

@Composable
private fun DtcSection(title: String, list: List<DtcResult>) {
    Surface(
        color = Color(0xFF1F1F2E),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                title,
                fontSize = 12.sp,
                color = Color(0xFF7C4DFF),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            if (list.isEmpty()) {
                Text("Ошибок нет", fontSize = 13.sp, color = Color(0xFF4CAF50))
            } else {
                list.forEach { d ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            d.code,
                            fontSize = 14.sp,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            d.description,
                            fontSize = 13.sp,
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
