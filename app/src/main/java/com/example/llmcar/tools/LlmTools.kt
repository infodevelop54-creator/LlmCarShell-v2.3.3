package com.example.llmcar.tools

import kotlinx.serialization.Serializable

@Serializable
data class ToolFunction(val name: String, val description: String, val parameters: ToolParameters)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList()
)

@Serializable
data class ToolProperty(val type: String, val description: String)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

object LlmTools {
    val definitions: List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            "get_car_data",
            "Показания датчиков OBD-II. PID: 0C RPM, 0D скорость, 05 ОЖ, 2F топливо, 11 дроссель, 42 АКБ.",
            ToolParameters(
                properties = mapOf("pid" to ToolProperty("string", "Код PID")),
                required = listOf("pid")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "run_shell",
            "Shell через Shizuku.",
            ToolParameters(
                properties = mapOf("command" to ToolProperty("string", "Команда")),
                required = listOf("command")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "open_maps",
            "Яндекс.Карты на адресе.",
            ToolParameters(
                properties = mapOf("query" to ToolProperty("string", "Адрес")),
                required = listOf("query")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "media_control",
            "Управление плеером: play, pause, toggle, next, prev, stop, vol_up, vol_down, vol_mute.",
            ToolParameters(
                properties = mapOf("action" to ToolProperty("string", "Действие")),
                required = listOf("action")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "set_volume",
            "Громкость 0..100.",
            ToolParameters(
                properties = mapOf("percent" to ToolProperty("integer", "0..100")),
                required = listOf("percent")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "open_app",
            "Открыть приложение. yandex_music, yandex_navi, spotify, vlc, maps, 2gis или package.",
            ToolParameters(
                properties = mapOf("app" to ToolProperty("string", "Алиас или package")),
                required = listOf("app")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "play_music",
            "Найти в Яндекс.Музыке.",
            ToolParameters(
                properties = mapOf("query" to ToolProperty("string", "Запрос")),
                required = listOf("query")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "navigate_to",
            "Маршрут в Яндекс.Навигаторе.",
            ToolParameters(
                properties = mapOf("address" to ToolProperty("string", "Адрес")),
                required = listOf("address")
            )
        )),
        ToolDefinition(function = ToolFunction(
            "add_to_playlist",
            "Добавить трек в плейлист (нужен Accessibility).",
            ToolParameters(
                properties = mapOf("playlist_name" to ToolProperty("string", "Плейлист")),
                required = listOf("playlist_name")
            )
        ))
    )
}
