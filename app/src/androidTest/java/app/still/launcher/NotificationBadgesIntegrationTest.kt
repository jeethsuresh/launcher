package app.still.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NotificationBadgesIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture get() = instrumentation.context
    private val badgeFill = 0xffb51e3c.toInt()
    private lateinit var activity: LauncherActivity
    private val component get() = NotificationBadges.component(context).flattenToString()

    @Test fun realNotificationsUpdateHomeDockDrawerFoldersAndSurviveReconnect() {
        val original = LauncherStore(context)
        val state = original.state
        val settings = original.settings
        val allowed = NotificationBadges.hasAccess(context)
        val badging = shell("settings get secure notification_badging").trim()
        try {
            shell("pm grant ${fixture.packageName} android.permission.POST_NOTIFICATIONS")
            shell("settings put secure notification_badging 1")
            broadcast("--es operation cleanup")
            shell("cmd notification allow_listener $component")
            start()
            awaitMain { NotificationBadges.connected }
            LauncherTestUi.click(activity) { it.contentDescription == "Open app drawer" }
            var apps = emptyList<LaunchableApp>()
            awaitMain {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                apps = (0 until grid.adapter.count).map { grid.adapter.getItem(it) as LaunchableApp }
                apps.any { it.info.componentName.packageName == fixture.packageName }
            }
            val app = apps.first { it.info.componentName.packageName == fixture.packageName }
            val folder = LauncherFolder(title = "Notifications", apps = apps.filter { it.key != app.key }.take(4).map { it.key } + app.key)
            val items = listOf(HomeItem(kind = ItemKind.APP, appKey = app.key), HomeItem(kind = ItemKind.FOLDER, appKey = folder.key, x = 1))
            LauncherStore(context).save(items, listOf(app.key, folder.key),
                settings.copy(columns = 4, rows = 6, dockSlots = 5, iconSize = 52, labels = true), listOf(folder))
            restart()
            awaitMain { badges().size == 4 && badges().first().contentDescription == app.label }
            val homeTiles = onMain { badges() }
            post(1, number = 7)
            awaitMain { badges().all { it.badgeCount == 7 } }
            lateinit var seven: Bitmap
            instrumentation.runOnMainSync {
                assertEquals(homeTiles, badges()) // Updates must not rebuild the workspace or disrupt dragging.
                assertTrue(badges().all { it.contentDescription.endsWith(", 7 notifications") })
                seven = badgeBitmap(homeTiles.first())
                assertBoldBadge(seven)
            }
            screenshot("numbered-badges-7")
            post(1, number = 2) // Replacing an existing notification replaces its count.
            awaitMain { badges().all { it.badgeCount == 2 } }
            instrumentation.runOnMainSync {
                val two = badgeBitmap(badges().first())
                assertFalse("Updating 7 to 2 must repaint the numeral", badgePixels(seven).contentEquals(badgePixels(two)))
                two.recycle(); seven.recycle()
                assertEquals(homeTiles, badges())
            }
            post(2)
            awaitMain { badges().all { it.badgeCount == 3 } }
            cancel(1)
            awaitMain { badges().all { it.badgeCount == 1 } }

            LauncherTestUi.click(activity) { it.contentDescription?.startsWith("Notifications, folder,") == true }
            awaitMain {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                (0 until grid.childCount).any { index ->
                    val tile = grid.getChildAt(index) as BadgedTile
                    tile.contentDescription == "Badge fixture, 1 notification" && tile.badgeCount == 1
                }
            }
            LauncherTestUi.click(activity) { it.contentDescription == "Back to home" }
            LauncherTestUi.click(activity) { it.contentDescription == "Open app drawer" }
            instrumentation.runOnMainSync { (LauncherTestUi.find(activity.window.decorView) { it is EditText } as EditText).setText(app.label) }
            awaitMain {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                grid.childCount == 1 && grid.getChildAt(0).contentDescription == "Badge fixture, 1 notification"
            }
            val drawerTile = onMain { badges().last() }
            cancel(2)
            awaitMain { !drawerTile.hasBadge }
            instrumentation.runOnMainSync {
                assertEquals(app.label, (LauncherTestUi.find(activity.window.decorView) { it is EditText } as EditText).text.toString())
                assertTrue(drawerTile.isAttachedToWindow)
            }
            post(3, ongoing = true, number = 50)
            post(4, channel = "badge-test-off", number = 50)
            post(5, summary = true, number = 50)
            SystemClock.sleep(500)
            awaitMain { badges().none { it.hasBadge } }
            post(6, group = true, number = 4)
            awaitMain { drawerTile.badgeCount == 4 }
            cancel(6)
            awaitMain { !drawerTile.hasBadge }

            post(7, number = 105)
            awaitMain { drawerTile.badgeCount == 105 }
            instrumentation.runOnMainSync {
                assertEquals("99+", badgeLabel(drawerTile.badgeCount))
                assertTrue(drawerTile.contentDescription.endsWith(", 105 notifications"))
                val bitmap = badgeBitmap(drawerTile)
                val fill = assertBoldBadge(bitmap)
                assertTrue("Badge should extend above the icon art", fill.top < drawerTile.art.top)
                assertTrue("Badge should extend to the right of the icon art", fill.right > drawerTile.art.right)
                bitmap.recycle()
            }
            screenshot("numbered-badges-99-plus")
            shell("settings put secure notification_badging 0")
            awaitMain { !drawerTile.hasBadge }
            shell("settings put secure notification_badging 1")
            awaitMain { drawerTile.badgeCount == 105 }
            shell("cmd notification disallow_listener $component")
            awaitMain { !drawerTile.hasBadge }
            shell("cmd notification allow_listener $component")
            awaitMain { drawerTile.badgeCount == 105 }
            val compactItems = listOf(items[0], items[1].copy(x = 3))
            LauncherStore(context).save(compactItems, listOf(app.key, folder.key),
                settings.copy(columns = 4, rows = 6, dockSlots = 7, iconSize = 36, labels = true), listOf(folder))
            restart()
            awaitMain { badges().size == 4 && badges().all { it.badgeCount == 105 } }
            instrumentation.runOnMainSync {
                badges().forEach { tile ->
                    assertTrue("Small icons may shrink to fit seven dock slots", tile.art.width in context.dp(28)..context.dp(36))
                    val bitmap = badgeBitmap(tile)
                    assertBoldBadge(bitmap)
                    bitmap.recycle()
                }
            }
            screenshot("numbered-badges-small-icons")
            broadcast("--es operation cleanup")
            awaitMain { badges().none { it.hasBadge } }
            assertEquals(compactItems, LauncherStore(context).items)
            assertEquals(listOf(app.key, folder.key), LauncherStore(context).dock)

            LauncherTestUi.settings(activity)
            LauncherTestUi.click(activity) { it.contentDescription?.startsWith("Notification badges,") == true }
            LauncherTestUi.tapText("Open Android settings")
            LauncherTestUi.await { instrumentation.uiAutomation.rootInActiveWindow?.packageName == "com.android.settings" }
        } finally {
            broadcast("--es operation cleanup")
            if (!allowed) shell("cmd notification disallow_listener $component")
            if (badging == "null") shell("settings delete secure notification_badging") else shell("settings put secure notification_badging $badging")
            if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
            LauncherStore(context).save(state.items, state.dock, settings, state.folders)
        }
    }

    private fun post(id: Int, channel: String = "badge-test", ongoing: Boolean = false, summary: Boolean = false, group: Boolean = false, number: Int = 0) {
        broadcast("--ei id $id --es channel $channel --ez ongoing $ongoing --ez summary $summary --ez group $group --ei number $number")
    }
    private fun badgeBitmap(tile: BadgedTile) = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888).also {
        tile.draw(Canvas(it))
    }
    private fun badgeFillBounds(bitmap: Bitmap): Rect {
        val bounds = Rect()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == badgeFill) bounds.union(x, y, x + 1, y + 1)
            }
        }
        assertFalse("Expected the badge's red fill", bounds.isEmpty)
        return bounds
    }
    private fun assertBoldBadge(bitmap: Bitmap): Rect {
        val fill = badgeFillBounds(bitmap)
        val expectedFillHeight = context.dp(24) - 2 * context.dp(2)
        assertEquals("Badge should retain its height", expectedFillHeight.toFloat(), fill.height().toFloat(), 1f)
        assertTrue("The top white outline should be fully visible",
            fill.top > 0 && bitmap.getPixel(fill.centerX(), fill.top - 1) == Color.WHITE)
        assertTrue("The right white outline should be fully visible",
            fill.right < bitmap.width && bitmap.getPixel(fill.right, fill.centerY()) == Color.WHITE)
        val interior = badgePixels(bitmap, fill)
        assertTrue("Badge numeral should be white on red", interior.any { it == Color.WHITE } && interior.any { it == badgeFill })
        return fill
    }
    private fun badgePixels(bitmap: Bitmap, fill: Rect = badgeFillBounds(bitmap)): IntArray {
        // Restrict to the badge interior so icon artwork and the white border cannot satisfy glyph assertions.
        val width = context.dp(10)
        val height = context.dp(10)
        val x = fill.centerX() - width / 2
        val y = fill.centerY() - height / 2
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, x, y, width, height) }
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100) // Let the invalidated count reach the screen before capturing its pixels.
        // Use the emulator shell's public directory so Gradle's test-package
        // cleanup cannot remove the images before CI collects them.
        shell("mkdir -p /sdcard/Pictures/still-launcher-badges")
        val path = "/sdcard/Pictures/still-launcher-badges/$name.png"
        shell("screencap -p $path")
        val bytes = shell("wc -c $path").trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.toLongOrNull()
        assertTrue("Missing rendered screenshot: $name", bytes != null && bytes > 0)
    }
    private fun cancel(id: Int) { broadcast("--es operation cancel --ei id $id") }
    private fun broadcast(extras: String) { shell("am broadcast --receiver-foreground -n ${fixture.packageName}/app.still.launcher.BadgeTestReceiver $extras") }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun start() { activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity }
    private fun restart() {
        instrumentation.runOnMainSync { activity.finish() }
        LauncherTestUi.await { activity.isDestroyed }
        start()
    }
    private fun badges(): List<BadgedTile> {
        fun collect(view: View): List<BadgedTile> = if (view is BadgedTile) listOf(view)
            else if (view is ViewGroup) (0 until view.childCount).flatMap { collect(view.getChildAt(it)) } else emptyList()
        return collect(activity.window.decorView)
    }
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun awaitMain(condition: () -> Boolean) = LauncherTestUi.await { onMain(condition) }
}
