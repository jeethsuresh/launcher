// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import android.content.Context
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Pre-exported frequencies from the exact bundled HeliBoard dictionaries, loaded off the UI thread. */
object StillSwipeLexicon {
    private var context: Context? = null
    private val cache = HashMap<String, List<SwipeWord>>()

    fun initialize(app: Context) { context = app.applicationContext }

    @Synchronized
    fun words(locale: Locale?): List<SwipeWord>? {
        if (locale?.language != "en") return null
        val tag = if (locale.country == "GB") "en-GB" else "en-US"
        cache[tag]?.let { return it }
        val assets = context?.assets ?: return null
        return try {
            val words = GZIPInputStream(assets.open("still/swipe-$tag.bin")).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else line.substring(0, tab).toIntOrNull()?.let { SwipeWord(line.substring(tab+1), it) }
                }.toList()
            }
            cache[tag] = words
            words
        } catch (error: java.io.IOException) {
            android.util.Log.w("StillSwipeLexicon", "Bundled gesture data unavailable for $tag", error)
            null
        }
    }
}
