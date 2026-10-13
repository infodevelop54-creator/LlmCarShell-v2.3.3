package com.example.llmcar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.service.LlmStatus
import com.example.llmcar.ui.components.OrbState
import com.example.llmcar.ui.components.SoundBars
import com.example.llmcar.ui.components.VoiceOrb
import com.example.llmcar.ui.theme.OverlayStyle
import com.example.llmcar.ui.theme.PaletteColors
import com.example.llmcar.ui.theme.VisualTheme
import com.example.llmcar.util.AppSignals
import com.example.llmcar.voice.VoiceEvent
import com.example.llmcar.voice.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    serviceStatus: LlmStatus,
    voiceStatus: String,
    autoStartMic: Boolean,
    onAutoMicConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel()

    val visualSettings = remember { SettingsRepository(context.applicationContext) }
    val palette = VisualTheme.palette(visualSettings.accentHue)

    var inputText by remember { mutableStateOf("") }
    var overlayVisible by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(vm.activePresetName()) }

    LaunchedEffect(autoStartMic) {
        if (autoStartMic) { overlayVisible = true; onAutoMicConsumed() }
    }
    LaunchedEffect(Unit) {
        AppSignals.presetChanged.collect { presetName = vm.activePresetName() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // ===================== Верхняя панель =====================
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LLM Car Shell", fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        when (serviceStatus) {
                            is LlmStatus.Idle -> "Движок не запущен — загрузите модель в настройках"
                            is LlmStatus.Loading -> "Загрузка модели…"
                            is LlmStatus.Ready -> "Готово"
                            is LlmStatus.Error -> "Ошибка: ${serviceStatus.message}"
                        },
                        fontSize = 13.sp,
                        color = when (serviceStatus) {
                            is LlmStatus.Ready -> Color(0xFF4CAF50)
                            is LlmStatus.Error -> Color(0xFFF44336)
                            else -> Color(0xFFFFC107)
                        }
                    )
                    Text("Режим: $presetName", fontSize = 12.sp, color = palette.accent)
                    if (voiceStatus.isNotBlank() && voiceStatus != "Не активно")
                        Text(voiceStatus, fontSize = 11.sp, color = Color(0xFF9090A0))
                }
                IconButton(onClick = onOpenDashboard) {
                    Icon(Icons.Default.Speed, "Дашборд", tint = Color.White)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Настройки", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===================== Сообщения =====================
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages) { m -> MessageBubble(m) }
                if (vm.liveText.isNotBlank() && vm.isSending) {
                    item { LiveStreamBubble(vm.liveText) }
                }
            }

            vm.errorText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFF44336))
            }

            Spacer(Modifier.height(12.dp))

            // ===================== Пилюля ввода =====================
            GeminiInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    val t = inputText.trim()
                    if (t.isNotBlank()) {
                        inputText = ""
                        vm.send(t)
                    }
                },
                onVoiceClick = { overlayVisible = true },
                ttsEnabled = visualSettings.ttsEnabled,
                onTtsToggle = { visualSettings.setTtsEnabled(!visualSettings.ttsEnabled) },
                currentPresetId = visualSettings.currentPresetId,
                onPresetSelected = { id ->
                    visualSettings.setPresetId(id)
                    AppSignals.firePresetChanged()
                },
                enabled = serviceStatus is LlmStatus.Ready && !vm.isSending,
                palette = palette,
                pillHeightDp = VisualTheme.pillHeight(visualSettings.pillSize),
                presetIcon = VisualTheme.presetIcon(visualSettings.presetIconStyle)
            )
        }

        // ===================== Голосовой оверлей (компактный) =====================
        // Теперь он не закрывает весь экран: полупрозрачный подложка + таблетка снизу.
        AnimatedVisibility(
            visible = overlayVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GeminiVoiceOverlayCompact(
                serviceStatus = serviceStatus,
                settings = visualSettings,
                viewModel = vm,
                onDismiss = { overlayVisible = false },
                palette = palette,
                overlayStyle = visualSettings.overlayStyle,
                pillHeightDp = VisualTheme.pillHeight(visualSettings.pillSize)
            )
        }
    }
}

// =============================================================
// Пилюля ввода в стиле Gemini (без изменений)
// =============================================================

@Composable
private fun GeminiInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    ttsEnabled: Boolean,
    onTtsToggle: () -> Unit,
    currentPresetId: String,
    onPresetSelected: (String) -> Unit,
    enabled: Boolean,
    palette: PaletteColors,
    pillHeightDp: Int,
    presetIcon: ImageVector
) {
    val hasText = inputText.isNotBlank()
    var presetMenu by remember { mutableStateOf(false) }
    val radius = pillHeightDp / 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeightDp.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(palette.pillBgStart, palette.pillBgEnd)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.pillBorderDim,
                        palette.pillBorderHot,
                        palette.pillBorderDim
                    )
                ),
                shape = RoundedCornerShape(radius.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { presetMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = presetIcon,
                        contentDescription = "Режим",
                        tint = palette.accent.copy(alpha = 0.9f),
                        modifier = Modifier.size((pillHeightDp * 0.38f).dp)
                    )
                }
                DropdownMenu(
                    expanded = presetMenu,
                    onDismissRequest = { presetMenu = false }
                ) {
                    PresetRepository.presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = VisualTheme.iconForPreset(p.id),
                                    contentDescription = null,
                                    tint = palette.accent
                                )
                            },
                            onClick = {
                                onPresetSelected(p.id)
                                presetMenu = false
                            }
                        )
                    }
                }
            }

            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                textStyle = TextStyle(
                    color = palette.inputText,
                    fontSize = (pillHeightDp * 0.27f).sp
                ),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Спросить ассистента",
                                color = palette.placeholder,
                                fontSize = (pillHeightDp * 0.27f).sp
                            )
                        }
                        inner()
                    }
                }
            )

            Box(
                modifier = Modifier
                    .size((pillHeightDp - 20).dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onTtsToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ttsEnabled) Icons.Default.VolumeUp
                                  else Icons.Default.VolumeOff,
                    contentDescription = "Озвучка",
                    tint = if (ttsEnabled) palette.accent
                           else palette.placeholder.copy(alpha = 0.7f),
                    modifier = Modifier.size((pillHeightDp * 0.31f).dp)
                )
            }

            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size((pillHeightDp - 12).dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((pillHeightDp + 14).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    palette.glow,
                                    palette.glow.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.btnInner, palette.btnOuter)
                            )
                        )
                        .clickable(enabled = enabled) {
                            if (hasText) onSend() else onVoiceClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasText) Icons.Default.Send
                                      else Icons.Default.Mic,
                        contentDescription = if (hasText) "Отправить" else "Голос",
                        tint = palette.accentSoft,
                        modifier = Modifier.size((pillHeightDp * 0.35f).dp)
                    )
                }
            }
        }
    }
}

// =============================================================
// Голосовой оверлей — компактная «таблетка» снизу
// =============================================================

@Composable
private fun GeminiVoiceOverlayCompact(
    serviceStatus: LlmStatus,
    settings: SettingsRepository,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    palette: PaletteColors,
    overlayStyle: OverlayStyle,
    pillHeightDp: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceController = remember { VoiceInputController(context) }

    var inputText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Готов слушать") }
    var rmsLevel by remember { mutableStateOf(0f) }
    var isListening by remember { mutableStateOf(false) }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var ttsSpeaking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppSignals.ttsSpeaking.collect { ttsSpeaking = it }
    }

    fun startListening() {
        if (isListening || ttsSpeaking) return
        isListening = true
        statusText = "Слушаю…"
        rmsLevel = 0f
        voiceJob = scope.launch {
            voiceController.listenOnce().collect { ev ->
                when (ev) {
                    is VoiceEvent.ReadyForSpeech -> statusText = "Слушаю…"
                    is VoiceEvent.RmsChanged ->
                        rmsLevel = ((ev.rmsDb + 2f) / 14f).coerceIn(0f, 1f)
                    is VoiceEvent.Partial -> {
                        inputText = ev.text
                        statusText = ev.text
                    }
                    is VoiceEvent.Final -> {
                        isListening = false
                        inputText = ev.text
                        if (ev.text.isNotBlank() && settings.voiceAutoSend) {
                            viewModel.send(ev.text); inputText = ""
                        } else if (ev.text.isNotBlank()) {
                            statusText = "Нажмите «Отправить»"
                        }
                    }
                    is VoiceEvent.Error -> {
                        isListening = false
                        statusText = "Ошибка: ${ev.message}"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (serviceStatus is LlmStatus.Ready) startListening()
    }

    val currentOrb = when {
        isListening -> OrbState.Listening
        viewModel.isSending -> OrbState.Thinking
        ttsSpeaking -> OrbState.Speaking
        else -> OrbState.Idle
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceJob?.cancel()
            voiceController.stop()
        }
    }

    val infinite = rememberInfiniteTransition(label = "ov")
    val wavePulse by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wave"
    )

    // ===================== Компактная таблетка =====================
    // Не занимает весь экран: снизу выезжает «пузырь» с орбом слева,
    // статусом в центре и кнопкой закрытия справа.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = palette.pillBgStart.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            palette.pillBorderDim,
                            palette.pillBorderHot,
                            palette.pillBorderDim
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Слева — мини-орб
                Box(
                    Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (overlayStyle) {
                        OverlayStyle.MINIMAL -> {
                            VoiceOrbSmall(
                                state = currentOrb,
                                accentColor = palette.accent,
                                size = 52.dp
                            )
                        }
                        OverlayStyle.GLOW -> {
                            VoiceOrbSmall(
                                state = currentOrb,
                                accentColor = palette.accent,
                                size = 52.dp
                            )
                        }
                        OverlayStyle.WAVE -> {
                            Box(
                                Modifier.size((64 * wavePulse).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VoiceOrbSmall(
                                    state = currentOrb,
                                    accentColor = palette.accent,
                                    size = 52.dp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Центр — статус/текст + эквалайзер
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val displayText = when {
                        viewModel.liveText.isNotBlank() -> viewModel.liveText
                        viewModel.messages.lastOrNull()?.role == "assistant" &&
                                !viewModel.isSending ->
                            viewModel.messages.last().content
                        viewModel.isSending -> "Думаю…"
                        else -> statusText
                    }
                    Text(
                        displayText,
                        fontSize = 14.sp,
                        color = if (viewModel.isSending) palette.accent
                                else palette.inputText,
                        maxLines = 2,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(4.dp))
                    SoundBars(
                        amplitude = rmsLevel,
                        active = currentOrb == OrbState.Listening ||
                                currentOrb == OrbState.Speaking,
                        barCount = 11
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Справа — крестик закрытия
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// =============================================================
// Мини-орб для компактного оверлея
// =============================================================

@Composable
private fun VoiceOrbSmall(
    state: OrbState,
    accentColor: Color,
    size: androidx.compose.ui.unit.Dp
) {
    val infinite = rememberInfiniteTransition(label = "orbsm")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse), label = "pulse")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.28f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse), label = "glow")

    val color: Color = when (state) {
        OrbState.Idle -> accentColor.copy(alpha = 0.55f)
        OrbState.Listening -> accentColor
        OrbState.Thinking -> Color(0xFFFFA726)
        OrbState.Speaking -> Color(0xFF4CAF50)
    }
    val icon: ImageVector = when (state) {
        OrbState.Idle -> Icons.Default.Mic
        OrbState.Listening -> Icons.Default.Mic
        OrbState.Thinking -> Icons.Default.MoreHoriz
        OrbState.Speaking -> Icons.Default.VolumeUp
    }

    Box(Modifier.size(size), Alignment.Center) {
        Box(Modifier.size(size).then(Modifier).background(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent)),
            shape = CircleShape))
        Box(Modifier.size(size * 0.72f).background(color.copy(alpha = 0.22f), CircleShape))
        Box(Modifier.size(size * 0.48f).background(color, CircleShape), Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.20f))
        }
    }
}

// =============================================================
// Пузыри сообщений
// =============================================================

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF2A2A3E)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Box(Modifier.padding(12.dp)) {
                Text(msg.content, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun LiveStreamBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            color = Color(0xFF2A2A3E).copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Box(Modifier.padding(12.dp)) {
                Text(text, color = Color(0xFFE0E0E0), fontSize = 15.sp)
            }
        }
    }
}
