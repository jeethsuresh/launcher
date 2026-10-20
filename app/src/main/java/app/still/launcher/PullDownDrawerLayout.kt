package app.still.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Hands scrolling to the app list until its top edge, then lets the sheet follow the finger. */
class PullDownDrawerLayout(context: Context) : LinearLayout(context) {
    var canPull: () -> Boolean = { false }
    var pullStarted: () -> Unit = {}
    var settled: () -> Unit = {}
    var dismissed: () -> Unit = {}
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var pulling = false
    private var tracker: VelocityTracker? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // AbsListView claims the gesture at its smaller touch slop. Keep observing
        // while it is at the top so a downward pull can transfer to the drawer.
        super.requestDisallowInterceptTouchEvent(disallowIntercept && !canPull())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker?.recycle(); tracker = VelocityTracker.obtain().apply { addMovement(event) }
                startX = event.rawX; startY = event.rawY; pulling = false
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                val dy = event.rawY - startY
                if (dy > slop * 2 && dy > abs(event.rawX - startX) && canPull()) {
                    pulling = true
                    animate().cancel()
                    parent.requestDisallowInterceptTouchEvent(true)
                    pullStarted()
                    return true
                }
                // Once a scrolling list reaches the top, a subsequent pull starts here.
                if (!canPull()) { startX = event.rawX; startY = event.rawY }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { tracker?.recycle(); tracker = null }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pulling) return super.onTouchEvent(event)
        tracker?.addMovement(event)
        val dy = (event.rawY - startY).coerceAtLeast(0f)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> translationY = dy * .85f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val density = resources.displayMetrics.density
                val close = event.actionMasked == MotionEvent.ACTION_UP &&
                    (dy > 100 * density || (dy > 32 * density && (tracker?.yVelocity ?: 0f) > 1000 * density))
                tracker?.recycle(); tracker = null; pulling = false
                parent.requestDisallowInterceptTouchEvent(false)
                animate().translationY(if (close) height.toFloat() else 0f).setDuration(180)
                    .withEndAction { if (close) dismissed() else settled() }.start()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animate().cancel(); tracker?.recycle(); tracker = null
        super.onDetachedFromWindow()
    }
}
