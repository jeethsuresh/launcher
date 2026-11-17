package app.still.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/** Native animators already apply the user's scale; do not multiply it twice. */
object GlyphMotion {
    const val PRESS = 80L
    const val RELEASE = 180L
    const val PAGE = 220L
    fun enabled() = ValueAnimator.areAnimatorsEnabled()

    fun arrive(view: View, distance: Float = view.context.dp(18).toFloat()) {
        view.animate().cancel()
        if (!enabled()) { view.alpha = 1f; view.translationY = 0f; return }
        view.alpha = .65f; view.translationY = distance
        view.animate().alpha(1f).translationY(0f).setDuration(RELEASE)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun finishTree(view: View) {
        when (view) {
            is TactileSurface -> view.finishMotion()
            is WorkspacePager -> view.finishMotion()
            is PullDownDrawerLayout -> view.finishMotion()
        }
        if (view is ViewGroup) {
            (0 until view.childCount).mapNotNull { view.getChildAt(it) }.forEach(::finishTree)
        }
    }
}

/** One observer per activity, not one observer per icon. */
class GlyphMotionSettings(private val context: Context, private val changed: () -> Unit) {
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { changed() }
    }
    fun start() {
        if (observing) return
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        observing = true
        changed()
    }
    fun stop() {
        if (!observing) return
        context.contentResolver.unregisterContentObserver(observer)
        observing = false
    }
}

/** Only the icon/art moves. Its parent remains the same-sized input target. */
open class TactileSurface(context: Context) : FrameLayout(context) {
    private var targetPressed = false
    init {
        isDuplicateParentStateEnabled = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),
                rounded(0x22315d49, context.dp(14).toFloat(), 0xff719c87.toInt()))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, context.dp(14).toFloat()))
        }
    }
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val pressed = drawableState.contains(android.R.attr.state_pressed)
        if (pressed == targetPressed) return
        targetPressed = pressed
        animate().cancel()
        if (!GlyphMotion.enabled()) { finishMotion(); return }
        val scale = if (pressed) .93f else 1f
        animate().scaleX(scale).scaleY(scale)
            .setDuration(if (pressed) GlyphMotion.PRESS else GlyphMotion.RELEASE)
            .setInterpolator(DecelerateInterpolator()).start()
    }
    fun finishMotion() { animate().cancel(); scaleX = 1f; scaleY = 1f }
    override fun onDetachedFromWindow() { finishMotion(); super.onDetachedFromWindow() }
}
