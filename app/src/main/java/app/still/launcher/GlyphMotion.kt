package app.still.launcher

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.graphics.Rect
import java.util.WeakHashMap
import java.util.Collections
import kotlin.math.hypot
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/** Native animators already apply the user's scale; do not multiply it twice. */
object GlyphMotion {
    const val PRESS = 80L
    const val RELEASE = 180L
    const val PAGE = 220L
    fun enabled() = ValueAnimator.areAnimatorsEnabled()
    private val active = Collections.newSetFromMap(WeakHashMap<TactileSurface, Boolean>())
    internal fun tracking(surface: TactileSurface, pressed: Boolean) {
        if (pressed) active.add(surface) else active.remove(surface)
    }
    fun cancelPresses() = active.toList().forEach { it.cancelPress() }
    fun preparePresses() = active.toList().forEach { it.finishMotion() }
    fun cancelOutside(x: Int, y: Int) = active.toList().forEach { surface ->
        val bounds = Rect()
        val target = surface.parent as? View ?: surface
        if (!target.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) surface.cancelPress()
    }

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

/** Observes native dispatch without consuming events or replacing click/long-click. */
class GlyphPressObserver(context: Context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var pointer = -1
    private var x = 0f
    private var y = 0f
    fun observe(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                GlyphMotion.preparePresses()
                pointer = event.getPointerId(0); x = event.rawX; y = event.rawY
            }
            MotionEvent.ACTION_MOVE -> if (event.findPointerIndex(pointer) == 0) {
                if (hypot(event.rawX - x, event.rawY - y) > slop) GlyphMotion.cancelPresses()
                else GlyphMotion.cancelOutside(event.rawX.toInt(), event.rawY.toInt())
            }
            MotionEvent.ACTION_CANCEL -> { GlyphMotion.cancelPresses(); pointer = -1 }
            MotionEvent.ACTION_UP -> pointer = -1
        }
    }
}

/** Only the icon/art moves. Its parent remains the same-sized input target. */
open class TactileSurface(context: Context) : FrameLayout(context) {
    private var targetPressed = false
    private var cancelled = false
    private var phase = 0f
    private var generation = 0
    private var motion: ValueAnimator? = null
    init {
        isDuplicateParentStateEnabled = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),
                rounded(0x22315d49, context.dp(14).toFloat(), 0xff719c87.toInt()))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, context.dp(14).toFloat()))
        }
    }
    private fun pose(energy: Float) {
        phase = energy
        val pose = IconDeformation.at(energy)
        scaleX = pose.x; scaleY = pose.y
        rotation = pose.rotation
        translationY = context.dp(2) * energy
    }
    private fun moveTo(value: Float, duration: Long, next: (() -> Unit)? = null) {
        val token = ++generation
        motion?.cancel()
        val animator = ValueAnimator.ofFloat(phase, value)
        motion = animator
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { pose(it.animatedValue as Float) }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (token == generation) { motion = null; next?.invoke() }
            }
        })
        animator.start()
    }
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val pressed = drawableState.contains(android.R.attr.state_pressed)
        if (pressed == targetPressed) return
        targetPressed = pressed
        GlyphMotion.tracking(this, pressed)
        if (!GlyphMotion.enabled()) { finishMotion(); return }
        if (pressed) {
            if (cancelled) return
            moveTo(.7f, GlyphMotion.PRESS) {
                if (targetPressed && !cancelled) moveTo(1f, 150)
            }
        } else {
            if (cancelled) { cancelled = false; moveTo(0f, 100) }
            else moveTo(-.62f, 100) { moveTo(0f, 220) }
        }
    }
    fun cancelPress() {
        cancelled = true
        GlyphMotion.tracking(this, false)
        if (GlyphMotion.enabled()) moveTo(0f, 100) else finishMotion()
    }
    fun finishMotion() {
        generation++; motion?.cancel(); motion = null
        animate().cancel(); pose(0f)
        cancelled = false
        GlyphMotion.tracking(this, false)
    }
    override fun onDetachedFromWindow() { finishMotion(); super.onDetachedFromWindow() }
}
