package com.example.llmcar.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class GoogleOfflineAsrEngine : GoogleOnlineAsrEngine() {

    override val displayName = "Google SpeechRecognizer (offline-пакет)"

    override fun isAvailable(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        else SpeechRecognizer.isRecognitionAvailable(context)

    override fun buildIntent(): Intent = super.buildIntent().apply {
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}