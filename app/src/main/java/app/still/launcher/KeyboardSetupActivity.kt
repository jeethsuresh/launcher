package app.still.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** System consent is always left to Android's keyboard settings and picker. */
class KeyboardSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(24, 28, 27)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(24, 28, 27))
        }
        fun text(value: String, size: Float, strong: Boolean = false) {
            content.addView(TextView(this).apply {
                text = value; textSize = size; setTextColor(Color.rgb(232, 240, 235))
                if (strong) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(12))
            })
        }
        fun button(value: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = value; isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
        }
        text("Still Keyboard", 30f, true)
        text("A familiar keyboard, built on HeliBoard. Rounded keys, an apostrophe beside the space bar, swipe typing, and emoji search.", 16f)
        text("Try it here", 20f, true)
        val editor = EditText(this).apply {
            hint = "Type or swipe a few words…"
            contentDescription = "Keyboard test field"
            textSize = 19f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, dp(110)))
        button("1 · Enable Still Keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("2 · Choose Still Keyboard") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        button("Keyboard settings") { startActivity(Intent(this, helium314.keyboard.settings.SettingsActivity::class.java)) }
        button("Toolbar setup") {
            startActivity(Intent(this, helium314.keyboard.settings.SettingsActivity::class.java).putExtra("still_settings_destination", "toolbar"))
        }
        text("Hold to act", 20f, true)
        text("Hold a suggested word to use it. Hold a toolbar button to run its action. Hold the left arrow to set up the toolbar. A quick tap on the toolbar does nothing. Letter, symbol, and emoji keys use normal taps.", 15f)
        text("Tap the smiley, then hold the magnifying glass to search emoji. Hold category icons to switch emoji groups. Search words stay inside the keyboard; only your chosen emoji is inserted. English US/UK dictionaries are bundled. Typing and search work offline.", 14f)
        button("Source & licenses") {
            val notices = TextView(this).apply {
                text = assets.open("still/NOTICES.txt").bufferedReader().use { it.readText() }
                textSize = 13f; setPadding(dp(20), dp(16), dp(20), dp(16)); setTextIsSelectable(true)
            }
            android.app.AlertDialog.Builder(this).setTitle("Open source notices")
                .setView(ScrollView(this).apply { addView(notices) }).setPositiveButton("Close", null).show()
        }
        text("Based on HeliBoard 3.5 · GPL-3.0\nEmoji names and keywords: Unicode CLDR.\nNo ads, account, or Internet permission.", 12f)
        setContentView(ScrollView(this).apply { addView(content); isFillViewport = true; fitsSystemWindows = true })
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
