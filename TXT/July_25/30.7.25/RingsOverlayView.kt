package com.veryeasygames.aimgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RingsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Ring(val outerColor: Int, val outerStrokeDp: Float,
                            val mainColor: Int,  val mainStrokeDp: Float,
                            val baseRadiusDp: Float)

    // Define your five rings here; baseRadiusDp is the “radius” you originally had in your XML
    private val rings = listOf(
        Ring(0xFF223EA4.toInt(), 5f,  0xFF0069D3.toInt(), 18f, 50f),  // Violet
        Ring(0xFFB27D00.toInt(), 4f,  0xFFFFD700.toInt(), 16f, 50f),  // Gold
        Ring(0xFF006400.toInt(), 4f,  0xFF00C853.toInt(), 16f, 50f),  // Green
        Ring(0xFFDAC100.toInt(), 4f,  0xFFFFFF00.toInt(), 16f, 50f),  // Yellow
        Ring(0xFF8B0000.toInt(), 4f,  0xFFFF0000.toInt(), 16f, 50f)   // Red
    )

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val mainPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Center and scale to the smaller of width/height
        val w    = width.toFloat()
        val h    = height.toFloat()
        val cx   = w/2f
        val cy   = h/2f
        val dm   = resources.displayMetrics
        val density = dm.density

        // Scale factor: screenMinPx / designDp (you used 110vpore in XML → treat base dp as 100dp)
        val screenMinPx = minOf(w, h)
        val designDp    = 110f  // your viewportHeight in XML was 110
        val scale       = screenMinPx / (designDp * density)

        // Draw each ring concentric at the same center
        rings.forEach { ring ->
            // Outer stroke
            outerPaint.color       = ring.outerColor
            outerPaint.strokeWidth = ring.outerStrokeDp * density * scale
            canvas.drawCircle(cx, cy, ring.baseRadiusDp * density * scale, outerPaint)

            // Main stroke
            mainPaint.color        = ring.mainColor
            mainPaint.strokeWidth  = ring.mainStrokeDp * density * scale
            canvas.drawCircle(cx, cy, ring.baseRadiusDp * density * scale - (ring.mainStrokeDp* density* scale), mainPaint)
        }
    }
}
