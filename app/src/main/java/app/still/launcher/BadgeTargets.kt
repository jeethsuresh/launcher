package app.still.launcher

/** Package identity includes the Android profile, so work and personal apps never share dots. */
data class BadgeTarget(val packageName: String, val userSerial: Long)

data class BadgeNotification(
    val target: BadgeTarget,
    val canShowBadge: Boolean,
    val ongoing: Boolean,
    val groupSummary: Boolean,
)

/** Dots describe active notifications, not unread-message counts. */
fun badgeTargets(notifications: List<BadgeNotification>): Set<BadgeTarget> = notifications
    .filter { it.canShowBadge && !it.ongoing && !it.groupSummary }
    .map { it.target }
    .toSet()
