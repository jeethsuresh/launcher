package app.still.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.GridView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FolderPopoverIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: LauncherActivity
    private lateinit var apps: List<LaunchableApp>
    private val pager get() = find { it is WorkspacePager } as WorkspacePager
    private val home get() = pager.parent as ViewGroup
    private val dock get() = (find { it.contentDescription?.startsWith("Empty dock slot 5") == true }!!.parent as View)
    private val popover get() = find { it is FolderPopover } as? FolderPopover
    private val grid get() = LauncherTestUi.find(popover!!) { it is GridView } as GridView
    private fun find(predicate: (View) -> Boolean) = LauncherTestUi.find(activity.window.decorView, predicate)
    private fun click(predicate: (View) -> Boolean) = LauncherTestUi.click(activity, predicate)
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun await(condition: () -> Boolean) = LauncherTestUi.await { onMain(condition) }
    private fun start() { activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity }
    private fun restart() {
        instrumentation.runOnMainSync { activity.finish() }
        LauncherTestUi.await { activity.isDestroyed }
        start()
        await { find { it.contentDescription == apps.first().label } != null }
    }
    private fun members(count: Int) = (apps.map { it.key } + (1..count).map { "app.unavailable$it/.Main#0" }).take(count)
    private fun open(title: String) {
        click { it.contentDescription?.startsWith("$title, folder,") == true }
        await { popover != null && grid.childCount > 0 }
    }
    private fun close() {
        click { it.contentDescription == "Back to home" }
        await { popover == null }
    }
    private fun marker(page: Int = 0) = HomeItem(kind = ItemKind.APP, appKey = apps.first().key, page = page)

    @Test fun overlaysCurrentPageAndOutsideTapDoesNotLaunchUnderlyingApp() = isolated {
        val folder = LauncherFolder(title = "Daily", apps = members(3))
        val item = HomeItem(kind = ItemKind.FOLDER, appKey = folder.key, page = 1, x = 1, y = 4)
        LauncherStore(context).apply { save(listOf(marker(1), item), emptyList(), folders = listOf(folder)); pages = 2 }
        restart()
        instrumentation.runOnMainSync { pager.goTo(1, animated = false) }
        await { pager.selectedPage == 1 && pager.currentWorkspace.width > 0 }
        val workspace = onMain { pager.currentWorkspace }
        val state = LauncherStore(context).state
        open("Daily")
        val bounds = LauncherTestUi.bounds(popover!!.sheet)
        val dockBounds = LauncherTestUi.bounds(dock)
        onMain {
            assertEquals(View.VISIBLE, home.visibility)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, home.importantForAccessibility)
            assertEquals(3, grid.numColumns) // Drawer uses six columns in this fixture.
            assertEquals(context.dp(64 + 2 * 96), popover!!.sheet.height)
            assertEquals(dockBounds.top - context.dp(8), bounds.bottom)
            assertSame(workspace, pager.currentWorkspace)
            assertNull(LauncherTestUi.find(popover!!) { it.contentDescription == "Search installed apps" })
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot().let { screenshot ->
            File(context.getExternalFilesDir(null), "folder-popover.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
        val underlying = onMain { find { it.contentDescription == apps.first().label }!! }
        val appBounds = LauncherTestUi.bounds(underlying)
        assertTrue(appBounds.bottom < bounds.top)
        LauncherTestUi.Pointer(appBounds.exactCenterX(), appBounds.exactCenterY()).up()
        await { popover == null }
        onMain {
            assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(activity))
            assertSame(workspace, pager.currentWorkspace)
            assertEquals(1, pager.selectedPage)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, home.importantForAccessibility)
        }
        assertEquals(state, LauncherStore(context).state)
        open("Daily")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { popover == null && pager.selectedPage == 1 }
        open("Daily")
        context.startActivity(Intent(context, LauncherActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await { popover == null && pager.selectedPage == 1 }
    }

    @Test fun growsTowardHomeFolderButDockFoldersUseTwoRows() = isolated {
        val upper = LauncherFolder(title = "Upper", apps = members(40))
        val lower = LauncherFolder(title = "Lower", apps = members(40))
        val pinned = LauncherFolder(title = "Pinned", apps = members(40))
        LauncherStore(context).save(listOf(marker(),
            HomeItem(kind = ItemKind.FOLDER, appKey = upper.key, x = 1),
            HomeItem(kind = ItemKind.FOLDER, appKey = lower.key, x = 1, y = 4)), listOf(pinned.key), folders = listOf(upper, lower, pinned))
        restart()
        val sourceTop = LauncherTestUi.bounds(onMain { find { it.contentDescription?.startsWith("Upper, folder,") == true }!! }).top
        open("Upper")
        val upperBounds = LauncherTestUi.bounds(popover!!.sheet)
        assertTrue(upperBounds.height() > context.dp(256))
        assertTrue(upperBounds.top >= sourceTop)
        close()
        open("Lower")
        val lowerBounds = LauncherTestUi.bounds(popover!!.sheet)
        assertEquals(context.dp(256), lowerBounds.height())
        assertEquals(upperBounds.bottom, lowerBounds.bottom)
        close()
        open("Pinned")
        assertEquals(context.dp(256), LauncherTestUi.bounds(popover!!.sheet).height())
        val dockBounds = LauncherTestUi.bounds(dock)
        LauncherTestUi.Pointer(dockBounds.exactCenterX(), dockBounds.exactCenterY()).up()
        await { popover == null }
    }

    @Test fun scrollsOverflowAndOnlyPullsDownAtTop() = isolated {
        val folder = LauncherFolder(title = "Many", apps = members(40))
        LauncherStore(context).save(listOf(marker()), listOf(folder.key), folders = listOf(folder))
        restart(); open("Many")
        val overlay = popover!!
        val bounds = LauncherTestUi.bounds(grid)
        LauncherTestUi.Pointer(bounds.exactCenterX(), bounds.bottom - context.dp(24).toFloat())
            .move(bounds.exactCenterX(), bounds.top + context.dp(20).toFloat(), 500).up()
        await { grid.canScrollVertically(-1) }
        onMain { grid.setSelection(30) }
        await { grid.firstVisiblePosition >= 27 }
        LauncherTestUi.Pointer(bounds.exactCenterX(), bounds.top + context.dp(20).toFloat())
            .move(bounds.exactCenterX(), bounds.bottom - context.dp(20).toFloat(), 500).up()
        await { grid.firstVisiblePosition < 30 }
        onMain { assertSame(overlay, popover); assertEquals(0f, overlay.sheet.translationY, 0f) }
        onMain { grid.setSelection(0) }
        await { !grid.canScrollVertically(-1) }
        LauncherTestUi.Pointer(bounds.exactCenterX(), bounds.top + context.dp(20).toFloat())
            .move(bounds.exactCenterX(), bounds.top + context.dp(60).toFloat(), 500).up()
        await { popover === overlay && overlay.sheet.translationY == 0f }
        LauncherTestUi.Pointer(bounds.exactCenterX(), bounds.top + context.dp(20).toFloat())
            .move(bounds.exactCenterX(), bounds.top + context.dp(180).toFloat(), 500).up()
        await { popover == null }
    }

    @Test fun renameAndDragOutKeepFolderAndSavedLayoutConsistent() = isolated {
        val folder = LauncherFolder(title = "Rename me", apps = members(3))
        LauncherStore(context).save(listOf(marker()), listOf(folder.key), folders = listOf(folder))
        restart(); open("Rename me")
        click { it is android.widget.TextView && it.text.toString() == "Rename" }
        val field = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Rename me").first { it.className == "android.widget.EditText" }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Renamed")
        }))
        LauncherTestUi.tapText("Save")
        await { LauncherStore(context).folders.single().title == "Renamed" && popover != null && grid.childCount > 0 }
        val firstTile = onMain { grid.getChildAt(0) }
        val from = LauncherTestUi.bounds(firstTile)
        LauncherTestUi.Pointer(from.exactCenterX(), from.exactCenterY()).hold(850).cancel()
        await { popover == null && pager.pagingEnabled }
        assertEquals(folder.apps, LauncherStore(context).folders.single().apps)
        open("Renamed")
        val tile = LauncherTestUi.bounds(onMain { grid.getChildAt(0) })
        val workspace = LauncherTestUi.bounds(pager)
        LauncherTestUi.Pointer(tile.exactCenterX(), tile.exactCenterY()).hold(850)
            .move(workspace.left + workspace.width() * 2.5f / 3, workspace.top + workspace.height() * 1.5f / 6).up()
        await { LauncherStore(context).folders.single().apps.size == 2 }
        assertTrue(LauncherStore(context).items.any { it.kind == ItemKind.APP && it.appKey == folder.apps.first() && it.x == 2 && it.y == 1 })
        assertEquals(listOf(folder.key), LauncherStore(context).dock)
        assertEquals(folder.apps.drop(1), LauncherStore(context).folders.single().apps)
    }

    private fun isolated(test: () -> Unit) {
        val original = LauncherStore(context)
        val state = original.state; val settings = original.settings; val pages = original.pages; val hidden = original.hiddenApps
        original.save(emptyList(), emptyList(), settings.copy(columns = 3, rows = 6, drawerColumns = 6, dockSlots = 5, iconSize = 52, locked = false), emptyList())
        original.pages = 1; original.saveHiddenApps(emptySet())
        start()
        try {
            click { it.contentDescription == "Open app drawer" }
            await {
                val drawer = find { it is GridView } as GridView
                apps = (0 until drawer.adapter.count).map { drawer.adapter.getItem(it) as LaunchableApp }
                apps.isNotEmpty()
            }
            click { it.contentDescription == "Back to home" }
            test()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            LauncherStore(context).apply { save(state.items, state.dock, settings, state.folders); this.pages = pages; saveHiddenApps(hidden) }
        }
    }
}
