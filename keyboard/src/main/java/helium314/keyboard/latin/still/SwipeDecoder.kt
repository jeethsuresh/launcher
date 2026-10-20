// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Still Launcher contributors
package helium314.keyboard.latin.still

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SwipePoint(val x: Float, val y: Float)
data class SwipeKey(val letter: Char, val center: SwipePoint)
data class SwipeWord(val text: String, val frequency: Int)
data class SwipeMatch(val word: String, val cost: Float)

/** Offline frequency-weighted path matching. Compact arrays avoid millions of point allocations. */
class SwipeDecoder(words: List<SwipeWord>, keys: List<SwipeKey>, private val keyWidth: Float) {
    private data class Shape(val word: SwipeWord, val path: FloatArray, val length: Float)
    private val centers = keys.associate { it.letter.lowercaseChar() to it.center }
    private val shapes = words.asSequence()
        .filter { it.text.length in 2..28 && it.frequency >= 0 }
        .sortedByDescending { it.frequency }.distinctBy { it.text.lowercase(Locale.ROOT) }.take(60_000)
        .mapNotNull { word ->
            val lower = word.text.lowercase(Locale.ROOT)
            // Most English entries are ASCII; avoid a native normalization call for each one.
            val normalized = if (lower.all { it.code < 128 }) lower else Normalizer.normalize(lower, Normalizer.Form.NFD)
            val letters = normalized.filter { it.isLetter() }
            if (letters.length < 2 || letters.any { it !in centers }) return@mapNotNull null
            val points = ArrayList<SwipePoint>(letters.length)
            for (letter in letters) {
                val point = centers.getValue(letter)
                if (points.lastOrNull() != point) points.add(point)
            }
            val length = length(points)
            if (length < keyWidth * .35f) null else Shape(word, resample(points), length)
        }.toList()

    fun decode(rawPoints: List<SwipePoint>, limit: Int = 8): List<SwipeMatch> {
        val points = rawPoints.filter { it.x.isFinite() && it.y.isFinite() }
        if (points.size < 2 || keyWidth <= 0 || limit <= 0) return emptyList()
        val length = length(points)
        if (length < keyWidth * .65f) return emptyList()
        val observed = resample(points)
        val scale = keyWidth * keyWidth
        val endpointLimit = scale * 1.4f * 1.4f
        val last = (SAMPLES-1)*2
        val candidates = shapes.asSequence().filter {
            squared(it.path, 0, observed, 0) < endpointLimit && squared(it.path, last, observed, last) < endpointLimit
        }.map { shape ->
            val endpoint = squared(shape.path, 0, observed, 0) + squared(shape.path, last, observed, last)
            var aligned = 0f
            for (i in 0 until SAMPLES) aligned += squared(observed, i*2, shape.path, i*2)
            aligned /= SAMPLES
            val lengthPenalty = abs(ln((length / shape.length).coerceAtLeast(.01f)))
            shape to ((aligned + endpoint*.8f)/scale + lengthPenalty*.18f)
        }.sortedBy { it.second }.take(96).toList()
        return candidates.map { (shape, coarse) ->
            val warp = warpDistance(observed, shape.path) / scale
            val frequencyPenalty = (255-shape.word.frequency.coerceIn(0, 255))/255f
            SwipeMatch(shape.word.text, warp*.70f + coarse*.30f + frequencyPenalty*.34f)
        }.filter { it.cost < 3.8f }.sortedBy { it.cost }.take(limit)
    }

    private fun warpDistance(a: FloatArray, b: FloatArray): Float {
        var previous = FloatArray(SAMPLES+1) { Float.POSITIVE_INFINITY }
        var current = FloatArray(SAMPLES+1) { Float.POSITIVE_INFINITY }
        previous[0] = 0f
        for (i in 1..SAMPLES) {
            current.fill(Float.POSITIVE_INFINITY)
            for (j in max(1, i-6)..min(SAMPLES, i+6)) {
                current[j] = squared(a, (i-1)*2, b, (j-1)*2) + min(previous[j-1], min(previous[j], current[j-1]))
            }
            val swap = previous; previous = current; current = swap
        }
        return previous[SAMPLES]/SAMPLES
    }

    companion object {
        private const val SAMPLES = 32
        private fun squared(a: FloatArray, i: Int, b: FloatArray, j: Int): Float {
            val dx = a[i]-b[j]; val dy = a[i+1]-b[j+1]
            return dx*dx+dy*dy
        }
        private fun distance(a: SwipePoint, b: SwipePoint): Float {
            val dx = a.x-b.x; val dy = a.y-b.y
            return sqrt(dx*dx+dy*dy)
        }
        private fun length(points: List<SwipePoint>): Float {
            var result = 0f
            for (i in 1 until points.size) result += distance(points[i-1], points[i])
            return result
        }
        private fun resample(points: List<SwipePoint>): FloatArray {
            val result = FloatArray(SAMPLES*2)
            val cumulative = FloatArray(points.size)
            for (i in 1 until points.size) cumulative[i] = cumulative[i-1]+distance(points[i-1], points[i])
            var segment = 1
            for (index in 0 until SAMPLES) {
                val target = cumulative.last()*index/(SAMPLES-1)
                while (segment < points.lastIndex && cumulative[segment] < target) segment++
                val previous = (segment-1).coerceAtLeast(0)
                val current = segment.coerceAtMost(points.lastIndex)
                val segmentLength = cumulative[current]-cumulative[previous]
                val fraction = if (segmentLength == 0f) 0f else (target-cumulative[previous])/segmentLength
                val a = points[previous]; val b = points[current]
                result[index*2] = a.x+(b.x-a.x)*fraction
                result[index*2+1] = a.y+(b.y-a.y)*fraction
            }
            return result
        }
    }
}
