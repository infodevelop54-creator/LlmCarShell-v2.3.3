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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.llmcar.ui.OverlayInputWidget
import com.example.llmcar.ui.theme.VisualTheme
import com.example.llmcar.util.AppSignals
import kotlin.math.abs

/**
 * v2.3.3 — floating widget + popup input overlay.
 *
 * Сервис держит ДВА независимых окна WindowManager:
 *   1) pillView — маленькая пилюля-кнопка, ПЕРЕМЕЩАЕМАЯ пальцем, без snap-to-edge,
 *      позиция сохраняется в SettingsRepository (overlayPosX / overlayPosY).
 *   2) overlayView — появляется по тапу на пилюлю, содержит поле ввода внизу экрана,
 *      НЕ перемещается, НЕ открывает чат, закрывается крестиком или тапом вне пилюли.
 *
 * Никаких разворотов в MainActivity (кроме одного случая — кнопка микрофона,
 * которая открывает MainActivity с автостартом голосового ввода).
 */
class FloatingButtonService : Service(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var wm: WindowManager
    private lateinit var settings: SettingsRepository
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    // --------- Окно пилюли ---------
    private var pillView: View? = null
    private lateinit var pillParams: WindowManager.LayoutParams

    // --------- Окно оверлея ввода ---------
    private var overlayView: View? = null
    private lateinit var overlayParams: WindowManager.LayoutParams

    // --------- Состояние перетаскивания пилюли ---------
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
        attachPill()
    }

    // ============================================================
    // Окно 1: пилюля-кнопка (перемещаемая)
    // ============================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPill() {
        val type = overlayType()

        pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        pillParams.gravity = Gravity.TOP or Gravity.START
        pillParams.x = settings.overlayPosX
        pillParams.y = settings.overlayPosY

        val cv = ComposeView(this)
        cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            PillRoot(
                settings = settings,
                chatVm = chatVm,
                onTap = { showInputOverlay() },
                onClose = { stopSelf() }
            )
        }
        cv.setOnTouchListener(pillTouchListener())
        pillView = cv
        wm.addView(pillView, pillParams)
    }

    private fun pillTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragging = false
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        dragStartX = pillParams.x
                        dragStartY = pillParams.y
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
                            pillParams.x = dragStartX + dx.toInt()
                            pillParams.y = dragStartY + dy.toInt()
                            try {
                                wm.updateViewLayout(v, pillParams)
                            } catch (_: Exception) {
                            }
                        }
                        return dragging
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragging) {
                            // Не прилипаем к краям — только ограничиваем видимой областью.
                            clampPillToScreen()
                            settings.setOverlayPosX(pillParams.x)
                            settings.setOverlayPosY(pillParams.y)
                        }
                        return dragging
                    }
                    else -> return false
                }
            }
        }
    }

    private fun clampPillToScreen() {
        val v = pillView ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val w = if (v.width > 0) v.width else 200
        val h = if (v.height > 0) v.height else 100
        pillParams.x = pillParams.x.coerceIn(0, (screenW - w).coerceAtLeast(0))
        pillParams.y = pillParams.y.coerceIn(0, (screenH - h).coerceAtLeast(0))
        try {
            wm.updateViewLayout(v, pillParams)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Окно 2: оверлей ввода (появляется/исчезает)
    // ============================================================

    private fun showInputOverlay() {
        if (overlayView != null) return
        val type = overlayType()

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START
        overlayParams.x = 0
        overlayParams.y = 0
        overlayParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

        val cv = ComposeView(this)
        cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            InputOverlayRoot(
                settings = settings,
                onSend = { text ->
                    chatVm.send(text)
                    hideInputOverlay()
                },
                onMicClick = {
                    hideInputOverlay()
                    openAppWithMic()
                },
                onClose = { hideInputOverlay() }
            )
        }
        overlayView = cv
        wm.addView(overlayView, overlayParams)
    }

    private fun hideInputOverlay() {
        val v = overlayView ?: return
        overlayView = null
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Вспомогательное
    // ============================================================

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun openAppWithMic() {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_AUTO_MIC, true)
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
        hideInputOverlay()
        val v = pillView
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (_: Exception) {
            }
        }
        pillView = null
        vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1002
    }
}

// =============================================================
// Compose для пилюли-кнопки
// =============================================================

@Composable
private fun PillRoot(
    settings: SettingsRepository,
    chatVm: ChatViewModel,
    onTap: () -> Unit,
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
            onExpand = onTap,
            onDragUp = onTap,   // не срабатывает — drag поглощается OnTouchListener
            onClose = onClose
        )
    }
}

// =============================================================
// Compose для оверлея ввода
// =============================================================

@Composable
private fun InputOverlayRoot(
    settings: SettingsRepository,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    onClose: () -> Unit
) {
    val palette = VisualTheme.palette(settings.accentHue)
    val pillHeightDp = VisualTheme.pillHeight(settings.pillSize)

    // Полноэкранная прозрачная подложка. Тап вне пилюли закрывает оверлей.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose
            )
    ) {
        // Пилюля ввода — внизу, «проглатывает» клики по себе,
        // чтобы они не закрывали оверлей.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* проглатываем */ }
                )
        ) {
            OverlayInputWidget(
                palette = palette,
                pillHeightDp = pillHeightDp,
                onSend = onSend,
                onMicClick = onMicClick,
                onClose = onClose
            )
        }
    }
}
