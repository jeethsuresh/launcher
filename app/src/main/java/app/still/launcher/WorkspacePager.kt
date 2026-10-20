package app.still.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keeps real current/neighbor pages alive so icons and live widgets track the finger together. */
class WorkspacePager(context: Context) : ViewGroup(context) {
    var makePage: (Int) -> WorkspaceView = { WorkspaceView(context) }
    var pageChanged: (Int) -> Unit = {}
    var pagingEnabled = true
    var selectedPage = 0
        private set
    private var layoutPage = 0
    private var pageCount = 1
    private val pages = linkedMapOf<Int, WorkspaceView>()
    val currentWorkspace get() = page(selectedPage)
    private var animator: ValueAnimator? = null
    private var animationTarget: Int? = null
    private var reloadPending = false
    private var scrolling = false
    private var downOnWidget = false
    private var downX = 0f
    private var downY = 0f
    private var tracker: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledPagingTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    init { clipChildren = true; clipToPadding = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun bind(count: Int, selected: Int) {
        val next = selected.coerceIn(0, count.coerceAtLeast(1) - 1)
        if ((scrolling || animator != null) && count == pageCount && next == selectedPage) {
            reloadPending = true
            return
        }
        stopAnimation()
        pageCount = count.coerceAtLeast(1); selectedPage = next; layoutPage = next
        scrolling = false; reloadPending = false; scrollTo(0, 0)
        rebuild()
    }

    fun setEditing(value: Boolean) { pages.values.forEach { it.editing = value } }

    fun goTo(target: Int, animated: Boolean = true) {
        val next = target.coerceIn(0, pageCount - 1)
        if (animator != null) stopAnimation()
        if (next == layoutPage && scrollX == 0) return
        val origin = layoutPage
        val direction = (next - origin).coerceIn(-1, 1)
        page(next)
        selectedPage = next; animationTarget = next
        updateAccessibility(); layoutPages()
        pageChanged(next)
        if (!animated || width == 0) { settle(); return }
        val destination = direction * width
        animator = ValueAnimator.ofInt(scrollX, destination).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { animator = null; settle() }
            })
            start()
        }
    }

    private fun page(index: Int): WorkspaceView = pages.getOrPut(index) {
        makePage(index).also { addView(it) }
    }

    private fun rebuild() {
        pages.clear(); removeAllViews()
        keepNeighbors()
    }

    private fun keepNeighbors() {
        val wanted = ((selectedPage - 1)..(selectedPage + 1)).filter { it in 0 until pageCount }.toSet()
        pages.keys.toList().filterNot { it in wanted }.forEach { removeView(pages.remove(it)) }
        wanted.forEach { page(it) }
        updateAccessibility(); requestLayout(); layoutPages()
    }

    private fun updateAccessibility() {
        pages.forEach { (index, view) ->
            view.importantForAccessibility = if (index == selectedPage) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun settle() {
        layoutPage = selectedPage; animationTarget = null; scrolling = false
        scrollTo(0, 0)
        if (reloadPending) { reloadPending = false; rebuild() } else keepNeighbors()
    }

    private fun stopAnimation() {
        animator?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        animator = null; animationTarget = null; layoutPage = selectedPage
        scrollTo(0, 0); layoutPages()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        pages.values.forEach { it.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = layoutPages()

    private fun layoutPages() {
        val target = animationTarget
        val far = target != null && abs(target - layoutPage) > 1
        pages.forEach { (index, view) ->
            view.visibility = if (far && index != layoutPage && index != target) INVISIBLE else VISIBLE
            val offset = if (far && index == target) (index - layoutPage).coerceIn(-1, 1) else index - layoutPage
            view.layout(offset * width, 0, (offset + 1) * width, height)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!pagingEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopAnimation(); scrolling = false
                downX = event.x; downY = event.y
                downOnWidget = currentWorkspace.isWidgetAt(event.x, event.y)
                tracker?.recycle(); tracker = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                if (!downOnWidget && abs(dx) > slop && abs(dx) > abs(dy)) {
                    scrolling = true; parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { tracker?.recycle(); tracker = null }
        }
        return scrolling
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pagingEnabled || downOnWidget) return false
        tracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val atBoundary = (dx > 0 && layoutPage == 0) || (dx < 0 && layoutPage == pageCount - 1)
                scrollTo((-(if (atBoundary) dx * .25f else dx).coerceIn(-width.toFloat(), width.toFloat())).roundToInt(), 0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val velocity = tracker?.xVelocity ?: 0f
                val moved = abs(event.x - downX)
                val fling = abs(velocity) > minFling && moved > slop
                val direction = if (fling) { if (velocity < 0) 1 else -1 } else { if (scrollX > 0) 1 else -1 }
                val turn = event.actionMasked == MotionEvent.ACTION_UP && (abs(scrollX) > width * .22f || fling)
                tracker?.recycle(); tracker = null; scrolling = false
                parent.requestDisallowInterceptTouchEvent(false)
                goTo(if (turn) layoutPage + direction else layoutPage)
            }
        }
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = pageCount > 1
        if (selectedPage < pageCount - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (selectedPage > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, args: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && selectedPage < pageCount - 1) { goTo(selectedPage + 1); return true }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && selectedPage > 0) { goTo(selectedPage - 1); return true }
        return super.performAccessibilityAction(action, args)
    }

    override fun onDetachedFromWindow() { stopAnimation(); tracker?.recycle(); tracker = null; super.onDetachedFromWindow() }
}
