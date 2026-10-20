package app.still.launcher

import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*

object LauncherTestUi {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let { return it }
        return null
    }
    private fun active(fallback: Activity): Activity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull { it.javaClass == fallback.javaClass } ?: fallback
    fun click(activity: Activity, predicate: (View) -> Boolean) {
        var view: View? = null
        await {
            instrumentation.runOnMainSync { view = find(active(activity).window.decorView, predicate) }
            view != null
        }
        instrumentation.runOnMainSync { assertTrue(view!!.performClick()) }
        instrumentation.waitForIdleSync()
    }
    fun settings(activity: Activity) {
        instrumentation.runOnMainSync {
            val date = find(active(activity).window.decorView) { it is TextClock }!!
            assertTrue((date.parent as View).performLongClick())
        }
        tapText("Launcher settings")
        await {
            var ready = false
            instrumentation.runOnMainSync { ready = find(active(activity).window.decorView) { it.contentDescription?.startsWith("Hidden apps,") == true } != null }
            ready
        }
    }
    fun tapText(text: String) {
        var bounds = Rect()
        await {
            val node = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString()?.equals(text, true) == true }
            node?.getBoundsInScreen(bounds)
            node != null && !bounds.isEmpty
        }
        Pointer(bounds.exactCenterX(), bounds.exactCenterY()).up()
        instrumentation.waitForIdleSync()
    }
    fun bounds(view: View): Rect {
        val rect = Rect()
        instrumentation.runOnMainSync {
            val location = IntArray(2); view.getLocationOnScreen(location)
            assertTrue(view.width > 0 && view.height > 0)
            rect.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        return rect
    }
    fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Timed out waiting for launcher state", condition())
    }
    class Pointer(private var x: Float, private var y: Float) {
        private val down = SystemClock.uptimeMillis()
        init { send(MotionEvent.ACTION_DOWN) }
        fun hold(ms: Long): Pointer { SystemClock.sleep(ms); return this }
        fun move(nextX: Float, nextY: Float, duration: Long = 300): Pointer {
            val fromX = x; val fromY = y
            for (step in 1..12) {
                x = fromX + (nextX - fromX) * step / 12
                y = fromY + (nextY - fromY) * step / 12
                SystemClock.sleep(duration / 12); send(MotionEvent.ACTION_MOVE)
            }
            return this
        }
        fun up() { send(MotionEvent.ACTION_UP) }
        fun cancel() { send(MotionEvent.ACTION_CANCEL) }
        private fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
    }
}
