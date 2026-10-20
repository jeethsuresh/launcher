package helium314.keyboard.latin.still

import org.junit.Assert.*
import org.junit.Test

class SwipeDecoderTest {
    private val keys = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMapIndexed { row, letters ->
        letters.mapIndexed { col, c -> SwipeKey(c, SwipePoint((col + listOf(0f, .5f, 1.5f)[row])*100f, row*100f)) }
    }
    private val words = listOf("hello", "help", "held", "world", "word", "would", "keyboard", "keyboards", "swipe", "swipes", "great", "grate", "phone", "photo", "there", "their", "these", "thanks", "thank", "tomorrow", "today", "happy", "home", "hope")
    private val decoder = SwipeDecoder(words.map { SwipeWord(it, 150) }, keys, 100f)
    private fun path(word: String) = word.map { letter -> keys.first { it.letter == letter }.center }.distinctUntilChanged()
    private fun List<SwipePoint>.distinctUntilChanged() = fold(mutableListOf<SwipePoint>()) { out, p -> out.apply { if (lastOrNull() != p) add(p) } }

    @Test fun variedPathsRankIntendedWordsFirst() {
        listOf("hello", "world", "keyboard", "swipe", "great", "phone", "there", "thanks", "tomorrow", "happy").forEach { word ->
            assertEquals(word, word, decoder.decode(path(word)).first().word)
        }
    }

    @Test fun toleratesUnevenSamplingAndTouchOffset() {
        val sampled = path("keyboard").zipWithNext().flatMap { (a,b) ->
            (0..10).map { n -> SwipePoint(a.x+(b.x-a.x)*n/10+11, a.y+(b.y-a.y)*n/10-8) }
        }
        assertEquals("keyboard", decoder.decode(sampled).first().word)
    }

    @Test fun rejectsTapsAndNonFiniteInput() {
        assertTrue(decoder.decode(listOf(SwipePoint(1f,1f),SwipePoint(2f,2f))).isEmpty())
        assertTrue(decoder.decode(listOf(SwipePoint(Float.NaN,0f))).isEmpty())
        assertTrue(decoder.decode(emptyList()).isEmpty())
    }

    @Test fun differentGeometryChangesMatching() {
        val shifted = keys.map { it.copy(center = SwipePoint(it.center.x*.7f+230f, it.center.y*1.2f+80f)) }
        val other = SwipeDecoder(words.map { SwipeWord(it,150) },shifted,70f)
        assertEquals("tomorrow", other.decode("tomorrow".map { c -> shifted.first { it.letter == c }.center }).first().word)
    }
}
