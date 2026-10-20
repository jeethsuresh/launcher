package helium314.keyboard.latin.still

import org.junit.Assert.*
import org.junit.Test

class EmojiSearchIndexTest {
    private val grin = SearchEmoji("😀", "grinning face", listOf("happy", "smile", "face"))
    private val index = EmojiSearchIndex(listOf(grin,
        SearchEmoji("🥲", "smiling face with tear", listOf("happy", "sad", "tear")),
        SearchEmoji("☕", "hot beverage", listOf("coffee", "café", "tea")),
        SearchEmoji("🐈", "cat", listOf("animal", "pet"))))

    @Test fun searchesNamesKeywordsAndMultipleTerms() {
        assertEquals(listOf(grin), index.search("grinning"))
        assertEquals(2, index.search("happy").size)
        assertEquals("🥲", index.search("happy tear").single().emoji)
        assertEquals("☕", index.search("CAFE").single().emoji)
        assertEquals("🐈", index.search("cat").first().emoji)
    }
    @Test fun emptyAndUnknownQueriesAreSafe() {
        assertEquals(listOf(grin),index.search(" "))
        assertTrue(index.search("doesnotexist").isEmpty())
        assertTrue(index.search("happy",0).isEmpty())
    }
}
