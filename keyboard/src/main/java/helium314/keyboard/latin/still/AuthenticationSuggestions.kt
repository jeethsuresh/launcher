// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.utils.InlineAutofillUtils
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.latin.utils.prefs

/** A separate row keeps credentials independent of prediction/clipboard toolbar updates. */
class AuthenticationSuggestions(private val ime: LatinIME) : ClipboardManager.OnPrimaryClipChangedListener {
    private val handler = Handler(Looper.getMainLooper())
    private var host: LinearLayout? = null
    private var inlineView: View? = null
    private var active = false
    private var generation = 0
    private val consumed = mutableSetOf<String>()
    private val changed: () -> Unit = { refresh() }
    private val expiry = Runnable { refresh() }
    private val preferenceChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == OneTimeCodes.CLIPBOARD_ENABLED_PREF) refresh()
    }
    private val clipboard get() = ime.getSystemService(ClipboardManager::class.java)

    fun onCreate() {
        clipboard?.addPrimaryClipChangedListener(this)
        RecentSmsCodes.subscribe(changed)
        ime.prefs().registerOnSharedPreferenceChangeListener(preferenceChanged)
    }

    fun onDestroy() {
        finish()
        clipboard?.removePrimaryClipChangedListener(this)
        RecentSmsCodes.unsubscribe(changed)
        ime.prefs().unregisterOnSharedPreferenceChangeListener(preferenceChanged)
    }

    fun attach(view: View) {
        host?.removeAllViews()
        host = view.findViewById(R.id.still_auth_suggestions)
        refresh()
    }

    fun newEditor() {
        generation++
        inlineView = null
        host?.removeAllViews()
        host?.visibility = View.GONE
    }

    fun start() { active = true; refresh() }
    fun finish() {
        active = false
        handler.removeCallbacks(expiry)
        newEditor()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineResponse(response: InlineSuggestionsResponse): Boolean {
        if (isDeviceLocked(ime)) return false
        val epoch = generation
        inlineView = if (response.inlineSuggestions.isEmpty()) null else
            InlineAutofillUtils.createView(response.inlineSuggestions, ime) { generation == epoch }
        refresh()
        return inlineView != null
    }

    override fun onPrimaryClipChanged() { refresh() }

    private fun clipboardCode(): Pair<String, Long>? {
        if (!ime.prefs().getBoolean(OneTimeCodes.CLIPBOARD_ENABLED_PREF, true)) return null
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) return null
        val timestamp = clip.description.timestamp
        if (!OneTimeCodes.isRecent(timestamp, System.currentTimeMillis())) return null
        // Only literal text is needed. Do not ask content providers to load clipboard URIs.
        val code = OneTimeCodes.fromClipboard(clip.getItemAt(0).text?.toString() ?: return null) ?: return null
        return code to timestamp
    }

    fun refresh() {
        handler.removeCallbacks(expiry)
        val row = host ?: return
        row.removeAllViews()
        row.visibility = View.GONE
        if (!active || isDeviceLocked(ime) || ime.isStillEmojiSearching || ime.currentInputEditorInfo?.inputType == InputType.TYPE_NULL) return
        val now = SystemClock.elapsedRealtime()
        val sms = if (RecentSmsCodes.enabled(ime)) RecentSmsCodes.store.current(now) else {
            RecentSmsCodes.store.clear(); emptyList()
        }
        val clip = clipboardCode()
        consumed.retainAll(sms.map { it.code } + listOfNotNull(clip?.first))
        val inputType = ime.currentInputEditorInfo?.inputType ?: return
        val numeric = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
        val codes = (sms.map { Triple(it.code, "SMS", OneTimeCodes.LIFETIME_MS - (now - it.receivedAt)) } +
            listOfNotNull(clip?.let { Triple(it.first, "Clipboard", OneTimeCodes.LIFETIME_MS - (System.currentTimeMillis() - it.second)) }))
            .distinctBy { it.first }.filter { it.first !in consumed && (!numeric || it.first.all(Char::isDigit)) }
        val content = LinearLayout(ime).apply { orientation = LinearLayout.HORIZONTAL }
        val epoch = generation
        for ((code, source, _) in codes) {
            val chip = TextView(ime).apply {
                text = "$source · $code"
                contentDescription = "$source verification code $code. Hold to insert."
                gravity = android.view.Gravity.CENTER
                setPadding(24, 0, 24, 0)
                setTextColor(helium314.keyboard.latin.settings.Settings.getValues().mColors.get(ColorType.KEY_TEXT))
                setOnLongClickListener {
                    val fresh = clipboardCode()?.first == code ||
                        (RecentSmsCodes.enabled(ime) && RecentSmsCodes.store.current(SystemClock.elapsedRealtime()).any { entry -> entry.code == code })
                    if (active && generation == epoch && fresh && !isDeviceLocked(ime) && !ime.isStillEmojiSearching) {
                        ime.commitAuthenticationCode(code)
                        consumed.add(code)
                    }
                    refresh()
                    true
                }
            }
            content.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        inlineView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            content.addView(it)
        }
        if (content.childCount == 0) return
        val height = ime.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        row.setBackgroundColor(helium314.keyboard.latin.settings.Settings.getValues().mColors.get(ColorType.STRIP_BACKGROUND))
        row.addView(HorizontalScrollView(ime).apply {
            isHorizontalScrollBarEnabled = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        row.visibility = View.VISIBLE
        if (codes.isNotEmpty()) handler.postDelayed(expiry, codes.minOf { it.third }.coerceAtLeast(1))
    }

    fun visibleHeight(): Int = host?.takeIf { it.visibility == View.VISIBLE }?.height ?: 0
}
