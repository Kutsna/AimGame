package com.veryeasygames.aimgame

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter


/**
 * A custom View that draws one of five concentric rings (violet, gold, green, yellow, red)
 * plus the tiny center hole, all scaled to the View’s size.
 */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Type {
        VIOLET, GOLD, GREEN, YELLOW, RED
    }

    /** Choose which ring to draw; default = VIOLET */
    var type: Type = Type.VIOLET
        set(value) {
            field = value
            invalidate()
        }

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val mainPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val holePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.LTGRAY
    }

    // add this mutable filter
    private var ringFilter: PorterDuffColorFilter? = null

    /** Allows you to tint both strokes the same way. */
    fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        ringFilter = PorterDuffColorFilter(color, mode)
        outerPaint.colorFilter = ringFilter
        mainPaint.colorFilter  = ringFilter
        invalidate()
    }

    fun clearColorFilter() {
        ringFilter = null
        outerPaint.colorFilter = null
        mainPaint.colorFilter = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w  = width.toFloat()
        val h  = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // we'll scale our “design” viewport (110×110) up to min(w,h)
        val scale = minOf(w, h) / 110f

        // configure paints & radii based on type
        when (type) {
            Type.VIOLET -> {
                outerPaint.color       = Color.parseColor("#223EA4")
                outerPaint.strokeWidth = 5f  * scale
                mainPaint.color        = Color.parseColor("#0069D3")
                mainPaint.strokeWidth  = 18f * scale
                holePaint.strokeWidth  = 1f  * scale
                drawRing(canvas, cx, cy, 50f*scale, 41f*scale, 1f*scale)
            }
            Type.GOLD -> {
                outerPaint.color       = Color.parseColor("#B27D00")
                outerPaint.strokeWidth = 4f  * scale
                mainPaint.color        = Color.parseColor("#FFD700")
                mainPaint.strokeWidth  = 16f * scale
                holePaint.strokeWidth  = 1f  * scale
                drawRing(canvas, cx, cy, 50f*scale, 41f*scale, 1f*scale)
            }
            Type.GREEN -> {
                outerPaint.color       = Color.parseColor("#006400")
                outerPaint.strokeWidth = 4f  * scale
                mainPaint.color        = Color.parseColor("#00C853")
                mainPaint.strokeWidth  = 16f * scale
                holePaint.strokeWidth  = 1f  * scale
                drawRing(canvas, cx, cy, 50f*scale, 41f*scale, 1f*scale)
            }
            Type.YELLOW -> {
                outerPaint.color       = Color.parseColor("#DAC100")
                outerPaint.strokeWidth = 4f  * scale
                mainPaint.color        = Color.parseColor("#FFFF00")
                mainPaint.strokeWidth  = 16f * scale
                holePaint.strokeWidth  = 1f  * scale
                drawRing(canvas, cx, cy, 50f*scale, 41f*scale, 1f*scale)
            }
            Type.RED -> {
                outerPaint.color       = Color.parseColor("#8B0000")
                outerPaint.strokeWidth = 4f  * scale
                mainPaint.color        = Color.parseColor("#FF0000")
                mainPaint.strokeWidth  = 16f * scale
                holePaint.strokeWidth  = 1f  * scale
                drawRing(canvas, cx, cy, 50f*scale, 41f*scale, 1f*scale)
            }
        }
    }

    /** Helper to draw the two concentric strokes and the little hole. */
    private fun drawRing(
        canvas: Canvas,
        cx: Float, cy: Float,
        outerRadius: Float,
        mainRadius:  Float,
        holeRadius:  Float
    ) {
        // Outer circle
        canvas.drawCircle(cx, cy, outerRadius, outerPaint)
        // Main circle
        canvas.drawCircle(cx, cy, mainRadius,  mainPaint)
        // Tiny hole in center
        canvas.drawCircle(cx, cy, holeRadius,  holePaint)
    }
}
