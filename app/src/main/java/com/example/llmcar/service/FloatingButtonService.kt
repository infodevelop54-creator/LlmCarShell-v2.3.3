package com.example.llmcar.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.R
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.ui.ChatViewModel
import com.example.llmcar.ui.OverlayCollapsedPill
import com.example.llmcar.ui.theme.VisualTheme
import com.example.llmcar.util.AppSignals
import kotlin.math.abs

/**
 * v2.3.2 — вариант B.
 * Свёрнутая пилюля поверх других приложений. Перетаскивается по экрану,
 * сохраняет позицию. По тапу открывает MainActivity (полноэкранный чат).
 * Развёрнутого overlay-чата больше нет — весь чат живёт в активити.
 */
class FloatingButtonService : Service(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var wm: WindowManager
    private var view: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private lateinit var settings: SettingsRepository

    // --------- Состояние перетаскивания ---------
    private var dragging: Boolean = false
    private var dragDownX: Float = 0f
    private var dragDownY: Float = 0f
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0

    private val vmStore = ViewModelStore()
    private val vmFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application) as T
        }
    }
    private val chatVm: ChatViewModel by lazy {
        ViewModelProvider(vmStore, vmFactory)[ChatViewModel::class.java]
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        startForeground(NOTIF_ID, notif())
        settings = SettingsRepository(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        attach()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = settings.overlayPosX
        params.y = settings.overlayPosY

        val cv = ComposeView(this)
        cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            OverlayRoot(
                settings = settings,
                chatVm = chatVm,
                onOpenApp = { openApp() },
                onClose = { stopSelf() }
            )
        }
        cv.setOnTouchListener(buildTouchListener())
        view = cv
        wm.addView(view, params)
    }

    private fun buildTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragging = false
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        dragStartX = params.x
                        dragStartY = params.y
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - dragDownX
                        val dy = event.rawY - dragDownY
                        if (!dragging && (abs(dx) > 12f || abs(dy) > 12f)) {
                            dragging = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (dragging) {
                            params.x = dragStartX + dx.toInt()
                            params.y = dragStartY + dy.toInt()
                            try {
                                wm.updateViewLayout(v, params)
                            } catch (_: Exception) {
                            }
                        }
                        return dragging
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragging) {
                            snapToEdge()
                            settings.setOverlayPosX(params.x)
                            settings.setOverlayPosY(params.y)
                        }
                        return dragging
                    }
                    else -> return false
                }
            }
        }
    }

    private fun snapToEdge() {
        val v = view ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val pillWidth = if (v.width > 0) v.width else (screenWidth * 3 / 4)
        val margin = 16
        params.x = if (params.x + pillWidth / 2 < screenWidth / 2) margin
                    else (screenWidth - pillWidth - margin).coerceAtLeast(margin)

        val screenHeight = resources.displayMetrics.heightPixels
        val pillHeight = if (v.height > 0) v.height else 100
        params.y = params.y.coerceIn(0, (screenHeight - pillHeight).coerceAtLeast(0))
        try {
            wm.updateViewLayout(v, params)
        } catch (_: Exception) {
        }
    }

    /**
     * Открывает MainActivity. launchMode="singleTask" в манифесте гарантирует,
     * что существующая активити получит onNewIntent, а не будет создана заново.
     */
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun notif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_FLOATING)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        val v = view
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (_: Exception) {
            }
        }
        view = null
        vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1002
    }
}

/**
 * Composable-корень сервиса. Показывает только свёрнутую пилюлю.
 */
@Composable
private fun OverlayRoot(
    settings: SettingsRepository,
    chatVm: ChatViewModel,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val palette = VisualTheme.palette(settings.accentHue)
    val pillHeightDp = VisualTheme.pillHeight(settings.pillSize)
    val presetIcon = VisualTheme.presetIcon(settings.presetIconStyle)

    var presetName by remember { mutableStateOf(chatVm.activePresetName()) }

    LaunchedEffect(Unit) {
        AppSignals.presetChanged.collect {
            presetName = chatVm.activePresetName()
        }
    }

    Box(Modifier.fillMaxWidth().wrapContentHeight()) {
        OverlayCollapsedPill(
            palette = palette,
            pillHeightDp = pillHeightDp,
            presetIcon = presetIcon,
            presetName = "Режим: $presetName",
            onExpand = onOpenApp,
            onDragUp = onOpenApp,
            onClose = onClose
        )
    }
}
