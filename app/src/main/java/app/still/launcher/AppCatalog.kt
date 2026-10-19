package app.still.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import java.text.Collator
import java.util.concurrent.Executors

data class LaunchableApp(val key: String, val label: String, val info: LauncherActivityInfo)

/** Catalog owns profile-aware discovery and icon loading. Future search providers can consume it. */
class AppCatalog(private val context: Context, private val onChanged: (List<LaunchableApp>) -> Unit) {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val icons = LruCache<String, Drawable>(100)
    private var closed = false
    private var generation = 0
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init { launcherApps.registerCallback(callback, main) }

    fun refresh() {
        if (closed) return
        val ticket = ++generation
        icons.evictAll()
        worker.execute {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val apps = launcherApps.profiles.flatMap { profile ->
                runCatching { launcherApps.getActivityList(null, profile) }.getOrDefault(emptyList())
                    .filter { it.componentName.packageName != context.packageName }
                    .map { LaunchableApp(key(it.componentName, profile), it.label.toString(), it) }
            }.distinctBy { it.key }.sortedWith { a, b -> collator.compare(a.label, b.label) }
            main.post { if (!closed && ticket == generation) onChanged(apps) }
        }
    }

    fun icon(app: LaunchableApp, result: (Drawable) -> Unit) {
        icons.get(app.key)?.let { result(it); return }
        if (closed) return
        worker.execute {
            val icon = runCatching { app.info.getBadgedIcon(context.resources.displayMetrics.densityDpi) }.getOrNull()
            if (icon != null) { icons.put(app.key, icon); main.post { if (!closed) result(icon) } }
        }
    }

    fun user(serial: Long): UserHandle = users.getUserForSerialNumber(serial) ?: Process.myUserHandle()
    fun serial(user: UserHandle): Long = users.getSerialNumberForUser(user)
    private fun key(component: ComponentName, user: UserHandle) = component.flattenToString() + "#" + serial(user)
    fun close() { closed = true; launcherApps.unregisterCallback(callback); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
