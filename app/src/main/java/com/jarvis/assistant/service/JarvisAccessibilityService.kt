package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lets JARVIS operate any app: read screen, tap, scroll, type, global keys. */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---------- reading ----------
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Screen is not readable."
        val sb = StringBuilder("App: ${root.packageName}\n")
        var count = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count > 120) return
            val label = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            if (n.isVisibleToUser && (label.isNotEmpty() || n.isEditable)) {
                count++
                sb.append("- ").append(label.take(80))
                if (n.isEditable) sb.append(" [input]")
                if (n.isClickable) sb.append(" [tap]")
                if (n.isScrollable) sb.append(" [scroll]")
                sb.append('\n')
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString()
    }

    private fun find(root: AccessibilityNodeInfo, exact: Boolean, q: String): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || hit != null) return
            val s = (n.text?.toString() ?: "").lowercase()
            val d = (n.contentDescription?.toString() ?: "").lowercase()
            val ok = if (exact) s == q || d == q else s.contains(q) || d.contains(q)
            if (n.isVisibleToUser && ok) { hit = n; return }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return hit
    }

    // ---------- acting ----------
    suspend fun clickText(target: String): String {
        val root = rootInActiveWindow ?: return "No active window."
        val q = target.lowercase().trim()
        val node = find(root, true, q) ?: find(root, false, q) ?: return "Could not find '$target' on screen."
        var c: AccessibilityNodeInfo? = node
        while (c != null && !c.isClickable) c = c.parent
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Tapped '$target'."
        val r = Rect(); node.getBoundsInScreen(r)
        return if (tap(r.exactCenterX(), r.exactCenterY())) "Tapped '$target'." else "Tap failed."
    }

    suspend fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    suspend fun scroll(direction: String): String {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat(); val h = m.heightPixels.toFloat()
        val p = Path()
        when (direction.lowercase()) {
            "down" -> { p.moveTo(w / 2, h * 0.72f); p.lineTo(w / 2, h * 0.28f) }
            "up" -> { p.moveTo(w / 2, h * 0.28f); p.lineTo(w / 2, h * 0.72f) }
            "left" -> { p.moveTo(w * 0.25f, h / 2); p.lineTo(w * 0.75f, h / 2) }
            "right" -> { p.moveTo(w * 0.75f, h / 2); p.lineTo(w * 0.25f, h / 2) }
            else -> return "Unknown direction."
        }
        return if (gesture(p, 350)) "Scrolled $direction." else "Scroll failed."
    }

    private suspend fun gesture(path: Path, dur: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "No active window."
        var target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null) {
            fun walk(n: AccessibilityNodeInfo?) {
                if (n == null || target != null) return
                if (n.isEditable && n.isVisibleToUser) { target = n; return }
                for (i in 0 until n.childCount) walk(n.getChild(i))
            }
            walk(root)
        }
        val t = target ?: return "No text field found."
        t.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (t.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Typed." else "Could not type."
    }

    fun pressKey(key: String): String {
        val ok = when (key.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "lock" -> Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "enter", "search" -> Build.VERSION.SDK_INT >= 30 &&
                (rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) == true)
            else -> return "Unknown key."
        }
        return if (ok) "Pressed ${key}." else "Key press failed."
    }

    /** Flips the first on/off switch on screen (used for Wi-Fi / Bluetooth pages). */
    fun setFirstSwitch(enabled: Boolean): String {
        val root = rootInActiveWindow ?: return "No active window."
        var sw: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || sw != null) return
            val cls = n.className?.toString() ?: ""
            if (n.isCheckable && (cls.contains("Switch") || cls.contains("Toggle") || cls.contains("CheckBox"))) { sw = n; return }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val s = sw ?: return "No switch found on this screen."
        if (s.isChecked == enabled) return "Already ${if (enabled) "on" else "off"}."
        var c: AccessibilityNodeInfo? = s
        while (c != null && !c.isClickable) c = c.parent
        return if (c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "Switched." else "Could not toggle."
    }
}
