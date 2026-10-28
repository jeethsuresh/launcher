package app.still.launcher

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import helium314.keyboard.latin.utils.prefs

/** System consent is always left to Android's keyboard settings and picker. */
class KeyboardSetupActivity : Activity() {
    private lateinit var smsButton: Button
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
        text("Passwords and verification codes", 20f, true)
        text("Choose Proton Pass as your Android password manager and enable its inline autofill. Tap its password suggestions to fill or unlock your vault. Proton Pass can copy 2FA codes after login; hold a recent code in the keyboard to insert it. Code suggestions disappear after 30 seconds.", 15f)
        button("Choose Proton Pass for autofill") {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:proton.android.pass"))
            try { startActivity(intent) }
            catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        text("Text-message codes are optional. Enable SMS access to suggest 4–8 character codes from newly received texts. Still keeps only the codes in memory for 30 seconds; it does not read your inbox or save messages. Some Android installers restrict SMS permission.", 14f)
        smsButton = Button(this).apply {
            isAllCaps = false
            setOnClickListener {
                if (helium314.keyboard.latin.still.RecentSmsCodes.enabled(this@KeyboardSetupActivity)) {
                    setSmsEnabled(false)
                } else if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
                    setSmsEnabled(true)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), SMS_PERMISSION_REQUEST)
                }
            }
        }
        content.addView(smsButton, LinearLayout.LayoutParams(-1, dp(54)))
        text("Hold to act", 20f, true)
        text("Hold a suggested word to use it. Hold a toolbar button to run its action. Hold the left arrow to set up the toolbar. A quick tap on the toolbar does nothing. Password-manager suggestions use the manager’s normal tap and unlock controls. Letter, symbol, and emoji keys use normal taps.", 15f)
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

    override fun onResume() {
        super.onResume()
        updateSmsButton()
    }

    private fun updateSmsButton() {
        smsButton.text = if (helium314.keyboard.latin.still.RecentSmsCodes.enabled(this)) "Turn off text-message code suggestions" else "Enable text-message code suggestions"
    }

    private fun setSmsEnabled(enabled: Boolean) {
        prefs().edit()
            .putBoolean(helium314.keyboard.latin.still.OneTimeCodes.SMS_ENABLED_PREF, enabled).apply()
        if (!enabled) helium314.keyboard.latin.still.RecentSmsCodes.store.clear()
        helium314.keyboard.latin.still.RecentSmsCodes.changed()
        updateSmsButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            setSmsEnabled(granted)
            if (!granted) android.widget.Toast.makeText(this, "SMS access was not granted. Password-manager and clipboard code suggestions still work.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    companion object { private const val SMS_PERMISSION_REQUEST = 40 }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
