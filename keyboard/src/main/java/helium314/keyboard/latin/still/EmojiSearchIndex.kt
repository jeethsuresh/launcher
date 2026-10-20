// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import java.text.Normalizer
import java.util.Locale

data class SearchEmoji(val emoji: String, val name: String, val keywords: List<String>)

/** CLDR names and keywords, searched locally without passing the query to an editor. */
class EmojiSearchIndex(entries: List<SearchEmoji>) {
    private data class Indexed(val entry: SearchEmoji, val name: String, val keywords: List<String>, val words: String)
    private val indexed = entries.map {
        val name = normalize(it.name)
        val keywords = it.keywords.map(::normalize)
        Indexed(it, name, keywords, name + " " + keywords.joinToString(" "))
    }
    fun search(query: String, limit: Int = 48): List<SearchEmoji> {
        if (limit <= 0) return emptyList()
        val normalized = normalize(query).trim()
        if (normalized.isEmpty()) {
            val common = listOf("😀", "😂", "❤️", "👍", "🥰", "🎉", "🙏", "🔥", "😊", "👋", "✅", "✨")
            return common.mapNotNull { emoji -> indexed.firstOrNull { it.entry.emoji == emoji }?.entry }.take(limit)
        }
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        return indexed.asSequence().filter { candidate -> tokens.all { it in candidate.words } }
            .sortedBy { candidate ->
                when {
                    candidate.name == normalized -> 0
                    normalized in candidate.keywords -> 1
                    candidate.name.startsWith(normalized) -> 2
                    else -> 3
                }
            }.map { it.entry }.take(limit).toList()
    }

    companion object {
        private fun normalize(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { Character.getType(it) !in 6..8 }
    }
}
