package app.still.launcher

import org.junit.Assert.*
import org.junit.Test

class BadgeTargetsTest {
    private val personal = BadgeTarget("chat.app", 0)
    private val work = BadgeTarget("chat.app", 10)
    private fun entry(target: BadgeTarget = personal, eligible: Boolean = true, ongoing: Boolean = false, summary: Boolean = false, number: Int = 0) =
        BadgeNotification(target, eligible, ongoing, summary, number)

    @Test fun reportedCountsAndFallbacksKeepProfilesSeparate() {
        assertEquals(mapOf(personal to 8, work to 2), badgeCounts(listOf(
            entry(number = 7), entry(), entry(work, number = 2), entry(summary = true, number = 100),
        )))
        assertEquals(mapOf(work to 2), badgeCounts(listOf(entry(work, number = 2))))
    }

    @Test fun channelAndRankingRulesOngoingAndSummariesAreExcluded() {
        assertTrue(badgeCounts(listOf(entry(eligible = false, number = 99), entry(ongoing = true, number = 99), entry(summary = true, number = 99))).isEmpty())
        assertEquals(mapOf(personal to 1), badgeCounts(listOf(entry(summary = true, number = 99), entry())))
    }

    @Test fun replacementsAndRemovalsUseTheCurrentSnapshot() {
        assertEquals(mapOf(personal to 8), badgeCounts(listOf(entry(number = 7), entry())))
        assertEquals(mapOf(personal to 3), badgeCounts(listOf(entry(number = 2), entry())))
        assertEquals(mapOf(personal to 1), badgeCounts(listOf(entry())))
        assertTrue(badgeCounts(emptyList()).isEmpty())
    }

    @Test fun missingAndInvalidNumbersContributeOneWithoutOverflow() {
        assertEquals(mapOf(personal to 3), badgeCounts(listOf(entry(), entry(number = -1), entry(number = Int.MIN_VALUE))))
        assertEquals(mapOf(personal to Int.MAX_VALUE), badgeCounts(listOf(entry(number = Int.MAX_VALUE), entry(number = 7))))
    }

    @Test fun foldersSumEachAppAndProfileOnce() {
        val mail = BadgeTarget("mail.app", 0)
        val counts = mapOf(personal to 7, work to 2, mail to 3)
        assertEquals(10, badgeCountFor(counts, listOf(personal, personal, mail).toSet()))
        assertEquals(9, badgeCountFor(counts, setOf(personal, work)))
        assertEquals(0, badgeCountFor(counts, emptySet()))
        assertEquals(Int.MAX_VALUE, badgeCountFor(mapOf(personal to Int.MAX_VALUE, mail to 1), setOf(personal, mail)))
    }

    @Test fun labelsCapOnlyTheVisualCountAndKeepAccessibleCounts() {
        assertEquals("", badgeLabel(0))
        assertEquals("1", badgeLabel(1))
        assertEquals("99", badgeLabel(99))
        assertEquals("99+", badgeLabel(100))
        assertEquals("99+", badgeLabel(Int.MAX_VALUE))
        assertEquals("", badgeDescription(0))
        assertEquals(", 1 notification", badgeDescription(1))
        assertEquals(", 105 notifications", badgeDescription(105))
    }
}
