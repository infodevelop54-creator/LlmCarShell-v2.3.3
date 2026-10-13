package com.example.llmcar.tools

import android.content.Context
import com.example.llmcar.integrations.*
import kotlinx.serialization.json.*

class ToolExecutor(
    private val context: Context,
    private val obd: ObdRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val media = MediaController(context)
    private val launcher = AppLauncher(context)

    suspend fun execute(name: String, argsJson: String): String {
        val a = runCatching { json.parseToJsonElement(argsJson).jsonObject }
            .getOrElse { return "Ошибка: ${it.message}" }
        return when (name) {
            "get_car_data" -> a["pid"]?.jsonPrimitive?.content?.let {
                obd.readPid(it).getOrElse { e -> "OBD: ${e.message}" }
            } ?: "Нет pid"
            "run_shell" -> {
                if (!ShizukuBridge.isAvailable()) "Shizuku недоступен"
                else a["command"]?.jsonPrimitive?.content?.let {
                    ShizukuBridge.runShell(it).getOrElse { e -> "Shell: ${e.message}" }
                } ?: "Нет команды"
            }
            "open_maps" -> a["query"]?.jsonPrimitive?.content?.let { launcher.navigateYandex(it) }
                    ?: "Нет query"
            "media_control" -> {
                val act = a["action"]?.jsonPrimitive?.content ?: return "Нет action"
                val e = when (act.lowercase()) {
                    "play" -> MediaController.Action.PLAY
                    "pause" -> MediaController.Action.PAUSE
                    "toggle", "play_pause" -> MediaController.Action.TOGGLE
                    "next" -> MediaController.Action.NEXT
                    "prev", "previous" -> MediaController.Action.PREV
                    "stop" -> MediaController.Action.STOP
                    "vol_up", "louder" -> MediaController.Action.VOL_UP
                    "vol_down", "quieter" -> MediaController.Action.VOL_DOWN
                    "vol_mute", "mute" -> MediaController.Action.VOL_MUTE
                    else -> return "Неизвестно: $act"
                }
                media.perform(e)
            }
            "set_volume" -> {
                val p = a["percent"]?.jsonPrimitive?.intOrNull
                    ?: a["percent"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return "Нет percent"
                media.setVolumePercent(p)
            }
            "open_app" -> a["app"]?.jsonPrimitive?.content?.let { launcher.openApp(it) }
                    ?: "Нет app"
            "play_music" -> a["query"]?.jsonPrimitive?.content?.let {
                launcher.searchYandexMusic(it)
            } ?: "Нет query"
            "navigate_to" -> a["address"]?.jsonPrimitive?.content?.let {
                launcher.navigateYandex(it)
            } ?: "Нет адреса"
            "add_to_playlist" -> a["playlist_name"]?.jsonPrimitive?.content?.let {
                launcher.addCurrentToPlaylist(it)
            } ?: "Нет плейлиста"
            else -> "Неизвестный инструмент: $name"
        }
    }
}