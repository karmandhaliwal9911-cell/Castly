package tv.castly.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

object RemoteState { @Volatile var allowed: String? = null }     // only the phone you tapped "Allow" for can control the TV

/** Turn on once: TV Settings > Accessibility > Castly Remote. Gives the phone a pointer, taps, typing and navigation. */
class RemoteService : AccessibilityService() {
    companion object { @Volatile var i: RemoteService? = null }
    private val ui = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val dot by lazy { View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xDD5EEAD4.toInt()); setStroke(3, Color.WHITE) } } }
    private val lp = WindowManager.LayoutParams(32, 32, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
    private var x = 0; private var y = 0; private var shown = false
    private val hide = Runnable { if (shown) { wm.removeView(dot); shown = false } }

    override fun onServiceConnected() {
        i = this
        val d = resources.displayMetrics; x = d.widthPixels / 2; y = d.heightPixels / 2
        thread(name = "pointer-udp") {                               // pointer moves arrive here from the phone
            val s = DatagramSocket(8091); val b = ByteArray(64)
            while (true) {
                val p = DatagramPacket(b, b.size); s.receive(p)
                if (p.address.hostAddress != RemoteState.allowed) continue
                val t = String(p.data, 0, p.length).split(" ")
                if (t.size == 3 && t[0] == "m") move(t[1].toIntOrNull() ?: 0, t[2].toIntOrNull() ?: 0)
                if (t.size == 2 && t[0] == "s") scroll(t[1].toIntOrNull() ?: 0)
            }
        }
    }

    fun move(dx: Int, dy: Int) = ui.post {
        val d = resources.displayMetrics
        x = (x + dx).coerceIn(0, d.widthPixels); y = (y + dy).coerceIn(0, d.heightPixels)
        lp.x = x - 16; lp.y = y - 16
        if (shown) wm.updateViewLayout(dot, lp) else { wm.addView(dot, lp); shown = true }
        ui.removeCallbacks(hide); ui.postDelayed(hide, 4000)         // pointer hides after 4 s
    }

    private var acc = 0f
    /** Scroll strip: collects finger movement, then swipes at the pointer (drag down = content moves down). */
    fun scroll(dy: Int) { acc += dy
        if (Math.abs(acc) < 60f) return
        val len = acc.coerceIn(-600f, 600f); acc = 0f
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x.toFloat(), (y - len).coerceIn(0f, resources.displayMetrics.heightPixels.toFloat())) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 120)).build(), null, null) }

    fun click() {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    private fun focused() = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun go(dir: Int) { focused()?.focusSearch(dir)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }

    fun key(n: String) {
        val a = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (n) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "UP" -> go(View.FOCUS_UP); "DOWN" -> go(View.FOCUS_DOWN)
            "LEFT" -> go(View.FOCUS_LEFT); "RIGHT" -> go(View.FOCUS_RIGHT)
            "OK" -> focused()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: click()
            "ENTER" -> focused()?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            "VOL_UP" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "VOL_DOWN" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "MUTE" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun text(full: String) {
        val f = focused() ?: return
        f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, full) })
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { i = null; super.onDestroy() }
}
