package com.example.llmcar.integrations

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

class MediaController(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    enum class Action { PLAY, PAUSE, TOGGLE, NEXT, PREV, STOP, VOL_UP, VOL_DOWN, VOL_MUTE }

    fun perform(a: Action): String = when (a) {
        Action.PLAY -> key(KeyEvent.KEYCODE_MEDIA_PLAY, "Воспроизведение")
        Action.PAUSE -> key(KeyEvent.KEYCODE_MEDIA_PAUSE, "Пауза")
        Action.TOGGLE -> key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Плей/пауза")
        Action.NEXT -> key(KeyEvent.KEYCODE_MEDIA_NEXT, "Следующий")
        Action.PREV -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Предыдущий")
        Action.STOP -> key(KeyEvent.KEYCODE_MEDIA_STOP, "Стоп")
        Action.VOL_UP -> adj(AudioManager.ADJUST_RAISE, "Громче")
        Action.VOL_DOWN -> adj(AudioManager.ADJUST_LOWER, "Тише")
        Action.VOL_MUTE -> {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "Mute"
        }
    }

    fun currentVolumePercent(): Int {
        val m = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (m > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / m else 0
    }

    fun setVolumePercent(p: Int): String {
        val m = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            m * p.coerceIn(0, 100) / 100, AudioManager.FLAG_SHOW_UI)
        return "Громкость: $p%"
    }

    private fun key(c: Int, l: String): String {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, c))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, c))
        return l
    }

    private fun adj(d: Int, l: String): String {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        return l
    }
}