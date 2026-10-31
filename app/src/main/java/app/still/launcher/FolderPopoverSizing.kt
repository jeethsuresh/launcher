package app.still.launcher

/** Two icon rows minimum; grow toward the source folder and scroll anything beyond it. */
object FolderPopoverSizing {
    fun height(appCount: Int, columns: Int, rowHeight: Int, chromeHeight: Int, availableHeight: Int, anchorDistance: Int?): Int {
        require(appCount >= 0 && columns > 0 && rowHeight > 0 && chromeHeight >= 0)
        val needed = (appCount + columns - 1) / columns
        val maximum = anchorDistance?.let { ((it - chromeHeight) / rowHeight).coerceAtLeast(2) } ?: 2
        val rows = needed.coerceAtLeast(2).coerceAtMost(maximum)
        // Insets, the keyboard, or a very small window must never push the sheet off screen.
        return (chromeHeight + rows * rowHeight).coerceAtMost(availableHeight.coerceAtLeast(0))
    }
}
