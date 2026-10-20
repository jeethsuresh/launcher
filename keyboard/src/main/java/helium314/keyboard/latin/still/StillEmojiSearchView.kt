// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import org.json.JSONArray
import java.util.concurrent.Executors

/** Search stays inside the IME. Only selecting a result commits text to the client. */
class StillEmojiSearchView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var ime: LatinIME? = null
    private var query = ""
    var active = false
        private set
    private var previousStripVisibility = VISIBLE
    private val prompt = TextView(context)
    private val results = LinearLayout(context)
    private val glyphs = HashMap<String, Boolean>()
    private val close = TextView(context)
    private val strip get() = KeyboardSwitcher.getInstance().stripContainer
    private val switcher get() = KeyboardSwitcher.getInstance()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(209, 213, 219))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        prompt.apply {
            textSize = 16f; setTextColor(Color.rgb(30, 30, 35))
            setPadding(dp(14), 0, dp(6), 0)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        row.addView(prompt, LayoutParams(0, dp(40), 1f))
        row.addView(close.apply {
            text = "✕"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(30, 30, 35)); contentDescription = "Hold to close emoji search"
            setOnClickListener { }
            setOnLongClickListener { stop(); ime?.onCodeInput(KeyCode.EMOJI, 0, 0, false); true }
        }, LayoutParams(dp(48), dp(40)))
        addView(row)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(results, LayoutParams(LayoutParams.WRAP_CONTENT, dp(52)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
    }

    fun attach(service: LatinIME) { ime = service }

    fun start() {
        if (active) return
        switcher.resetKeyboardStateToAlphabet(ime?.currentAutoCapsState ?: 0, ime?.currentRecapitalizeState ?: 0)
        previousStripVisibility = strip.visibility
        active = true
        query = ""
        val colors = Settings.getValues().mColors
        setBackgroundColor(colors.get(ColorType.STRIP_BACKGROUND))
        prompt.setTextColor(colors.get(ColorType.KEY_TEXT))
        close.setTextColor(colors.get(ColorType.KEY_TEXT))
        visibility = VISIBLE
        retainSearchLayout()
        refresh()
        if (index == null) loader.execute {
            try {
                val data = JSONArray(context.assets.open("still/emoji-en.json").bufferedReader().use { it.readText() })
                val entries = (0 until data.length()).map { n ->
                    val item = data.getJSONObject(n)
                    val words = item.getJSONArray("keywords")
                    SearchEmoji(item.getString("emoji"), item.getString("name"), (0 until words.length()).map { words.getString(it) })
                }
                index = EmojiSearchIndex(entries)
            } catch (error: Exception) {
                android.util.Log.e("StillEmojiSearch", "Unable to load bundled CLDR names", error)
            }
            post { if (active) refresh() }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        query = ""
        visibility = GONE
        if (switcher.keyboard != null)
            switcher.resetKeyboardStateToAlphabet(ime?.currentAutoCapsState ?: 0, ime?.currentRecapitalizeState ?: 0)
        strip.visibility = previousStripVisibility
        val settings = Settings.getValues()
        switcher.mainKeyboardView?.setGestureHandlingEnabledByUser(
            settings.mGestureInputEnabled, settings.mGestureTrailEnabled, settings.mGestureFloatingPreviewTextEnabled)
    }

    fun handle(code: Int): Boolean {
        if (code == SEARCH_CODE) { start(); return true }
        if (!active) return false
        when {
            code == KeyCode.DELETE -> {
                if (query.isNotEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length, -1))
            }
            code == 10 -> { visibleResults().firstOrNull()?.let { choose(it) }; return true }
            code == KeyCode.EMOJI -> { stop(); return false }
            code in listOf(KeyCode.SHIFT, KeyCode.SYMBOL_ALPHA, KeyCode.ALPHA, KeyCode.SYMBOL) -> return false
            code > 0 && Character.isValidCodePoint(code) -> if (query.length < 80) query += String(Character.toChars(code))
            // Function keys and cursor movement must never affect the client during search.
            else -> return true
        }
        refresh()
        return true
    }

    fun append(text: String) {
        if (!active) return
        query = (query + text).take(80)
        refresh()
    }

    fun retainSearchLayout() {
        if (!active) return
        strip.visibility = GONE
        switcher.mainKeyboardView?.setGestureHandlingEnabledByUser(false, false, false)
    }

    private fun visibleResults() = index?.search(query)?.filter {
        !helium314.keyboard.keyboard.emoji.SupportedEmojis.isUnsupported(it.emoji) &&
            glyphs.getOrPut(it.emoji) { prompt.paint.hasGlyph(it.emoji) }
    }.orEmpty()

    private fun choose(entry: SearchEmoji) {
        stop()
        ime?.onTextInput(entry.emoji)
    }

    private fun refresh() {
        prompt.text = if (query.isEmpty()) "Search emoji · type below" else "Search emoji: $query"
        prompt.contentDescription = prompt.text
        results.removeAllViews()
        val matches = visibleResults()
        if (matches.isEmpty()) {
            results.addView(TextView(context).apply {
                text = if (index == null) "Loading emoji…" else "No matching emoji"
                textSize = 14f; gravity = Gravity.CENTER_VERTICAL; setTextColor(prompt.currentTextColor)
                setPadding(dp(14), 0, dp(14), 0)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(52)))
        }
        matches.forEach { entry ->
            results.addView(TextView(context).apply {
                text = entry.emoji; textSize = 29f; gravity = Gravity.CENTER
                contentDescription = entry.name
                isFocusable = true
                setOnClickListener { choose(entry) }
            }, LayoutParams(dp(52), dp(52)))
        }
        requestLayout()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val SEARCH_CODE = -11001
        @Volatile private var index: EmojiSearchIndex? = null
        private val loader = Executors.newSingleThreadExecutor()
    }
}
