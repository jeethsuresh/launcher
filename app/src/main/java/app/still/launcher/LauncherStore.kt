package app.still.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LauncherSettings(
    val columns: Int = 4,
    val rows: Int = 6,
    val drawerColumns: Int = 4,
    val dockSlots: Int = 5,
    val iconSize: Int = 52,
    val labels: Boolean = true,
    val locked: Boolean = false,
    val theme: String = "dark",
    val wallpaper: String = "device",
    val dim: Int = 15,
)

@android.annotation.SuppressLint("ApplySharedPref")
class LauncherStore(context: Context) {
    private val prefs = context.getSharedPreferences("still_launcher_v1", Context.MODE_PRIVATE)
    var settings: LauncherSettings = loadSettings()
        private set
    var items: List<HomeItem> = readItems()
        private set
    var dock: List<String> = readStrings("dock")
        private set
    var hiddenApps: Set<String> = readStrings("hidden_apps").toSet()
        private set

    fun saveHiddenApps(keys: Set<String>) {
        hiddenApps = keys.toSet()
        prefs.edit().putString("hidden_apps", JSONArray(hiddenApps.sorted()).toString()).commit()
    }

    var pages: Int
        get() = maxOf(prefs.getInt("pages", 1), (items.maxOfOrNull { it.page } ?: 0) + 1)
        set(value) { prefs.edit().putInt("pages", value.coerceAtLeast(1)).commit() }
    val initialized: Boolean get() = prefs.getBoolean("initialized", false)

    fun save(items: List<HomeItem> = this.items, dock: List<String> = this.dock, settings: LauncherSettings = this.settings) {
        this.items = items.toList(); this.dock = dock.toList(); this.settings = settings
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("kind", item.kind.name); put("app", item.appKey)
            put("widget", item.widgetId); put("page", item.page); put("x", item.x); put("y", item.y)
            put("w", item.spanX); put("h", item.spanY)
        }) }
        // Discrete layout edits are small transactions. Commit before returning so a process
        // stop immediately after a drop/resize cannot resurrect the previous layout.
        prefs.edit().putString("items", array.toString()).putString("dock", JSONArray(dock).toString())
            .putString("settings", JSONObject().apply {
                put("columns", settings.columns); put("rows", settings.rows); put("drawerColumns", settings.drawerColumns)
                put("dockSlots", settings.dockSlots); put("iconSize", settings.iconSize); put("labels", settings.labels)
                put("locked", settings.locked); put("theme", settings.theme); put("wallpaper", settings.wallpaper); put("dim", settings.dim)
            }.toString()).putBoolean("initialized", true).commit()
    }

    var pendingWidget: Int
        get() = prefs.getInt("pendingWidget", -1)
        set(value) { prefs.edit().putInt("pendingWidget", value).commit() }

    private fun readItems(): List<HomeItem> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let {
            HomeItem(it.getString("id"), ItemKind.valueOf(it.getString("kind")), it.optString("app"),
                it.optInt("widget", -1), it.optInt("page"), it.optInt("x"), it.optInt("y"), it.optInt("w", 1), it.optInt("h", 1))
        } }
    }.getOrDefault(emptyList())

    private fun readStrings(key: String): List<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun loadSettings(): LauncherSettings = runCatching {
        val j = JSONObject(prefs.getString("settings", "{}")!!)
        LauncherSettings(j.optInt("columns", 4).coerceIn(3, 7), j.optInt("rows", 6).coerceIn(4, 9),
            j.optInt("drawerColumns", 4).coerceIn(3, 6), j.optInt("dockSlots", 5).coerceIn(3, 7),
            j.optInt("iconSize", 52).coerceIn(36, 64), j.optBoolean("labels", true), j.optBoolean("locked"),
            j.optString("theme", "dark"), j.optString("wallpaper", "device"), j.optInt("dim", 15).coerceIn(0, 60))
    }.getOrDefault(LauncherSettings())
}
