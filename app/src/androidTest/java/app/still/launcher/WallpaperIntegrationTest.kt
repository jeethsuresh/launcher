package app.still.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Dedicated-emulator tests: verify the composed screen, including Android's wallpaper surface. */
class WallpaperIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val manager get() = WallpaperManager.getInstance(context)
    private val blue = Color.rgb(30, 90, 210)
    private val green = Color.rgb(40, 170, 90)

    @Test fun testDeviceWallpaperIsVisibleAndUpdates() = withLauncher("device") { _ ->
        assertBackground(blue, "device")
        // Mimic changing wallpaper in another activity and returning home.
        val other = instrumentation.startActivitySync(Intent(context, KeyboardSetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        setWallpaper(green)
        instrumentation.runOnMainSync { other.finish() }
        instrumentation.waitForIdleSync()
        assertBackground(green, "external-change")
    }

    @Test fun testPresetsAndPhotosFollowLaterSystemChanges() = withLauncher("tide") { activity ->
        // A setting saved by an older version must not mask the active wallpaper.
        assertBackground(blue, "legacy-preset")
        LauncherTestUi.settings(activity)
        click(activity) { it.contentDescription?.startsWith("Wallpapers,") == true }
        val previousId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        click(activity) { it.contentDescription == "Dune wallpaper. Tap to apply." }
        await { LauncherStore(context).settings.wallpaper == "dune" &&
            manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) != previousId }
        await { !backgroundIs(blue) }
        instrumentation.waitForIdleSync()

        // Deliver a selected image through the same activity-result path as the document picker.
        val image = File(context.cacheDir, "wallpaper-test.png")
        Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(green)
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        instrumentation.runOnMainSync {
            val result = LauncherActivity::class.java.getDeclaredMethod("onActivityResult",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
            result.isAccessible = true
            val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<LauncherActivity>().single()
            result.invoke(current, LauncherActivity.PHOTO, Activity.RESULT_OK, Intent().setData(Uri.fromFile(image)))
        }
        assertBackground(green, "photo")
        setWallpaper(blue)
        assertBackground(blue, "external-after-photo")
        image.delete()
    }

    private fun withLauncher(style: String, test: (LauncherActivity) -> Unit) {
        val store = LauncherStore(context)
        val original = store.settings
        store.save(settings = original.copy(wallpaper = style, dim = 0))
        setWallpaper(blue)
        val activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        try { test(activity) } finally {
            instrumentation.runOnMainSync {
                Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                    .filterIsInstance<LauncherActivity>().distinct().forEach { it.finish() }
            }
            store.save(settings = original)
            // This test runs on a dedicated emulator; leave a launcher preset instead of a test color.
            val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            StillWallpaper.render(android.graphics.Canvas(bitmap), 1080f, 2400f, "tide")
            try { manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM) }
            finally { bitmap.recycle() }
        }
    }

    private fun setWallpaper(color: Int) {
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        try { assertTrue(manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM) > 0) }
        finally { bitmap.recycle() }
    }

    private fun click(activity: Activity, predicate: (View) -> Boolean) = LauncherTestUi.click(activity, predicate)

    private fun assertBackground(color: Int, name: String) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (SystemClock.uptimeMillis() < deadline) {
            if (backgroundIs(color)) return
            SystemClock.sleep(150)
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, "wallpaper-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        fail("Active Android wallpaper is hidden ($name)")
    }

    private fun backgroundIs(color: Int): Boolean {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return false
        // Empty left workspace space, away from the clock, widgets, icons, and system bars.
        val actual = bitmap.getPixel(bitmap.width / 12, bitmap.height * 11 / 20)
        bitmap.recycle()
        return kotlin.math.abs(Color.red(actual) - Color.red(color)) <= 3 &&
            kotlin.math.abs(Color.green(actual) - Color.green(color)) <= 3 &&
            kotlin.math.abs(Color.blue(actual) - Color.blue(color)) <= 3
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Wallpaper operation did not finish", condition())
    }
}
