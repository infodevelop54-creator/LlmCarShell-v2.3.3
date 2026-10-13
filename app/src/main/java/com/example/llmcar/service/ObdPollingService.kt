package com.example.llmcar.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.integrations.ObdRepository
import com.example.llmcar.util.AppSignals
import kotlinx.coroutines.*

class ObdPollingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val obd = ObdRepository(applicationContext)

    override fun onCreate() {
        super.onCreate(); startForeground(NOTIF_ID, notif("Опрос…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mac = intent?.getStringExtra(EXTRA_MAC) ?: return START_NOT_STICKY
        scope.launch {
            val r = obd.connect(mac)
            if (r.isFailure) {
                AppSignals.pushObdLive("Ошибка: ${r.exceptionOrNull()?.message}", emptyMap())
                stopSelf(); return@launch
            }
            loop()
        }
        return START_STICKY
    }

    private suspend fun loop() {
        val pids = mapOf(
            "RPM" to "0C", "Speed" to "0D", "Coolant" to "05",
            "Fuel" to "2F", "Battery" to "42"
        )
        while (scope.isActive) {
            val v = mutableMapOf<String, String>()
            pids.forEach { (l, p) ->
                runCatching { obd.readPid(p).getOrNull() }.getOrNull()?.let { v[l] = it }
            }
            AppSignals.pushObdLive("OK", v)
            updateNotif("RPM: ${v["RPM"] ?: "—"} · Speed: ${v["Speed"] ?: "—"} · Coolant: ${v["Coolant"] ?: "—"}")
            delay(1000L)
        }
    }

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_ENGINE)
            .setContentTitle("OBD Live Dashboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun updateNotif(t: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(t))
    }

    override fun onDestroy() {
        obd.disconnect(); scope.cancel()
        AppSignals.pushObdLive("Остановлено", emptyMap())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1004
        const val EXTRA_MAC = "obd_mac"
    }
}