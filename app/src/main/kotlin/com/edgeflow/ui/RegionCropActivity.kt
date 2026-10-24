package com.edgeflow.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.edgeflow.core.RegionSelection
import com.edgeflow.ui.i18n.UiTranslations
import java.io.File

/**
 * Screenshot cropping runs after AccessibilityService has captured the target
 * app. The raw capture stays in private cache and is deleted after editing.
 */
class RegionCropActivity : ComponentActivity() {
    private fun tr(value: String) = UiTranslations.forContext(this, value)
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.edgeflow.ui.i18n.AppLanguage.wrap(newBase))
    }
    private var temporaryImage: File? = null
    private var screenshot: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_CACHE_NAME) ?: run { finish(); return }
        if (!name.matches(Regex("region-[a-f0-9-]{36}\\.png"))) {
            finish()
            return
        }
        val file = File(cacheDir, name)
        temporaryImage = file
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            Toast.makeText(this, tr("Screenshot unavailable"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        screenshot = bitmap

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 22))
        }
        val title = TextView(this).apply {
            text = tr("Drag across the screenshot to choose a region")
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(18, 18, 18, 18)
        }
        body.addView(title)
        val cropView = RegionSelectionView(this, bitmap)
        body.addView(cropView, LinearLayout.LayoutParams(-1, 0, 1f))
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(18, 12, 18, 22)
        }
        val cancel = Button(this).apply {
            text = tr("Cancel")
            setOnClickListener { finish() }
        }
        val save = Button(this).apply {
            text = tr("Save to Pictures")
            isEnabled = false
            setOnClickListener {
                val region = cropView.currentRegion() ?: return@setOnClickListener
                runCatching {
                    val cropped = Bitmap.createBitmap(
                        bitmap, region.left, region.top, region.width(), region.height(),
                    )
                    try {
                        saveToPictures(cropped)
                    } finally {
                        cropped.recycle()
                    }
                }.onSuccess {
                    Toast.makeText(this@RegionCropActivity, tr("Screenshot saved"), Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure {
                    Toast.makeText(this@RegionCropActivity, tr(it.message ?: "Save failed"), Toast.LENGTH_LONG).show()
                }
            }
        }
        cropView.onSelectionChanged = { save.isEnabled = it }
        toolbar.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(save, LinearLayout.LayoutParams(0, -2, 2f))
        body.addView(toolbar)
        setContentView(body)
    }

    private fun saveToPictures(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "EdgeFlow-region-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EdgeFlow")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create image in MediaStore")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "PNG encoding failed"
                }
            } ?: error("Could not open image output stream")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    override fun onDestroy() {
        screenshot?.recycle()
        screenshot = null
        if (!isChangingConfigurations) temporaryImage?.delete()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CACHE_NAME = "region_cache_name"
    }
}

private class RegionSelectionView(
    context: android.content.Context,
    private val screenshot: Bitmap,
) : View(context) {

    var onSelectionChanged: ((Boolean) -> Unit)? = null
    private val imageBounds = RectF()
    private var anchorX = 0f
    private var anchorY = 0f
    private val selection = RectF()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5534B4FF
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private var hasSelection = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / screenshot.width, height.toFloat() / screenshot.height)
        val imageW = screenshot.width * scale
        val imageH = screenshot.height * scale
        imageBounds.set(
            (width - imageW) / 2f, (height - imageH) / 2f,
            (width + imageW) / 2f, (height + imageH) / 2f,
        )
        canvas.drawBitmap(screenshot, null, imageBounds, imagePaint)
        if (hasSelection) {
            canvas.drawRect(selection, selectedPaint)
            canvas.drawRect(selection, outline)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBounds.width() <= 0f || imageBounds.height() <= 0f) return false
        val x = event.x.coerceIn(imageBounds.left, imageBounds.right)
        val y = event.y.coerceIn(imageBounds.top, imageBounds.bottom)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anchorX = x
                anchorY = y
                selection.set(x, y, x, y)
                hasSelection = true
                onSelectionChanged?.invoke(false)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (!hasSelection) return false
                selection.set(minOf(x, anchorX), minOf(y, anchorY), maxOf(x, anchorX), maxOf(y, anchorY))
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onSelectionChanged?.invoke(currentRegion() != null)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                hasSelection = false
                onSelectionChanged?.invoke(false)
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun currentRegion(): Rect? {
        if (!hasSelection || imageBounds.width() <= 0f || imageBounds.height() <= 0f) return null
        return RegionSelection.toBitmapRect(
            selection.left - imageBounds.left,
            selection.top - imageBounds.top,
            selection.right - imageBounds.left,
            selection.bottom - imageBounds.top,
            imageBounds.width(), imageBounds.height(), screenshot.width, screenshot.height,
        )?.let { Rect(it.left, it.top, it.right, it.bottom) }
    }
}
