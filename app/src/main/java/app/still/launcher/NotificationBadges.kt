package app.still.launcher

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Process-local state only. No notification text, history, or layout changes are retained. */
object NotificationBadges {
    private var counts = emptyMap<BadgeTarget, Int>()
    private val listeners = mutableSetOf<() -> Unit>()
    var connected = false
        internal set

    fun component(context: Context) = ComponentName(context, NotificationBadgesService::class.java)
    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
    } else {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component(context) } == true
    }

    fun refreshAccess(context: Context) {
        if (!hasAccess(context)) update(emptyMap())
        else if (!connected) runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    fun countFor(entries: Set<BadgeTarget>) = badgeCountFor(counts, entries)
    fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    internal fun update(value: Map<BadgeTarget, Int>) {
        if (counts == value) return
        counts = value
        listeners.toList().forEach { it() }
    }
}

/** Android binds this service only after the user explicitly grants notification access. */
class NotificationBadgesService : NotificationListenerService() {
    private var listening = false
    private val settingsChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("notification_badging"), false, settingsChanges)
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("enabled_notification_listeners"), false, settingsChanges)
    }

    override fun onListenerConnected() {
        listening = true
        NotificationBadges.connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        listening = false
        NotificationBadges.connected = false
        NotificationBadges.update(emptyMap())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh(rankingMap)

    private fun refresh(rankings: RankingMap? = null) {
        if (!listening) return
        if (!NotificationBadges.hasAccess(this) || Settings.Secure.getInt(contentResolver, "notification_badging", 1) == 0) {
            NotificationBadges.update(emptyMap())
            return
        }
        // Rebuild from Android's authoritative snapshot, including after reconnect or ranking changes.
        // A failed/withdrawn connection must never leave stale counts on screen.
        val entries = runCatching {
            val users = getSystemService(UserManager::class.java)
            val currentRankings = rankings ?: currentRanking
            activeNotifications.orEmpty().mapNotNull { sbn ->
                val serial = users.getSerialNumberForUser(sbn.user)
                if (serial < 0) return@mapNotNull null
                val ranking = Ranking()
                val eligible = currentRankings.getRanking(sbn.key, ranking) && ranking.canShowBadge() &&
                    ranking.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE == 0
                BadgeNotification(BadgeTarget(sbn.packageName, serial), eligible, sbn.isOngoing,
                    sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, sbn.notification.number)
            }
        }.getOrDefault(emptyList())
        NotificationBadges.update(badgeCounts(entries))
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(settingsChanges)
        onListenerDisconnected()
        super.onDestroy()
    }
}

/** Updates only the count and accessible name, preserving scroll, search, widgets, and active drags. */
class BadgedIcon(context: Context, private val targets: Set<BadgeTarget>, private val changed: (Int) -> Unit) : TactileSurface(context) {
    var badgeCount = 0
        private set
    val hasBadge get() = badgeCount > 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val observer: () -> Unit = {
        badgeCount = NotificationBadges.countFor(targets)
        changed(badgeCount)
        invalidate()
    }

    init { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); NotificationBadges.observe(observer) }
    override fun onDetachedFromWindow() { NotificationBadges.removeObserver(observer); super.onDetachedFromWindow() }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!hasBadge) return
        val margin = context.dp(1).toFloat()
        val outline = context.dp(2).toFloat()
        // A centered icon can extend into the tile's top padding on short home grid rows.
        val tile = parent as? ViewGroup
        val clippedTop = if (tile?.clipToPadding == true) (tile.paddingTop - top).coerceAtLeast(0) else 0
        val badgeTop = margin + clippedTop
        val availableWidth = width - margin * 2
        val badgeHeight = minOf(context.dp(24).toFloat(), height - badgeTop - margin, availableWidth)
        if (badgeHeight <= outline * 2) return
        val text = badgeLabel(badgeCount)
        val padding = context.dp(4).toFloat()
        paint.textSize = context.dp(15).toFloat()
        val maxTextWidth = (availableWidth - padding * 2).coerceAtLeast(1f)
        val textWidth = paint.measureText(text)
        if (textWidth > maxTextWidth) paint.textSize *= maxTextWidth / textWidth
        val badgeWidth = maxOf(badgeHeight, paint.measureText(text) + padding * 2).coerceAtMost(availableWidth)
        val right = width - margin
        val left = right - badgeWidth
        val bottom = badgeTop + badgeHeight
        val radius = badgeHeight / 2
        paint.color = Color.WHITE
        canvas.drawRoundRect(left, badgeTop, right, bottom, radius, radius, paint)
        paint.color = 0xffb51e3c.toInt()
        val innerRadius = radius - outline
        canvas.drawRoundRect(left + outline, badgeTop + outline, right - outline, bottom - outline,
            innerRadius, innerRadius, paint)
        paint.color = Color.WHITE
        val metrics = paint.fontMetrics
        canvas.drawText(text, (left + right) / 2, badgeTop + (badgeHeight - metrics.ascent - metrics.descent) / 2, paint)
    }
}
