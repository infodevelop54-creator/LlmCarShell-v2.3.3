package com.example.llmcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.ui.theme.PaletteColors

/**
 * Компактный оверлей ввода, выезжающий снизу экрана.
 * Без заднего фона, без страницы чата.
 *
 * Использование:
 *   OverlayInputWidget(
 *       palette = ...,
 *       pillHeightDp = 56,
 *       onSend = { text -> ... },
 *       onMicClick = { ... },
 *       onClose = { ... }
 *   )
 */
@Composable
fun OverlayInputWidget(
    palette: PaletteColors,
    pillHeightDp: Int = 56,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val hasText = text.isNotBlank()
    val radius = pillHeightDp / 2

    // Отступы от краёв экрана, чтобы оверлей не прилипал к краям.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Крестик закрытия — маленький, над пилюлей
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(palette.pillBgEnd.copy(alpha = 0.85f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Основная пилюля ввода
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
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
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
                // Иконка режима слева (декоративная)
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = palette.accent.copy(alpha = 0.85f),
                        modifier = Modifier.size((pillHeightDp * 0.36f).dp)
                    )
                }

                // Поле ввода
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    textStyle = TextStyle(
                        color = palette.inputText,
                        fontSize = (pillHeightDp * 0.28f).sp
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val t = text.trim()
                        if (t.isNotBlank()) {
                            text = ""
                            onSend(t)
                        }
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    "Спросить ассистента…",
                                    color = palette.placeholder,
                                    fontSize = (pillHeightDp * 0.28f).sp
                                )
                            }
                            inner()
                        }
                    }
                )

                // Кнопка микрофона (открывает MainActivity с автостартом mic)
                Box(
                    modifier = Modifier
                        .size((pillHeightDp - 20).dp)
                        .clip(CircleShape)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Голос",
                        tint = palette.accent.copy(alpha = 0.85f),
                        modifier = Modifier.size((pillHeightDp * 0.32f).dp)
                    )
                }

                // Кнопка отправки
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
                        .clickable(enabled = hasText) {
                            val t = text.trim()
                            if (t.isNotBlank()) {
                                text = ""
                                onSend(t)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (hasText) palette.accentSoft
                               else palette.accentSoft.copy(alpha = 0.4f),
                        modifier = Modifier.size((pillHeightDp * 0.34f).dp)
                    )
                }
            }
        }
    }
}
