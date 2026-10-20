package app.still.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs

class WorkspaceView(context: Context) : ViewGroup(context) {
    var columns = 4
    var rows = 6
    var editing = false
        set(value) { field = value; invalidate() }
    var dropPreview: HomeItem? = null
        set(value) { field = value; invalidate() }
    var dropAllowed = true
    var blankHold: (() -> Unit)? = null
    var swipe: ((Int, Int) -> Unit)? = null
    val cellWidth get() = width.toFloat() / columns
    val cellHeight get() = height.toFloat() / rows
    private val placements = mutableMapOf<View, HomeItem>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var downX = 0f
    private var downY = 0f
    private var onWidget = false
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) { blankHold?.invoke() }
    })

    init { setWillNotDraw(false); clipChildren = false; clipToPadding = false; isFocusable = true; contentDescription = "Home screen. Swipe up for apps. Touch and hold to customize." }
    fun tile(view: View, item: HomeItem) { placements[view] = item; addView(view) }
    override fun removeAllViews() { placements.clear(); super.removeAllViews() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        placements.forEach { (view, item) ->
            view.measure(MeasureSpec.makeMeasureSpec((measuredWidth.toFloat() / columns * item.spanX).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((measuredHeight.toFloat() / rows * item.spanY).toInt(), MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        placements.forEach { (view, item) ->
            val x = (item.x * cellWidth).toInt(); val y = (item.y * cellHeight).toInt()
            view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        if (editing) {
            paint.color = 0x40ffffff
            for (x in 0 until columns) for (y in 0 until rows) canvas.drawCircle((x + .5f) * cellWidth, (y + .5f) * cellHeight, 2 * resources.displayMetrics.density, paint)
        }
        dropPreview?.let {
            paint.color = if (dropAllowed) 0x55b6dac7 else 0x55f2a399
            canvas.drawRoundRect(RectF(it.x * cellWidth + 4, it.y * cellHeight + 4, (it.x + it.spanX) * cellWidth - 4, (it.y + it.spanY) * cellHeight - 4), 22f, 22f, paint)
        }
    }
    fun isWidgetAt(x: Float, y: Float): Boolean = placements.any { (view, item) ->
        item.kind == ItemKind.WIDGET && x >= view.left && x < view.right && y >= view.top && y < view.bottom
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                onWidget = isWidgetAt(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (!onWidget && abs(event.y - downY) > 32 * resources.displayMetrics.density && abs(event.y - downY) > abs(event.x - downX)) return true
        }
        return false
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX; val dy = event.y - downY
            val threshold = 48 * resources.displayMetrics.density
            if (abs(dy) > threshold && abs(dy) > abs(dx)) swipe?.invoke(0, if (dy < 0) 1 else -1)
            else if (abs(dx) < threshold && abs(dy) < threshold) performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
