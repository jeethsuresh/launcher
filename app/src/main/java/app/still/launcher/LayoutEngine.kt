package app.still.launcher

import java.util.UUID

enum class ItemKind { APP, WIDGET, CLOCK, FOLDER }

data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: ItemKind,
    val appKey: String = "",
    val widgetId: Int = -1,
    val page: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

/** Platform-independent placement rules. Every mutation is validated before persistence. */
class LayoutEngine(val columns: Int, val rows: Int) {
    init { require(columns > 0 && rows > 0) }

    fun fits(item: HomeItem, items: List<HomeItem>): Boolean =
        item.page >= 0 && item.x >= 0 && item.y >= 0 && item.spanX > 0 && item.spanY > 0 &&
            item.x + item.spanX <= columns && item.y + item.spanY <= rows &&
            items.none { other ->
                other.id != item.id && other.page == item.page &&
                    item.x < other.x + other.spanX && item.x + item.spanX > other.x &&
                    item.y < other.y + other.spanY && item.y + item.spanY > other.y
            }

    fun vacant(item: HomeItem, items: List<HomeItem>, startPage: Int = 0): HomeItem? {
        if (item.spanX > columns || item.spanY > rows) return null
        for (page in startPage..maxOf(startPage, (items.maxOfOrNull { it.page } ?: 0) + 1)) {
            for (y in 0..rows - item.spanY) for (x in 0..columns - item.spanX) {
                val candidate = item.copy(page = page, x = x, y = y)
                if (fits(candidate, items)) return candidate
            }
        }
        return null
    }

    /** Reflow keeps stable positions when possible, and creates pages instead of losing items. */
    fun reflow(items: List<HomeItem>): List<HomeItem>? {
        val packed = mutableListOf<HomeItem>()
        for (item in items.sortedByDescending { it.spanX * it.spanY }) {
            val resized = item.copy(spanX = item.spanX.coerceAtMost(columns), spanY = item.spanY.coerceAtMost(rows))
            val placed = if (fits(resized, packed)) resized else vacant(resized, packed, item.page)
            packed += placed ?: return null
        }
        return packed
    }
}

/** Inserting a page shifts positions without changing item identities, spans, or cell coordinates. */
object PageLayout {
    fun insert(items: List<HomeItem>, at: Int): List<HomeItem> {
        require(at >= 0)
        return items.map { if (it.page >= at) it.copy(page = it.page + 1) else it }
    }
}
