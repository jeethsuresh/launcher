package app.still.launcher

import org.junit.Assert.*
import org.junit.Test

class BadgeTargetsTest {
    private val personal = BadgeTarget("chat.app", 0)
    private val work = BadgeTarget("chat.app", 10)
    private fun entry(target: BadgeTarget = personal, eligible: Boolean = true, ongoing: Boolean = false, summary: Boolean = false) =
        BadgeNotification(target, eligible, ongoing, summary)

    @Test fun groupsDoNotDuplicateDotsAndProfilesStaySeparate() {
        assertEquals(setOf(personal, work), badgeTargets(listOf(entry(), entry(), entry(work), entry(summary = true))))
        assertEquals(setOf(work), badgeTargets(listOf(entry(work))))
    }

    @Test fun channelAndRankingRulesOngoingAndSummariesAreExcluded() {
        assertTrue(badgeTargets(listOf(entry(eligible = false), entry(ongoing = true), entry(summary = true))).isEmpty())
        assertEquals(setOf(personal), badgeTargets(listOf(entry(summary = true), entry())))
    }

    @Test fun removingLastNotificationOrDisconnectingClearsDot() {
        assertEquals(setOf(personal), badgeTargets(listOf(entry(), entry())))
        assertEquals(setOf(personal), badgeTargets(listOf(entry())))
        assertTrue(badgeTargets(emptyList()).isEmpty())
    }
}
