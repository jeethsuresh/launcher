// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.DictionaryCollection
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.utils.SuggestionResults
import java.util.WeakHashMap

/** Adapts HeliBoard's gesture events to the bundled, source-available decoder. */
object StillGestureDecoder {
    private data class Cached(val signature: Int, val decoder: SwipeDecoder)
    private val decoders = WeakHashMap<DictionaryCollection, Cached>()

    @Synchronized
    fun suggest(dictionary: DictionaryCollection, keyboard: Keyboard, input: InputPointers, beginningOfSentence: Boolean): SuggestionResults {
        val result = SuggestionResults(SuggestedWords.MAX_SUGGESTIONS, beginningOfSentence, true)
        if (input.pointerSize < 2) return result
        val keys = keyboard.sortedKeys.filter { Character.isLetter(it.code) && it.code <= Char.MAX_VALUE.code }
        if (keys.isEmpty()) return result
        val geometry = keys.map { SwipeKey(it.code.toChar().lowercaseChar(), SwipePoint(it.x+it.width/2f, it.y+it.height/2f)) }
        val signature = geometry.hashCode()
        val decoder = decoders[dictionary]?.takeIf { it.signature == signature }?.decoder ?: run {
            val width = keys.map { it.width }.average().toFloat().coerceAtLeast(1f)
            SwipeDecoder(dictionary.gestureWords, geometry, width).also { decoders[dictionary] = Cached(signature, it) }
        }
        val pointerId = input.pointerIds[0]
        val points = (0 until input.pointerSize).filter { input.pointerIds[it] == pointerId }
            .map { SwipePoint(input.xCoordinates[it].toFloat(), input.yCoordinates[it].toFloat()) }
        val matches = decoder.decode(points)
        matches.forEach { match ->
            result.add(SuggestedWordInfo(match.word, "", (1_000_000-match.cost*50_000).toInt(),
                SuggestedWordInfo.KIND_CORRECTION, dictionary, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
        return result
    }
}
