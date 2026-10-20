package app.still.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.util.SizeF
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.roundToInt

/** UI coordinator; discovery, durable state, geometry and widget lifecycle have separate owners. */
class LauncherActivity : Activity() {
    private lateinit var store: LauncherStore
    private lateinit var catalog: AppCatalog
    private lateinit var widgets: Widgets
    private lateinit var root: FrameLayout
    private lateinit var safe: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var workspace: WorkspaceView
    private lateinit var dock: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var top: LinearLayout
    private lateinit var wallpaperDim: View
    private var apps = emptyList<LaunchableApp>()
    private var currentPage = 0
    private var panel: View? = null
    private var panelKind = ""
    private var drawerAdapter: AppsAdapter? = null
    private var editing = false
    private var dragging = false
    private var draggedItem: HomeItem? = null
    private var lastEdge = 0
    private var activeDialog: AlertDialog? = null
    private var destroyed = false
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var previousNightMode = 0
    private val ink get() = if (light) 0xff202a25.toInt() else 0xffedf3ee.toInt()
    private val muted get() = if (light) 0xff65736b.toInt() else 0xff9eafa6.toInt()
    private val surface get() = if (light) 0xfff3f5f0.toInt() else 0xff101b18.toInt()
    private val card get() = if (light) 0xffe7ece4.toInt() else 0xff1c2a24.toInt()
    private val accent get() = if (light) 0xff315f47.toInt() else 0xffc0dfc9.toInt()
    private val light get() = store.settings.theme == "light" || (store.settings.theme == "system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO)
    private val engine get() = LayoutEngine(store.settings.columns, store.settings.rows)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LauncherStore(this)
        previousNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        currentPage = savedInstanceState?.getInt("page") ?: 0
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        widgets = Widgets(this, store, ::placeWidget, ::message)
        catalog = AppCatalog(this, ::catalogChanged)
        createHome()
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        catalog.refresh()
    }

    override fun onStart() { super.onStart(); runCatching { widgets.host.startListening() } }
    override fun onStop() { runCatching { widgets.host.stopListening() }; super.onStop() }
    override fun onResume() { super.onResume(); if (::catalog.isInitialized) catalog.refresh() }
    override fun onDestroy() {
        destroyed = true; activeDialog?.dismiss(); catalog.close(); io.shutdownNow(); main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("page", currentPage); super.onSaveInstanceState(outState) }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val themeChanged = night != previousNightMode && store.settings.theme == "system"
        previousNightMode = night
        renderHome()
        root.requestApplyInsets()
        // Keyboard/window changes must not recreate the drawer or clear an active search.
        if (themeChanged) {
            val query = drawerAdapter?.query ?: ""
            when (panelKind) {
                "settings" -> showSettings()
                "drawer" -> showDrawer(query)
                "widgets" -> showWidgets()
                "wallpapers" -> showWallpapers()
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activeDialog?.dismiss()
        if (panel != null || editing) { closePanel(); setEditing(false) } else { currentPage = 0; renderHome() }
    }
    @Deprecated("Platform callback supports Android 8 and later")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        when { activeDialog?.isShowing == true -> activeDialog?.dismiss(); panel != null -> closePanel(); editing -> setEditing(false); currentPage != 0 -> { currentPage = 0; renderHome() }; else -> Unit }
    }

    private fun createHome() {
        root = FrameLayout(this)
        // Android owns the active image/live wallpaper. Draw only the readability shade here:
        // a cached preset or photo would hide changes made by another wallpaper picker.
        wallpaperDim = View(this)
        root.addView(wallpaperDim, FrameLayout.LayoutParams(-1, -1))
        safe = FrameLayout(this); root.addView(safe, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
                safe.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                panel?.let { view ->
                    val layout = view.layoutParams as FrameLayout.LayoutParams
                    val bottom = if (panelKind == "drawer") (keyboard-bars.bottom).coerceAtLeast(0) else 0
                    if (layout.bottomMargin != bottom) { layout.bottomMargin = bottom; view.layoutParams = layout }
                }
            } else safe.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(8)) }
        safe.addView(home, FrameLayout.LayoutParams(-1, -1))
        top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(2), 0) }
        home.addView(top, LinearLayout.LayoutParams(-1, dp(52)))
        workspace = WorkspaceView(this).apply {
            blankHold = { customize() }
            swipe = { horizontal, vertical ->
                if (vertical == 1) showDrawer()
                else if (horizontal != 0) { currentPage = (currentPage + horizontal).coerceIn(0, store.pages - 1); renderHome() }
            }
            setOnDragListener { _, event -> workspaceDrag(event) }
        }
        home.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        home.addView(dots, LinearLayout.LayoutParams(-1, dp(36)))
        dock = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), 0, dp(6), 0) }
        home.addView(dock, LinearLayout.LayoutParams(-1, dp(84)).apply { setMargins(dp(6), 0, dp(6), 0) })
        val drawerButton = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; contentDescription = "Open app drawer"; isFocusable = true }
        drawerButton.addView(Glyph(this, "up", 0xffdfebe1.toInt()), LinearLayout.LayoutParams(dp(24), dp(32)))
        drawerButton.addView(label("All apps", 12f, 0xffdfebe1.toInt()), LinearLayout.LayoutParams(-2, -2))
        drawerButton.setOnClickListener { showDrawer() }
        home.addView(drawerButton, LinearLayout.LayoutParams(-1, dp(40)))
        setContentView(root)
        renderHome()
    }

    private fun catalogChanged(result: List<LaunchableApp>) {
        apps = result
        if (!store.initialized && result.isNotEmpty()) {
            val clock = HomeItem(kind = ItemKind.CLOCK, spanX = store.settings.columns, spanY = 2)
            val priority = listOf("phone", "dialer", "messag", "chrome", "browser", "camera")
            val favorites = priority.mapNotNull { term -> result.firstOrNull { it.info.componentName.packageName.contains(term) } }.distinctBy { it.key }.take(5)
            val pins = (favorites + result).distinctBy { it.key }.take(store.settings.dockSlots)
            var layout = listOf(clock)
            result.filter { app -> pins.none { it.key == app.key } }.take(4).forEachIndexed { index, app ->
                val bottom = HomeItem(kind = ItemKind.APP, appKey = app.key, x = index, y = store.settings.rows - 1)
                layout = layout + bottom
            }
            store.save(items = layout, dock = pins.map { it.key })
        }
        // Keep unavailable-profile shortcuts; they return when that profile is unlocked.
        renderHome()
        drawerAdapter?.filter(drawerAdapter?.query ?: "")
    }

    private fun renderHome() {
        if (!::workspace.isInitialized) return
        val settings = store.settings
        wallpaperDim.setBackgroundColor(Color.argb(settings.dim * 255 / 100, 0, 0, 0))
        currentPage = currentPage.coerceIn(0, store.pages - 1)
        workspace.columns = settings.columns; workspace.rows = settings.rows; workspace.editing = editing
        workspace.removeAllViews()
        store.items.filter { it.page == currentPage }.forEach { item ->
            val tile = when (item.kind) {
                ItemKind.APP -> appTile(item.appKey, settings.labels, false).apply {
                    setOnClickListener { if (editing) itemMenu(item) else launch(item.appKey) }
                    setOnLongClickListener { beginDrag(this, DragPayload(item.id, item.appKey)); true }
                    addActions(this, item)
                }
                ItemKind.CLOCK -> clockTile(item)
                ItemKind.WIDGET -> widgetTile(item)
            }
            workspace.tile(tile, item)
        }
        workspace.requestLayout()
        renderTop(); renderDock(); renderDots(); updateSystemBars()
    }

    private fun renderTop() {
        top.removeAllViews()
        if (dragging) {
            top.addView(dropTarget("Remove", "trash") { payload -> removePayload(payload) }, LinearLayout.LayoutParams(0, -1, 1f))
            if (draggedItem?.kind in listOf(ItemKind.WIDGET, ItemKind.CLOCK)) {
                top.addView(dropTarget("Options", "settings") { payload ->
                    store.items.find { it.id == payload.itemId }?.let { item -> main.post { itemMenu(item) } }
                }, LinearLayout.LayoutParams(0, -1, 1f))
            } else top.addView(dropTarget("App info", "info") { payload -> appInfo(payload.appKey) }, LinearLayout.LayoutParams(0, -1, 1f))
        } else if (editing) {
            top.addView(label("Arrange your home", 14f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(pill("Done", 0xffd0e8d6.toInt(), 0xff1a392a.toInt()) { setEditing(false) }, LinearLayout.LayoutParams(dp(78), dp(40)))
        } else {
            top.addView(label("STILL", 11f, 0xaaffffff.toInt(), true).apply { letterSpacing = .24f }, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(iconButton("settings", "Launcher settings", Color.WHITE) { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    private fun renderDock() {
        dock.removeAllViews(); dock.background = rounded(0x30effff0, dp(30).toFloat(), 0x22ffffff)
        for (slot in 0 until store.settings.dockSlots) {
            val key = store.dock.getOrNull(slot)
            val tile = if (key == null) FrameLayout(this).apply {
                contentDescription = "Empty dock slot ${slot+1}. Tap to choose an app."
                addView(Glyph(this@LauncherActivity, "plus", 0x80ffffff.toInt()), FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
                setOnClickListener { if (store.settings.locked) message("Unlock the layout in settings to edit the dock.") else chooseDockApp(slot) }
            } else appTile(key, false, false).apply {
                setOnClickListener { if (editing) dockMenu(key) else launch(key) }
                setOnLongClickListener { beginDrag(this, DragPayload(appKey = key, fromDock = true)); true }
                setAccessibilityDelegate(object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(AccessibilityNodeInfo.AccessibilityAction(10001, "Dock options"))
                    }
                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        if (action == 10001) { dockMenu(key); return true }; return super.performAccessibilityAction(host, action, args)
                    }
                })
            }
            dock.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
        }
        dock.setOnDragListener { _, event ->
            val payload = event.localState as? DragPayload
            val isApp = payload != null && (payload.itemId == null || store.items.find { it.id == payload.itemId }?.kind == ItemKind.APP)
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> isApp
                DragEvent.ACTION_DRAG_ENTERED -> { dock.alpha = .65f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { dock.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    if (isApp) pinDock(payload!!.appKey, (event.x / (dock.width.toFloat()/store.settings.dockSlots)).toInt().coerceIn(0, store.settings.dockSlots-1), payload.itemId)
                    true
                }
                else -> true
            }
        }
    }

    private fun renderDots() {
        dots.removeAllViews()
        for (page in 0 until store.pages) {
            val dot = FrameLayout(this).apply {
                contentDescription = "Home page ${page+1} of ${store.pages}"; isSelected = page == currentPage
                addView(View(this@LauncherActivity).apply { background = rounded(if (page == currentPage) 0xffe1f0e4.toInt() else 0x55ffffff, dp(3).toFloat()) },
                    FrameLayout.LayoutParams(dp(if (page == currentPage) 18 else 5), dp(5), Gravity.CENTER))
                setOnClickListener { currentPage = page; renderHome() }
            }
            dots.addView(dot, LinearLayout.LayoutParams(dp(32), dp(36)))
        }
        if (editing) dots.addView(iconButton("plus", "Add home page", Color.WHITE) { addPage() }, LinearLayout.LayoutParams(dp(40), dp(36)))
    }

    private fun clockTile(item: HomeItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(if (item.spanY == 1) 4 else 8), dp(8), dp(if (item.spanY == 1) 4 else 8))
        addView(TextClock(this@LauncherActivity).apply {
            format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, MMMM d"; textSize = if (item.spanY == 1) 12f else 15f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xffd4e5d9.toInt()); includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, -2))
        addView(TextClock(this@LauncherActivity).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = if (item.spanY == 1) 32f else if (item.spanX >= 3) 72f else 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); letterSpacing = -.055f
            setTextColor(0xfff1f7ed.toInt()); includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        contentDescription = "Clock and date. Touch and hold to drag. Drop on Options to resize."
        setOnClickListener {
            if (editing) itemMenu(item) else safeStart(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), "No clock app is installed.")
        }
        setOnLongClickListener { beginDrag(this, DragPayload(item.id)); true }
        addActions(this, item)
    }

    private fun appTile(key: String, showLabel: Boolean, onSurface: Boolean): View {
        val app = apps.find { it.key == key }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(2), dp(5), dp(2), dp(3)); isFocusable = true
            contentDescription = app?.label ?: "Unavailable app"
            val icon = ImageView(this@LauncherActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER; tag = key
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageDrawable(packageManager.defaultActivityIcon)
            }
            // Narrow grids and seven-slot docks should never clip an icon.
            val available = resources.displayMetrics.widthPixels / maxOf(store.settings.columns, store.settings.dockSlots) - dp(10)
            val side = minOf(dp(store.settings.iconSize), available).coerceAtLeast(dp(28))
            addView(icon, LinearLayout.LayoutParams(side, side))
            if (app != null) catalog.icon(app) { drawable -> if (icon.tag == key) icon.setImageDrawable(drawable) }
            if (showLabel) addView(label(app?.label ?: "Unavailable", 11f, if (onSurface) ink else Color.WHITE).apply {
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                if (!onSurface) setShadowLayer(dp(3).toFloat(), 0f, 1f, 0x99000000.toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(22)).apply { topMargin = dp(5) })
        }
    }

    private fun widgetTile(item: HomeItem): View {
        val info = widgets.manager.getAppWidgetInfo(item.widgetId)
        if (info == null) return label("Widget unavailable\nTap to remove", 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER; background = rounded(0x22ffffff, dp(20).toFloat()); setOnClickListener { itemMenu(item) }
        }
        val hostView = widgets.host.createView(this, item.widgetId, info) as TouchWidgetHost
        hostView.held = {
            if (store.settings.locked) message("Unlock the layout in settings to edit widgets.")
            else beginDrag(hostView, DragPayload(item.id))
        }
        hostView.editMode = editing
        hostView.tapped = { itemMenu(item) }
        addActions(hostView, item)
        hostView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (r > l && b > t) updateWidgetSize(hostView, r-l, b-t)
        }
        return hostView
    }

    private fun updateWidgetSize(view: AppWidgetHostView, width: Int, height: Int) {
        val density = resources.displayMetrics.density
        val w = width.coerceAtLeast(1) / density
        val h = height.coerceAtLeast(1) / density
        if (Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
        else view.updateAppWidgetSize(Bundle(), w.toInt(), h.toInt(), w.toInt(), h.toInt())
    }

    private fun addActions(view: View, item: HomeItem) {
        view.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(10001, "Item options"))
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == 10001) { itemMenu(item); return true }; return super.performAccessibilityAction(host, action, args)
            }
        })
    }

    private data class DragPayload(val itemId: String? = null, val appKey: String = "", val fromDock: Boolean = false)

    private fun beginDrag(view: View, payload: DragPayload) {
        if (store.settings.locked) { message("Layout is locked. Unlock it in settings."); return }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dragging = true; draggedItem = store.items.find { it.id == payload.itemId }
        lastEdge = 0; workspace.editing = true; renderTop()
        if (!view.startDragAndDrop(ClipData.newPlainText("Still item", payload.appKey), View.DragShadowBuilder(view), payload, 0)) endDrag()
    }

    private val edgeTurn = Runnable {
        if (dragging && lastEdge != 0) {
            val next = currentPage + lastEdge
            if (next >= 0 && next < store.pages) { currentPage = next; renderHome() }
        }
    }

    private fun workspaceDrag(event: DragEvent): Boolean {
        val payload = event.localState as? DragPayload ?: return false
        val original = store.items.find { it.id == payload.itemId }
        val item = original ?: HomeItem(kind = ItemKind.APP, appKey = payload.appKey)
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_LOCATION -> {
                val x = (event.x/workspace.cellWidth - item.spanX/2f).roundToInt().coerceIn(0, store.settings.columns-item.spanX)
                val y = (event.y/workspace.cellHeight - item.spanY/2f).roundToInt().coerceIn(0, store.settings.rows-item.spanY)
                val candidate = item.copy(page = currentPage, x = x, y = y)
                workspace.dropAllowed = engine.fits(candidate, store.items); workspace.dropPreview = candidate
                val edge = if (event.x < dp(22)) -1 else if (event.x > workspace.width-dp(22)) 1 else 0
                if (edge != lastEdge) { main.removeCallbacks(edgeTurn); lastEdge = edge; if (edge != 0) main.postDelayed(edgeTurn, 650) }
            }
            DragEvent.ACTION_DROP -> {
                val candidate = workspace.dropPreview
                if (candidate != null && engine.fits(candidate, store.items)) {
                    store.save(items = store.items.filterNot { it.id == candidate.id } + candidate,
                        dock = if (payload.fromDock) store.dock.filterNot { it == payload.appKey } else store.dock)
                    renderHome()
                } else message("That space is occupied.")
                workspace.dropPreview = null
            }
            DragEvent.ACTION_DRAG_EXITED -> { workspace.dropPreview = null; lastEdge = 0; main.removeCallbacks(edgeTurn) }
            DragEvent.ACTION_DRAG_ENDED -> endDrag()
        }
        return true
    }

    private fun endDrag() {
        dragging = false; draggedItem = null; workspace.dropPreview = null; workspace.editing = editing
        main.removeCallbacks(edgeTurn); renderTop(); dock.alpha = 1f
    }

    private fun dropTarget(text: String, symbol: String, dropped: (DragPayload) -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
        addView(Glyph(this@LauncherActivity, symbol), LinearLayout.LayoutParams(dp(32), dp(40)))
        addView(label(text, 13f, Color.WHITE), LinearLayout.LayoutParams(-2, -2))
        contentDescription = text
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { alpha = .5f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { alpha = 1f; true }
                DragEvent.ACTION_DROP -> { (event.localState as? DragPayload)?.let(dropped); true }
                else -> true
            }
        }
    }

    private fun removePayload(payload: DragPayload) {
        payload.itemId?.let { id ->
            store.items.find { it.id == id }?.let { if (it.kind == ItemKind.WIDGET) widgets.host.deleteAppWidgetId(it.widgetId) }
        }
        store.save(items = store.items.filterNot { it.id == payload.itemId }, dock = if (payload.fromDock) store.dock.filterNot { it == payload.appKey } else store.dock)
        renderHome()
    }

    private fun setEditing(value: Boolean) { editing = value; renderHome() }

    private fun customize() {
        val options = mutableListOf("Widgets", "Wallpapers", "Launcher settings")
        if (!store.settings.locked) options.addAll(listOf("Arrange home screen", "Add home page", "Add clock"))
        if (!store.settings.locked && store.pages > 1 && store.items.none { it.page == currentPage }) options.add("Remove empty page")
        dialog("Make it yours", options) { choice -> when (options[choice]) {
            "Widgets" -> showWidgets(); "Wallpapers" -> showWallpapers(); "Launcher settings" -> showSettings()
            "Arrange home screen" -> setEditing(true); "Add home page" -> addPage(); "Add clock" -> addClock()
            "Remove empty page" -> {
                store.save(items = store.items.map { if (it.page > currentPage) it.copy(page = it.page-1) else it })
                store.pages = store.pages-1; currentPage = (currentPage-1).coerceAtLeast(0); renderHome()
            }
        } }
    }

    private fun addPage() { store.pages = store.pages+1; currentPage = store.pages-1; setEditing(true) }

    private fun addClock() {
        val item = engine.vacant(HomeItem(kind = ItemKind.CLOCK, spanX = store.settings.columns, spanY = 2), store.items, currentPage) ?: return
        store.save(items = store.items + item); currentPage = item.page; closePanel(); renderHome()
    }

    private fun itemMenu(item: HomeItem) {
        if (store.settings.locked) { message("Layout is locked. Unlock it in settings."); return }
        val options = mutableListOf("Move to another cell", "Move to page…")
        if (item.kind != ItemKind.APP) options.add("Resize")
        if (item.kind == ItemKind.WIDGET && widgets.manager.getAppWidgetInfo(item.widgetId)?.configure != null) options.add("Widget settings")
        if (item.kind == ItemKind.APP) options.addAll(listOf("Pin to dock", "App info"))
        options.add("Remove from home")
        dialog(if (item.kind == ItemKind.APP) apps.find { it.key == item.appKey }?.label ?: "App" else if (item.kind == ItemKind.CLOCK) "Clock" else "Widget", options) { index ->
            when (options[index]) {
                "Move to another cell" -> moveItem(item)
                "Move to page…" -> chooseItemPage(item)
                "Resize" -> resizeItem(item)
                "Widget settings" -> widgets.reconfigure(item.widgetId)
                "Pin to dock" -> pinDock(item.appKey, store.dock.size, item.id)
                "App info" -> appInfo(item.appKey)
                "Remove from home" -> removePayload(DragPayload(item.id))
            }
        }
    }

    private fun moveItem(item: HomeItem) {
        val spots = mutableListOf<HomeItem>()
        for (y in 0 until store.settings.rows) for (x in 0 until store.settings.columns) {
            val candidate = item.copy(page = currentPage, x = x, y = y)
            if (engine.fits(candidate, store.items)) spots += candidate
        }
        dialog("Move to cell", spots.map { "Column ${it.x+1}, row ${it.y+1}" }) { i ->
            store.save(items = store.items.map { if (it.id == item.id) spots[i] else it }); renderHome()
        }
    }

    private fun chooseItemPage(item: HomeItem) {
        dialog("Move to page", (0..store.pages).map { if (it == store.pages) "New page" else "Page ${it+1}" }) { page ->
            val placed = engine.vacant(item.copy(page = page), store.items.filterNot { it.id == item.id }, page)
            if (placed != null) {
                store.save(items = store.items.map { if (it.id == item.id) placed else it })
                currentPage = placed.page; renderHome()
            }
        }
    }

    private fun dockMenu(key: String) {
        dialog(apps.find { it.key == key }?.label ?: "Dock app", listOf("Replace app", "Remove from dock", "App info")) { index ->
            if (index != 2 && store.settings.locked) { message("Unlock the layout in settings to edit the dock."); return@dialog }
            when (index) {
                0 -> chooseDockApp(store.dock.indexOf(key))
                1 -> { store.save(dock = store.dock.filterNot { it == key }); renderDock() }
                2 -> appInfo(key)
            }
        }
    }

    private fun chooseDockApp(slot: Int) {
        dialog("Choose dock app", apps.map { it.label }) { index -> pinDock(apps[index].key, slot, replace = true) }
    }

    private fun pinDock(key: String, slot: Int, sourceId: String? = null, replace: Boolean = false) {
        if (store.settings.locked) { message("Unlock the layout in settings to edit the dock."); return }
        val list = store.dock.filterNot { it == key }.toMutableList()
        if (list.size >= store.settings.dockSlots && !replace) {
            dialog("Replace a dock app", store.dock.map { apps.find { app -> app.key == it }?.label ?: "Unavailable" }) { i -> pinDock(key, i, sourceId, true) }
            return
        }
        val index = slot.coerceIn(0, list.size)
        if (replace && index < list.size) list[index] = key else list.add(index, key)
        if (list.size > store.settings.dockSlots) list.removeAt(list.lastIndex)
        store.save(items = store.items.filterNot { it.id == sourceId }, dock = list)
        renderHome()
    }

    private fun launch(key: String) {
        val app = apps.find { it.key == key } ?: run { message("This app or profile is currently unavailable."); return }
        hideKeyboard()
        try { catalog.launcherApps.startMainActivity(app.info.componentName, app.info.user, null, null) }
        catch (error: Exception) { android.util.Log.w("StillLauncher", "Unable to launch ${app.info.componentName}", error); message("This app cannot be opened right now.") }
    }

    private fun appInfo(key: String) {
        val app = apps.find { it.key == key } ?: return
        runCatching { catalog.launcherApps.startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }
            .onFailure { message("App information is unavailable.") }
    }

    private fun showDrawer(initialQuery: String = "") {
        val body = panelBase("All apps", "${apps.size} apps · A little order, a little space.", "drawer")
        val searchWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(card, dp(22).toFloat()); setPadding(dp(8), 0, dp(8), 0)
        }
        searchWrap.addView(Glyph(this, "search", muted), LinearLayout.LayoutParams(dp(44), dp(52)))
        val search = EditText(this).apply {
            hint = "Search your apps"; setHintTextColor(muted); setTextColor(ink); textSize = 16f
            setSingleLine(true); background = null; setPadding(0, 0, 0, 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            contentDescription = "Search installed apps"
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(56), 1f))
        val clear = iconButton("close", "Clear search", muted) { search.text.clear() }
        searchWrap.addView(clear, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(searchWrap, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(18) })
        val gridFrame = FrameLayout(this)
        val grid = GridView(this).apply {
            numColumns = store.settings.drawerColumns; verticalSpacing = dp(12); horizontalSpacing = dp(2)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; clipToPadding = false; setPadding(0, dp(8), 0, dp(20))
            isVerticalScrollBarEnabled = false
        }
        val empty = label("No apps found\nTry another name.", 16f, muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(8).toFloat(), 1f) }
        gridFrame.addView(grid, FrameLayout.LayoutParams(-1, -1)); gridFrame.addView(empty, FrameLayout.LayoutParams(-1, -1)); grid.emptyView = empty
        val adapter = AppsAdapter(); drawerAdapter = adapter; grid.adapter = adapter
        (body as PullDownDrawerLayout).canPull = { !grid.canScrollVertically(-1) }
        body.addView(gridFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter(s.toString()); clear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        clear.visibility = View.INVISIBLE
        search.setOnEditorActionListener { _, _, _ -> adapter.filtered.firstOrNull()?.let { launch(it.key) }; true }
        grid.setOnItemClickListener { _, _, position, _ -> launch(adapter.filtered[position].key) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            val app = adapter.filtered[position]
            dialog(app.label, listOf("Add to home", "Pin to dock", "App info")) { choice -> when (choice) {
                0 -> addApp(app.key); 1 -> pinDock(app.key, store.dock.size); 2 -> appInfo(app.key)
            } }; true
        }
        search.setText(initialQuery)
        adapter.filter(initialQuery)
        search.clearFocus(); gridFrame.isFocusableInTouchMode = true; gridFrame.requestFocus()
    }

    private inner class AppsAdapter : BaseAdapter() {
        var filtered = apps
        var query = ""
        fun filter(value: String) { query = value; val q = value.trim(); filtered = apps.filter { it.label.contains(q, true) }; notifyDataSetChanged() }
        override fun getCount() = filtered.size
        override fun getItem(position: Int) = filtered[position]
        override fun getItemId(position: Int) = filtered[position].key.hashCode().toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = appTile(filtered[position].key, true, true).apply {
            layoutParams = AbsListView.LayoutParams(-1, dp(102)); isFocusable = false; isClickable = false; isLongClickable = false
        }
    }

    private fun addApp(key: String) {
        if (store.settings.locked) { message("Unlock the layout in settings to add apps."); return }
        val placed = engine.vacant(HomeItem(kind = ItemKind.APP, appKey = key), store.items, currentPage) ?: return
        store.save(items = store.items + placed); currentPage = placed.page; closePanel(); renderHome(); message("Added to home")
    }

    private fun panelBase(title: String, subtitle: String, kind: String): LinearLayout {
        closePanel()
        val body = (if (kind == "drawer") PullDownDrawerLayout(this) else LinearLayout(this)).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(surface, dp(28).toFloat())
            setPadding(dp(24), dp(8), dp(24), dp(12)); isClickable = true
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("STILL / ${kind.uppercase(Locale.ROOT)}", 10f, muted, true).apply { letterSpacing = .18f }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton("close", "Back to home", ink) { closePanel() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header, LinearLayout.LayoutParams(-1, dp(52)))
        body.addView(label(title, 34f, ink).apply { letterSpacing = -.04f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        body.addView(label(subtitle, 13f, muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(24) })
        home.visibility = View.INVISIBLE
        panel = body; panelKind = kind
        if (body is PullDownDrawerLayout) {
            body.pullStarted = { hideKeyboard(); home.visibility = View.VISIBLE }
            body.settled = { if (panel === body) home.visibility = View.INVISIBLE }
            body.dismissed = { if (panel === body) closePanel() }
        }
        safe.addView(body, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(8) })
        body.alpha = 0f; body.translationY = dp(24).toFloat(); body.animate().alpha(1f).translationY(0f).setDuration(180).start()
        updateSystemBars(); root.requestApplyInsets()
        return body
    }

    private fun closePanel() {
        hideKeyboard(); panel?.let { safe.removeView(it) }; if (::home.isInitialized) home.visibility = View.VISIBLE; panel = null; panelKind = ""; drawerAdapter = null
        if (::root.isInitialized) { updateSystemBars(); root.requestApplyInsets() }
    }

    private fun showSettings() {
        val body = panelBase("Your home, your rules.", "Small adjustments. A space that feels like you.", "settings")
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val isDefault = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) else false
        val defaultCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(if (light) 0xffd7e8d7.toInt() else 0xff2b4133.toInt(), dp(22).toFloat()) }
        defaultCard.addView(label(if (isDefault) "A place to come home to." else "Make yourself at home.", 17f, ink, true))
        defaultCard.addView(label(if (isDefault) "Still is your default launcher." else "Set Still as your default home app.", 13f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(16) })
        defaultCard.addView(pill(if (isDefault) "Manage default launcher" else "Set as default", accent, if (light) Color.WHITE else 0xff193625.toInt()) { requestHome() }, LinearLayout.LayoutParams(-1, dp(46)))
        content.addView(defaultCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(26) })
        section(content, "LAYOUT")
        settingsRow(content, "Home screen grid", "${store.settings.columns} columns × ${store.settings.rows} rows", "grid") { gridSettings() }
        settingsRow(content, "Dock", "${store.settings.dockSlots} app slots", "home") { numberChoice("Dock slots", 3..7, store.settings.dockSlots) { changeDockSize(it) } }
        settingsRow(content, "App drawer grid", "${store.settings.drawerColumns} columns", "grid") { numberChoice("Drawer columns", 3..6, store.settings.drawerColumns) { store.save(settings = store.settings.copy(drawerColumns = it)); showSettings() } }
        settingsRow(content, "Icon size", "${store.settings.iconSize} dp", "widgets") { numberChoice("Icon size", listOf(36, 40, 44, 48, 52, 56, 60, 64), store.settings.iconSize) { store.save(settings = store.settings.copy(iconSize = it)); renderHome(); showSettings() } }
        switchRow(content, "App labels", "Show names on the home screen", store.settings.labels) { store.save(settings = store.settings.copy(labels = it)); renderHome() }
        switchRow(content, "Lock layout", "Keep icons and widgets in place", store.settings.locked) { store.save(settings = store.settings.copy(locked = it)); if (it) setEditing(false) }
        section(content, "LOOK & FEEL")
        settingsRow(content, "Wallpapers", "Photos, landscapes, and device wallpapers", "wallpaper") { showWallpapers() }
        settingsRow(content, "Appearance", store.settings.theme.replaceFirstChar { it.uppercase() }, "settings") {
            val themes = listOf("system", "light", "dark")
            dialog("Appearance", themes.map { it.replaceFirstChar { c -> c.uppercase() } }) { i -> store.save(settings = store.settings.copy(theme = themes[i])); showSettings() }
        }
        section(content, "WIDGETS & PAGES")
        settingsRow(content, "Add a widget", "From the apps already on your phone", "widgets") { showWidgets() }
        settingsRow(content, "Add a clock", "A simple clock and date", "plus") { if (!store.settings.locked) addClock() else message("Unlock the layout first.") }
        settingsRow(content, "Arrange home screen", "Drag icons. Tap items for more options.", "grid") { if (!store.settings.locked) { closePanel(); setEditing(true) } else message("Unlock the layout first.") }
        settingsRow(content, "Manage pages", "${store.pages} home ${if (store.pages == 1) "page" else "pages"}", "home") { pageManager() }
        section(content, "KEYBOARD")
        settingsRow(content, "Still Keyboard", "Swipe typing, emoji search, and held toolbar controls", "settings") {
            startActivity(Intent(this, KeyboardSetupActivity::class.java))
        }
        section(content, "STILL LAUNCHER")
        content.addView(label("A quieter kind of home.\nVersion ${BuildConfig.VERSION_NAME} · Works entirely on your device.\nNo ads. No account. No network permission.", 12f, muted).apply { setLineSpacing(dp(6).toFloat(), 1f); setPadding(dp(4), dp(8), 0, dp(24)) })
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(label(title, 10f, muted, true).apply { letterSpacing = .16f; setPadding(dp(4), dp(14), 0, dp(12)) }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun settingsRow(parent: LinearLayout, title: String, subtitle: String, symbol: String, clicked: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(14), dp(8), dp(14)); ripple(Color.TRANSPARENT, 16) }
        row.addView(Glyph(this, symbol, muted), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 15f, ink, true))
        text.addView(label(subtitle, 12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 24f, muted), LinearLayout.LayoutParams(dp(20), -2))
        row.setOnClickListener { clicked() }; row.isFocusable = true; row.contentDescription = "$title, $subtitle"
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun switchRow(parent: LinearLayout, title: String, subtitle: String, value: Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(15), dp(8), dp(15)) }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 15f, ink, true)); text.addView(label(subtitle, 12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply { isChecked = value; contentDescription = title; thumbTintList = android.content.res.ColorStateList.valueOf(accent) }
        toggle.setOnCheckedChangeListener { _, checked -> changed(checked) }; row.addView(toggle, LinearLayout.LayoutParams(dp(58), dp(48)))
        parent.addView(row)
    }

    private fun gridSettings() {
        var columns = store.settings.columns; var rows = store.settings.rows
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val preview = GridPreview(this).apply { cols = columns; lines = rows }
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(180)))
        box.addView(stepper("Columns", columns, 3, 7) { columns = it; preview.cols = it; preview.invalidate() })
        box.addView(stepper("Rows", rows, 4, 9) { rows = it; preview.lines = it; preview.invalidate() })
        box.addView(label("Your apps and widgets will move to fit. Extra items are kept on new pages.", 13f, muted).apply { setPadding(0, dp(12), 0, dp(6)) })
        val d = dialogBuilder().setTitle("Home screen grid").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (changeGrid(columns, rows)) { d.dismiss(); showSettings() }
        } }; showDialog(d)
    }

    private fun changeGrid(columns: Int, rows: Int): Boolean {
        if (store.settings.locked) { message("Unlock the layout before changing the grid."); return false }
        val newCellW = workspace.width.toFloat()/columns; val newCellH = workspace.height.toFloat()/rows
        // A provider's minimum is a physical dimension, so check it against the proposed cell size.
        val adjusted = store.items.map { item ->
            if (item.kind != ItemKind.WIDGET) item else {
                val info = widgets.manager.getAppWidgetInfo(item.widgetId)
                if (info == null) item else {
                    val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
                    val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                    val minW = if (horizontal && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
                    val minH = if (vertical && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
                    val padding = AppWidgetHostView.getDefaultPaddingForWidget(this, info.provider, null)
                    val w = ceil((minW+padding.left+padding.right)/newCellW).toInt().coerceAtLeast(1)
                    val h = ceil((minH+padding.top+padding.bottom)/newCellH).toInt().coerceAtLeast(1)
                    if (w > columns || h > rows) { message("A widget is too large for this grid. Remove it or choose a larger grid."); return false }
                    item.copy(spanX = maxOf(item.spanX.coerceAtMost(columns), w), spanY = maxOf(item.spanY.coerceAtMost(rows), h))
                }
            }
        }
        val packed = LayoutEngine(columns, rows).reflow(adjusted) ?: return false
        store.save(items = packed, settings = store.settings.copy(columns = columns, rows = rows)); renderHome()
        return true
    }

    private fun changeDockSize(slots: Int) {
        if (store.settings.locked) { message("Unlock the layout before changing the dock."); return }
        var layout = store.items
        store.dock.drop(slots).forEach { key -> engine.vacant(HomeItem(kind = ItemKind.APP, appKey = key), layout)?.let { layout = layout + it } }
        store.save(items = layout, dock = store.dock.take(slots), settings = store.settings.copy(dockSlots = slots)); renderHome(); showSettings()
    }

    private fun resizeItem(item: HomeItem) {
        val info = if (item.kind == ItemKind.WIDGET) widgets.manager.getAppWidgetInfo(item.widgetId) else null
        val horizontal = item.kind == ItemKind.CLOCK || info?.resizeMode?.and(AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val vertical = item.kind == ItemKind.CLOCK || info?.resizeMode?.and(AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
        if (!horizontal && !vertical) { message("This widget has a fixed size."); return }
        val cw = workspace.cellWidth.coerceAtLeast(1f); val ch = workspace.cellHeight.coerceAtLeast(1f)
        val padding = info?.let { AppWidgetHostView.getDefaultPaddingForWidget(this, it.provider, null) } ?: android.graphics.Rect()
        val minX = if (info == null) 2 else ceil(((if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth)+padding.left+padding.right)/cw).toInt().coerceAtLeast(1)
        val minY = if (info == null) 1 else ceil(((if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight)+padding.top+padding.bottom)/ch).toInt().coerceAtLeast(1)
        var maxX = store.settings.columns-item.x; var maxY = store.settings.rows-item.y
        if (Build.VERSION.SDK_INT >= 31 && info != null) {
            if (info.maxResizeWidth > 0) maxX = minOf(maxX, ((info.maxResizeWidth+padding.left+padding.right)/cw).toInt().coerceAtLeast(minX))
            if (info.maxResizeHeight > 0) maxY = minOf(maxY, ((info.maxResizeHeight+padding.top+padding.bottom)/ch).toInt().coerceAtLeast(minY))
        }
        var w = item.spanX; var h = item.spanY
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(12)) }
        val preview = GridPreview(this).apply { cols = store.settings.columns; lines = store.settings.rows; selection = item }
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(200)))
        val hint = label("", 13f, muted)
        fun update() {
            val candidate = item.copy(spanX = w, spanY = h)
            val valid = engine.fits(candidate, store.items)
            preview.selection = candidate; preview.valid = valid; preview.invalidate()
            hint.text = if (valid) "$w × $h cells" else "Another item is in the way. Try a smaller size."
        }
        if (horizontal) box.addView(stepper("Width", w, minOf(minX, w), maxOf(maxX, w)) { w = it; update() })
        if (vertical) box.addView(stepper("Height", h, minOf(minY, h), maxOf(maxY, h)) { h = it; update() })
        box.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }); update()
        val d = dialogBuilder().setTitle("Resize ${if (item.kind == ItemKind.CLOCK) "clock" else "widget"}").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val candidate = item.copy(spanX = w, spanY = h)
            if (engine.fits(candidate, store.items)) {
                store.save(items = store.items.map { if (it.id == item.id) candidate else it }); renderHome(); d.dismiss()
            } else message("That size overlaps another item.")
        } }; showDialog(d)
    }

    private fun stepper(title: String, value: Int, min: Int, max: Int, changed: (Int) -> Unit): View {
        var number = value
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        row.addView(label(title, 16f, ink), LinearLayout.LayoutParams(0, -2, 1f))
        val current = label(value.toString(), 18f, ink, true).apply { gravity = Gravity.CENTER; contentDescription = "$title $value" }
        row.addView(iconButton("minus", "Decrease $title", accent) { if (number > min) { number--; current.text = "$number"; current.contentDescription = "$title $number"; changed(number) } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(current, LinearLayout.LayoutParams(dp(40), -2))
        row.addView(iconButton("plus", "Increase $title", accent) { if (number < max) { number++; current.text = "$number"; current.contentDescription = "$title $number"; changed(number) } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun pageManager() {
        val options = (0 until store.pages).map { "Page ${it+1} · ${store.items.count { item -> item.page == it }} items" } + "Add a page"
        dialog("Home pages", options) { i ->
            if (i == store.pages) { if (!store.settings.locked) { closePanel(); addPage() } else message("Unlock the layout first.") }
            else { currentPage = i; closePanel(); renderHome(); if (store.items.none { it.page == i }) message("Touch and hold the empty page to remove it.") }
        }
    }

    private fun showWidgets() {
        if (store.settings.locked) { message("Unlock the layout in settings to add widgets."); return }
        val body = panelBase("Room for a little more.", "Grouped by app. Hold a home widget to drag it; drop on Options to resize.", "widgets")
        val providers = widgets.providers()
        data class Entry(val app: String, val info: AppWidgetProviderInfo?)
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val groups = providers.groupBy { it.provider.packageName to it.profile }.values
            .map { group -> widgets.appLabel(group.first()) to group }
            .sortedWith { a, b -> collator.compare(a.first, b.first).takeIf { it != 0 }
                ?: a.second.first().provider.packageName.compareTo(b.second.first().provider.packageName) }
        val entries = groups.flatMap { (app, group) ->
            listOf(Entry(app, null)) + group.sortedWith { a, b -> collator.compare(a.loadLabel(packageManager), b.loadLabel(packageManager)) }
                .map { Entry(app, it) }
        }
        val list = ListView(this).apply { divider = null; isVerticalScrollBarEnabled = false }
        list.adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position: Int) = entries[position].info != null
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) = if (entries[position].info == null) 0 else 1
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val entry = entries[position]
                val info = entry.info ?: return label(entry.app, 19f, ink, true).apply {
                    setPadding(dp(4), dp(20), dp(4), dp(12))
                    if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                    contentDescription = "${entry.app} widgets"
                }
                val row = LinearLayout(this@LauncherActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(16), dp(12), dp(16)); background = rounded(card, dp(18).toFloat()) }
                val icon = ImageView(this@LauncherActivity).apply { setImageDrawable(info.loadIcon(this@LauncherActivity, resources.displayMetrics.densityDpi)); scaleType = ImageView.ScaleType.FIT_CENTER }
                row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(16) })
                val labels = LinearLayout(this@LauncherActivity).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(label(info.loadLabel(packageManager), 15f, ink, true))
                val size = widgetSpan(info)
                labels.addView(label("${size.first} × ${size.second} cells${if (info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) " · Resizable" else " · Fixed size"}", 12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(Glyph(this@LauncherActivity, "plus", accent), LinearLayout.LayoutParams(dp(36), dp(40)))
                return FrameLayout(this@LauncherActivity).apply { addView(row, FrameLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
            }
        }
        if (providers.isEmpty()) body.addView(label("No widgets are available yet. Install an app with widgets, then come back here.", 16f, muted))
        else body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, i, _ ->
            val info = entries[i].info ?: return@setOnItemClickListener
            val (w, h) = widgetSpan(info)
            if (w > store.settings.columns || h > store.settings.rows) message("This widget is too large for your grid. Change the grid in settings.")
            else { closePanel(); widgets.add(info) }
        }
    }

    private fun widgetSpan(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(this, info.provider, null)
        val w = ceil((info.minWidth+padding.left+padding.right)/workspace.cellWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val h = ceil((info.minHeight+padding.top+padding.bottom)/workspace.cellHeight.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        // targetCell dimensions are recommendations; physical minWidth/minHeight stay authoritative.
        return Pair(w, h)
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo): Boolean {
        val (w, h) = widgetSpan(info)
        val placed = engine.vacant(HomeItem(kind = ItemKind.WIDGET, widgetId = id, spanX = w, spanY = h), store.items, currentPage)
        if (placed == null) { message("This widget does not fit the current grid."); return false }
        store.save(items = store.items + placed); currentPage = placed.page; renderHome(); return true
    }

    private fun showWallpapers() {
        val body = panelBase("A different perspective.", "A few quiet landscapes, or something of your own.", "wallpapers")
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val cards = LinearLayout(this)
        listOf("tide" to "Tide", "dune" to "Dune", "ink" to "Ink").forEach { (style, title) ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val image = StillWallpaper(this).apply { this.style = style; dim = 0; background = rounded(card, dp(18).toFloat()); clipToOutline = true }
            column.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
            column.addView(label(title, 13f, ink, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14); bottomMargin = dp(16) })
            column.setOnClickListener { applyWallpaper(style) }; column.contentDescription = "$title wallpaper. Tap to apply."; column.isFocusable = true
            cards.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        content.addView(cards)
        settingsRow(content, "Choose a photo", "Pick an image from your device", "wallpaper") {
            safeStartForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, PHOTO)
        }
        settingsRow(content, "Use device wallpaper", "Includes live wallpapers", "home") { store.save(settings = store.settings.copy(wallpaper = "device")); renderHome(); closePanel() }
        settingsRow(content, "System wallpaper picker", "Browse your phone's wallpaper collection", "wallpaper") {
            try {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                store.save(settings = store.settings.copy(wallpaper = "device")); closePanel()
            } catch (_: Exception) { message("No wallpaper picker is installed.") }
        }
        section(content, "READABILITY")
        content.addView(label("Darken wallpaper · ${store.settings.dim}%", 14f, ink).apply { tag = "dimLabel" })
        val dimLabel = content.findViewWithTag<TextView>("dimLabel")
        val slider = SeekBar(this).apply { max = 60; progress = store.settings.dim; contentDescription = "Wallpaper darkness" }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) { dimLabel.text = "Darken wallpaper · $value%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) { store.save(settings = store.settings.copy(dim = seekBar.progress)); renderHome() }
        }); content.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun applyWallpaper(style: String) {
        io.execute {
            val outcome = runCatching {
                val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                try {
                    StillWallpaper.render(Canvas(bitmap), 1080f, 2400f, style)
                    check(WallpaperManager.getInstance(this).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM) > 0)
                } finally { bitmap.recycle() }
            }
            main.post { if (!destroyed) {
                if (outcome.isSuccess) { store.save(settings = store.settings.copy(wallpaper = style)); renderHome(); closePanel(); message("Wallpaper applied") }
                else message("Wallpaper could not be applied on this device.")
            } }
        }
    }

    private fun importPhoto(uri: Uri) {
        io.execute {
            val result = runCatching {
                val image = decodePhoto(uri)
                try {
                    check(WallpaperManager.getInstance(this).setBitmap(image, null, true, WallpaperManager.FLAG_SYSTEM) > 0)
                } finally { image.recycle() }
            }
            main.post { if (!destroyed) result.onSuccess {
                store.save(settings = store.settings.copy(wallpaper = "photo")); closePanel(); renderHome()
            }.onFailure { message("That image could not be used as a wallpaper.") } }
        }
    }

    private fun decodePhoto(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, 2560f/maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1), (info.size.height*scale).toInt().coerceAtLeast(1))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight)/sample > 2560) sample *= 2
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Invalid image")
        val orientation = runCatching { contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        } }.getOrNull() ?: 1
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return decoded
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                safeStartForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_ROLE); return
            }
        }
        safeStart(Intent(Settings.ACTION_HOME_SETTINGS), "Home app settings are unavailable.")
    }

    @Deprecated("Platform result bridge for system widget flows")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (widgets.result(requestCode, resultCode)) return
        if (requestCode == PHOTO && resultCode == RESULT_OK) data?.data?.let(::importPhoto)
        if (requestCode == HOME_ROLE && panelKind == "settings") showSettings()
    }

    private fun iconButton(symbol: String, description: String, color: Int, clicked: () -> Unit): View = Glyph(this, symbol, color).apply {
        contentDescription = description; isFocusable = true; ripple(Color.TRANSPARENT, 24); setOnClickListener { clicked() }
    }
    private fun pill(text: String, color: Int, textColor: Int, clicked: () -> Unit): View = label(text, 14f, textColor, true).apply {
        gravity = Gravity.CENTER; ripple(color, 24); isFocusable = true; setOnClickListener { clicked() }
    }
    private fun dialog(title: String, options: List<String>, selected: (Int) -> Unit) {
        showDialog(dialogBuilder().setTitle(title).setItems(options.toTypedArray()) { _, index -> selected(index) }.setNegativeButton("Cancel", null).create())
    }
    private fun dialogBuilder() = AlertDialog.Builder(this, if (light) android.R.style.Theme_Material_Light_Dialog_Alert else android.R.style.Theme_Material_Dialog_Alert)
    private fun showDialog(dialog: AlertDialog) {
        activeDialog?.dismiss(); activeDialog = dialog; dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
    }
    private fun numberChoice(title: String, range: IntRange, current: Int, changed: (Int) -> Unit) = numberChoice(title, range.toList(), current, changed)
    private fun numberChoice(title: String, values: List<Int>, current: Int, changed: (Int) -> Unit) {
        showDialog(dialogBuilder().setTitle(title).setSingleChoiceItems(values.map { it.toString() }.toTypedArray(), values.indexOf(current)) { dialog, which -> dialog.dismiss(); changed(values[which]) }.setNegativeButton("Cancel", null).create())
    }
    private fun hideKeyboard() { currentFocus?.let { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) } }
    private fun updateSystemBars() {
        val lightBars = panel != null && light
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(if (lightBars) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        } else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            if (lightBars) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
    }
    private fun safeStart(intent: Intent, error: String) { try { startActivity(intent) } catch (_: Exception) { message(error) } }
    private fun safeStartForResult(intent: Intent, request: Int) { try { startActivityForResult(intent, request) } catch (_: Exception) { message("This system screen is unavailable.") } }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    private class GridPreview(context: android.content.Context) : View(context) {
        var cols = 4; var lines = 6; var selection: HomeItem? = null; var valid = true
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val margin = context.dp(12).toFloat(); val w = minOf(width-margin*2, height*.70f)
            val h = height-margin*2; val left = (width-w)/2; val cw = w/cols; val ch = h/lines
            paint.color = 0x447c9c84
            for (x in 0 until cols) for (y in 0 until lines) canvas.drawRoundRect(left+x*cw+2, margin+y*ch+2, left+(x+1)*cw-2, margin+(y+1)*ch-2, 4f,4f,paint)
            selection?.let { paint.color = if (valid) 0xff9bc5a5.toInt() else 0xffce8d84.toInt(); canvas.drawRoundRect(left+it.x*cw+2,margin+it.y*ch+2,left+(it.x+it.spanX)*cw-2,margin+(it.y+it.spanY)*ch-2,8f,8f,paint) }
        }
    }

    companion object { const val PHOTO = 51; const val HOME_ROLE = 52 }
}
