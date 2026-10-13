package com.example.llmcar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.service.*
import com.example.llmcar.ui.*
import com.example.llmcar.ui.theme.LlmCarTheme
import com.example.llmcar.util.OverlayPermission
import java.io.File

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { }
    private val autoMic = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoMic.value = intent?.getBooleanExtra(EXTRA_AUTO_MIC, false) ?: false
        requestPermissionsIfNeeded()
        startLlmService()
        syncFloatingService()
        syncVoiceService()

        setContent {
            LlmCarTheme {
                val serviceStatus by LlmForegroundService.status.collectAsState()
                val voiceStatus by VoiceActivationService.status.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }

                when (screen) {
                    Screen.Chat -> ChatScreen(
                        serviceStatus = serviceStatus,
                        voiceStatus = voiceStatus,
                        autoStartMic = autoMic.value,
                        onAutoMicConsumed = { autoMic.value = false },
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenDashboard = { screen = Screen.ObdDashboard }
                    )
                    Screen.Settings -> SettingsScreen(
                        serviceStatus = serviceStatus,
                        onBack = { screen = Screen.Chat; syncFloatingService(); syncVoiceService() },
                        onOpenHistory = { screen = Screen.History }
                    )
                    Screen.History -> HistoryScreen(onBack = { screen = Screen.Settings })
                    Screen.ObdDashboard -> ObdDashboardScreen(onBack = { screen = Screen.Chat })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_MIC, false)) autoMic.value = true
    }

    override fun onResume() { super.onResume(); syncFloatingService(); syncVoiceService() }

    /**
     * Запускает LlmForegroundService. Если в настройках сохранён lastModelPath
     * и файл существует — сразу даёт сервису команду ACTION_LOAD_MODEL, чтобы
     * модель поднялась автоматически после перезапуска приложения.
     */
    private fun startLlmService() {
        val repo = SettingsRepository(this)
        val last = repo.lastModelPath
        val i = Intent(this, LlmForegroundService::class.java)
        if (!last.isNullOrBlank() && File(last).exists()) {
            i.action = LlmForegroundService.ACTION_LOAD_MODEL
            i.putExtra(LlmForegroundService.EXTRA_MODEL_PATH, last)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(i) else startService(i)
    }

    private fun syncFloatingService() {
        val repo = SettingsRepository(this)
        val want = repo.floatingButtonEnabled && OverlayPermission.isGranted(this)
        val intent = Intent(this, FloatingButtonService::class.java)
        if (want) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else stopService(intent)
    }

    private fun syncVoiceService() {
        val repo = SettingsRepository(this)
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val want = repo.backgroundListening && hasMic
        val intent = Intent(this, VoiceActivationService::class.java)
        if (want) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else stopService(intent)
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }

    private sealed interface Screen {
        data object Chat : Screen
        data object Settings : Screen
        data object History : Screen
        data object ObdDashboard : Screen
    }

    companion object { const val EXTRA_AUTO_MIC = "auto_mic" }
}
