package app.still.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration

/** Keeps widget permission/configuration flows separate from workspace rendering. */
class Widgets(
    private val activity: Activity,
    private val store: LauncherStore,
    private val added: (Int, AppWidgetProviderInfo) -> Boolean,
    private val message: (String) -> Unit,
) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = object : AppWidgetHost(activity, 1024) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView =
            TouchWidgetHost(context)
    }

    fun providers(): List<AppWidgetProviderInfo> = activity.getSystemService(android.content.pm.LauncherApps::class.java).profiles
        .flatMap { runCatching { manager.getInstalledProvidersForProfile(it) }.getOrDefault(emptyList()) }
        .sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        val app = activity.getSystemService(android.content.pm.LauncherApps::class.java)
            .getApplicationInfo(info.provider.packageName, 0, info.profile)
        activity.packageManager.getApplicationLabel(app).toString()
    }.getOrDefault(info.provider.packageName)

    fun add(info: AppWidgetProviderInfo, placement: HomeItem? = null) {
        cancelPending()
        val id = host.allocateAppWidgetId()
        store.savePendingWidget(id, placement)
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, options)) configure(id)
            else activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }, BIND)
        } catch (error: Exception) { android.util.Log.w("StillWidgets", "Unable to bind widget", error); cancelPending(); message("This widget could not be added.") }
    }

    private fun configure(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: run { cancelPending(); return }
        val optional = android.os.Build.VERSION.SDK_INT >= 31 && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        if (info.configure != null && !optional) {
            try { host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE, null) }
            catch (error: Exception) { android.util.Log.w("StillWidgets", "Unable to configure widget", error); cancelPending(); message("The widget's setup screen is unavailable.") }
        } else finish(id, info)
    }

    fun reconfigure(id: Int) {
        try { host.startAppWidgetConfigureActivityForResult(activity, id, 0, RECONFIGURE, null) }
        catch (_: Exception) { message("This widget has no settings screen.") }
    }

    private fun finish(id: Int, info: AppWidgetProviderInfo) {
        if (!added(id, info)) host.deleteAppWidgetId(id)
        store.pendingWidget = -1
    }

    fun result(request: Int, result: Int): Boolean {
        if (request == RECONFIGURE) return true
        if (request != BIND && request != CONFIGURE) return false
        val id = store.pendingWidget
        if (id == -1) return true
        if (result != Activity.RESULT_OK) { cancelPending(); return true }
        if (request == BIND) configure(id)
        else manager.getAppWidgetInfo(id)?.let { finish(id, it) } ?: cancelPending()
        return true
    }

    fun cancelPending() {
        val id = store.pendingWidget
        if (id != -1) {
            // A process can die after saving the layout but before clearing the pending flag.
            if (store.items.none { it.widgetId == id }) host.deleteAppWidgetId(id)
            store.pendingWidget = -1
        }
    }

    companion object { const val BIND = 41; const val CONFIGURE = 42; const val RECONFIGURE = 43 }
}

/** Detect a hold without swallowing a widget's ordinary buttons, lists or other touches. */
class TouchWidgetHost(context: Context) : AppWidgetHostView(context) {
    var held: (() -> Unit)? = null
    var editMode = false
    var tapped: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var active = false
    private val hold = Runnable {
        active = true
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        held?.invoke()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; active = false
                postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) + kotlin.math.abs(event.y - downY) > ViewConfiguration.get(context).scaledTouchSlop) removeCallbacks(hold)
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if (editMode && !active) { performClick(); tapped?.invoke(); return true }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(hold)
        }
        return if (active || editMode) true else super.dispatchTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { removeCallbacks(hold); super.onDetachedFromWindow() }
}
