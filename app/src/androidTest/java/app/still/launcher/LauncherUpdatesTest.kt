package app.still.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherUpdatesTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: LauncherActivity
    private val pager get() = LauncherTestUi.find(activity.window.decorView) { it is WorkspacePager } as WorkspacePager
    private fun click(predicate: (View) -> Boolean) = LauncherTestUi.click(activity, predicate)
    private fun start() { activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity }
    private fun restart() { instrumentation.runOnMainSync { activity.finish() }; LauncherTestUi.await { activity.isDestroyed }; start() }
    private fun cell(x: Int, y: Int): Pair<Float, Float> {
        val r = LauncherTestUi.bounds(pager)
        return r.left + r.width() * (x + .5f) / 4 to r.top + r.height() * (y + .5f) / 6
    }
    private fun drag(from: Pair<Float, Float>, to: Pair<Float, Float>) = LauncherTestUi.Pointer(from.first, from.second).hold(850).move(to.first, to.second).up()
    private fun viewPoint(predicate: (View) -> Boolean): Pair<Float, Float> {
        var found: View? = null
        LauncherTestUi.await { instrumentation.runOnMainSync { found = LauncherTestUi.find(activity.window.decorView, predicate) }; found != null }
        val r = LauncherTestUi.bounds(found!!); return r.exactCenterX() to r.exactCenterY()
    }
    private fun apps(): List<LaunchableApp> {
        click { it.contentDescription == "Open app drawer" }
        var apps = emptyList<LaunchableApp>()
        LauncherTestUi.await { instrumentation.runOnMainSync {
            val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
            apps = (0 until grid.adapter.count).map { grid.adapter.getItem(it) as LaunchableApp }
        }; apps.size >= 5 }
        return apps
    }

    @Test fun foldersCreateJoinMoveToDockExtractRenameAndPersist() = isolated {
        val apps = apps(); click { it.contentDescription == "Back to home" }
        val a = HomeItem(kind = ItemKind.APP, appKey = apps[0].key, x = 0, y = 1)
        val b = HomeItem(kind = ItemKind.APP, appKey = apps[1].key, x = 1, y = 1)
        LauncherStore(context).save(items = listOf(a, b), dock = listOf(apps[3].key, apps[4].key))
        restart()
        drag(cell(0, 1), cell(1, 1))
        LauncherTestUi.await { LauncherStore(context).folders.size == 1 }
        val folderKey = LauncherStore(context).folders.single().key
        assertEquals(listOf(apps[1].key, apps[0].key), LauncherStore(context).folders.single().apps)
        apps()
        val from = viewPoint { it.contentDescription == apps[2].label }
        drag(from, cell(1, 1))
        LauncherTestUi.await { LauncherStore(context).folders.single().apps.size == 3 }
        val emptySlot = viewPoint { it.contentDescription?.startsWith("Empty dock slot 3") == true }
        drag(cell(1, 1), emptySlot)
        LauncherTestUi.await { folderKey in LauncherStore(context).dock }
        assertTrue(LauncherStore(context).items.isEmpty())
        click { it.contentDescription?.startsWith("Folder, folder, 3") == true }
        click { it is TextView && it.text.toString() == "Rename" }
        instrumentation.runOnMainSync { (LauncherTestUi.find(activity.window.decorView) { it is EditText && it.contentDescription == "Folder name" } as? EditText)?.setText("Daily") }
        // Dialog windows are separate from the activity decor.
        val field = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Folder").firstOrNull { it.className == "android.widget.EditText" }
        if (field != null) field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Daily") })
        LauncherTestUi.tapText("Save")
        LauncherTestUi.await { LauncherStore(context).folders.single().title == "Daily" }
        val extract = viewPoint { it.contentDescription == apps[0].label }
        drag(extract, cell(2, 2))
        LauncherTestUi.await { LauncherStore(context).items.any { it.appKey == apps[0].key } }
        assertEquals(2, LauncherStore(context).folders.single().apps.size)
        restart()
        assertEquals("Daily", LauncherStore(context).folders.single().title)
        assertEquals(folderKey, LauncherStore(context).dock.last())
    }

    @Test fun dockAppsCreateFolder() = isolated {
        val apps = apps(); click { it.contentDescription == "Back to home" }
        LauncherStore(context).save(items = emptyList(), dock = listOf(apps[0].key, apps[1].key))
        restart(); instrumentation.waitForIdleSync(); SystemClock.sleep(400)
        drag(viewPoint { it.contentDescription == apps[0].label }, viewPoint { it.contentDescription == apps[1].label })
        try { LauncherTestUi.await { LauncherStore(context).folders.size == 1 } }
        catch (error: Throwable) { throw AssertionError("Dock grouping state: ${LauncherStore(context).state}", error) }
        assertEquals(listOf(apps[1].key, apps[0].key), LauncherStore(context).folders.single().apps)
        assertEquals(1, LauncherStore(context).dock.size)
    }

    @Test fun uninstallDropUsesAndroidConfirmation() = isolated {
        val fixture = apps().single { it.info.componentName.packageName == "app.still.removalfixture" }
        val from = viewPoint { it.contentDescription == fixture.label }
        val pointer = LauncherTestUi.Pointer(from.first, from.second).hold(850)
        val target = viewPoint { it.contentDescription == "Uninstall" }
        pointer.move(target.first, target.second).up()
        LauncherTestUi.await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.findAccessibilityNodeInfosByText("Do you want to uninstall this app?")?.isNotEmpty() == true
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val positive = root.findAccessibilityNodeInfosByText("OK").firstOrNull { it.className == "android.widget.Button" }
            ?: root.findAccessibilityNodeInfosByText("Uninstall").first { it.className == "android.widget.Button" }
        LauncherTestUi.tapText(positive.text.toString())
        LauncherTestUi.await { runCatching { context.packageManager.getApplicationInfo("app.still.removalfixture", 0) }.isFailure }
    }

    @Test fun drawerBottomSearchAndHideDrop() = isolated {
        val apps = apps()
        lateinit var search: View; lateinit var grid: View
        instrumentation.runOnMainSync {
            assertNull(LauncherTestUi.find(activity.window.decorView) { it is TextView && it.text.toString() in listOf("DRAWER", "All apps") })
            search = LauncherTestUi.find(activity.window.decorView) { it is EditText }!!
            grid = LauncherTestUi.find(activity.window.decorView) { it is GridView }!!
        }
        assertTrue(LauncherTestUi.bounds(search).top >= LauncherTestUi.bounds(grid).bottom)
        val from = viewPoint { it.contentDescription == apps[0].label }
        val pointer = LauncherTestUi.Pointer(from.first, from.second).hold(850)
        val target = viewPoint { it.contentDescription == "Hide" }
        pointer.move(target.first, target.second).up()
        LauncherTestUi.await { apps[0].key in LauncherStore(context).hiddenApps }
        restart(); apps()
        instrumentation.runOnMainSync {
            val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
            assertFalse((0 until grid.adapter.count).any { (grid.adapter.getItem(it) as LaunchableApp).key == apps[0].key })
        }
    }

    @Test fun independentSurfaceColoursAndOpacitySurviveRestart() = isolated {
        LauncherStore(context).save(settings = LauncherStore(context).settings.copy(drawerColor = "#183b56", drawerOpacity = 43, dockColor = "#573654", dockOpacity = 72))
        restart(); apps()
        instrumentation.runOnMainSync {
            val body = LauncherTestUi.find(activity.window.decorView) { it is PullDownDrawerLayout }!!
            assertEquals(Color.argb(43 * 255 / 100, 0x18, 0x3b, 0x56), (body.background as GradientDrawable).color!!.defaultColor)
        }
        click { it.contentDescription == "Back to home" }
        LauncherTestUi.settings(activity)
        click { it.contentDescription?.startsWith("Dock colour & opacity,") == true }
        fun findColour(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.contentDescription == "Colour #000000") return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { findColour(it)?.let { found -> return found } }
            return null
        }
        LauncherTestUi.await { findColour(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true }
        LauncherTestUi.tapText("Apply")
        assertEquals("#000000", LauncherStore(context).settings.dockColor)
        assertEquals(72, LauncherStore(context).settings.dockOpacity)
        assertEquals(43, LauncherStore(context).settings.drawerOpacity)
    }

    private fun isolated(test: () -> Unit) {
        val original = LauncherStore(context); val state = original.state; val settings = original.settings; val pages = original.pages; val hidden = original.hiddenApps
        original.save(items = emptyList(), dock = emptyList(), folders = emptyList(), settings = settings.copy(columns = 4, rows = 6, locked = false))
        original.pages = 1; original.saveHiddenApps(emptySet()); start()
        try { test() } finally {
            instrumentation.runOnMainSync { activity.finish() }
            LauncherStore(context).apply { save(state.items, state.dock, settings, state.folders); this.pages = pages; saveHiddenApps(hidden) }
        }
    }
}
