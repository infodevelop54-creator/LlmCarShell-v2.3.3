package com.example.llmcar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class LlmCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(
            CHANNEL_ENGINE, getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW))
        m.createNotificationChannel(NotificationChannel(
            CHANNEL_FLOATING, getString(R.string.floating_channel_name),
            NotificationManager.IMPORTANCE_LOW))
        m.createNotificationChannel(NotificationChannel(
            CHANNEL_VOICE, getString(R.string.voice_channel_name),
            NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val CHANNEL_ENGINE = "llm_engine_channel"
        const val CHANNEL_FLOATING = "floating_channel"
        const val CHANNEL_VOICE = "voice_channel"
    }
}