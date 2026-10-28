package helium314.keyboard.latin.still

import org.junit.Assert.*
import org.junit.Test

class OneTimeCodesTest {
    @Test fun extractsNumericAndAlphanumericCodesWithoutLosingZeroes() {
        assertEquals(listOf("001234"), OneTimeCodes.fromMessage("Your verification code is 001234."))
        assertEquals(listOf("a2B4c6D8"), OneTimeCodes.fromMessage("Use a2B4c6D8 to verify your login"))
        assertEquals(listOf("ABCD"), OneTimeCodes.fromMessage("Your one-time code: ABCD"))
        assertEquals(listOf("A1b2"), OneTimeCodes.fromMessage("A1b2"))
    }

    @Test fun rejectsUnrelatedWordsLongTokensDatesAndLinks() {
        for (text in listOf("Meet me at 1234", "Security code 123", "OTP 123456789", "Your login code is https://example.com/A1b2", "code on 2026-10-27")) {
            assertEquals(text, emptyList<String>(), OneTimeCodes.fromMessage(text))
        }
    }

    @Test fun clipboardOnlyAcceptsAnEntireShortCode() {
        assertEquals("012345", OneTimeCodes.fromClipboard("012345"))
        assertEquals("ABCD", OneTimeCodes.fromClipboard("ABCD"))
        assertNull(OneTimeCodes.fromClipboard("Your code is 1234"))
        assertNull(OneTimeCodes.fromClipboard("password"))
        assertNull(OneTimeCodes.fromClipboard("123456789"))
        assertNull(OneTimeCodes.fromClipboard("123-456"))
    }

    @Test fun expiresAtThirtySecondsAndRejectsFutureAndMissingTimestamps() {
        assertTrue(OneTimeCodes.isRecent(1_000, 30_999))
        assertFalse(OneTimeCodes.isRecent(1_000, 31_000))
        assertFalse(OneTimeCodes.isRecent(0, 1))
        assertFalse(OneTimeCodes.isRecent(2_000, 1_000))
    }

    @Test fun receivingAnotherCodeDoesNotRefreshOldCodes() {
        val store = RecentCodeStore()
        store.add(listOf("000001"), 1_000)
        store.add(listOf("AB12"), 20_000)
        assertEquals(listOf("AB12"), store.current(31_000).map { it.code })
        assertTrue(store.current(50_000).isEmpty())
    }

    @Test fun deduplicatesBoundsAndClearsMemory() {
        val store = RecentCodeStore()
        store.add(listOf("1111", "2222", "3333", "4444"), 1_000)
        assertEquals(3, store.current(1_001).size)
        store.add(listOf("4444"), 2_000)
        assertEquals(1, store.current(2_001).count { it.code == "4444" })
        store.clear()
        assertTrue(store.current(2_002).isEmpty())
    }
}
