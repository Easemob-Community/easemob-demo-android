package com.hyphenate.chatdemo.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.hyphenate.chatdemo.R
import kotlin.math.hypot

/**
 * Tiled diagonal watermark for the chat background, aligned with the iOS
 * demo's ChatWatermarkView: 15sp bold text rotated -45 degrees, light mode
 * black at 5.5% alpha / dark mode white at 8% alpha, alternating rows offset
 * by half a column.
 */
class ChatWatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        textPaint.color = if (dark) 0x14FFFFFF.toInt() else 0x0E000000
        val text = context.getString(R.string.demo_chat_watermark)
        val density = resources.displayMetrics.density
        val columnSpacing = textPaint.measureText(text) + 48f * density
        val rowSpacing = 80f * density
        val radius = hypot(width.toDouble(), height.toDouble()).toFloat() / 2f

        canvas.save()
        // Same as iOS: move the origin to the view center, then rotate, and draw
        // the tiled text around the new origin so the pattern covers every corner.
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(-45f)
        var row = 0
        var y = -radius - rowSpacing
        while (y <= radius + rowSpacing) {
            val offset = if (row % 2 == 0) 0f else columnSpacing / 2f
            var x = -radius - columnSpacing
            while (x <= radius + columnSpacing) {
                canvas.drawText(text, x + offset, y, textPaint)
                x += columnSpacing
            }
            y += rowSpacing
            row++
        }
        canvas.restore()
    }
}
