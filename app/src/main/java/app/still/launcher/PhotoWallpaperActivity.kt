package app.still.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

/** A separate preview window keeps document-provider returns independent of home gestures/dialogs. */
class PhotoWallpaperActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var image: Bitmap? = null
    private var destroyed = false
    private var applying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run { finish(); return }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16)); setBackgroundColor(0xff101b18.toInt()) }
        body.addView(label("Photo wallpaper", 22f, Color.WHITE, true))
        val frame = FrameLayout(this)
        val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "Wallpaper preview" }
        frame.addView(preview, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val display = resources.displayMetrics
            val height = minOf(frame.height - dp(24), ((frame.width - dp(24)).toFloat() * display.heightPixels / display.widthPixels).toInt()).coerceAtLeast(1)
            val width = (height.toFloat() * display.widthPixels / display.heightPixels).toInt().coerceAtLeast(1)
            if (preview.layoutParams.width != width || preview.layoutParams.height != height) preview.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        }
        body.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this)
        val cancel = Button(this).apply { text = "Cancel"; setOnClickListener { if (!applying) finish() } }
        val apply = Button(this).apply { text = "Apply"; isEnabled = false }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(56), 1f)); buttons.addView(apply, LinearLayout.LayoutParams(0, dp(56), 1f))
        body.addView(buttons); setContentView(body)
        worker.execute {
            val result = runCatching { PhotoImages.decodePhoto(this, uri) }
            main.post {
                if (destroyed) { result.getOrNull()?.recycle(); return@post }
                result.onSuccess { bitmap -> image = bitmap; preview.setImageBitmap(bitmap); apply.isEnabled = true }
                    .onFailure { android.util.Log.w("StillWallpaper", "Unable to read selected image", it); Toast.makeText(this, "Unable to read this image. Download it to your device and choose it from Files.", Toast.LENGTH_LONG).show(); finish() }
            }
        }
        apply.setOnClickListener {
            val selected = image ?: return@setOnClickListener
            if (applying) return@setOnClickListener
            applying = true; apply.isEnabled = false; cancel.isEnabled = false
            val display = resources.displayMetrics
            val width = display.widthPixels.coerceAtMost(1440)
            val height = (width.toFloat() * display.heightPixels / display.widthPixels).toInt()
            worker.execute {
                val result = runCatching {
                    val cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        val scale = maxOf(width.toFloat() / selected.width, height.toFloat() / selected.height)
                        val left = (width - selected.width * scale) / 2; val top = (height - selected.height * scale) / 2
                        Canvas(cropped).drawBitmap(selected, null, RectF(left, top, left + selected.width * scale, top + selected.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
                        val pending = File(filesDir, "home-wallpaper.pending.jpg")
                        pending.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                        val manager = WallpaperManager.getInstance(this)
                        check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed)
                        val id = manager.setBitmap(cropped, null, true, WallpaperManager.FLAG_SYSTEM)
                        check(id > 0 && pending.renameTo(File(filesDir, "home-wallpaper.jpg")))
                        LauncherStore(this).apply { photoWallpaperId = id; save(settings = settings.copy(wallpaper = "photo")) }
                    } finally { cropped.recycle() }
                }
                main.post {
                    if (!destroyed) result.onSuccess { applying = false; setResult(RESULT_OK); finish() }.onFailure {
                        android.util.Log.w("StillWallpaper", "Unable to apply selected image", it)
                        Toast.makeText(this, "Android could not set this wallpaper. Check your device's wallpaper restrictions.", Toast.LENGTH_LONG).show()
                        applying = false; apply.isEnabled = true; cancel.isEnabled = true
                    }
                    else selected.recycle()
                }
            }
        }
    }

    override fun onDestroy() {
        destroyed = true; if (!applying) image?.recycle(); worker.shutdown(); super.onDestroy()
    }
}

object PhotoImages {
    fun decodePhoto(context: android.content.Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, 2560f/maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1), (info.size.height*scale).toInt().coerceAtLeast(1))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight)/sample > 2560) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Invalid image")
        val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        } }.getOrNull() ?: 1
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return decoded
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }


}
