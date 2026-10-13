package com.example.llmcar.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Оттенок интерфейса ввода и оверлея. */
enum class AccentHue { TEAL, PURPLE, BLUE, GREEN, ORANGE, PINK }

/** Размер пилюли ввода. */
enum class PillSize { COMPACT, NORMAL, LARGE }

/** Иконка у кнопки пресета (слева). */
enum class PresetIconStyle { SPARKLES, CHAT, STAR, ROBOT, GRID }

/** Стиль голосового оверлея. */
enum class OverlayStyle { GLOW, MINIMAL, WAVE }

data class PaletteColors(
    val accent: Color,
    val accentSoft: Color,
    val pillBgStart: Color,
    val pillBgEnd: Color,
    val pillBorderDim: Color,
    val pillBorderHot: Color,
    val glow: Color,
    val btnInner: Color,
    val btnOuter: Color,
    val placeholder: Color,
    val inputText: Color,
    val overlayBgTop: Color,
    val overlayBgBottom: Color
)

object VisualTheme {

    fun palette(hue: AccentHue): PaletteColors = when (hue) {
        AccentHue.TEAL -> PaletteColors(
            accent = Color(0xFF7EE5D0),
            accentSoft = Color(0xFFB0F0E0),
            pillBgStart = Color(0xFF0D1F1F),
            pillBgEnd = Color(0xFF153030),
            pillBorderDim = Color(0x1A5BC4B0),
            pillBorderHot = Color(0x665BC4B0),
            glow = Color(0x4D5BC4B0),
            btnInner = Color(0xFF2D6E6A),
            btnOuter = Color(0xFF1B4A48),
            placeholder = Color(0xFF7A8F8C),
            inputText = Color(0xFFE0F0EE),
            overlayBgTop = Color(0xFF0A1717),
            overlayBgBottom = Color(0xFF0E2020)
        )
        AccentHue.PURPLE -> PaletteColors(
            accent = Color(0xFFB794F6),
            accentSoft = Color(0xFFD6C4FF),
            pillBgStart = Color(0xFF1A1228),
            pillBgEnd = Color(0xFF2A1E42),
            pillBorderDim = Color(0x1A8E6ED4),
            pillBorderHot = Color(0x668E6ED4),
            glow = Color(0x4D8E6ED4),
            btnInner = Color(0xFF503C78),
            btnOuter = Color(0xFF2F2450),
            placeholder = Color(0xFF8E87A8),
            inputText = Color(0xFFF0E8FF),
            overlayBgTop = Color(0xFF120A1E),
            overlayBgBottom = Color(0xFF1A1228)
        )
        AccentHue.BLUE -> PaletteColors(
            accent = Color(0xFF64B5F6),
            accentSoft = Color(0xFFB0D8FF),
            pillBgStart = Color(0xFF0D1A2A),
            pillBgEnd = Color(0xFF152A44),
            pillBorderDim = Color(0x1A4A90C4),
            pillBorderHot = Color(0x664A90C4),
            glow = Color(0x4D4A90C4),
            btnInner = Color(0xFF2E5A8C),
            btnOuter = Color(0xFF1A3A5C),
            placeholder = Color(0xFF8098B0),
            inputText = Color(0xFFE4F0FC),
            overlayBgTop = Color(0xFF0A1420),
            overlayBgBottom = Color(0xFF0E1E30)
        )
        AccentHue.GREEN -> PaletteColors(
            accent = Color(0xFF81C784),
            accentSoft = Color(0xFFB8E8BA),
            pillBgStart = Color(0xFF0D1F12),
            pillBgEnd = Color(0xFF15301E),
            pillBorderDim = Color(0x1A5BC46A),
            pillBorderHot = Color(0x665BC46A),
            glow = Color(0x4D5BC46A),
            btnInner = Color(0xFF2D6E38),
            btnOuter = Color(0xFF1B4A22),
            placeholder = Color(0xFF7A8F7E),
            inputText = Color(0xFFE0F0E2),
            overlayBgTop = Color(0xFF0A1710),
            overlayBgBottom = Color(0xFF0E2018)
        )
        AccentHue.ORANGE -> PaletteColors(
            accent = Color(0xFFFFB74D),
            accentSoft = Color(0xFFFFD8A0),
            pillBgStart = Color(0xFF25190D),
            pillBgEnd = Color(0xFF3A2815),
            pillBorderDim = Color(0x1AC4913A),
            pillBorderHot = Color(0x66C4913A),
            glow = Color(0x4DC4913A),
            btnInner = Color(0xFF8C5F2E),
            btnOuter = Color(0xFF5C3D1A),
            placeholder = Color(0xFFA89580),
            inputText = Color(0xFFFFF0DC),
            overlayBgTop = Color(0xFF1A1008),
            overlayBgBottom = Color(0xFF261A0E)
        )
        AccentHue.PINK -> PaletteColors(
            accent = Color(0xFFF48FB1),
            accentSoft = Color(0xFFFFC4D6),
            pillBgStart = Color(0xFF251320),
            pillBgEnd = Color(0xFF3A1E32),
            pillBorderDim = Color(0x1AC45B85),
            pillBorderHot = Color(0x66C45B85),
            glow = Color(0x4DC45B85),
            btnInner = Color(0xFF8C3F5C),
            btnOuter = Color(0xFF5C2A3E),
            placeholder = Color(0xFFA88294),
            inputText = Color(0xFFFFE4EE),
            overlayBgTop = Color(0xFF1A0A14),
            overlayBgBottom = Color(0xFF26101E)
        )
    }

    fun pillHeight(size: PillSize): Int = when (size) {
        PillSize.COMPACT -> 48
        PillSize.NORMAL -> 58
        PillSize.LARGE -> 68
    }

    fun presetIcon(style: PresetIconStyle): ImageVector = when (style) {
        PresetIconStyle.SPARKLES -> Icons.Default.AutoAwesome
        PresetIconStyle.CHAT -> Icons.Default.Chat
        PresetIconStyle.STAR -> Icons.Default.Star
        PresetIconStyle.ROBOT -> Icons.Default.SmartToy
        PresetIconStyle.GRID -> Icons.Default.GridView
    }

    fun iconForPreset(id: String): ImageVector = when (id) {
        "diagnostic" -> Icons.Default.Build
        "navigator" -> Icons.Default.Map
        "media" -> Icons.Default.MusicNote
        "free" -> Icons.Default.Forum
        else -> Icons.Default.AutoAwesome
    }

    fun label(hue: AccentHue): String = when (hue) {
        AccentHue.TEAL -> "Бирюзовый"
        AccentHue.PURPLE -> "Фиолетовый"
        AccentHue.BLUE -> "Синий"
        AccentHue.GREEN -> "Зелёный"
        AccentHue.ORANGE -> "Оранжевый"
        AccentHue.PINK -> "Розовый"
    }

    fun label(size: PillSize): String = when (size) {
        PillSize.COMPACT -> "Компактный"
        PillSize.NORMAL -> "Обычный"
        PillSize.LARGE -> "Крупный"
    }

    fun label(style: PresetIconStyle): String = when (style) {
        PresetIconStyle.SPARKLES -> "Искры"
        PresetIconStyle.CHAT -> "Чат"
        PresetIconStyle.STAR -> "Звезда"
        PresetIconStyle.ROBOT -> "Робот"
        PresetIconStyle.GRID -> "Сетка"
    }

    fun label(style: OverlayStyle): String = when (style) {
        OverlayStyle.GLOW -> "Свечение"
        OverlayStyle.MINIMAL -> "Минимализм"
        OverlayStyle.WAVE -> "Волна"
    }
}
