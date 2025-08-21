package com.veryeasygames.aimgame

import android.graphics.*
import android.graphics.drawable.Drawable

class VioletRingDrawable : Drawable() {

    // Paint for the violet ring.
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8A2BE2")  // Violet
    }

    // Path to hold the donut shape.
    private val ringPath = Path()

    // Ring thickness (20px).
    private val ringThickness = 20f

    // Fractional positions for the holes.
    private val holeFractions = listOf(
        Pair(0.5f, 0.483f),
        Pair(0.573f, 0.52f),
        Pair(0.632f, 0.55f),
        Pair(0.69f, 0.577f),
        Pair(0.76f, 0.61f)
    )

    // Radius for each hole (adjust if needed).
    private val holeRadius = 10f

    // Paint for clearing pixels.
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun draw(canvas: Canvas) {
        // Save the layer so CLEAR mode works properly.
        val layerId = canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), null)

        // Determine center and radii.
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val outerRadius = (minOf(bounds.width(), bounds.height()) / 2).toFloat()
        val innerRadius = outerRadius - ringThickness

        // Create a donut shape using EVEN_ODD fill.
        ringPath.reset()
        ringPath.addCircle(cx, cy, outerRadius, Path.Direction.CW)
        ringPath.addCircle(cx, cy, innerRadius, Path.Direction.CCW)
        ringPath.fillType = Path.FillType.EVEN_ODD

        // Draw the ring.
        canvas.drawPath(ringPath, ringPaint)

        // Clear out the holes.
        for ((fx, fy) in holeFractions) {
            val hx = bounds.left + fx * bounds.width()
            val hy = bounds.top + fy * bounds.height()
            canvas.drawCircle(hx, hy, holeRadius, clearPaint)
        }
        canvas.restoreToCount(layerId)
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter
    }
}
