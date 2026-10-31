package app.still.launcher

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.FrameLayout

/** Process-local state only. No notification text, history, or layout changes are retained. */
object NotificationBadges {
    private var targets = emptySet<BadgeTarget>()
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
        if (!hasAccess(context)) update(emptySet())
        else if (!connected) runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    fun containsAny(entries: Set<BadgeTarget>) = entries.any { it in targets }
    fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    internal fun update(value: Set<BadgeTarget>) {
        if (targets == value) return
        targets = value
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
        NotificationBadges.update(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh(rankingMap)

    private fun refresh(rankings: RankingMap? = null) {
        if (!listening) return
        if (!NotificationBadges.hasAccess(this) || Settings.Secure.getInt(contentResolver, "notification_badging", 1) == 0) {
            NotificationBadges.update(emptySet())
            return
        }
        // Rebuild from Android's authoritative snapshot, including after reconnect or ranking changes.
        // A failed/withdrawn connection must never leave stale dots on screen.
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
                    sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            }
        }.getOrDefault(emptyList())
        NotificationBadges.update(badgeTargets(entries))
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(settingsChanges)
        onListenerDisconnected()
        super.onDestroy()
    }
}

/** Updates only the dot and accessible name, preserving scroll, search, widgets, and active drags. */
class BadgedIcon(context: Context, private val targets: Set<BadgeTarget>, private val changed: (Boolean) -> Unit) : FrameLayout(context) {
    var hasBadge = false
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val observer: () -> Unit = {
        hasBadge = NotificationBadges.containsAny(targets)
        changed(hasBadge)
        invalidate()
    }

    init { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); NotificationBadges.observe(observer) }
    override fun onDetachedFromWindow() { NotificationBadges.removeObserver(observer); super.onDetachedFromWindow() }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!hasBadge) return
        val radius = context.dp(5).toFloat()
        val x = width - radius - context.dp(1)
        val y = radius + context.dp(1)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, radius + context.dp(1), paint)
        paint.color = 0xffd83c50.toInt()
        canvas.drawCircle(x, y, radius, paint)
    }
}
