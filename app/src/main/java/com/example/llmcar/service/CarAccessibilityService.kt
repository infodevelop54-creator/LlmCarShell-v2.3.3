package com.example.llmcar.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected(); inst = this; _active.value = true
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        inst = null; _active.value = false; super.onDestroy()
    }

    fun clickByText(text: String): Boolean {
        val r = rootInActiveWindow ?: return false
        return r.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { click(it) } ?: false
    }

    fun clickByViewId(id: String): Boolean {
        val r = rootInActiveWindow ?: return false
        return r.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { click(it) } ?: false
    }

    fun dumpActiveWindowText(): String {
        val r = rootInActiveWindow ?: return ""
        val sb = StringBuilder(); collect(r, sb, 0); return sb.toString().trim()
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, d: Int) {
        if (n == null || d > 40) return
        n.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, d + 1)
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null) {
            if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            c = c.parent
        }
        return false
    }

    companion object {
        @Volatile private var inst: CarAccessibilityService? = null
        private val _active = MutableStateFlow(false)
        val active: StateFlow<Boolean> = _active.asStateFlow()
        fun isRunning() = inst != null
        fun clickText(t: String) = inst?.clickByText(t) ?: false
        fun clickViewId(id: String) = inst?.clickByViewId(id) ?: false
        fun dumpWindow(): String = inst?.dumpActiveWindowText() ?: ""
    }
}