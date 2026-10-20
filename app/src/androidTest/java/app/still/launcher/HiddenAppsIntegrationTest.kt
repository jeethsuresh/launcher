package app.still.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import android.view.InputDevice
import android.graphics.Rect
import android.widget.EditText
import android.widget.GridView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class HiddenAppsIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: LauncherActivity

    @Test fun testHideSearchRestartAndRestoreWithoutChangingShortcuts() = withCleanHiddenApps {
        val before = LauncherStore(context)
        val items = before.items
        val dock = before.dock
        openDrawer()
        val all = drawerApps()
        val target = all.first()
        val unavailable = "app.unavailable/.Main#999"
        // Saving changes to available apps must preserve a temporarily unavailable profile choice.
        before.saveHiddenApps(setOf(unavailable))
        restart()
        openDrawer()
        instrumentation.runOnMainSync {
            val grid = findView { it is GridView } as GridView
            assertTrue(grid.onItemLongClickListener!!.onItemLongClick(grid, grid.getChildAt(0), 0, grid.adapter.getItemId(0)))
        }
        clickDialog(target.label, "Hide from All apps")
        await { target.key in LauncherStore(context).hiddenApps }
        assertEquals(all.size - 1, drawerApps().size)
        instrumentation.runOnMainSync {
            (findView { it.contentDescription == "Search installed apps" } as EditText).setText(target.label)
        }
        assertTrue(drawerApps().none { it.key == target.key })
        assertEquals(items, LauncherStore(context).items)
        assertEquals(dock, LauncherStore(context).dock)

        restart()
        openDrawer()
        await { drawerApps().size == all.size - 1 }
        assertTrue(drawerApps().none { it.key == target.key })
        openManager()
        clickDialog("Hidden apps", target.label)
        clickDialog("Hidden apps", "Cancel")
        assertTrue(target.key in LauncherStore(context).hiddenApps)
        clickView { it.contentDescription?.startsWith("Hidden apps,") == true }
        clickDialog("Hidden apps", target.label)
        clickDialog("Hidden apps", "Save")
        assertEquals(setOf(unavailable), LauncherStore(context).hiddenApps)
        clickView { it.contentDescription == "Back to home" }
        openDrawer()
        await { drawerApps().size == all.size }
        assertTrue(drawerApps().any { it.key == target.key })
    }

    @Test fun testAllHiddenCanBeRestoredFromSettings() = withCleanHiddenApps {
        openDrawer()
        val all = drawerApps()
        LauncherStore(context).saveHiddenApps(all.map { it.key }.toSet())
        restart()
        clickView { it.contentDescription == "Open app drawer" }
        instrumentation.waitForIdleSync()
        assertTrue(drawerApps().isEmpty())
        instrumentation.runOnMainSync {
            assertNotNull(findView { it is android.widget.TextView && it.text.toString().contains("Restore apps in Launcher settings") })
        }
        openManager()
        clickDialog("Hidden apps", "Show all")
        assertTrue(LauncherStore(context).hiddenApps.isEmpty())
        clickView { it.contentDescription == "Back to home" }
        openDrawer()
        await { drawerApps().size == all.size }
    }

    private fun withCleanHiddenApps(test: () -> Unit) {
        val original = LauncherStore(context).hiddenApps
        LauncherStore(context).saveHiddenApps(emptySet())
        start()
        try { test() } finally {
            instrumentation.runOnMainSync { activity.finish() }
            LauncherStore(context).saveHiddenApps(original)
        }
    }

    private fun start() {
        activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
    }

    private fun restart() {
        instrumentation.runOnMainSync { activity.finish() }
        await { activity.isDestroyed }
        start()
    }

    private fun openDrawer() {
        clickView { it.contentDescription == "Open app drawer" }
        await { drawerApps().isNotEmpty() }
    }

    private fun openManager() {
        clickView { it.contentDescription == "Back to home" }
        clickView { it.contentDescription == "Launcher settings" }
        clickView { it.contentDescription?.startsWith("Hidden apps,") == true }
    }

    private fun drawerApps(): List<LaunchableApp> {
        var result = emptyList<LaunchableApp>()
        instrumentation.runOnMainSync {
            val grid = findView { it is GridView } as GridView
            result = (0 until grid.adapter.count).map { grid.adapter.getItem(it) as LaunchableApp }
        }
        return result
    }

    private fun findView(predicate: (View) -> Boolean): View? {
        fun find(view: View): View? {
            if (predicate(view)) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(activity.window.decorView)
    }

    private fun clickView(predicate: (View) -> Boolean) {
        instrumentation.runOnMainSync { assertTrue(checkNotNull(findView(predicate)).performClick()) }
        instrumentation.waitForIdleSync()
    }

    private fun clickDialog(title: String, text: String) {
        var node: AccessibilityNodeInfo? = null
        await {
            node = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString()?.equals(text, ignoreCase = true) == true }
            node != null
        }
        val bounds = Rect()
        node!!.getBoundsInScreen(bounds)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("Cannot click $text in $title", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        instrumentation.waitForIdleSync()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Timed out waiting for hidden-app state", condition())
    }
}
