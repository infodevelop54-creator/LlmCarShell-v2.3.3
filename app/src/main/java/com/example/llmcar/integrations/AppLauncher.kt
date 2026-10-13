package com.example.llmcar.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.llmcar.service.CarAccessibilityService

class AppLauncher(private val context: Context) {

    companion object {
        val KNOWN = mapOf(
            "yandex_music" to "ru.yandex.music",
            "yandex_navi" to "ru.yandex.yandexnavi",
            "yandex_radio" to "ru.yandex.radio",
            "youtube_music" to "com.google.android.apps.youtube.music",
            "spotify" to "com.spotify.music",
            "vlc" to "org.videolan.vlc",
            "maps" to "ru.yandex.yandexmaps",
            "google_maps" to "com.google.android.apps.maps",
            "2gis" to "ru.dublgis.dgismobile"
        )
    }

    fun openApp(a: String): String {
        val pkg = KNOWN[a] ?: a
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "Не установлено: $pkg"
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i); "Открыто: $pkg" }
            .getOrElse { "Ошибка: ${it.message}" }
    }

    fun searchYandexMusic(q: String): String {
        val i = Intent(Intent.ACTION_VIEW,
            Uri.parse("yandexmusic://search?query=${Uri.encode(q)}"))
            .setPackage(KNOWN["yandex_music"])
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(i); true }.getOrDefault(false)
        return if (ok) "Ищу в Яндекс.Музыке: $q" else openApp("yandex_music")
    }

    fun navigateYandex(addr: String): String {
        val i = Intent(Intent.ACTION_VIEW,
            Uri.parse("yandexnavi://build_route_on_map?text=${Uri.encode(addr)}"))
            .setPackage(KNOWN["yandex_navi"])
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(i); true }.getOrDefault(false))
            return "Маршрут: $addr"
        val b = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://yandex.ru/maps/?rtext=~${Uri.encode(addr)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(b); "Маршрут (браузер): $addr" }
            .getOrElse { "Ошибка: ${it.message}" }
    }

    fun addCurrentToPlaylist(name: String): String {
        if (!CarAccessibilityService.isRunning()) return "Accessibility Service выключен"
        openApp("yandex_music"); Thread.sleep(1500)
        val opened = CarAccessibilityService.clickViewId("ru.yandex.music:id/overflow") ||
                CarAccessibilityService.clickText("…") ||
                CarAccessibilityService.clickText("...")
        if (!opened) return "Не нашёл «...»"
        Thread.sleep(800)
        if (!(CarAccessibilityService.clickText("Добавить в плейлист") ||
              CarAccessibilityService.clickText("В плейлист")))
            return "Нет пункта «Добавить в плейлист»"
        Thread.sleep(800)
        return if (CarAccessibilityService.clickText(name))
            "Добавлено в «$name»" else "Плейлист «$name» не найден"
    }
}