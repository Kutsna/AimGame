package com.veryeasygames.aimgame

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.exitProcess
import android.util.AttributeSet
import com.google.android.gms.ads.AdSize
import android.graphics.RectF

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle), SensorEventListener {

    var showPlayNextUI = false
    var levelStartListener: LevelStartListener? = null

    interface LevelStartListener {
        fun onLevelStarted(currentLevel: Int)
        fun onLevelCompleted(currentLevel: Int)
    }

    var showHoles: Boolean = false

    // Sensor and physics.
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var accelX = 0f
    private var accelY = 0f
    private val friction = 0.99f

    // --- Reordered Balls Array:
    // Balls are arranged so that index 4 (the central ball) is the largest.
    private val balls = arrayOf(
        // Index 0: central red
        Ball(0f, 0f, 0f, 0f, 38f * 1.6f, basePoints = 100),
        // Yellow ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 30f * 1.45f, basePoints = 200),
        // Green ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f, basePoints = 300),
        // Gold ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f, basePoints = 400),
        // Violet ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f, basePoints = 500),

        Ball(0f, 0f, 0f, 0f, 30f * 1.45f, basePoints = 200),
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f, basePoints = 300),
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f, basePoints = 400),
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f, basePoints = 500),

        Ball(0f, 0f, 0f, 0f, 30f * 1.45f, basePoints = 200),
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f, basePoints = 300),
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f, basePoints = 400),
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f, basePoints = 500)
    )

    // Data class for a ball.
    data class Ball(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float,
        var placed: Boolean = false,
        var active: Boolean = false,
        var canSnap: Boolean = true,
        val basePoints: Int,

        // orbit fields
        var orbiting: Boolean = false,
        var orbitCenterX: Float = 0f,
        var orbitCenterY: Float = 0f,
        var orbitRadius: Float = 0f,
        var orbitAngle: Float = 0f,
        var orbitAccum: Float = 0f,

        // 👇 new property to track which hole this ball is heading into
        var snappedHole: Int? = null
    )

    // data class for one little floating +100 animation
    private data class FloatingScore(
        var x: Float,
        var y: Float,
        val text: String,
        var alpha: Int = 255,
        var size: Float,
        val dx: Float,        // horizontal motion per frame
        val dy: Float,        // vertical motion per frame
        val color: Int
    )
    private var currentLevel = 1

    // Hole positions used for snapping.
    // Initially these are provided as relative fractions but updated positions from MainActivity are absolute.
    private var holePositions: Array<Pair<Float, Float>> = arrayOf(
        0.5f   to 0.483f,    // red center
        0.573f to 0.52f,     // yellow
        0.632f to 0.55f,     // green
        0.69f  to 0.578f,    // gold
        0.75f  to 0.607f     // violet
    )
    private var holePositionsAbsolute: Boolean = false

    // 1) Helper to decide how many holes (and balls) on each level:
    private fun getCountForLevel(level: Int): Int = when {
        // Levels 1–2: 5 holes
        level in 1..2    -> 5
        // Levels 3–10: level+3 → 6…13 holes
        level in 3..10   -> level + 3
        // Levels 11–19: repeat levels 2–10 counts (2→5, 3→6…10→13)
        level in 11..19  -> (level - 9) + 3
        else             -> 0  // game over
    }

    // 2) Dynamic timer per level:
    private fun getTimeLimitForLevel(level: Int): Int = when {
        level in 1..2   -> 90
        level in 3..10   -> 60
        level in 11..15  -> 60 // - 10 * (level - 11)  // 12→50,13→40,14→30,15→20
        level in 16..19  -> 90
        else             -> 0
    }


    private val floatingColors = arrayOf(
        Color.RED,     // central (index 0)
        Color.YELLOW,  // index 1
        Color.GREEN,   // index 2
        Color.rgb(255, 150, 0), // index 3
        Color.MAGENTA     // index 4
    )

    /**
     * Updates the hole positions used for ball snapping.
     * The positions are expected to be absolute screen coordinates (in pixels).
     */

    fun updateHolePositions(
        newPositions: Array<Pair<Float, Float>>,
        ringFinished: Boolean
    ) {
        val count = getCountForLevel(currentLevel)
        val base = newPositions.toList().take(5)

        // 1) Still drawing calibration? Just show the 5 detected points.
        if (!ringFinished) {
            holePositions = base.toTypedArray()
            holePositionsAbsolute = false
            return
        }

        // convert center to pixels once
        val (cxF, cyF) = base[0]
        val centerX = if (cxF <= 1f) cxF * width else cxF
        val centerY = if (cyF <= 1f) cyF * height else cyF

        val holes = mutableListOf<Pair<Float, Float>>()

        // —— LEVELS 1–6: mirror logic, no special Level 1 case ——
        if (currentLevel < 7) {
            // add exactly 'count' base holes (count == 5 on Level 1)
            holes += base.take(count)

            // only mirror if count > 5 (i.e. Levels 2–6 have count 6–9)
            if (count > 5) {
                val need = count - 5
                for (i in 1 until 1 + need) {
                    val (fx, fy) = base[i]
                    val px = if (fx <= 1f) fx * width else fx
                    val py = if (fy <= 1f) fy * height else fy
                    val ox = 2 * centerX - px
                    val oy = 2 * centerY - py
                    holes += (ox to oy)
                }
            }

            // —— LEVELS 7–10 & 16–19: Level-6 holes + incremental 120° additions ——
        } else if ((currentLevel in 7..10) || (currentLevel in 16..19)) {
            // rebuild Level 6’s 9-hole mirror layout
            holes += base
            run {
                for (i in 1..4) {
                    val (fx, fy) = base[i]
                    val px = if (fx <= 1f) fx * width else fx
                    val py = if (fy <= 1f) fy * height else fy
                    val ox = 2 * centerX - px
                    val oy = 2 * centerY - py
                    holes += (ox to oy)
                }
            }
            // now add one extra per level at 120° on rings 1…4
            val extra = ((currentLevel - 6).coerceAtMost(4))
            val sector = (2 * Math.PI / 3).toFloat()
            for (ringIdx in 1..extra) {
                val (fx, fy) = base[ringIdx]
                val px = if (fx <= 1f) fx * width else fx
                val py = if (fy <= 1f) fy * height else fy
                val r      = hypot(px - centerX, py - centerY)
                val angle0 = atan2(py - centerY, px - centerX)
                val a = angle0 + sector
                holes += (centerX + cos(a) * r to centerY + sin(a) * r)
            }

            // —— LEVELS 11–15: two-hole mirror logic ——
        } else if (currentLevel in 11..15) {
            holes += base.take(count.coerceAtMost(5))
            if (count > 5) {
                val need = count - 5
                for (i in 1..need) {
                    val (fx, fy) = base[i]
                    val px = if (fx <= 1f) fx * width else fx
                    val py = if (fy <= 1f) fy * height else fy
                    val ox = 2 * centerX - px
                    val oy = 2 * centerY - py
                    holes += (ox to oy)
                }
            }
        }

        holePositions = holes.toTypedArray()
        holePositionsAbsolute = true
        showHoles = false

        Log.d("GameView", "Hole positions (count=${holePositions.size}):")
        holePositions.forEachIndexed { idx, (x, y) ->
            Log.d("GameView", "  [$idx] = ($x,$y)")
        }
    }


    // SoundPool and sounds.
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<String, Int>()
    private val handler = Handler(Looper.getMainLooper())

    // Tracks the last time each sound key was played
    private val lastSoundTimes = mutableMapOf<String, Long>()

    // list to hold ongoing animations
    private val floatingScores = mutableListOf<FloatingScore>()

    // how long each floats (ms)
    private val FLOAT_DURATION = 3600L

    // Game state.

    private var levelTimeLimit = 90 // seconds
    private var levelStartTime = 0L
    private var levelTimer = 90 // seconds remaining
    private var overallScore = 0 // overall score so far
    private var levelStartScore = 0 // score at the beginning of the level
    private var ballSpawnIndex = 0 // sequential ball spawning index
    private val spawnDelay = 500L // delay between ball spawns (ms)
    private var levelFailed = false
    private var levelCompleted = false
    private var gamePaused = false
    private var timerStarted = false
    private var timerPaused = false
    private var pausedElapsed: Long = 0L
    private var scoreFrozen = false
    private var failureAlertShown = false
    private var startDialogShowing = false

    // Save-and-Exit and Pause button rectangles.
    private var saveExitButtonRect: RectF = RectF()
    private var pauseButtonRect: RectF = RectF()

    // SharedPreferences.
    private val prefs: SharedPreferences =
        context.getSharedPreferences("AimgamePrefs", Context.MODE_PRIVATE)

    private var failureDialogRunnable: Runnable? = null

    // New variables for the Options menu.
    private var optionsExpanded = false
    private var optionsButtonRect: RectF = RectF()
    // We'll compute item rectangles on the fly in onDraw.
    private val optionsList = listOf("Save&Exit", "New game", "Tilted", "Flat")

    private var baselineSet = false
    private var baselineX = 0f
    private var baselineY = 0f


    init {
        keepScreenOn = true
        showHoles = false
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(40)
            .setAudioAttributes(audioAttributes)
            .build()
        soundIds["ball1"] = soundPool.load(context, R.raw.ball1_click, 1)
        soundIds["ball2"] = soundPool.load(context, R.raw.ball2_click, 1)
        soundIds["ball3"] = soundPool.load(context, R.raw.ball3_click, 1)
        soundIds["ball4"] = soundPool.load(context, R.raw.ball4_click, 1)
        soundIds["ball5"] = soundPool.load(context, R.raw.ball5_click, 1)
        soundIds["edge"] = soundPool.load(context, R.raw.edge_wrap_click, 1)
        soundIds["success"] = soundPool.load(context, R.raw.success_chime, 1)
        soundIds["pwa"] = soundPool.load(context, R.raw.pwa, 1)
        soundIds["fail"] = soundPool.load(context, R.raw.fail, 1)
        soundIds["ballout"] = soundPool.load(context, R.raw.ballout, 1)
        soundIds["bell"]   = soundPool.load(context, R.raw.bell, 1)
        soundIds["bell_0"] = soundPool.load(context, R.raw.bell_0, 1)  // red
        soundIds["bell_1"] = soundPool.load(context, R.raw.bell_1, 1)  // yellow
        soundIds["bell_2"] = soundPool.load(context, R.raw.bell_2, 1)  // green
        soundIds["bell_3"] = soundPool.load(context, R.raw.bell_3, 1)  // gold
        soundIds["bell_4"] = soundPool.load(context, R.raw.bell_4, 1)  // violet
        loadGameState()
        startDialogShowing = true
        showStartDialog()
    }

    fun calibrateBaseline() {
        // Reset the flag so that the next sensor reading is used.
        baselineSet = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        optionsButtonRect = RectF(20f, h - 500f, 420f, h - 400f)
        saveExitButtonRect = RectF(w - 220f, 20f, w - 20f, 120f)
        pauseButtonRect = RectF(20f, 20f, 220f, 120f)
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Don’t allow Options while any blocking dialog is up
            if (startDialogShowing || levelFailed || levelCompleted) {
                return super.onTouchEvent(event)
            }

            // 1) Check if the Options button itself was tapped
            if (optionsButtonRect.contains(event.x, event.y)) {
                if (!gamePaused) pauseGame() else resumeGame()
                optionsExpanded = !optionsExpanded
                invalidate()
                return true
            }

            // 2) If dropdown is open, check its items (they sit *below* the button)
            if (optionsExpanded) {
                val itemHeight = 90f
                val spacing    = 12f
                var topY       = optionsButtonRect.bottom + spacing
                for (i in optionsList.indices) {
                    val itemRect = RectF(
                        optionsButtonRect.left,
                        topY,
                        optionsButtonRect.right,
                        topY + itemHeight
                    )
                    if (itemRect.contains(event.x, event.y)) {
                        handleOptionSelection(i)
                        optionsExpanded = false
                        invalidate()
                        return true
                    }
                    topY += itemHeight + spacing
                }

                // 3) Tapped outside the dropdown → collapse menu
                optionsExpanded = false
                resumeGame()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }


    private fun handleOptionSelection(index: Int) {
        when (index) {
            0 -> {                         // "Save & Exit"
                saveGameState()
                exitProcess(0)
            }
            1 -> newGame()                 // "New game"
            2 -> {                         // "Tilted"
                orientationOffset = -45    // Rotate sensor values by -45° so the game behaves as flat.
                Log.d("Options", "Orientation set to Tilted; calibrating baseline.")
                calibrateBaseline()       // This will cause the next sensor reading to become the new baseline.
                resumeGame()
            }
            3 -> {                         // "Flat"
                orientationOffset = 0    // Set Flat mode (no rotation)
                // Reset calibration to default: effectively no offset.
                baselineSet = true
                baselineX = 0f
                baselineY = 0f
                Log.d("Options", "Orientation set to Flat; calibration reset to default (0,0).")
                resumeGame()
            }

        }
    }

    // Orientation offset; used in onSensorChanged to adjust accelerometer values.
    var orientationOffset = 0  // 0: Flat (original)


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // compute banner height in px
        val bannerHeightPx = AdSize.BANNER.getHeightInPixels(context).toFloat()

        if (showHoles) {
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            val holeRadius = 12f

            holePositions.forEachIndexed { i, (fx, fy) ->
                if (currentLevel >= 16 && i >= ballSpawnIndex) return@forEachIndexed

                val x = if (holePositionsAbsolute) fx else fx * width
                // shift down by the banner thickness
                val y = if (holePositionsAbsolute) fy else fy * height

                canvas.drawCircle(x, y, holeRadius, holePaint)
            }
        }

        // --- DRAW OPTIONS BUTTON & DROPDOWN at top-right ---
        if (!startDialogShowing && !levelFailed && !levelCompleted) {
            // 1) Metrics & sizing
            val margin    = 16f                                    // margin to screen edges
            val padding   = 20f                                    // inside button padding
            val textSize  = 62f                                    // your existing textSize
            val textLabel = "Options"

            // Paints for text
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.WHITE
                this.textSize = textSize
                typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style     = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = Color.BLACK
                this.textSize   = textSize
                typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style       = Paint.Style.STROKE
                strokeWidth = 4f
                textAlign   = Paint.Align.CENTER
            }

            // Measure text width
            val textWidth = strokePaint.measureText(textLabel)
            val btnWidth  = textWidth + padding * 2
            val btnHeight = textSize + padding

            // Compute the RectF for the button in top-right
            optionsButtonRect.set(
                width  - margin - btnWidth, // left
                margin,                     // top
                width  - margin,            // right
                margin + btnHeight          // bottom
            )

            // Background paint
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.DKGRAY
                isAntiAlias = true
            }

            // Draw the rounded-rect button
            canvas.drawRoundRect(optionsButtonRect, 12f, 12f, bgPaint)

            // Draw the label (stroke then fill), vertically centered
            val textX = optionsButtonRect.centerX()
            val textY = optionsButtonRect.centerY() + textSize/3
            canvas.drawText(textLabel, textX, textY, strokePaint)
            canvas.drawText(textLabel, textX, textY, fillPaint)

            // 2) If expanded, draw the dropdown *below* the button
            if (optionsExpanded) {
                val itemHeight = 80f
                val spacing    = 8f
                var topY       = optionsButtonRect.bottom + spacing

                for (label in optionsList) {
                    // Dropdown item rect, same width as button
                    val itemRect = RectF(
                        optionsButtonRect.left,
                        topY,
                        optionsButtonRect.right,
                        topY + itemHeight
                    )

                    // Background for each item
                    val itemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.GRAY
                        alpha = 220
                    }
                    canvas.drawRoundRect(itemRect, 12f, 12f, itemBg)

                    // Text paints for items (right-aligned)
                    val itemFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color     = Color.WHITE
                        this.textSize  = 50f
                        typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        style     = Paint.Style.FILL
                        textAlign = Paint.Align.RIGHT
                    }
                    val itemStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color       = Color.BLACK
                        this.textSize    = 50f
                        typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        style       = Paint.Style.STROKE
                        strokeWidth = 3f
                        textAlign   = Paint.Align.RIGHT
                    }

                    // Draw the label inside the item, aligned to the right padding
                    val px = itemRect.right - padding
                    val py = itemRect.centerY() + itemFill.textSize/3
                    canvas.drawText(label, px, py, itemStroke)
                    canvas.drawText(label, px, py, itemFill)

                    topY += itemHeight + spacing
                }
            }
        }

        // Draw UI (score, timer, level) at the bottom.
        if (!levelCompleted && !levelFailed) {
            // Define margins and bar dimensions.
            val barMargin = 20f           // doubled from 20f.
            val bottomMargin = 20f          // doubled from 20f.
            // Using the factor from your previous code: (35f * 1.8f) was your base height; multiply by 2:
            val barHeight = 35f * 2.8f  // same for each bar.
            val gapBetweenBars = 16f        // doubled from 8f.
            val barMaxWidth = width - 2 * barMargin

            // Compute vertical positions for each bar. We want to show three bars (Level, Score, Time)
            // stacked from top (highest bar) to bottom. The bottom bar (Time) sits above bottomMargin.
            val timeBarTop = height - bottomMargin - barHeight
            val scoreBarTop = timeBarTop - gapBetweenBars - barHeight
            val levelBarTop = scoreBarTop - gapBetweenBars - barHeight


            // --- SHIFT UP LEVEL & SCORE ---
            canvas.save()
            canvas.translate(0f, -bannerHeightPx)

            // Level Bar (red)
            val levelFraction = (currentLevel / 15f).coerceIn(0f, 1f)
            val filledLevelWidth = levelFraction * barMaxWidth
            val levelBarPaint = Paint().apply { color = Color.rgb(255, 150, 0) }
            canvas.drawRect(barMargin, levelBarTop, barMargin + filledLevelWidth, levelBarTop + barHeight, levelBarPaint)

            val levelTextFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }
            val levelTextStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.STROKE
                strokeWidth = 4f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Level: $currentLevel", barMargin + 20f, levelBarTop + barHeight - 20f, levelTextStrokePaint)
            canvas.drawText("Level: $currentLevel", barMargin + 20f, levelBarTop + barHeight - 20f, levelTextFillPaint)

            // Score Bar (blue)
            val scoreFraction = (overallScore / 100000f).coerceIn(0f, 1f)
            val filledScoreWidth = scoreFraction * barMaxWidth
            val scoreBarPaint = Paint().apply { color = Color.BLUE }
            canvas.drawRect(barMargin, scoreBarTop, barMargin + filledScoreWidth, scoreBarTop + barHeight, scoreBarPaint)
            val scoreTextFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }
            val scoreTextStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.STROKE
                strokeWidth = 4f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Score: $overallScore", barMargin + 20f, scoreBarTop + barHeight - 20f, scoreTextStrokePaint)
            canvas.drawText("Score: $overallScore", barMargin + 20f, scoreBarTop + barHeight - 20f, scoreTextFillPaint)


            // Time Bar (interpolated color)
            val currentTime = System.currentTimeMillis()
            // if timerPaused, use the frozen pausedElapsed; otherwise use the live clock
            val rawElapsed = if (timerPaused) {
                pausedElapsed.toFloat() / 1000f
            } else {
                (currentTime - levelStartTime).toFloat() / 1000f
            }
            val elapsedTime = if (currentLevel == 1 && !timerStarted) 0f else rawElapsed
            val effectiveElapsed = if (elapsedTime > levelTimeLimit) levelTimeLimit.toFloat() else elapsedTime
            levelTimer = (levelTimeLimit - effectiveElapsed).toInt()

            // Compute timeFraction clamped to [0,1].
            val timeFraction = ((levelTimeLimit - effectiveElapsed) / levelTimeLimit).coerceIn(0f, 1f)

            val filledTimeWidth = timeFraction * barMaxWidth

            fun interpolateColor(start: Int, end: Int, fraction: Float): Int {
                val a = Color.alpha(start) + ((Color.alpha(end) - Color.alpha(start)) * fraction).toInt()
                val r = Color.red(start) + ((Color.red(end) - Color.red(start)) * fraction).toInt()
                val g = Color.green(start) + ((Color.green(end) - Color.green(start)) * fraction).toInt()
                val b = Color.blue(start) + ((Color.blue(end) - Color.blue(start)) * fraction).toInt()
                return Color.argb(a, r, g, b)
            }
            val green = Color.GREEN
            val yellow = Color.YELLOW
            val orange = Color.rgb(255, 165, 0)
            val red = Color.RED
            val timeBarColor: Int = when {
                timeFraction >= 0.66f -> {
                    val localFrac = (timeFraction - 0.66f) / (1f - 0.66f)
                    interpolateColor(yellow, green, localFrac)
                }
                timeFraction >= 0.33f -> {
                    val localFrac = (timeFraction - 0.33f) / (0.66f - 0.33f)
                    interpolateColor(orange, yellow, localFrac)
                }
                else -> {
                    val localFrac = timeFraction / 0.33f
                    interpolateColor(red, orange, localFrac)
                }
            }
            val timeBarPaint = Paint().apply { color = timeBarColor }
            canvas.drawRect(barMargin, timeBarTop, barMargin + filledTimeWidth, timeBarTop + barHeight, timeBarPaint)

            val timeTextFillPaint = Paint().apply {
                color = Color.RED
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }
            val timeTextStrokePaint = Paint().apply {
                color = Color.BLACK
                textSize = 70f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                style = Paint.Style.STROKE
                strokeWidth = 4f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Time: $levelTimer", barMargin + 20f, timeBarTop + barHeight - 20f, timeTextStrokePaint)
            canvas.drawText("Time: $levelTimer", barMargin + 20f, timeBarTop + barHeight - 20f, timeTextFillPaint)

            canvas.restore()
        }


        // Update game state if not paused.
        if (!startDialogShowing && (!gamePaused || levelFailed)) {
            updateGameState(canvas)
        }

        // draw & advance all floating scores
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.BLACK
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val iter = floatingScores.iterator()
        while (iter.hasNext()) {
            val fs = iter.next()

            // set current paint state
            stroke.textSize = fs.size
            stroke.alpha    = fs.alpha
            fill.textSize   = fs.size
            fill.alpha      = fs.alpha
            fill.color      = fs.color

            // draw stroke then fill
            canvas.drawText(fs.text, fs.x, fs.y, stroke)
            canvas.drawText(fs.text, fs.x, fs.y, fill)

            // advance position & fade & grow
            fs.x    += fs.dx      // horizontal drift
            fs.y    += fs.dy      // vertical drift (negative dy → upward)
            fs.alpha = (fs.alpha - 2).coerceAtLeast(0)  // fade 2 units/frame (slower)
            fs.size  *= 1.02f     // grow 2% per frame

            // remove when invisible
            if (fs.alpha == 0) iter.remove()
        }

        // Draw balls.
        for (ball in balls) {
            if (!ball.active) continue
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
            val shadowOffset = ball.radius / 3
            canvas.drawCircle(ball.x + shadowOffset, ball.y + shadowOffset, ball.radius, shadowPaint)
            val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            ballPaint.shader = RadialGradient(
                ball.x - ball.radius / 4,
                ball.y - ball.radius / 4,
                ball.radius,
                intArrayOf(Color.WHITE, Color.DKGRAY),
                floatArrayOf(0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(ball.x, ball.y, ball.radius, ballPaint)
        }
        postInvalidateOnAnimation()
    }

    // --- Updated Game Physics and Snapping (with collision handling improvements).
    private fun updateGameState(canvas: Canvas) {
        val now = System.currentTimeMillis()

        // 1) Timer
        val rawElapsed = (now - levelStartTime) / 1000f
        val elapsedTime = if (currentLevel == 1 && !timerStarted) 0f else rawElapsed
        levelTimer = (levelTimeLimit - elapsedTime.coerceAtMost(levelTimeLimit.toFloat())).toInt()

        val maxBalls = getCountForLevel(currentLevel)
        // 2) Score from placed balls
        val placedPts = balls.take(maxBalls).sumOf { if (it.placed) it.basePoints * currentLevel else 0 }
        overallScore = (levelStartScore + placedPts).coerceAtLeast(0).also {
            if (levelStartScore + placedPts < 0) scoreFrozen = true
        }

        // 3) Vortex animation
        val laps = 3
        val totalAngle = laps * 2 * Math.PI.toFloat()
        val step = (2 * Math.PI.toFloat()) / 60f
        val decay = 0.98f
        balls.forEach { ball ->
            if (!ball.orbiting) return@forEach
            ball.orbitAngle += step
            ball.orbitAccum += step
            ball.orbitRadius *= decay
            ball.x = ball.orbitCenterX + cos(ball.orbitAngle) * ball.orbitRadius
            ball.y = ball.orbitCenterY + sin(ball.orbitAngle) * ball.orbitRadius
            if (ball.orbitAccum >= totalAngle) {
                ball.orbiting = false
                ball.placed   = true
                ball.x = ball.orbitCenterX
                ball.y = ball.orbitCenterY
                // playSound("pwa")
            }
        }

        // 4) Physics
        val snapFactor = when (currentLevel) {
            1,2 -> 2.5f
            3,4   -> 2.25f
            5   -> 2.0f
            6   -> 1.75f
            7,10  -> 1.5f
            else-> 1.0f
        }
        val sens = when (currentLevel) {
            1 -> 0.1f; // one-by-one balls, snap-out not possible
            2 -> 0.2f; // 5 balls, unsnap not possible
            3-> 0.3f; // 6 balls, unsnap not possible
            4-> 0.3f; // 7 balls, unsnap not possible
            5-> 0.5f; // 8 balls, unsnap not possible
            6-> 0.5f; // 9 balls, unsnap not possible
            7,11 -> 0.7f; // 5-9 balls, unsnap possible
            else -> 1.0f
        }
        balls.forEach { ball ->
            if (!ball.active || ball.placed || ball.orbiting) return@forEach
            val mf = (balls.minOf { it.radius } / ball.radius) * 0.8f
            ball.vx = (ball.vx + (-accelX * sens)*mf) * friction
            ball.vy = (ball.vy + ( accelY * sens)*mf) * friction
            ball.x += ball.vx; ball.y += ball.vy
            if (ball.x < ball.radius) { ball.x = ball.radius; ball.vx = -ball.vx }
            else if (ball.x > width - ball.radius) { ball.x = width - ball.radius; ball.vx = -ball.vx }
            if (ball.y < ball.radius) { ball.y = ball.radius; ball.vy = -ball.vy }
            else if (ball.y > height - ball.radius) { ball.y = height - ball.radius; ball.vy = -ball.vy }
        }

        // 5) Collision
        for (i in balls.indices) for (j in i+1 until balls.size) {
            val b1 = balls[i]; val b2 = balls[j]
            if (!b1.active||!b2.active) continue
            val dx = b2.x - b1.x; val dy = b2.y - b1.y
            val dist = hypot(dx,dy)
            if (dist >= b1.radius + b2.radius) continue

            if (currentLevel <= 10 && (b1.placed xor b2.placed)) {
                // bounce moving off placed
                val (imm,mov) = if(b1.placed) b1 to b2 else b2 to b1
                val ddx = mov.x-imm.x; val ddy = mov.y-imm.y
                val d = hypot(ddx,ddy).coerceAtLeast(0.0001f)
                val nx = ddx/d; val ny = ddy/d
                val dot = mov.vx*nx + mov.vy*ny
                mov.vx -= 2*dot*nx; mov.vy -= 2*dot*ny
                playBallSound(mov)
                val overlap = (b1.radius+b2.radius)-dist
                mov.x += nx*overlap; mov.y += ny*overlap
            } else {
                // unsnap only levels ≥ 7
                if (currentLevel >= 11) listOf(b1,b2).forEach { bb ->
                    if (bb.placed) {
                        bb.placed = false
                        bb.canSnap = false
                        handler.postDelayed({ bb.canSnap = true }, 500)
                        playSound("pwa")
                    }

                }
                // elastic swap
                val tvx=b1.vx; val tvy=b1.vy
                b1.vx=b2.vx; b1.vy=b2.vy; b2.vx=tvx; b2.vy=tvy
                playBallSound(b1); playBallSound(b2)
                val overlap = (b1.radius+b2.radius)-dist
                val ax=(dx/dist)*overlap/2f; val ay=(dy/dist)*overlap/2f
                b1.x-=ax; b1.y-=ay; b2.x+=ax; b2.y+=ay

            }
        }

        // 6) Snapping / VORTEX ENTRY
        // A) Start with any holes already spoken for by balls
        val reservedHoles = mutableSetOf<Int>().apply {
            balls.forEach { b ->
                if ((b.orbiting || b.placed) && b.snappedHole != null)
                    add(b.snappedHole!!)
            }
        }

        for ((idx, ball) in balls.take(maxBalls).withIndex()) {
            // only active, not‐placed, not‐orbiting balls
            if (!ball.active || ball.placed || ball.orbiting) continue
            // on ≥Level 11 also respect canSnap
            if (currentLevel >= 11 && !ball.canSnap) continue

            // B) Map _this_ ball → the list of holes it may target
            val rawAllowed: List<Int> = when (idx) {
                0   -> listOf(0)
                1   -> listOf(1, 5, 9)
                2   -> listOf(2, 6, 10)
                3   -> listOf(3, 7, 11)
                4   -> listOf(4, 8, 12)
                5   -> listOf(1, 5, 9)
                6   -> listOf(2, 6, 10)
                7   -> listOf(3, 7, 11)
                8   -> listOf(4, 8, 12)
                9   -> listOf(1, 5, 9)
                10  -> listOf(2, 6, 10)
                11  -> listOf(3, 7, 11)
                12  -> listOf(4, 8, 12)
                else-> emptyList()
            }


            // C) Filter out holes that don't exist or are already taken
            val allowed = rawAllowed
                .filter { it in holePositions.indices }
                .filter { it !in reservedHoles }
            if (allowed.isEmpty()) continue

            // D) Try each allowed hole; as soon as one snaps, reserve it and break
            for (h in allowed) {
                val (fx, fy) = holePositions[h]
                val hx = if (holePositionsAbsolute) fx else fx * width
                val hy = if (holePositionsAbsolute) fy else fy * height

                val dxB = ball.x - hx
                val dyB = ball.y - hy
                val distB  = hypot(dxB, dyB)
                val speedB = hypot(ball.vx, ball.vy)
                val thresh = if (h == 0) 50f * snapFactor else ball.radius * snapFactor

                if ((distB < thresh && speedB < 1f) || distB < 10f) {
                    // mark & reserve this hole
                    ball.snappedHole = h
                    reservedHoles.add(h)

                    // begin the vortex animation
                    playBellSound(ball)
                    ball.orbiting     = true
                    ball.orbitCenterX = hx
                    ball.orbitCenterY = hy
                    ball.orbitRadius  = distB
                    ball.orbitAngle   = atan2(dyB, dxB)
                    ball.orbitAccum   = 0f

                    // Floating score pop
                    val pts  = ball.basePoints * currentLevel
                    val ang  = Random.nextDouble(-Math.PI, -Math.PI / 3)
                    val spd  = 9f
                    val dxS  = (cos(ang) * spd).toFloat()
                    val dyS  = (sin(ang) * spd).toFloat()
                    val cidx = h.coerceAtMost(floatingColors.lastIndex)
                    floatingScores += FloatingScore(hx, hy, "+$pts", 255, 32f * 3, dxS, dyS, floatingColors[cidx])

                    // Start timer on level 1 first snap
                    if (currentLevel == 1 && !timerStarted) {
                        timerStarted    = true
                        levelStartTime  = System.currentTimeMillis()
                    }
                    // Unfreeze score if needed
                    if (scoreFrozen) {
                        scoreFrozen     = false
                        levelStartScore = overallScore
                        levelStartTime  = System.currentTimeMillis()
                    }
                    break  // only snap once
                }
            }
        }

        // 7) Spawning
        if (currentLevel==1) {
            if (ballSpawnIndex>0 && ballSpawnIndex<maxBalls && balls[ballSpawnIndex-1].placed) {
                balls[ballSpawnIndex].active=true
                playBallSound(balls[ballSpawnIndex])
                ballSpawnIndex++
            }
        } else {
            if (ballSpawnIndex<maxBalls && now - levelStartTime >= ballSpawnIndex*spawnDelay) {
                if (!balls[ballSpawnIndex].active) {
                    balls[ballSpawnIndex].active=true
                    playBallSound(balls[ballSpawnIndex])
                    ballSpawnIndex++
                }
            }
        }

        // 8) Failure
        if (levelTimer<=0 && !levelCompleted && !levelFailed) {
            levelFailed=true; gamePaused=true
        }
        if (levelFailed) {
            balls.forEach {
                if (it.active) {
                    val tx=width-it.radius; val ty=it.radius
                    it.x+=(tx-it.x); it.y+=(ty-it.y)
                    if (hypot(it.x-tx, it.y-ty)<5f) it.active=false
                }
            }
            showHoles = false


            if (balls.none{it.active} && !failureAlertShown) {
                failureAlertShown=true
                playSound("fail")
                // Create a Runnable for showing the "Time's Up!" dialog.
                failureDialogRunnable = Runnable {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(Html.fromHtml("<font color='#FFFFFF'>Time's up!</font>", Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Start New Game") { _, _ -> newGame() }
                        .setNegativeButton("Restart last Level $currentLevel at Score: $levelStartScore") { _, _ -> restartLevel() }
                        .setCancelable(false)
                        .create()
                    dialog.window?.apply {
                        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setDimAmount(0f)
                        val bannerHeightPx = AdSize.BANNER.getHeightInPixels(context)
                        attributes = attributes.apply {
                            y = bannerHeightPx
                        }
                    }
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            setTextColor(Color.GREEN)
                            setShadowLayer(2f, 4f, 4f, Color.BLACK)
                            setTextSize(20f)
                        }
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                            setTextColor(Color.YELLOW)
                            setShadowLayer(2f, 4f, 4f, Color.BLACK)
                            setTextSize(20f)
                        }
                    }
                    dialog.show()
                }
                sensorManager.unregisterListener(this)
                // Post the failure dialog runnable after a delay.
                handler.postDelayed(failureDialogRunnable!!, 1000)
            }
        }

        // 9) Completion
        // compute banner height in px
        val bannerHeightPx = AdSize.BANNER.getHeightInPixels(context).toFloat()

        if (ballSpawnIndex>=maxBalls && balls.take(maxBalls).all{it.placed}
            && levelTimer>0 && !levelCompleted) {

            // canvas.save()
            // canvas.translate(0f, -bannerHeightPx)

            // 👇 recalc now that every ball is definitely placed
            val placedPts = balls.take(maxBalls).sumOf { it.basePoints * currentLevel }
            overallScore = levelStartScore + placedPts

            levelCompleted=true;
            gamePaused=true
            sensorManager.unregisterListener(this)
            playSound("success")
            levelStartListener?.onLevelCompleted(currentLevel)
            showHoles = false
            canvas.save()
            canvas.translate(0f, -bannerHeightPx)

            if (currentLevel != 19) {
                handler.postDelayed({
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(Html.fromHtml("<font color='green'>Level $currentLevel completed!<br>Score: $overallScore</font>", Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Play Next Level") { _, _ -> nextLevel() }
                        .setCancelable(false)
                        .create()
                    dialog.window?.apply {
                        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setDimAmount(0f)
                        val bannerHeightPx = AdSize.BANNER.getHeightInPixels(context)
                        attributes = attributes.apply {
                            y = bannerHeightPx
                        }
                    }
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            setTextColor(Color.RED)
                            setShadowLayer(2f, 4f, 4f, Color.BLACK)
                            setTextSize(32f)
                        }
                    }
                    dialog.show()
                }, 1000)
            } else {
                sensorManager.unregisterListener(this)
                handler.postDelayed({
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(Html.fromHtml("<font color='#FFFFFF'>Congratulations!!!</font>", Html.FROM_HTML_MODE_LEGACY))
                        .setMessage(Html.fromHtml("<font color='green'>You have reached the highest level.\n" +
                                "Do you want to start from the very beginning?</font>", Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Yes") { _, _ -> newGame() }
                        .setNegativeButton("No") { _, _ -> (context as Activity).finish() }
                        .setCancelable(false)
                        .show()
                    dialog.window?.apply {
                        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setDimAmount(0f)
                        val bannerHeightPx = AdSize.BANNER.getHeightInPixels(context)
                        attributes = attributes.apply {
                            y = bannerHeightPx
                        }
                    }
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            setTextColor(Color.RED)
                            setShadowLayer(2f, 4f, 4f, Color.BLACK)
                            setTextSize(32f)
                        }
                    }
                    dialog.show()
                }, 1000)
                return
            }
            canvas.restore()
        }
    }



    private fun nx(overlap: Float, dx: Float, distance: Float): Float = (dx / distance) * overlap
    private fun ny(overlap: Float, dy: Float, distance: Float): Float = (dy / distance) * overlap

    private fun playBallSound(ball: Ball) {
        val index = balls.indexOf(ball)
        when (index) {
            0 -> playSoundThrottled("ball1")
            1 -> playSoundThrottled("ball2")
            2 -> playSoundThrottled("ball3")
            3 -> playSoundThrottled("ball4")
            4 -> playSoundThrottled("ball5")

            5 -> playSoundThrottled("ball2")
            6 -> playSoundThrottled("ball3")
            7 -> playSoundThrottled("ball4")
            8 -> playSoundThrottled("ball5")

            9 -> playSoundThrottled("ball2")
            10 -> playSoundThrottled("ball3")
            11 -> playSoundThrottled("ball4")
            12 -> playSoundThrottled("ball5")
        }
    }

    private fun playBellSound(ball: Ball) {
        // find its index among the first five snap-targets
        // val idx = balls.indexOf(ball).coerceIn(0, 4)
        // soundPool.play(soundIds["bell_$idx"] ?: 0,1f, 1f, 0, 0, 1f
        val idx = balls.indexOf(ball)
        when (idx) {
            0 -> soundPool.play(soundIds["bell_0"] ?: 0, 1f, 1f, 0, 0, 1f)
            1 -> soundPool.play(soundIds["bell_1"] ?: 0, 1f, 1f, 0, 0, 1f)
            2 -> soundPool.play(soundIds["bell_2"] ?: 0, 1f, 1f, 0, 0, 1f)
            3 -> soundPool.play(soundIds["bell_3"] ?: 0, 1f, 1f, 0, 0, 1f)
            4 -> soundPool.play(soundIds["bell_4"] ?: 0, 1f, 1f, 0, 0, 1f)
            5 -> soundPool.play(soundIds["bell_1"] ?: 0, 1f, 1f, 0, 0, 1f)
            6 -> soundPool.play(soundIds["bell_2"] ?: 0, 1f, 1f, 0, 0, 1f)
            7 -> soundPool.play(soundIds["bell_3"] ?: 0, 1f, 1f, 0, 0, 1f)
            8 -> soundPool.play(soundIds["bell_4"] ?: 0, 1f, 1f, 0, 0, 1f)
            9 -> soundPool.play(soundIds["bell_1"] ?: 0, 1f, 1f, 0, 0, 1f)
            10 -> soundPool.play(soundIds["bell_2"] ?: 0, 1f, 1f, 0, 0, 1f)
            11 -> soundPool.play(soundIds["bell_3"] ?: 0, 1f, 1f, 0, 0, 1f)
            12 -> soundPool.play(soundIds["bell_4"] ?: 0, 1f, 1f, 0, 0, 1f)

        }
    }

    private fun playSoundThrottled(key: String, left: Float = 1f, right: Float = 1f, priority: Int = 0, loop: Int = 0, rate: Float = 1f) {
        val now = System.currentTimeMillis()
        val last = lastSoundTimes[key] ?: 0L
        if (now - last >= 1000L) {
            soundPool.play(soundIds[key] ?: return, left, right, priority, loop, rate)
            lastSoundTimes[key] = now
        }
    }

    private fun playSound(key: String) {
        soundPool.play(soundIds[key] ?: 0, 1f, 1f, 0, 0, 1f)
    }

    private fun startLevel(resume: Boolean = false) {
        gamePaused = false
        levelFailed = false
        levelCompleted = false
        showHoles = false

        failureAlertShown = false
        scoreFrozen = true
        timerStarted = (currentLevel != 1)

        if (resume) {
            // on a resume, use your loaded overallScore as the baseline
            levelStartScore = overallScore
            // levelStartTime was already loaded from prefs in loadGameState()
        } else {
            // brand‑new level: snapshot score & reset timer
            levelStartScore = overallScore
            levelStartTime  = System.currentTimeMillis()
        }
        levelTimeLimit = when (currentLevel) {
            in 1..2 -> 90
            in 3..10 -> 60
            11, 13 -> 50 // 5-7 balls
            14, 15 -> 40 // 8-9 balls
            16, 19 -> 30 // 10-13 balls
            else -> 0
        }
        ballSpawnIndex = 0
        // Initialize ball positions: start from the upper-left.
        for (ball in balls) {
            ball.x = ball.radius
            ball.y = ball.radius
            ball.vx = if (currentLevel <= 2) Random.nextFloat() * 5f - 2.5f else Random.nextFloat() * 7f - 4f
            ball.vy = if (currentLevel <= 2) Random.nextFloat() * 5f - 2.5f else Random.nextFloat() * 7f - 4f
            ball.placed = false
            ball.active = false
        }
        // For level 1, activate the first ball.
        if (currentLevel == 1) {
            balls[0].active = true
            ballSpawnIndex = 1
            playBallSound(balls[0])
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        levelStartListener?.onLevelStarted(currentLevel)

        levelTimeLimit = getTimeLimitForLevel(currentLevel)

    }

    private fun nextLevel() {
        sensorManager.unregisterListener(this)
        currentLevel++
        showHoles = false
        startLevel()
    }

    private fun restartLevel() {
        sensorManager.unregisterListener(this)
        overallScore = levelStartScore
        showHoles = false
        startLevel()
    }

    private fun newGame() {
        sensorManager.unregisterListener(this)
        showHoles = false
        overallScore = 0
        currentLevel = 1
        orientationOffset = 0
        baselineSet = true
        baselineX = 0f
        baselineY = 0f
        resumeGame()
        startLevel()
    }

    private fun saveGameState() {
        prefs.edit()
            .putInt("overallScore", overallScore)
            .putInt("currentLevel", currentLevel)
            .putLong("levelStartTime", levelStartTime)
            .apply()
    }

    private fun loadGameState() {
        showHoles = false
        val savedScore = prefs.getInt("overallScore", -1)
        val savedLevel = prefs.getInt("currentLevel", -1)
        val savedLevelStartTime = prefs.getLong("levelStartTime", 0L)
        if (savedScore >= 0 && savedLevel >= 0) {
            overallScore = savedScore
            currentLevel = savedLevel
            if (savedLevelStartTime != 0L) {
                levelStartTime = savedLevelStartTime
            }
        }
    }

    private fun showStartDialog() {
        sensorManager.unregisterListener(this)
        showHoles = false
        handler.post {
            startDialogShowing = true

            val savedScore = prefs.getInt("overallScore", 0)
            val savedLevel = prefs.getInt("currentLevel", 1)
            val builder = AlertDialog.Builder(context)
                .setTitle(Html.fromHtml("<font color='#FFFFFF'>AimGame</font>", Html.FROM_HTML_MODE_LEGACY))

            if (prefs.contains("overallScore") && prefs.contains("currentLevel")) {
                builder
                    .setMessage(Html.fromHtml("<font color='#FFFFFF'></font>", Html.FROM_HTML_MODE_LEGACY))
                    // 2. Use the local savedScore/savedLevel in the label:
                    .setPositiveButton("Continue last session at Score: $savedScore, Level: $savedLevel") { _, _ ->
                        // 3. Now restore into your fields and start:
                        failureDialogRunnable?.let { handler.removeCallbacks(it) }
                        failureAlertShown = false
                        overallScore = savedScore
                        currentLevel = savedLevel
                        orientationOffset = 0
                        baselineSet = true
                        baselineX = 0f
                        baselineY = 0f
                        startDialogShowing = false
                        startLevel(resume = true)
                    }
                    .setNegativeButton("New Game") { _, _ ->
                        failureDialogRunnable?.let { handler.removeCallbacks(it) }
                        failureAlertShown = false
                        overallScore = 0
                        currentLevel = 1
                        orientationOffset = 0
                        baselineSet = true
                        baselineX = 0f
                        baselineY = 0f
                        startDialogShowing = false
                        startLevel(resume = false)
                    }
            } else {
                builder.setMessage(Html.fromHtml("<font color='#FFFFFF'></font>", Html.FROM_HTML_MODE_LEGACY))
                    .setPositiveButton("Start") { _, _ ->
                        failureDialogRunnable?.let { handler.removeCallbacks(it) }
                        failureAlertShown = false
                        overallScore = 0
                        currentLevel = 1
                        orientationOffset = 0
                        baselineSet = true
                        baselineX = 0f
                        baselineY = 0f
                        startDialogShowing = false
                        startLevel(resume = false)
                    }
            }
            val dialog = builder.setCancelable(false).create()
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.BOTTOM)
                val screenHeight = context.resources.displayMetrics.heightPixels
                val params = attributes
                params.y = screenHeight / 7
                attributes = params
            }
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    setTextColor(Color.GREEN)
                    setShadowLayer(2f, 4f, 4f, Color.BLACK)
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                    setTextColor(Color.GREEN)
                    setShadowLayer(2f, 4f, 4f, Color.BLACK)
                }
            }
            dialog.show()
        }
    }

    fun pauseGame() {
        if (!timerPaused) {
            // record how many ms have passed so far
            pausedElapsed = System.currentTimeMillis() - levelStartTime
        }
        timerPaused = true
        gamePaused = true
        saveGameState()
        sensorManager.unregisterListener(this)
    }

    fun resumeGame() {
        if (timerPaused) {
            // restart the clock so that (now – levelStartTime) == pausedElapsed
            levelStartTime = System.currentTimeMillis() - pausedElapsed
        }
        gamePaused = false
        timerPaused = false
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            // Clear the failure flags so that updateGameState() does not reschedule the failure dialog.
            levelFailed = false
            failureAlertShown = false
            // Optionally, if you want to restart timing on resume (so that the failure condition does not hold immediately),
            // you might reset levelStartTime here.
            // levelStartTime = System.currentTimeMillis()
            // Now resume sensors, etc.
            if (!startDialogShowing) {
                resumeGame()
            }
        } else {
            // When hidden, cancel any pending dialogs.
            handler.removeCallbacksAndMessages(null)
            pauseGame()
        }
    }

    // (Other functions such as onSensorChanged, level management, save/load dialogs remain as previously.)
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val rawX = event.values[0]
            val rawY = event.values[1]

            if (!baselineSet) {
                baselineX = rawX
                baselineY = rawY
                baselineSet = true
                Log.d("SensorCalib", "Calibrated baseline: ($baselineX, $baselineY) for orientationOffset $orientationOffset")
            }

            // Compute effective (calibrated) sensor values.
            val calibratedX = rawX - baselineX
            val calibratedY = rawY - baselineY

            accelX = calibratedX
            accelY = calibratedY
            Log.d("Sensor", "Raw: ($rawX, $rawY); Calibrated: ($calibratedX, $calibratedY); offset: $orientationOffset")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}
