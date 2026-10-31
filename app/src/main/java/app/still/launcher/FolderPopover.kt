package app.still.launcher

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/** Transparent, modal overlay: the current home and dock remain visible underneath. */
class FolderPopover(
    context: Context,
    val sheet: PullDownDrawerLayout,
    private val appCount: Int,
    private val columns: Int,
    private val rowHeight: Int,
    private val chromeHeight: Int,
    private val dockTop: () -> Int,
    private val folderTop: () -> Int?,
    dismissed: () -> Unit,
) : FrameLayout(context) {
    private val inset = context.dp(12)
    private val gap = context.dp(8)
    private var bottomEdge = 0

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener { dismissed() }
        addView(sheet)
    }

    private fun measureSheet() {
        bottomEdge = (dockTop() - top - gap).coerceIn(0, measuredHeight)
        val anchorDistance = folderTop()?.let { bottomEdge - (it - top) }
        val height = FolderPopoverSizing.height(appCount, columns, rowHeight, chromeHeight, bottomEdge, anchorDistance)
        sheet.measure(MeasureSpec.makeMeasureSpec((measuredWidth - inset * 2).coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        measureSheet()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Home is laid out before this overlay. Use its current geometry after inset/size changes.
        measureSheet()
        sheet.layout(inset, bottomEdge - sheet.measuredHeight, width - inset, bottomEdge)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        // Pulling the sheet down reveals home without drawing across the fixed dock.
        canvas.clipRect(0, 0, width, bottomEdge)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
