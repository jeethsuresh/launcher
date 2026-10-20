package app.still.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.GridView
import android.widget.ListView
import android.widget.TextClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class HomeDragIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: LauncherActivity
    private val pager get() = checkNotNull(LauncherTestUi.find(activity.window.decorView) { it is WorkspacePager }) as WorkspacePager
    private fun click(predicate: (View) -> Boolean) = LauncherTestUi.click(activity, predicate)
    private fun start() { activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity }
    private fun restart() {
        instrumentation.runOnMainSync { activity.finish() }
        LauncherTestUi.await { activity.isDestroyed }
        start()
    }
    private fun firstApp(): LaunchableApp {
        click { it.contentDescription == "Open app drawer" }
        var app: LaunchableApp? = null
        LauncherTestUi.await {
            instrumentation.runOnMainSync {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                if (grid.adapter.count > 0) app = grid.adapter.getItem(0) as LaunchableApp
            }
            app != null
        }
        return app!!
    }
    private fun drawerPointer(): LauncherTestUi.Pointer {
        var view: View? = null
        instrumentation.runOnMainSync {
            val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
            view = grid.getChildAt(0)
        }
        val r = LauncherTestUi.bounds(view!!)
        return LauncherTestUi.Pointer(r.exactCenterX(), r.exactCenterY()).hold(850)
    }
    private fun cell(x: Int, y: Int, w: Int = 1, h: Int = 1): Pair<Float, Float> {
        val r = LauncherTestUi.bounds(pager)
        return (r.left + r.width() * (x + w / 2f) / 4) to (r.top + r.height() * (y + h / 2f) / 6)
    }
    private fun move(pointer: LauncherTestUi.Pointer, point: Pair<Float, Float>) = pointer.move(point.first, point.second)
    private fun settled(page: Int) = LauncherTestUi.await { pager.selectedPage == page && pager.scrollX == 0 }

    @Test fun testDrawerDragAndBothEdgePagesPreserveExistingItems() = isolated {
        val app = firstApp()
        click { it.contentDescription == "Back to home" }
        val marker = HomeItem(kind = ItemKind.APP, appKey = app.key, x = 1, y = 1)
        LauncherStore(context).save(items = listOf(marker))
        restart(); firstApp()
        val r = LauncherTestUi.bounds(pager)
        val pointer = drawerPointer().move((activity.resources.displayMetrics.widthPixels - 3).toFloat(), r.exactCenterY()).hold(1200)
        settled(1)
        assertEquals(2, LauncherStore(context).pages)
        pointer.hold(800)
        assertEquals("Holding an edge must not create endless pages", 2, LauncherStore(context).pages)
        move(pointer, cell(0, 2)).up()
        LauncherTestUi.await { LauncherStore(context).items.size == 2 }
        val added = LauncherStore(context).items.single { it.id != marker.id }
        assertEquals(1, added.page); assertEquals(0, added.x); assertEquals(2, added.y)

        val start = cell(0, 2)
        val left = LauncherTestUi.Pointer(start.first, start.second).hold(850).move(3f, r.exactCenterY()).hold(1000)
        settled(0)
        move(left, cell(1, 3)).move(3f, r.exactCenterY()).hold(1000)
        settled(0)
        assertEquals(3, LauncherStore(context).pages)
        move(left, cell(2, 3)).up()
        LauncherTestUi.await { LauncherStore(context).items.first { it.id == added.id }.page == 0 }
        val items = LauncherStore(context).items
        assertEquals(marker.copy(page = 1), items.single { it.id == marker.id })
        assertEquals(added.copy(page = 0, x = 2, y = 3), items.single { it.id == added.id })
        assertEquals(2, items.size)
    }

    @Test fun testSwipeTracksFingerAndHeaderUsesDate() = isolated {
        instrumentation.runOnMainSync {
            assertNull(LauncherTestUi.find(activity.window.decorView) { it.contentDescription == "Launcher settings" })
            val date = LauncherTestUi.find(activity.window.decorView) { it is TextClock } as TextClock
            assertEquals(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()), date.text.toString())
        }
        LauncherTestUi.settings(activity)
        click { it.contentDescription == "Back to home" }
        LauncherStore(context).pages = 2
        restart()
        val r = LauncherTestUi.bounds(pager)
        val pointer = LauncherTestUi.Pointer(r.left + r.width() * .8f, r.exactCenterY())
            .move(r.left + r.width() * .45f, r.exactCenterY(), 350)
        assertTrue("The page must move before the finger is released", pager.scrollX > r.width() / 4 && pager.scrollX < r.width() / 2)
        assertEquals(0, pager.selectedPage)
        pointer.up(); settled(1)
        LauncherTestUi.Pointer(r.left + r.width() * .2f, r.exactCenterY()).move(r.left + r.width() * .65f, r.exactCenterY()).up()
        settled(0)
        LauncherTestUi.Pointer(r.left + r.width() * .8f, r.exactCenterY()).move(r.left + r.width() * .7f, r.exactCenterY()).cancel()
        settled(0)
    }

    @Test fun testWidgetDropSurvivesConfigurationAndCancelsCleanly() = isolated {
        firstApp()
        click { it is android.widget.TextView && it.text.toString() == "Widgets" }
        val (view, info) = widgetRow()
        val w = ceil((info.minWidth + AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null).let { it.left + it.right }) / pager.currentWorkspace.cellWidth).toInt()
        val h = ceil((info.minHeight + AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null).let { it.top + it.bottom }) / pager.currentWorkspace.cellHeight).toInt()
        val r = LauncherTestUi.bounds(pager)
        val source = LauncherTestUi.bounds(view)
        val pointer = LauncherTestUi.Pointer(source.exactCenterX(), source.exactCenterY()).hold(850)
            .move((activity.resources.displayMetrics.widthPixels - 3).toFloat(), r.exactCenterY()).hold(1000)
        settled(1)
        assertEquals(-1, LauncherStore(context).pendingWidget)
        move(pointer, cell(1, 1, w, h)).up()
        approveWidgetBinding()
        val pending = LauncherStore(context).pendingWidgetPlacement!!
        assertEquals(1, pending.page); assertEquals(1, pending.x); assertEquals(1, pending.y)
        instrumentation.runOnMainSync { activity.recreate() }
        LauncherTestUi.tapText("Finish widget setup")
        LauncherTestUi.await { LauncherStore(context).items.any { it.kind == ItemKind.WIDGET } }
        refreshActivity()
        val placed = LauncherStore(context).items.single { it.kind == ItemKind.WIDGET }
        assertEquals(pending.copy(widgetId = placed.widgetId), placed)
        assertEquals(-1, LauncherStore(context).pendingWidget)
        assertNull(LauncherStore(context).pendingWidgetPlacement)

        click { it.contentDescription == "Open app drawer" }
        click { it is android.widget.TextView && it.text.toString() == "Widgets" }
        val second = LauncherTestUi.bounds(widgetRow().first)
        val canceled = LauncherTestUi.Pointer(second.exactCenterX(), second.exactCenterY()).hold(850)
        move(canceled, cell(0, 4, w, h)).up()
        approveWidgetBinding()
        val id = LauncherStore(context).pendingWidget
        LauncherTestUi.tapText("Cancel widget setup")
        LauncherTestUi.await { LauncherStore(context).pendingWidget == -1 }
        assertNull(LauncherStore(context).pendingWidgetPlacement)
        assertFalse(id in AppWidgetHost(context, 1024).appWidgetIds)
        assertEquals(listOf(placed), LauncherStore(context).items)
    }

    private fun widgetRow(): Pair<View, AppWidgetProviderInfo> {
        lateinit var list: ListView
        var index = -1
        var info: AppWidgetProviderInfo? = null
        instrumentation.runOnMainSync {
            list = LauncherTestUi.find(activity.window.decorView) { it is ListView } as ListView
            for (i in 0 until list.adapter.count) {
                val entry = list.adapter.getView(i, null, list).tag as? AppWidgetProviderInfo
                if (entry?.provider?.className == DragTestWidgetProvider::class.java.name) { index = i; info = entry; break }
            }
            assertNotNull("Instrumentation widget must be installed", info)
            list.setSelection(index)
        }
        instrumentation.waitForIdleSync(); SystemClock.sleep(250)
        var view: View? = null
        instrumentation.runOnMainSync { view = list.getChildAt(index - list.firstVisiblePosition) }
        return view!! to info!!
    }

    private fun approveWidgetBinding() {
        try { LauncherTestUi.await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val finish = root?.findAccessibilityNodeInfosByText("Finish widget setup")?.isNotEmpty() == true
            if (!finish) {
                val allow = root?.findAccessibilityNodeInfosByText("Create")?.firstOrNull { it.className.toString() == "android.widget.Button" }
                    ?: root?.findAccessibilityNodeInfosByText("Allow")?.firstOrNull { it.className.toString() == "android.widget.Button" }
                if (allow != null) LauncherTestUi.tapText(allow.text.toString())
            }
            finish
        } } catch (error: Throwable) {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.cacheDir, "home-widget-bind.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val nodes = mutableListOf<String>()
            fun collect(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                nodes += "${node.className}: ${node.text} / ${node.contentDescription}"
                for (i in 0 until node.childCount) collect(node.getChild(i))
            }
            collect(instrumentation.uiAutomation.rootInActiveWindow)
            java.io.File(context.cacheDir, "home-widget-bind-ui.txt").writeText("pending=${LauncherStore(context).pendingWidget} placement=${LauncherStore(context).pendingWidgetPlacement}\n" + nodes.joinToString("\n"))
            throw error
        }
    }

    private fun refreshActivity() = LauncherTestUi.await {
        var found = false
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<LauncherActivity>().firstOrNull()?.let { activity = it; found = true }
        }
        found
    }

    private fun isolated(test: () -> Unit) {
        val original = LauncherStore(context)
        val originalPages = original.pages
        val originalItems = original.items
        val originalDock = original.dock
        val originalSettings = original.settings
        val host = AppWidgetHost(context, 1024)
        val ids = host.appWidgetIds.toSet()
        original.save(items = emptyList(), settings = original.settings.copy(columns = 4, rows = 6, locked = false))
        original.pages = 1
        start()
        try { test() } finally {
            instrumentation.runOnMainSync {
                Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                    .filterIsInstance<LauncherActivity>().distinct().forEach { it.finish() }
            }
            host.appWidgetIds.filterNot { it in ids }.forEach { host.deleteAppWidgetId(it) }
            LauncherStore(context).apply {
                save(items = originalItems, dock = originalDock, settings = originalSettings)
                pages = originalPages; savePendingWidget(-1, null)
            }
        }
    }
}
