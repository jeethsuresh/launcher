package app.still.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView

fun Context.dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
fun rounded(color: Int, radius: Float = 24f, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    stroke?.let { setStroke(1, it) }
}
fun View.ripple(color: Int, radius: Int = 24) {
    background = RippleDrawable(ColorStateList.valueOf(0x22ffffff), rounded(color, context.dp(radius).toFloat()), rounded(Color.WHITE, context.dp(radius).toFloat()))
}
fun Context.label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color)
    typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    includeFontPadding = false
}

/** Original, resolution-independent wallpapers. No downloaded assets or network permission. */
class StillWallpaper(context: Context) : View(context) {
    var style = "tide"
        set(value) { field = value; invalidate() }
    var dim = 15
        set(value) { field = value; invalidate() }
    var photo: Bitmap? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val image = photo
        if (style == "photo" && image != null) {
            val scale = maxOf(width.toFloat() / image.width, height.toFloat() / image.height)
            val w = image.width * scale; val h = image.height * scale
            canvas.drawBitmap(image, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint)
        } else if (style != "device") render(canvas, width.toFloat(), height.toFloat(), style)
        paint.shader = null; paint.color = Color.argb(dim * 255 / 100, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
    companion object {
        fun render(c: Canvas, w: Float, h: Float, style: String) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val colors = when (style) {
                "dune" -> intArrayOf(0xff5d3d3c.toInt(), 0xffb88d71.toInt(), 0xffe8b890.toInt())
                "ink" -> intArrayOf(0xff131c36.toInt(), 0xff343a62.toInt(), 0xff777398.toInt())
                else -> intArrayOf(0xff102e2c.toInt(), 0xff325b51.toInt(), 0xff819b7d.toInt())
            }
            p.shader = LinearGradient(0f, 0f, w, h, colors[0], colors[1], Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            val path = Path().apply {
                moveTo(w * 1.25f, h * .05f)
                cubicTo(w * .65f, h * .38f, w * .98f, h * .51f, w * .40f, h * .71f)
                cubicTo(-w * .12f, h * .90f, w * .02f, h * 1.1f, w * .5f, h * 1.2f)
                lineTo(w * 1.3f, h * 1.2f); close()
            }
            p.shader = LinearGradient(w, h*.2f, 0f, h, colors[1], colors[2], Shader.TileMode.CLAMP)
            c.drawPath(path, p)
            val ridge = Path().apply {
                moveTo(w * 1.1f, h*.43f)
                cubicTo(w*.96f, h*.65f, w*.18f, h*.70f, w*.17f, h*1.05f)
                lineTo(w*1.1f, h*1.1f); close()
            }
            p.shader = LinearGradient(w, h*.5f, 0f, h*1.1f, colors[0], colors[1], Shader.TileMode.CLAMP)
            c.drawPath(ridge, p)
            p.shader = RadialGradient(w*.15f, h*.15f, h*.7f, 0x18ffffff, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
    }
}

class Glyph(context: Context, private val name: String, var tint: Int = Color.WHITE) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        c.save(); val side = minOf(width, height) * .50f
        c.translate((width-side)/2, (height-side)/2); c.scale(side/24f, side/24f); p.color = tint
        fun line(x: Float, y: Float, xx: Float, yy: Float) = c.drawLine(x,y,xx,yy,p)
        when (name) {
            "search" -> { c.drawCircle(10f,10f,6.5f,p); line(15f,15f,21f,21f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "back" -> { line(14f,5f,7f,12f); line(7f,12f,14f,19f); line(7f,12f,21f,12f) }
            "up" -> { line(6f,14f,12f,8f); line(12f,8f,18f,14f) }
            "plus" -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
            "minus" -> line(5f,12f,19f,12f)
            "check" -> { line(4f,12f,9f,17f); line(9f,17f,20f,6f) }
            "settings" -> {
                c.drawCircle(12f,12f,3.5f,p); c.drawCircle(12f,12f,8f,p)
                for (i in 0..7) { c.save(); c.rotate(i*45f,12f,12f); line(12f,1f,12f,4f); c.restore() }
            }
            "wallpaper" -> { c.drawRoundRect(3f,3f,21f,21f,3f,3f,p); c.drawCircle(15.5f,8f,1.5f,p); line(3f,17f,9f,11f); line(9f,11f,15f,17f); line(14f,16f,17f,13f); line(17f,13f,21f,17f) }
            "widgets" -> { c.drawRoundRect(3f,3f,11f,11f,1.5f,1.5f,p); c.drawRoundRect(14f,3f,21f,11f,1.5f,1.5f,p); c.drawRoundRect(3f,14f,11f,21f,1.5f,1.5f,p); c.drawCircle(17.5f,17.5f,3.5f,p) }
            "info" -> { c.drawCircle(12f,12f,9f,p); line(12f,11f,12f,17f); c.drawCircle(12f,7f,.5f,p) }
            "trash" -> { line(4f,6f,20f,6f); line(9f,3f,15f,3f); line(6f,6f,7f,21f); line(7f,21f,17f,21f); line(17f,21f,18f,6f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
            "home" -> { line(3f,11f,12f,3f); line(12f,3f,21f,11f); line(6f,9f,6f,21f); line(6f,21f,18f,21f); line(18f,21f,18f,9f) }
            else -> for (x in 0..2) for (y in 0..2) c.drawCircle(5f+x*7f,5f+y*7f,1.5f,p)
        }
        c.restore()
    }
}
