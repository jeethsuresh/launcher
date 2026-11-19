package app.still.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
            LauncherStore(context).save(items, listOf(app.key, folder.key), settings.copy(columns = 4, rows = 6, dockSlots = 5), listOf(folder))
            restart()
            awaitMain { badges().size == 4 && (badges().first().parent as View).contentDescription == app.label }
            val homeIcons = onMain { badges() }
            post(1, number = 7)
            awaitMain { badges().all { it.badgeCount == 7 } }
            lateinit var seven: Bitmap
            instrumentation.runOnMainSync {
                assertEquals(homeIcons, badges()) // Updates must not rebuild the workspace or disrupt dragging.
                assertTrue(badges().all { (it.parent as View).contentDescription.endsWith(", 7 notifications") })
                val icon = badges().first()
                assertTrue(icon.width > 0 && icon.height > 0)
                seven = badgeBitmap(icon)
                assertBoldBadge(seven)
            }
            screenshot("numbered-badges-7")
            post(1, number = 2) // Replacing an existing notification replaces its count.
            awaitMain { badges().all { it.badgeCount == 2 } }
            instrumentation.runOnMainSync {
                val two = badgeBitmap(badges().first())
                assertFalse("Updating 7 to 2 must repaint the numeral", badgePixels(seven).contentEquals(badgePixels(two)))
                two.recycle(); seven.recycle()
                assertEquals(homeIcons, badges())
            }
            post(2)
            awaitMain { badges().all { it.badgeCount == 3 } }
            cancel(1)
            awaitMain { badges().all { it.badgeCount == 1 } }

            LauncherTestUi.click(activity) { it.contentDescription?.startsWith("Notifications, folder,") == true }
            awaitMain {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                (0 until grid.childCount).any { index ->
                    val tile = grid.getChildAt(index)
                    tile.contentDescription == "Badge fixture, 1 notification" &&
                        (LauncherTestUi.find(tile) { it is BadgedIcon } as BadgedIcon).badgeCount == 1
                }
            }
            LauncherTestUi.click(activity) { it.contentDescription == "Back to home" }
            LauncherTestUi.click(activity) { it.contentDescription == "Open app drawer" }
            instrumentation.runOnMainSync { (LauncherTestUi.find(activity.window.decorView) { it is EditText } as EditText).setText(app.label) }
            awaitMain {
                val grid = LauncherTestUi.find(activity.window.decorView) { it is GridView } as GridView
                grid.childCount == 1 && grid.getChildAt(0).contentDescription == "Badge fixture, 1 notification"
            }
            val drawerIcon = onMain { badges().last() }
            cancel(2)
            awaitMain { !drawerIcon.hasBadge }
            instrumentation.runOnMainSync {
                assertEquals(app.label, (LauncherTestUi.find(activity.window.decorView) { it is EditText } as EditText).text.toString())
                assertTrue(drawerIcon.isAttachedToWindow)
            }
            post(3, ongoing = true, number = 50)
            post(4, channel = "badge-test-off", number = 50)
            post(5, summary = true, number = 50)
            SystemClock.sleep(500)
            awaitMain { badges().none { it.hasBadge } }
            post(6, group = true, number = 4)
            awaitMain { drawerIcon.badgeCount == 4 }
            cancel(6)
            awaitMain { !drawerIcon.hasBadge }

            post(7, number = 105)
            awaitMain { drawerIcon.badgeCount == 105 }
            instrumentation.runOnMainSync {
                assertEquals("99+", badgeLabel(drawerIcon.badgeCount))
                assertTrue((drawerIcon.parent as View).contentDescription.endsWith(", 105 notifications"))
            }
            screenshot("numbered-badges-99-plus")
            shell("settings put secure notification_badging 0")
            awaitMain { !drawerIcon.hasBadge }
            shell("settings put secure notification_badging 1")
            awaitMain { drawerIcon.badgeCount == 105 }
            shell("cmd notification disallow_listener $component")
            awaitMain { !drawerIcon.hasBadge }
            shell("cmd notification allow_listener $component")
            awaitMain { drawerIcon.badgeCount == 105 }
            LauncherStore(context).save(items, listOf(app.key, folder.key), settings.copy(columns = 4, rows = 6, dockSlots = 7, iconSize = 36), listOf(folder))
            restart()
            awaitMain { badges().size == 4 && badges().all { it.badgeCount == 105 } }
            instrumentation.runOnMainSync {
                badges().forEach { icon ->
                    assertTrue("Small icons may shrink to fit seven dock slots", icon.width in context.dp(28)..context.dp(36))
                    val bitmap = badgeBitmap(icon)
                    assertBoldBadge(bitmap)
                    bitmap.recycle()
                }
            }
            screenshot("numbered-badges-small-icons")
            broadcast("--es operation cleanup")
            awaitMain { badges().none { it.hasBadge } }
            assertEquals(items, LauncherStore(context).items)
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
    private fun badgeBitmap(icon: BadgedIcon) = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.translate(-icon.left.toFloat(), -icon.top.toFloat())
        (icon.parent as View).draw(canvas)
    }
    private fun fillColumns(bitmap: Bitmap, y: Int) = (0 until bitmap.width).filter { x -> bitmap.getPixel(x, y) == badgeFill }
    private fun assertBoldBadge(bitmap: Bitmap) {
        val filledRows = (0 until bitmap.height).filter { y -> fillColumns(bitmap, y).isNotEmpty() }
        assertTrue("Badge fill should be at least 18 dp tall", filledRows.size >= context.dp(18))
        val top = filledRows.first()
        val topFill = fillColumns(bitmap, top)
        assertTrue("Tile padding should leave the top white outline visible",
            top > 0 && bitmap.getPixel(topFill[topFill.size / 2], top - 1) == Color.WHITE)
        val interior = badgePixels(bitmap)
        assertTrue("Badge numeral should be white on red", interior.any { it == Color.WHITE } && interior.any { it == badgeFill })
    }
    private fun badgePixels(bitmap: Bitmap): IntArray {
        // Restrict to the badge interior so icon artwork and the white border cannot satisfy glyph assertions.
        val x = bitmap.width - context.dp(18)
        val y = (0 until bitmap.height).first { fillColumns(bitmap, it).isNotEmpty() } + context.dp(5)
        val width = context.dp(10)
        val height = context.dp(10)
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
    private fun badges(): List<BadgedIcon> {
        fun collect(view: View): List<BadgedIcon> = if (view is BadgedIcon) listOf(view)
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
