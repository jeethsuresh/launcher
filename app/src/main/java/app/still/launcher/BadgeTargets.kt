package app.still.launcher

/** Package identity includes the Android profile, so work and personal counts stay separate. */
data class BadgeTarget(val packageName: String, val userSerial: Long)

data class BadgeNotification(
    val target: BadgeTarget,
    val canShowBadge: Boolean,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val number: Int = 0,
)

/** Android's optional number represents multiple items; an unspecified count contributes one.
 * Rebuild from the current snapshot so replacements and removals cannot accumulate stale counts. */
fun badgeCounts(notifications: List<BadgeNotification>): Map<BadgeTarget, Int> {
    val counts = mutableMapOf<BadgeTarget, Int>()
    notifications.filter { it.canShowBadge && !it.ongoing && !it.groupSummary }.forEach {
        counts[it.target] = addBadgeCounts(counts[it.target] ?: 0, it.number.coerceAtLeast(1))
    }
    return counts
}

/** A folder can contain multiple activities for the same app; count each package/profile once. */
fun badgeCountFor(counts: Map<BadgeTarget, Int>, targets: Set<BadgeTarget>): Int =
    targets.fold(0) { total, target -> addBadgeCounts(total, counts[target] ?: 0) }

private fun addBadgeCounts(first: Int, second: Int): Int =
    (first.toLong() + second.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

fun badgeLabel(count: Int): String = when {
    count <= 0 -> ""
    count > 99 -> "99+"
    else -> count.toString()
}

fun badgeDescription(count: Int): String = when {
    count <= 0 -> ""
    count == 1 -> ", 1 notification"
    else -> ", $count notifications"
}
