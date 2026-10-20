package app.still.launcher

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.graphics.Bitmap
import helium314.keyboard.keyboard.KeyboardSwitcher

/** Run on an emulator with Still Keyboard selected. Exercises real continuous touch strokes. */
@Suppress("DEPRECATION")
class KeyboardIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun testSymbolsWorkAfterEmojiSearch() {
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
        SystemClock.sleep(1500)
        instrumentation.runOnMainSync {
            val switcher = KeyboardSwitcher.getInstance()
            val search = switcher.mainKeyboardView.rootView.findViewById<helium314.keyboard.latin.still.StillEmojiSearchView>(helium314.keyboard.latin.R.id.still_emoji_search)
            val code = helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
            switcher.onEvent(helium314.keyboard.event.Event.createSoftwareKeypressEvent(code.EMOJI,0,0,0,false),0,0)
            search.start()
            "cat".forEach { search.handle(it.code) }
            assertEquals("Search queries must not enter the client", "", editor.text.toString())
            search.stop()
            assertTrue(switcher.keyboard!!.mId.isAlphabetKeyboard)
            switcher.onPressKey(code.SYMBOL_ALPHA,true,0,0)
            switcher.onReleaseKey(code.SYMBOL_ALPHA,false,0,0)
            assertFalse("Symbols must remain reachable after search", switcher.keyboard!!.mId.isAlphabetKeyboard)
            switcher.onPressKey(code.SYMBOL_ALPHA,true,0,0)
            switcher.onReleaseKey(code.SYMBOL_ALPHA,false,0,0)
            assertTrue(switcher.keyboard!!.mId.isAlphabetKeyboard)
            activity.finish()
        }
    }
    @Test
    fun testContinuousSwipes() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, KeyboardSetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var editor: EditText
        instrumentation.runOnMainSync {
            fun find(view: View): EditText? {
                if (view is EditText && view.contentDescription == "Keyboard test field") return view
                if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            editor = find(activity.window.decorView)!!
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        SystemClock.sleep(3500)
        val automation = instrumentation.uiAutomation
        val failures = mutableListOf<String>()
        for (word in listOf("hello", "world", "keyboard", "swipe", "great", "phone", "there", "thanks", "tomorrow", "happy")) {
            instrumentation.runOnMainSync { editor.setText("") }
            SystemClock.sleep(700)
            val points = mutableListOf<Pair<Float, Float>>()
            instrumentation.runOnMainSync {
                val view = KeyboardSwitcher.getInstance().mainKeyboardView
                assertNotNull("Select Still Keyboard before running this test", view)
                val location = IntArray(2); view.getLocationOnScreen(location)
                val keys = view.keyboard!!.sortedKeys
                assertTrue("Keyboard must be displayed", view.isShown && view.height > 0)
                android.util.Log.i("StillKeyboardTest", "view ${location.toList()}, size ${view.width}x${view.height}, padding ${view.paddingLeft},${view.paddingTop}")
                word.forEachIndexed { i, char ->
                    if (i == 0 || char != word[i-1]) {
                        val key = keys.first { Character.toLowerCase(it.code) == char.code }
                        points += (location[0]+view.paddingLeft+key.x+key.width/2f) to
                            (location[1]+view.paddingTop+key.y+key.height/2f)
                    }
                }
            }
            val down = SystemClock.uptimeMillis()
            fun event(action: Int, point: Pair<Float, Float>) {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
                e.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(automation.injectInputEvent(e, true))
                e.recycle()
            }
            event(MotionEvent.ACTION_DOWN, points.first())
            points.zipWithNext().forEach { (a,b) ->
                for (i in 1..10) {
                    SystemClock.sleep(8)
                    event(MotionEvent.ACTION_MOVE, (a.first+(b.first-a.first)*i/10) to (a.second+(b.second-a.second)*i/10))
                }
            }
            event(MotionEvent.ACTION_UP, points.last())
            var actual = ""
            val deadline = SystemClock.uptimeMillis() + 5000
            do {
                SystemClock.sleep(100)
                instrumentation.runOnMainSync { actual = editor.text.toString().trim().lowercase() }
            } while (actual.isEmpty() && SystemClock.uptimeMillis() < deadline)
            android.util.Log.i("StillKeyboardTest", "$word -> $actual")
            if (word != actual) {
                failures += "$word -> $actual"
                automation.takeScreenshot()?.let { shot ->
                    java.io.File(instrumentation.targetContext.cacheDir,"swipe-$word.png").outputStream().use {
                        shot.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                }
            }
        }
        instrumentation.runOnMainSync { activity.finish() }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }
}
