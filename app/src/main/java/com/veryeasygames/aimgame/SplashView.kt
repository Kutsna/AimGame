package com.veryeasygames.aimgame

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SplashView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Main title
    private val titleText = "AimGame"
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("constantia", Typeface.ITALIC)
        textSize = 44f * resources.displayMetrics.density
        color = Color.YELLOW
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }

    // Subtitle
    private val subtitleText = "                  VeryEasyGames"
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("constantia", Typeface.ITALIC)
        textSize = 12f * resources.displayMetrics.density   // half the size
        color = Color.GREEN
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }

    // animated properties
    var revealProgress: Float = 0f
        set(v) { field = v.coerceIn(0f,1f); invalidate() }
    var glowRadius:   Float = 0f
        set(v) { field = v; invalidate() }
    var textScale:    Float = 1f
        set(v) { field = v; invalidate() }

    private val titleBounds    = Rect()
    private val subtitleBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1) Fill the background
        canvas.drawColor(Color.parseColor("#3E2723"))

        // 2) Measure title & subtitle
        titlePaint.getTextBounds(titleText, 0, titleText.length, titleBounds)
        subtitlePaint.getTextBounds(subtitleText, 0, subtitleText.length, subtitleBounds)

        val titleW  = titleBounds.width()
        val titleH  = titleBounds.height()
        val subW    = subtitleBounds.width()
        val subH    = subtitleBounds.height()

        // center X
        val cx = width / 2f

        // Y positions: subtit0le above title by 16dp + half-heights
        val gap = 7f * resources.displayMetrics.density
        val yTitle = (height + titleH) / 2f - titleBounds.bottom
        val ySub   = yTitle - titleH/2f - gap - subH/2f

        // apply overall scale around center
        canvas.save()
        canvas.scale(textScale, textScale, cx, height/2f)

        // draw subtitle (no reveal/clip)
        canvas.drawText(
            subtitleText,
            cx - subW/2f,
            ySub,
            subtitlePaint
        )

        // apply glow to title
        titlePaint.setShadowLayer(glowRadius, 0f, 0f, Color.YELLOW)

        // clip-for-reveal on title only
        val revealW = titleW * revealProgress
        val xTitle  = cx - titleW/2f - titleBounds.left
        canvas.save()
        canvas.clipRect(xTitle, 0f, xTitle + revealW, height.toFloat())
        canvas.drawText(
            titleText,
            xTitle,
            yTitle,
            titlePaint
        )
        canvas.restore()

        canvas.restore()
    }
}
