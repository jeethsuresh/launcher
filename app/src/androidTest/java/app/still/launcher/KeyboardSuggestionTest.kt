package app.still.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.DictionaryFactory
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Runs against the packaged native dictionaries, real IME connection and rendered code row. */
class KeyboardSuggestionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> withKeyboard(test: (EditText, LatinIME) -> T): T {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, KeyboardSetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var editor: EditText
        instrumentation.runOnMainSync {
            fun find(view: View): EditText? {
                if (view is EditText) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            editor = find(activity.window.decorView)!!
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        SystemClock.sleep(2000)
        val field = KeyboardSwitcher::class.java.getDeclaredField("mLatinIME").apply { isAccessible = true }
        val ime = field.get(KeyboardSwitcher.getInstance()) as LatinIME
        try { return test(editor, ime) }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun commonTyposHaveUsefulDictionarySuggestions() = withKeyboard { _, _ ->
        val keyboard = KeyboardSwitcher.getInstance().keyboard!!
        val failures = mutableListOf<String>()
        for (locale in listOf(Locale.US, Locale.UK)) {
            val dictionary = DictionaryFactory.createMainDictionaryCollection(instrumentation.targetContext, locale, false)
            try {
                assertTrue("Bundled dictionary must load for $locale", dictionary.isInitialized)
                for ((typo, expected) in listOf("teh" to "the", "recieve" to "receive", "definately" to "definitely", "hellp" to "hello", "becuase" to "because")) {
                    val codePoints = StringUtils.toCodePointArray(typo)
                    val composer = WordComposer().apply { setComposingWord(codePoints, keyboard.getCoordinates(codePoints)) }
                    val suggestions = dictionary.getSuggestions(composer.composedDataSnapshot,
                        NgramContext.getEmptyPrevWordsContext(0), keyboard.proximityInfo.nativeProximityInfo,
                        SettingsValuesForSuggestion(true, false), 0, 1f,
                        floatArrayOf(Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL))
                        .orEmpty().sortedByDescending { it.mScore }.take(3).map { it.mWord.lowercase(locale) }
                    android.util.Log.i("StillSuggestionTest", "$locale $typo -> $suggestions")
                    if (expected !in suggestions) failures.add("$locale: $typo -> $suggestions; expected $expected")
                }
            } finally { dictionary.close() }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun codeRowInsertsLeadingZeroesWithoutSpacesAndExpires() = withKeyboard { editor, ime ->
        lateinit var row: ViewGroup
        instrumentation.runOnMainSync {
            row = KeyboardSwitcher.getInstance().mainKeyboardView.rootView.findViewById(helium314.keyboard.latin.R.id.still_auth_suggestions)
            ime.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("2FA", "001234"))
            ime.refreshAuthenticationSuggestions()
        }
        instrumentation.waitForIdleSync()
        fun findCode(view: View): TextView? {
            if (view is TextView && view.text.contains("001234")) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findCode(view.getChildAt(i))?.let { return it }
            return null
        }
        instrumentation.runOnMainSync {
            assertEquals(View.VISIBLE, row.visibility)
            val chip = findCode(row) ?: throw AssertionError("Clipboard code must be visible")
            chip.performLongClick()
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertEquals("001234", editor.text.toString()) }
        instrumentation.runOnMainSync {
            ime.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("2FA", "567890"))
            ime.refreshAuthenticationSuggestions()
            assertEquals(View.VISIBLE, row.visibility)
        }
        SystemClock.sleep(31_000)
        instrumentation.runOnMainSync { assertEquals("Code row must expire without another keystroke", View.GONE, row.visibility) }
    }

    @Test fun typedTyposReachTheVisibleSuggestionStrip() = withKeyboard { editor, ime ->
        for ((typo, expected) in listOf("teh" to "the", "recieve" to "receive", "definately" to "definitely")) {
            instrumentation.runOnMainSync { editor.setText("") }
            SystemClock.sleep(300)
            for (char in typo) {
                instrumentation.runOnMainSync {
                    val key = KeyboardSwitcher.getInstance().keyboard!!.sortedKeys.first { it.code == char.code }
                    ime.onCodeInput(char.code, key.x + key.width / 2, key.y + key.height / 2, false)
                }
                SystemClock.sleep(60)
            }
            SystemClock.sleep(700)
            instrumentation.runOnMainSync {
                val strip = KeyboardSwitcher.getInstance().mainKeyboardView.rootView.findViewById<ViewGroup>(helium314.keyboard.latin.R.id.suggestion_strip_view)
                fun words(view: View): List<String> = if (view is TextView) listOf(view.text.toString().lowercase())
                    else if (view is ViewGroup) (0 until view.childCount).flatMap { words(view.getChildAt(it)) } else emptyList()
                val shown = words(strip)
                android.util.Log.i("StillSuggestionTest", "visible $typo -> $shown")
                assertTrue("$typo must offer $expected in the visible strip: $shown", expected in shown)
            }
        }
    }
}
