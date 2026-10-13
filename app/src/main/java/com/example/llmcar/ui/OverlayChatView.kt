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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.service.LlmStatus
import com.example.llmcar.ui.theme.PaletteColors
import com.example.llmcar.ui.theme.VisualTheme
import com.example.llmcar.util.AppSignals

/**
 * Свёрнутое состояние оверлея: маленькая «таблетка» внизу экрана
 * с тремя полосками сверху (drag → развернуть).
 */
@Composable
fun OverlayCollapsedPill(
    palette: PaletteColors,
    pillHeightDp: Int,
    presetIcon: androidx.compose.ui.graphics.vector.ImageVector,
    presetName: String,
    onExpand: () -> Unit,
    onDragUp: () -> Unit,
    onClose: () -> Unit
) {
    val radius = pillHeightDp / 2
    // Свайп вверх от «ползунка» — колбэк onDragUp
    var accumulatedDrag by remember { mutableStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 12.dp)
    ) {
        // ---- Ползунок из трёх полосок ----
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(palette.pillBgEnd.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { accumulatedDrag = 0f }
                    ) { _, delta ->
                        accumulatedDrag += delta
                        if (accumulatedDrag < -60f) {
                            onDragUp()
                            accumulatedDrag = 0f
                        }
                    }
                }
                .clickable { onExpand() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(palette.accent.copy(alpha = 0.85f),
                                RoundedCornerShape(1.dp))
                    )
                }
            }
        }

        // ---- Сама пилюля ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeightDp.dp)
                .clip(RoundedCornerShape(radius.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(palette.pillBgStart, palette.pillBgEnd)
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            palette.pillBorderDim,
                            palette.pillBorderHot,
                            palette.pillBorderDim
                        )
                    ),
                    RoundedCornerShape(radius.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка режима
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size((pillHeightDp - 16).dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = presetIcon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size((pillHeightDp * 0.38f).dp)
                )
            }

            // Текст режима (тап → развернуть)
            Text(
                text = presetName,
                color = palette.inputText,
                fontSize = (pillHeightDp * 0.27f).sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .clickable { onExpand() }
            )

            // Кнопка закрытия (крестик)
            Box(
                modifier = Modifier
                    .size((pillHeightDp - 20).dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Свернуть",
                    tint = palette.placeholder.copy(alpha = 0.85f),
                    modifier = Modifier.size((pillHeightDp * 0.30f).dp)
                )
            }

            // Кнопка разворота
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size((pillHeightDp - 12).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(palette.btnInner, palette.btnOuter)
                        )
                    )
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Открыть чат",
                    tint = palette.accentSoft,
                    modifier = Modifier.size((pillHeightDp * 0.35f).dp)
                )
            }
        }
    }
}

/**
 * Развёрнутое состояние: полноэкранное окно чата поверх других приложений.
 * Внутри — упрощённая версия ChatScreen: список сообщений + пилюля ввода.
 * Крестик сверху справа сворачивает обратно в пилюлю.
 */
@Composable
fun OverlayExpandedChat(
    palette: PaletteColors,
    pillHeightDp: Int,
    presetIcon: androidx.compose.ui.graphics.vector.ImageVector,
    viewModel: ChatViewModel,
    settings: SettingsRepository,
    serviceStatus: LlmStatus,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var presetName by remember { mutableStateOf(viewModel.activePresetName()) }
    var presetMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppSignals.presetChanged.collect { presetName = viewModel.activePresetName() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.overlayBgTop
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(palette.overlayBgTop, palette.overlayBgBottom)
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ===================== Верхняя панель =====================
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LLM Car Shell",
                        fontSize = 18.sp,
                        color = palette.accentSoft,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Default.Speed, "Дашборд", tint = palette.accent)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Настройки", tint = palette.accent)
                    }
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Default.Close, "Свернуть", tint = palette.accent)
                    }
                }

                // Статус
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                color = when (serviceStatus) {
                                    is LlmStatus.Ready -> Color(0xFF4CAF50)
                                    is LlmStatus.Error -> Color(0xFFF44336)
                                    else -> Color(0xFFFFC107)
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (serviceStatus) {
                            is LlmStatus.Idle -> "Движок не запущен"
                            is LlmStatus.Loading -> "Загрузка модели…"
                            is LlmStatus.Ready -> "Готово · режим: $presetName"
                            is LlmStatus.Error -> "Ошибка: ${serviceStatus.message}"
                        },
                        fontSize = 12.sp,
                        color = palette.placeholder
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ===================== Сообщения =====================
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.messages) { m -> OverlayMessageBubble(m, palette) }
                    if (viewModel.liveText.isNotBlank() && viewModel.isSending) {
                        item { OverlayLiveStreamBubble(viewModel.liveText, palette) }
                    }
                }

                viewModel.errorText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = Color(0xFFF44336))
                }

                Spacer(Modifier.height(12.dp))

                // ===================== Пилюля ввода =====================
                OverlayInputPill(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        val t = inputText.trim()
                        if (t.isNotBlank()) {
                            inputText = ""
                            viewModel.send(t)
                        }
                    },
                    enabled = serviceStatus is LlmStatus.Ready && !viewModel.isSending,
                    palette = palette,
                    pillHeightDp = pillHeightDp,
                    presetIcon = presetIcon,
                    presetMenuOpen = presetMenu,
                    onPresetMenuOpenChange = { presetMenu = it },
                    onPresetSelected = { id ->
                        settings.setPresetId(id)
                        AppSignals.firePresetChanged()
                    }
                )
            }
        }
    }
}

// =============================================================
// Внутренние composable оверлея
// =============================================================

@Composable
private fun OverlayInputPill(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    palette: PaletteColors,
    pillHeightDp: Int,
    presetIcon: androidx.compose.ui.graphics.vector.ImageVector,
    presetMenuOpen: Boolean,
    onPresetMenuOpenChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit
) {
    val hasText = inputText.isNotBlank()
    val radius = pillHeightDp / 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeightDp.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(palette.pillBgStart, palette.pillBgEnd)
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        palette.pillBorderDim,
                        palette.pillBorderHot,
                        palette.pillBorderDim
                    )
                ),
                RoundedCornerShape(radius.dp)
            )
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка режима (тап → меню пресетов)
            Box {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { onPresetMenuOpenChange(true) },
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
                    expanded = presetMenuOpen,
                    onDismissRequest = { onPresetMenuOpenChange(false) }
                ) {
                    PresetRepository.presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            leadingIcon = {
                                Icon(
                                    VisualTheme.iconForPreset(p.id),
                                    contentDescription = null,
                                    tint = palette.accent
                                )
                            },
                            onClick = {
                                onPresetSelected(p.id)
                                onPresetMenuOpenChange(false)
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
                                "Спросить ассистента",
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
                    .padding(end = 6.dp)
                    .size((pillHeightDp - 12).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(palette.btnInner, palette.btnOuter)
                        )
                    )
                    .clickable(enabled = enabled) {
                        if (hasText) onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasText) Icons.Default.Send
                                  else Icons.Default.Mic,
                    contentDescription = null,
                    tint = palette.accentSoft,
                    modifier = Modifier.size((pillHeightDp * 0.35f).dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayMessageBubble(msg: UiMessage, palette: PaletteColors) {
    val isUser = msg.role == "user"
    val bg = if (isUser) palette.accent.copy(alpha = 0.25f) else Color(0xFF2A2A3E)
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
                Text(
                    msg.content,
                    color = if (isUser) palette.accentSoft else Color(0xFFE0E0E0),
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun OverlayLiveStreamBubble(text: String, palette: PaletteColors) {
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
