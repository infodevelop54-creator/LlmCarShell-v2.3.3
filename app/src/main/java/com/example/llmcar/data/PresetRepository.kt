package com.example.llmcar.data

data class Preset(
    val id: String,
    val displayName: String,
    val prompt: String,
    val voiceTriggers: List<String>,
    val textTriggers: List<String>,
    val icon: String
)

object PresetRepository {

    val presets = listOf(
        Preset(
            id = "assistant",
            displayName = "Ассистент",
            icon = "Assistant",
            voiceTriggers = listOf("ассистент", "обычный режим", "режим ассистент"),
            textTriggers = emptyList(),
            prompt = """
Ты — встроенный голосовой ассистент автомобиля. Отвечай кратко: 1–2 предложения,
без вступлений вроде «Конечно!». Язык — русский. Используй доступные инструменты
для данных автомобиля, не выдумывай значения. Никаких длинных списков и markdown.
            """.trimIndent()
        ),
        Preset(
            id = "diagnostic",
            displayName = "Диагност OBD",
            icon = "Build",
            voiceTriggers = listOf("диагност", "режим диагностика", "режим диагноста"),
            textTriggers = listOf(
                "ошибк", "диагност", "dtc", "чек", "check engine",
                "obd", "код", "неисправн", "двигател", "расшифруй"
            ),
            prompt = """
Ты — автомобильный диагност. Общайся техническим языком, коротко.
Всегда обращайся к инструментам OBD-II за фактическими данными.
При кодах ошибок (DTC) объясняй причину и что делать.
            """.trimIndent()
        ),
        Preset(
            id = "navigator",
            displayName = "Навигатор",
            icon = "Map",
            voiceTriggers = listOf("навигатор", "режим навигация", "режим навигатор"),
            textTriggers = listOf(
                "маршрут", "навига", "адрес", "поехали", "куда",
                "доехать", "поезжай", "построй"
            ),
            prompt = """
Ты — навигационный ассистент. Помогаешь строить маршруты, искать адреса.
Используй инструменты navigate_to и open_maps. Отвечай кратко, только по существу.
            """.trimIndent()
        ),
        Preset(
            id = "media",
            displayName = "Медиа",
            icon = "MusicNote",
            voiceTriggers = listOf("медиа", "музыка", "режим музыка", "режим медиа"),
            textTriggers = listOf(
                "музык", "играй", "трек", "песн", "плейлист", "включи",
                "пауза", "громч", "тише", "следующ", "предыдущ"
            ),
            prompt = """
Ты — медиа-ассистент автомобиля. Управляешь музыкой и медиаплеерами:
media_control, open_app, play_music. Отвечай одной короткой фразой.
            """.trimIndent()
        ),
        Preset(
            id = "free",
            displayName = "Свободный диалог",
            icon = "Forum",
            voiceTriggers = listOf("свободный", "свободный режим", "режим диалог"),
            textTriggers = emptyList(),
            prompt = """
Ты — дружелюбный собеседник. Отвечай развёрнуто, можешь шутить.
            """.trimIndent()
        )
    )

    val defaultId = "assistant"

    fun byId(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets.first()

    fun matchByVoice(recognized: String, normalize: (String) -> String): Preset? {
        val n = normalize(recognized)
        if (n.isBlank()) return null
        return presets.firstOrNull { p ->
            p.voiceTriggers.any { t ->
                val tt = normalize(t)
                tt.isNotEmpty() && (n == tt || n.contains(tt))
            }
        }
    }

    /**
     * Автовыбор пресета по тексту запроса.
     * Возвращает null, если ничего не совпало (тогда оставляем текущий пресет).
     * Пресет "assistant" и "free" не участвуют — они выбираются вручную,
     * чтобы не перебивать остальные.
     */
    fun matchByText(userText: String): Preset? {
        val t = userText.lowercase().trim()
        if (t.isBlank()) return null
        return presets
            .filter { it.id != "assistant" && it.id != "free" }
            .firstOrNull { p ->
                p.textTriggers.any { kw -> t.contains(kw) }
            }
    }
}
