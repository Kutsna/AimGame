package com.veryeasygames.aimgame

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random
import kotlin.system.exitProcess
import android.util.AttributeSet
import com.google.android.gms.ads.AdSize
import android.graphics.RectF
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.min


class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,

) : View(context, attrs, defStyle), SensorEventListener {
    private val designWidth = 1080f
    private var scaleFactor = 1f
    lateinit var billingManager: BillingManager

    interface OnTimeUpListener {
        fun onTimeUpDialogShown()
    }
    var timeUpListener: OnTimeUpListener? = null

    var showPlayNextUI = false
    var levelStartListener: LevelStartListener? = null
    // var levelFailedListener: LevelFailedListener? = null
    var showTimer = false
    // val typeface = ResourcesCompat.getFont(context, R.font.constantia)

    var removeAds: Boolean = false
    var removeTimeLimit: Boolean = false

    private var optionsButtonPressed = false
    private var pressedDropdownIndex = -1   // -1 = none

    interface LevelStartListener {
        fun onLevelStarted(currentLevel: Int)
        fun onLevelCompleted(currentLevel: Int)
    }
    interface LevelFailedListener {
        fun onLevelFailed(currentLevel: Int)
    }

    var levelFailedListener: LevelFailedListener? = null

    private fun triggerLevelFailure() {
        // whenever your game detects a “fail”:
        levelFailedListener?.onLevelFailed(currentLevel)
    }

    var showHoles: Boolean = false

    // Sensor and physics.
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var accelX = 0f * scaleFactor
    private var accelY = 0f * scaleFactor
    private val friction = 0.99f * scaleFactor

    // --- Reordered Balls Array:
    // Balls are arranged so that index 4 (the central ball) is the largest.
    private val balls = arrayOf(
        // Index 0: central red
        Ball(0f, 0f, 0f, 0f, 38f * 1.6f * scaleFactor, basePoints = 100),
        // Yellow ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 30f * 1.45f * scaleFactor, basePoints = 200),
        // Green ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f * scaleFactor, basePoints = 300),
        // Gold ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f * scaleFactor, basePoints = 400),
        // Violet ring → 2 balls
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f * scaleFactor, basePoints = 500),

        Ball(0f, 0f, 0f, 0f, 30f * 1.45f * scaleFactor, basePoints = 200),
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f * scaleFactor, basePoints = 300),
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f * scaleFactor, basePoints = 400),
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f * scaleFactor, basePoints = 500),

        Ball(0f, 0f, 0f, 0f, 30f * 1.45f * scaleFactor, basePoints = 200),
        Ball(0f, 0f, 0f, 0f, 24f * 1.35f * scaleFactor, basePoints = 300),
        Ball(0f, 0f, 0f, 0f, 20f * 1.25f * scaleFactor, basePoints = 400),
        Ball(0f, 0f, 0f, 0f, 16f * 1.15f * scaleFactor, basePoints = 500)
    )

    // Data class for a ball.
    data class Ball(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val baseRadius: Float,
        var radius: Float = baseRadius,
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
    var currentLevel = 1

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
        level in 11..13  -> 60 // - 10 * (level - 11)  // 12→50,13→40,14→30,15→20
        level in 14..15  -> 45
        level in 16..19  -> 30
        else             -> 0
    }

    private val floatingColors = arrayOf(
        Color.RED,     // central (index 0)
        Color.YELLOW,  // index 1
        Color.GREEN,   // index 2
        Color.rgb(255, 150, 0), // index 3
        Color.MAGENTA     // index 4
    )

    var soundEnabled: Boolean = true


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
        if (currentLevel <= 6) {
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
            val sector = (2 * Math.PI / 4).toFloat() // (2 * Math.PI / 3) would be 120 degrees.
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
    private var levelStartTime: Long = 0L
    private var pausedElapsed: Long = 0L
    private var timerPaused: Boolean = false
    private var levelTimer: Int = 0
    private var overallScore = 0 // overall score so far
    private var levelStartScore = 0 // score at the beginning of the level
    private var ballSpawnIndex = 0 // sequential ball spawning index
    private val spawnDelay = 500L // delay between ball spawns (ms)
    private var levelFailed = false
    private var levelCompleted = false
    var gamePaused = false
    private var timerStarted = false
    private var scoreFrozen = false
    private var failureAlertShown = false
    private var startDialogShowing = false
    var firstSnapOccurred: Boolean = false

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
    private val optionsList = listOf("AllOptions") // , "Set Tilt", "Set Flat")

    private var baselineSet = false
    private var baselineX = 0f * scaleFactor
    private var baselineY = 0f * scaleFactor

    // Loaded once when GameView is constructed
    private var highScore: Int = prefs.getInt("HIGH_SCORE", 0)

    // store the height of the system bars (status+cutout)
    private var topInset = 0


    init {
        keepScreenOn = true
        showHoles = false
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        // ask Android to send us our WindowInsets once attached
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            insets
        }
        // trigger the first insets dispatch
        ViewCompat.requestApplyInsets(this)

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
        soundIds["tick"] = soundPool.load(context, R.raw.tick, 1)
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
        // scale everything so that designWidth → actual width
        super.onSizeChanged(w, h, oldw, oldh)
        scaleFactor = w / designWidth
        balls.forEach { it.radius = it.baseRadius * scaleFactor

        optionsButtonRect = RectF(20f * scaleFactor, h - 500f * scaleFactor, 420f * scaleFactor, h - 400f * scaleFactor)
        saveExitButtonRect = RectF(w - 220f * scaleFactor, 20f * scaleFactor, w - 20f * scaleFactor, 120f * scaleFactor)
        pauseButtonRect = RectF(20f * scaleFactor, 20f * scaleFactor, 220f * scaleFactor, 120f * scaleFactor)
        }
    }



    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (event.action == MotionEvent.ACTION_DOWN) {
            // Don’t allow Options while any blocking dialog is up
            if (startDialogShowing || levelFailed || levelCompleted) {
                return super.onTouchEvent(event)
            }

            if (optionsButtonRect.contains(x, y)) {
                // instead of: optionsExpanded = true; invalidate()
                // simply call your existing function:
                if (soundEnabled) {
                    playSound("tick")
                }
                pauseGame()
                allOptions()
                return true
            }

            // 1) Check if the Options button itself was tapped
            if (optionsButtonRect.contains(event.x, event.y)) {
                if (!gamePaused) pauseGame() else resumeGame()
                optionsExpanded = !optionsExpanded
                invalidate()
                return true
            }


        }
        return super.onTouchEvent(event)
    }

    // helper extension to make a color darker
    private fun Int.darker(factor: Float = 0.8f): Int {
        val a = Color.alpha(this)
        val r = (Color.red(this) * factor).toInt().coerceAtLeast(0)
        val g = (Color.green(this) * factor).toInt().coerceAtLeast(0)
        val b = (Color.blue(this) * factor).toInt().coerceAtLeast(0)
        return Color.argb(a, r, g, b)
    }

    // Orientation offset; used in onSensorChanged to adjust accelerometer values.
    var orientationOffset = -45  // 0: Flat (original)

    // New: how big to draw the Hi Score (1f = normal size)
    private var hiScoreScale = 1f * scaleFactor

    // Call this whenever you detect a new high score
    fun animateHiScoreBounce() {
        ValueAnimator.ofFloat(1f * scaleFactor, 2.2f * scaleFactor, 1f * scaleFactor).apply {
            if (soundEnabled) {
                playSoundThrottled("ball1")
                playSoundThrottled("ball4")
            }
            duration = 1_000L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                hiScoreScale = animation.animatedValue as Float
                invalidate()    // redraw with the updated scale
            }
            start()
        }
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK       // or whatever hole color you want
        style = Paint.Style.STROKE  // or STROKE if you just want an outline
    }


    @SuppressLint("DiscouragedApi")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // compute banner height in px
        val bannerHeightPx = AdSize.LARGE_BANNER.getHeightInPixels(context).toFloat()
        val floorY = height - bannerHeightPx

        // 1) Compute screen size in dp
        val dm = resources.displayMetrics
        val widthDp  = width  / dm.density
        val heightDp = height / dm.density
        val baseDp = min(widthDp, heightDp)

        val w    = width.toFloat()
        val h    = height.toFloat()
        val scale = minOf(w, h) / 110f    // exactly your vector viewport height
        val holeRadiusDp = 0.6f             // or whatever dp you want your hole radius to be
        val holeRpx = holeRadiusDp * resources.displayMetrics.density * scale

        for ((fx, fy) in holePositions) {
            val cx = if (holePositionsAbsolute) fx else fx * w
            val cy = if (holePositionsAbsolute) fy else fy * h
            canvas.drawCircle(cx, cy, holeRpx, holePaint)
        }

        // ─── SHOW TILT INSTRUCTION UNTIL 1st BALL SNAPS ────────────────────────────
        if (currentLevel == 1 && !balls[0].placed) {
            val fullText = "Tilt your phone/tablet to\ndrop the ball into hole.\n\nTo adjust position hold your phone\n" +
                    "    comfortably tilted - use Set Tilt."
                        .trimIndent()
            val lines = fullText.split("\n")



            // 2) Use the smaller dp dimension to pick a % (e.g. 5%)
            val fraction = if (baseDp < 500f) {
                0.04f   // phones & small tablets: 5% of baseDp
            } else {
                0.03f  // large tablets: only 2.5%
            }
            val textSp = baseDp * fraction   // 5% of smaller axis, in dp

            val instructPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.CYAN
                textSize  = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    textSp,
                    dm
                )
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }
            // starting Y position (20% down)
            var y = height * 0.07f

            // draw each line, advancing by font spacing
            lines.forEach { line ->
                canvas.drawText(line, width / 2f, y, instructPaint)
                y += instructPaint.fontSpacing
            }
        }


        if (showHoles) {
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
                strokeCap = Paint.Cap.ROUND
            }
            val holeRadius = 1f * scale

            holePositions.forEachIndexed { i, (fx, fy) ->
                if (currentLevel >= 11 && i >= ballSpawnIndex) return@forEachIndexed

                val x = if (holePositionsAbsolute) fx else fx * width * scaleFactor
                // shift down by the banner thickness
                val y = if (holePositionsAbsolute) fy else fy * height * scaleFactor

                canvas.drawCircle(x, y, holeRadius, holePaint)
            }
        }

        // --- DRAW OPTIONS BUTTON & DROPDOWN at top-right ---
        if (!startDialogShowing && !levelFailed && !levelCompleted) {
            // 1) Metrics & sizing
            // val margin    = 16f * scaleFactor
            // get the OS status-bar height

            val padding   = 20f * scaleFactor                                    // inside button padding
            val textSize  = 62f * scaleFactor

            val bggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // if pressed, dark grey; otherwise normal
                color = if (optionsButtonPressed) Color.DKGRAY.darker() else Color.DKGRAY
            }
            canvas.drawRoundRect(optionsButtonRect, 16f * scaleFactor, 16f * scaleFactor, bggPaint)
            // your existing textSize
            val textLabel = " Ξ "

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
                strokeWidth = 4f * scaleFactor
                textAlign   = Paint.Align.CENTER
            }

            // Measure text width
            val textWidth = strokePaint.measureText(textLabel)
            val btnWidth  = if (baseDp < 500f) {
                150f * scaleFactor
            } else {
                200f * scaleFactor // large tablets
            }
            val btnHeight = if (baseDp < 500f) {
                90f * scaleFactor
            } else {
                130f * scaleFactor // large tablets
            }

            // flush‐right, but **offset vertically** by exactly topInset
            optionsButtonRect.set(
                width  - btnWidth,                    // left
                topInset.toFloat(),                   // top
                width.toFloat(),                      // right
                topInset + btnHeight                  // bottom
            )



            // Draw the label (stroke then fill), vertically centered
            val textX = optionsButtonRect.centerX()
            val textY = optionsButtonRect.centerY() + textSize/3
            canvas.drawText(textLabel, textX, textY, strokePaint)
            canvas.drawText(textLabel, textX, textY, fillPaint)

            // 2) If expanded, draw the dropdown *below* the button
            if (optionsExpanded) {
                val itemHeight = 80f * scaleFactor
                val spacing    = 8f  * scaleFactor
                var topY       = optionsButtonRect.bottom + spacing

                for ((i, label) in optionsList.withIndex()) {
                    val itemRect = RectF(
                        optionsButtonRect.left,
                        topY,
                        optionsButtonRect.right,
                        topY + itemHeight
                    )
/*
                    // pick dark gray if pressed, otherwise normal gray
                    val bgColor = if (i == pressedDropdownIndex) Color.DKGRAY else Color.GRAY

                    val itemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = bgColor
                        alpha = 220
                    }
                    canvas.drawRoundRect(itemRect, 12f * scaleFactor, 12f * scaleFactor, itemBgPaint)

 */

                    // Text paints for items (right-aligned)
                    val itemFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color     = Color.WHITE
                        this.textSize  = 50f * scaleFactor
                        typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        style     = Paint.Style.FILL
                        textAlign = Paint.Align.RIGHT
                    }
                    val itemStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color       = Color.BLACK
                        this.textSize    = 50f * scaleFactor
                        typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        style       = Paint.Style.STROKE
                        strokeWidth = 3f * scaleFactor
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

        // 3) Draw the SCORE text in an exotic font, centered at ¼-screen down───
        run {
            // Prepare an “exotic” paint for the score
            val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Pick a “fancier” built-in typeface; you can replace "cursive" with any installed font name
                typeface = Typeface.create("constantia", Typeface.BOLD_ITALIC)
                textSize = width * 0.1f * scaleFactor   // 10% of screen width, adjust as desired
                color = Color.MAGENTA
                style = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
            }
            // Coordinates: x=center, y=¼ of the screen height
            val xPos = width / 2f
            val yPos = height / 4.1f // one-fourth down from top
            canvas.drawText("$overallScore", xPos, yPos, scorePaint) // Print Score only
            if (overallScore > highScore) {
                highScore = overallScore
                animateHiScoreBounce()
                prefs.edit().putInt("HIGH_SCORE", highScore).apply()
                // Toast.makeText(context, "New Hi Score!", Toast.LENGTH_SHORT).show()
            }

        }

        // ─── 4) Draw “Level: …” at lower fourth ─────────────────────────────────
        run {
            val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // same exotic font/color as Score
                typeface = Typeface.create("constantia", Typeface.BOLD_ITALIC)
                textSize = width * 0.06f
                color = Color.MAGENTA
                style = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
            }
            val levelX = width / 2f
            val levelY = height / 1.35f
            canvas.drawText(" Level $currentLevel", levelX, levelY, levelPaint)
        }

        // ─── 5) Draw the HIGH SCORE below the Violet ring ─────────────────────────
        run {
            val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("constantia", Typeface.BOLD_ITALIC)
                textSize = width * 0.04f       // adjust as needed
                color = Color.YELLOW
                style = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
            }
            // Use the same fractional X as your violet ring (0.141f),
            // and offset Y a bit below (here +0.10f of screen height)
            val hiX = width / 2f
            val hiY = height / 3.7f
            // scale around the text center, then draw
            canvas.save()
            canvas.scale(hiScoreScale, hiScoreScale, hiX, hiY)
            canvas.drawText("Hi Score: $highScore", hiX, hiY, hiPaint)
            canvas.restore()
        }

        // ─── 2) Draw ONLY two bars at the bottom: LEVEL and TIME (continuous) ──
        if (!levelCompleted && !levelFailed) {
            val barMargin = 12f * scaleFactor
            val bottomMargin = 12f * scaleFactor
            val barHeight = 25f * 2.8f * scaleFactor     // same as before, for consistency
            val gapBetweenBars = 16f * scaleFactor
            val barMaxWidth = width - 2 * barMargin * scaleFactor

            // -- Compute LEVEL bar (exactly as you had, just moved down) --
            val levelFraction = (currentLevel / (15f * scaleFactor)).coerceIn(0f * scaleFactor, 1f * scaleFactor)
            val filledLevelWidth = levelFraction * barMaxWidth
            // val levelBarPaint = Paint().apply { color = Color.rgb(255, 150, 0) }

            // The TIME bar will sit at the very bottom:
            val timeBarTop = height - bottomMargin - barHeight
            // The LEVEL bar now sits one bar+gap above that (in place of old “score bar”):
            // val levelBarTop = timeBarTop - gapBetweenBars - barHeight


            // --- SHIFT UP LEVEL & SCORE ---
            canvas.save()
            canvas.translate(0f, -bannerHeightPx)


            // Time Bar (interpolated color)
            val currentTime = System.currentTimeMillis()
            // if timerPaused, use the frozen pausedElapsed; otherwise use the live clock
            val rawElapsed = if (timerPaused) {
                pausedElapsed.toFloat() / 1000f
            } else {
                (currentTime - levelStartTime).toFloat() / 1000f
            }
            val elapsedTime = if (currentLevel >= 11 && !timerStarted) 0f else rawElapsed
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

            // Draw the TIME bar rectangle (filling left→right)
            if (currentLevel >= 11 && firstSnapOccurred) {       // was showTimer
                val timeBarPaint = Paint().apply { color = timeBarColor }
                canvas.drawRect(
                    barMargin,
                    timeBarTop,
                    barMargin + filledTimeWidth,
                    timeBarTop + barHeight,
                    timeBarPaint
                )
            }
            canvas.restore()
        }


        // Update game state if not paused.
        if (!startDialogShowing && (!gamePaused || levelFailed)) {
            updateGameState(canvas)
        }

        // draw & advance all floating scores
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * scaleFactor; color = Color.BLACK
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
            fs.size  *= 1.02f * scaleFactor     // grow 2% per frame

            // remove when invisible
            if (fs.alpha == 0) iter.remove()
        }

        // Draw balls.
        if (!levelCompleted && !levelFailed && !startDialogShowing) {
            if (baseDp > 500f)
                scaleFactor = baseDp / designWidth

            for (ball in balls) {
                if (!ball.active) continue
                // ADDED: If the ball’s bottom (y + radius) has dropped below floorY, snap it and reverse dy:
                if (ball.y + ball.radius >= floorY) {
                    ball.y = floorY - ball.radius * scaleFactor
                    ball.vy = -ball.vy
                }
                // Draw the ball
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
                val shadowOffset = ball.radius / 3 * scaleFactor
                canvas.drawCircle(ball.x + shadowOffset, ball.y + shadowOffset, ball.radius * scaleFactor, shadowPaint)
                // Draw the ball
                val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                ballPaint.shader = RadialGradient(
                    ball.x - ball.radius / 4 * scaleFactor,
                    ball.y - ball.radius / 4 * scaleFactor,
                    ball.radius * scaleFactor,
                    intArrayOf(Color.WHITE, Color.DKGRAY),
                    floatArrayOf(0.3f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(ball.x, ball.y, ball.radius * scaleFactor, ballPaint)
            }
            // postInvalidateOnAnimation()
            // Less heat of Phone, but shows balls early. Slows the app terribly.
            if (!timerPaused && !levelCompleted && !levelFailed && !startDialogShowing) {
                // handler.postDelayed({ invalidate() }, 1L)  // Slows the app terribly.
                postInvalidateOnAnimation()
            }
        }
    }

    // --- Updated Game Physics and Snapping (with collision handling improvements).
    private fun updateGameState(canvas: Canvas) {
        val now = System.currentTimeMillis()

        if (currentLevel >= 11 && !removeTimeLimit) {
            // 1) Compute how many seconds have passed since the first snap (or zero until then)
            val elapsedSeconds: Float = if (!firstSnapOccurred) {
                0f
            } else {
                val rawElapsedMs = if (timerPaused) pausedElapsed else (now - levelStartTime)
                (rawElapsedMs.toFloat() / 1000f).coerceAtLeast(0f)
            }

            // 2) Turn it into a countdown: time left = limit – elapsed
            val remainingSeconds = (levelTimeLimit - elapsedSeconds).coerceAtLeast(0f)
            levelTimer = remainingSeconds.toInt()       // ← This drives your on-screen display

            // 3) Failure: when the countdown hits zero, and neither complete nor already failed
            if (remainingSeconds <= 0f && !levelCompleted && !levelFailed) {
                if (currentLevel >= 11) {
                    levelFailed = true
                    gamePaused  = true
                    timerPaused = true
                }

            }
        } else {
            // Levels 1–10: keep the timer pegged at full, and never fail by timeout
            levelTimer = levelTimeLimit
        }

        val maxBalls = getCountForLevel(currentLevel)
        // 2) Score from placed balls
        val placedPts = balls.take(maxBalls).sumOf { if (it.placed) it.basePoints * currentLevel else 0 }
        overallScore = (levelStartScore + placedPts).coerceAtLeast(0).also {
            if (levelStartScore + placedPts < 0) scoreFrozen = true
        }


        // 3) Vortex animation
        val laps = 3
        val totalAngle = laps * 2 * Math.PI.toFloat()
        val step = (2 * Math.PI.toFloat()) / 60f * scaleFactor
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
            1,2 -> 2.5f * scaleFactor
            3,4   -> 2.25f * scaleFactor
            5   -> 2.0f * scaleFactor
            6   -> 1.75f * scaleFactor
            7,10  -> 1.5f * scaleFactor
            else-> 1.0f * scaleFactor
        }
        val sens = when (currentLevel) {
            1 -> 0.1f * scaleFactor; // one-by-one balls, snap-out not possible
            2 -> 0.2f * scaleFactor; // 5 balls, unsnap not possible
            3-> 0.3f * scaleFactor; // 6 balls, unsnap not possible
            4-> 0.3f * scaleFactor; // 7 balls, unsnap not possible
            5-> 0.5f * scaleFactor; // 8 balls, unsnap not possible
            6-> 0.5f * scaleFactor; // 9 balls, unsnap not possible
            7,11 -> 0.7f * scaleFactor; // 5-9 balls, unsnap possible
            else -> 1.0f * scaleFactor
        }
        balls.forEach { ball ->
            if (!ball.active || ball.placed || ball.orbiting) return@forEach
            val mf = (balls.minOf { it.radius } / ball.radius) * 0.8f * scaleFactor
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
                // unsnap only levels ≥ 11
                if (currentLevel >= 11) listOf(b1,b2).forEach { bb ->
                    if (bb.placed) {
                        bb.placed = false
                        bb.canSnap = false
                        handler.postDelayed({ bb.canSnap = true }, 500)
                        if (soundEnabled) {
                            playSound("pwa")
                        }
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

                if (distB < thresh) {
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

                    // ────────────  S E T  “F I R S T  S N A P”  F L A G  ────────────
                    if (!firstSnapOccurred) {
                        firstSnapOccurred = true
                        levelStartTime    = System.currentTimeMillis()
                        pausedElapsed     = 0L
                        timerPaused       = false
                    }
                    // else { timerPaused = true }

                    // Floating score pop
                    val pts  = ball.basePoints * currentLevel
                    val ang  = Random.nextDouble(-Math.PI, -Math.PI / 3)
                    val spd  = 9f
                    val dxS  = (cos(ang) * spd).toFloat()
                    val dyS  = (sin(ang) * spd).toFloat()
                    val cidx = h.coerceAtMost(floatingColors.lastIndex)
                    floatingScores += FloatingScore(hx, hy, "+$pts", 255, 32f * 3, dxS, dyS, floatingColors[cidx])

                    // Start timer on level 11 first snap
                    if (currentLevel >= 11 && !timerStarted && firstSnapOccurred) {
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
                    Handler(Looper.getMainLooper()).postDelayed({
                        ballSpawnIndex++
                    }, 400L)
                    playBallSound(balls[ballSpawnIndex])
                }
            }
        }

        // 8) Failure
        if (levelTimer<=0 && !levelCompleted && !levelFailed) {
            if (currentLevel >= 11) {
                levelFailed=true; gamePaused=true
                levelFailedListener?.onLevelFailed(currentLevel)
            }

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
                if (soundEnabled) {
                    playSound("fail")
                }
                // Create a Runnable for showing the "Time's Up!" dialog.
                failureDialogRunnable = Runnable {
                    // 1) Build & create the dialog
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("Time's up!")
                        .setPositiveButton("Start new game") { _, _ -> newGame() }
                        .setNegativeButton(
                            "Restart last Level $currentLevel\n          at score: $levelStartScore \n  "
                        ) { _, _ -> restartLevel() }
                        .setCancelable(false)
                        .create()

                    // 2) Show it (so views exist)
                    dialog.show()

                    val constantiaItalic = ResourcesCompat.getFont(
                        context, R.font.constani
                    )
                    val constantiaNormal = ResourcesCompat.getFont(
                        context, R.font.constan
                    )

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        isAllCaps    = false
                        setSingleLine(false)
                        maxLines     = 3
                        ellipsize    = null
                        gravity      = Gravity.START
                    }

                    // 3) Apply your standard framed window style
                    dialog.window?.apply {
                        addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setLayout(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                        setBackgroundDrawableResource(R.drawable.dialog_frame)
                        setGravity(Gravity.CENTER)
                    }
                    dialog.setCanceledOnTouchOutside(false)

                    // now that the dialog window is actually attached, bump the banners on top
                    timeUpListener?.onTimeUpDialogShown()

                    // 4) Grab the title TextView and force lowercase, Constantia Italic, green
                    val titleId = context.resources.getIdentifier(
                        "alertTitle", "id", "android"
                    )
                    dialog.findViewById<TextView>(titleId)?.apply {
                        text = text.toString()
                        typeface     = constantiaNormal
                        setTextColor(Color.RED)
                    }

                    // 5) Style the buttons the same way
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        isAllCaps    = false
                        typeface     = constantiaItalic
                        setTextColor(Color.YELLOW)
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        isAllCaps    = false
                        typeface     = constantiaItalic
                        setTextColor(Color.GREEN)
                        setPadding(0, 0, 150, 0)
                    }
                }

                sensorManager.unregisterListener(this)
                // Post the failure dialog runnable after a delay.
                handler.postDelayed(failureDialogRunnable!!, 1000)
            }
        }

        // 9) Completion
        // compute banner height in px
        val bannerHeightPx = AdSize.LARGE_BANNER.getHeightInPixels(context).toFloat()

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
            if (soundEnabled) {
                playSound("success")
            }
            levelStartListener?.onLevelCompleted(currentLevel)
            showHoles = false
            canvas.save()
            canvas.translate(0f, -bannerHeightPx)

            if (currentLevel != 19) {
                pauseGame()
                showHoles = false
                handler.postDelayed({
                    val dialog = AlertDialog.Builder(context)
                        // .setTitle(Html.fromHtml("<font color='green'>     Level $currentLevel completed!", Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Play Level ${currentLevel+1}") { _, _ -> nextLevel() }
                        .setCancelable(false)
                        .create()
                    dialog.window?.apply {
                        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setDimAmount(0f)
                        val bannerHeightPx = AdSize.LARGE_BANNER.getHeightInPixels(context)
                        attributes = attributes.apply {
                            y = bannerHeightPx
                        }
                    }
                    val constantiaItalic = ResourcesCompat.getFont(
                        context,
                        R.font.constani
                    )
                    dialog.setOnShowListener {
                        pauseGame()
                        sensorManager.unregisterListener(this)

                        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        btn?.apply {
                            // 1) Style as before
                            isAllCaps    = false
                            typeface     = constantiaItalic
                            setTextColor(Color.GREEN)
                            setShadowLayer(2f * scaleFactor,4f * scaleFactor,4f * scaleFactor,
                                Color.BLACK
                            )
                            setTextSize(TypedValue.COMPLEX_UNIT_PX, 84f * scaleFactor)
                            setPadding(0, 25, 0, 40)

                            // 2) Make the button fill its container
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width   = ViewGroup.LayoutParams.MATCH_PARENT
                                gravity = Gravity.CENTER
                                // optionally remove any margins if you set them elsewhere
                                leftMargin = 0
                                rightMargin = 0
                            }
                            // 3) Center the text inside it
                            gravity = Gravity.CENTER
                        }
                    }
                    dialog.show()
                    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                    // 3) Apply your standard framed window style
                    dialog.window?.apply {
                        addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setLayout(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                        setBackgroundDrawableResource(R.drawable.dialog_frame)
                        setGravity(Gravity.CENTER_HORIZONTAL)
                        setPadding(0,100,0,100)
                    }
                    dialog.setCanceledOnTouchOutside(false)
                    // now that the dialog window is actually attached, bump the banners on top
                    timeUpListener?.onTimeUpDialogShown()

                }, 1000)
            }
            else {
                sensorManager.unregisterListener(this)
                handler.postDelayed({
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("Congratulations!!!")
                        .setMessage("GAME OVER\n\nWould you like to\nrestart the Game?")
                        .setPositiveButton("Yes") { _, _ -> newGame() }
                        .setNegativeButton("No") { _, _ -> (context as Activity).finish() }
                        .setCancelable(false)
                        .create()

                    dialog.show()
                    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                    dialog.setOnShowListener { dlgInterface ->
                        // cast the DialogInterface back to AlertDialog
                        val alert = dlgInterface as? AlertDialog ?: return@setOnShowListener

                        // find the AppCompat title TextView
                        val tv = alert.findViewById<TextView>(
                            androidx.appcompat
                                .R
                                .id
                                .alertTitle)
                            ?: return@setOnShowListener

                        // pivot in the center
                        tv.pivotX = tv.width  * 0.5f
                        tv.pivotY = tv.height * 0.5f

                        // build the three animators
                        val scaleX = ObjectAnimator.ofFloat(tv, View.SCALE_X, 1f, 1.5f, 1f)
                        val scaleY = ObjectAnimator.ofFloat(tv, View.SCALE_Y, 1f, 1.5f, 1f)
                        val colorAnim = ValueAnimator.ofObject(
                            ArgbEvaluator(),
                            Color.RED,
                            Color.MAGENTA,
                            Color.BLUE,
                            Color.CYAN,
                            Color.GREEN,
                            Color.YELLOW,
                            Color.RED
                        ).apply {
                            duration = 300L
                            addUpdateListener { anim ->
                                tv.setTextColor(anim.animatedValue as Int)
                            }
                        }

                        // play them together
                        AnimatorSet().apply {
                            playTogether(scaleX, scaleY, colorAnim)
                            duration = 300L
                            start()
                        }
                    }
                    val constantiaItalic = ResourcesCompat.getFont(
                        context, R.font.constani
                    )
                    val constantiaNormal = ResourcesCompat.getFont(
                        context, R.font.constan
                    )
                    val titleId = context.resources.getIdentifier(
                        "alertTitle", "id", "android"
                    )
                    dialog.findViewById<TextView>(titleId)?.apply {
                        text = text.toString()
                        typeface     = constantiaNormal
                        maxLines     = 5
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                        // setTextColor(Color.RED)
                        setBackgroundColor(Color.TRANSPARENT)
                        setPadding(75,25,0,50)
                    }
                    val messageId = context.resources.getIdentifier(
                        "alertMessage", "id", "android"
                    )
                    dialog.findViewById<TextView>(android.R.id.message)?.apply {
                        // ensure the exact same message string is shown
                        text = text.toString()
                        // apply your bundled Constantia Italic face
                        typeface = constantiaItalic
                        // and make it green
                        setTextColor(Color.GREEN)
                        setGravity(Gravity.CENTER)
                        setBackgroundColor(Color.TRANSPARENT)
                        setPadding(55,80,55,160)
                    }

                    // 3) Apply your standard framed window style
                    dialog.window?.apply {
                        addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setLayout(WRAP_CONTENT, WRAP_CONTENT)
                        setBackgroundDrawableResource(R.drawable.dialog_frame)
                        setGravity(Gravity.CENTER)

                        // now that the dialog window is actually attached, bump the banners on top
                        timeUpListener?.onTimeUpDialogShown()
                        // (Optional) pad inside your frame if you like
                        decorView.setPadding(20, 40, 20, 80)
                        // now make the whole window 90% transparent (alpha=0.1f)
                        val lp = attributes
                        lp.alpha = 1.0f     // 0.0 = fully transparent, 1.0 = fully opaque
                        attributes = lp
                    }
                    dialog.setCanceledOnTouchOutside(false)
                }, 1000)
                return
            }
            canvas.restore()
        }
    }


    private fun playBallSound(ball: Ball) {
        val index = balls.indexOf(ball)
        if (soundEnabled) {
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
    }

    private fun playBellSound(ball: Ball) {
        // find its index among the snap-targets
        if (soundEnabled) {
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
        timerStarted = (currentLevel >= 11)

        if (resume) {
            // on a resume, use your loaded overallScore as the baseline
            levelStartScore = overallScore
            timerPaused = true
            levelStartTime  = System.currentTimeMillis()
            // levelStartTime was already loaded from prefs in loadGameState()
        } else {
            // brand‑new level: snapshot score & reset timer
            timerPaused = true
            levelStartScore = overallScore
            levelStartTime  = System.currentTimeMillis()
        }
        if (soundEnabled) {
            playSound("success")
        }
        levelTimeLimit = when (currentLevel) {
            in 1..2 -> 90
            in 3..10 -> 60
            11, 13 -> 60 // 5-7 balls
            14, 15 -> 45 // 8-9 balls
            16, 19 -> 30 // 10-13 balls
            else -> 0
        }
        ballSpawnIndex = 0

        firstSnapOccurred = false    // <— reset the flag so timer sits at zero for the next level
        levelTimer = 0 // was levelTimeLimit
        timerPaused = false

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
        // showScore = false
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
        orientationOffset = -45
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
        val savedScore = prefs.getInt("overallScore", 0)
        val typeface = ResourcesCompat.getFont(context, R.font.constani)
        showHoles = false

        // If this is a fresh install or hi-score == 0, skip the popup entirely
        if (savedScore == 0) {
            showInstructionDialog()
            return
        }

        handler.post {
            startDialogShowing = true

            val savedScore = prefs.getInt("overallScore", 0)
            val savedLevel = prefs.getInt("currentLevel", 1)

            // 1) Inflate the custom view
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_start, null)

            // 2) Create the AlertDialog *before* wiring buttons
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            // 3) Make window background transparent
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 4) Optional: position it near the bottom
            dialog.window?.apply {
                // Center the dialog on screen
                setGravity(Gravity.CENTER)
                // No need for a y‐offset when centering
                val params = attributes
                params.y = 0
                attributes = params
            }

            // 5) Grab your views
            val tvTitle   = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            val btnPos    = dialogView.findViewById<Button>(R.id.btnPositive)
            val btnNeg    = dialogView.findViewById<Button>(R.id.btnNegative)
            val btnOptions    = dialogView.findViewById<Button>(R.id.btnOptions)

            // 6) Set up title
            tvTitle.text = Html.fromHtml(
                "<font color='#FFFFFF'>AimGame</font>",
                Html.FROM_HTML_MODE_LEGACY
            )

            // 7) Configure message & buttons based on saved state
            if (prefs.contains("overallScore") && prefs.contains("currentLevel")) {
                // tvMessage.text = "Continue last session?"
                btnPos.apply {
                    text = "Continue last session at Score: $savedScore, \nLevel: $savedLevel"
                    setTypeface(typeface, Typeface.NORMAL)  // second arg can be Typeface.NORMAL/BOLD/ITALIC
                }
                btnPos.setOnClickListener {
                    // restore state
                    overallScore     = savedScore
                    currentLevel     = savedLevel
                    orientationOffset= -45
                    baselineSet      = true
                    baselineX        = 0f * scaleFactor
                    baselineY        = 0f * scaleFactor
                    startDialogShowing = false

                    startLevel(resume = true)
                    dialog.dismiss()
                    calibrateBaseline()       // This will cause the next sensor reading to become the new baseline.
                    invalidate()
                    resumeGame()
                }
                btnNeg.apply {
                    text = "New Game"
                    setTypeface(typeface, Typeface.ITALIC)  // second arg can be Typeface.NORMAL/BOLD/ITALIC
                }
                btnNeg.setOnClickListener {
                    overallScore       = 0
                    currentLevel       = 1
                    orientationOffset  = -45
                    baselineSet        = true
                    baselineX          = 0f * scaleFactor
                    baselineY          = 0f * scaleFactor
                    startDialogShowing = false
                    startLevel(resume = false)
                    dialog.dismiss()
                    invalidate()
                }
                btnOptions.apply {
                    text = "Options"
                    setTypeface(typeface, Typeface.ITALIC)  // second arg can be Typeface.NORMAL/BOLD/ITALIC
                }
                btnOptions.setOnClickListener {
                    allOptions()
                }

            } else {
                tvMessage.visibility = View.GONE
                btnNeg.apply {
                    btnPos.text = "Start"
                    setTypeface(typeface, Typeface.ITALIC)  // second arg can be Typeface.NORMAL/BOLD/ITALIC
                }
                btnPos.setOnClickListener {
                    overallScore       = 0
                    currentLevel       = 1
                    orientationOffset  = -45
                    baselineSet        = true
                    baselineX          = 0f * scaleFactor
                    baselineY          = 0f * scaleFactor
                    startDialogShowing = false
                    startLevel(resume = false)
                    dialog.dismiss()
                    invalidate()
                }
                btnNeg.visibility = View.GONE
            }

            // 8) Finally, show it
            dialog.show()
            // Make the popup window 80% of screen-width and wrap_content for height

            val width = (context.resources.displayMetrics.widthPixels * 1.0).toInt()
            dialog.window?.setLayout(
                width,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            dialog.window?.setGravity(Gravity.CENTER)
        }
    }

    fun onDialogCancel() {
        orientationOffset = -45
        baselineSet = true
        baselineX = 0f * scaleFactor
        baselineY = 0f * scaleFactor
        startDialogShowing = false
        startLevel(resume = false)
        invalidate()
        resumeGame()
    }

    private fun allOptions() {
        sensorManager.unregisterListener(this)
        val items = arrayOf(
            "How to Play",
            "Continue Last Session",
            "New Game",
            "Adjust Comfortable Tilt",
            "Play on Horizontal/Flat Screen",
            "Remove Ads and Time Limit",
            "Privacy Policy",
            "Terms of Use",
            "Contact",
            "Save & Exit",
            "Close")

        // 1) Build an ArrayAdapter pointing at our new item layout:
        val adapter = ArrayAdapter(
            context,                  // or `context` if not in an Activity
            R.layout.dialog_list_item,
            items
        )

        // 2) Create the dialog with an adapter callback
        val dialog = AlertDialog.Builder(context)
            .setAdapter(adapter) { dialogInterface, which ->
                // Dismiss the menu and allow the game thread to resume
                dialogInterface.dismiss()
                startDialogShowing = false

                when (which) {
                    0 -> showHowToPlay()
                    1 -> {                  // Continue Last Session
                        // restore state
                        // overallScore     = savedScore
                        // currentLevel     = savedLevel
                        orientationOffset= -45
                        baselineSet      = true
                        baselineX        = 0f * scaleFactor
                        baselineY        = 0f * scaleFactor
                        startDialogShowing = false
                        startLevel(resume = false)
                        dialogInterface.dismiss()
                        invalidate()
                        calibrateBaseline()       // This will cause the next sensor reading to become the new baseline.
                        resumeGame()
                        // loadGameState()               // reload saved score & level :contentReference[oaicite:2]{index=2}
                        // resumeGame()                  // re-register accelerometer & restart timer :contentReference[oaicite:3]{index=3}
                    }
                    2 -> {                  // New Game
                        overallScore       = 0
                        currentLevel       = 1
                        orientationOffset  = -45
                        baselineSet        = true
                        baselineX          = 0f * scaleFactor
                        baselineY          = 0f * scaleFactor
                        startDialogShowing = false
                        startLevel(resume = false)
                        dialogInterface.dismiss()
                        invalidate()
                    }

                    3 -> {                          // "Tilted"
                        orientationOffset = -45    // Rotate sensor values by -45° so the game behaves as flat.
                        calibrateBaseline()        // This will cause the next sensor reading to become the new baseline.
                        resumeGame()
                        dialogInterface.dismiss()
                        invalidate()
                    }
                    4 -> {                         // "Flat"

                        orientationOffset = 0    // Set Flat mode (no rotation)
                        // Reset calibration to default: effectively no offset.
                        baselineSet = true
                        baselineX = 0f * scaleFactor
                        baselineY = 0f * scaleFactor
                        resumeGame()
                        dialogInterface.dismiss()
                        invalidate()
                    }
                    5 -> billingManager.showRemoveOptionsDialog()
                    6 -> showPrivacyPolicy()
                    7 -> showTermsOfUse()
                    8 -> {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@veryeasy.games")
                            putExtra(Intent.EXTRA_SUBJECT, "AimGame Support")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            startLevel(resume = false)
                            dialogInterface.dismiss()
                            invalidate()
                        }
                    }
                    9 -> {                         // "Save & Exit"
                        saveGameState()
                        exitProcess(0)
                    }
                    else -> {
                        if (soundEnabled) {
                            playSound("tick")
                        }
                        onDialogCancel()
                        dialogInterface.dismiss()
                    }
                }
            }
            .create()

        // 4) allow tapping outside to cancel & restart level
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            orientationOffset = -45
            baselineSet = true
            baselineX = 0f * scaleFactor
            baselineY = 0f * scaleFactor
            startDialogShowing = false
            startLevel(resume = false)
            invalidate()
            resumeGame()
        }

        // Show and style
        dialog.show()
        dialog.window?.apply {
            // 1) Compute 80% of screen‐width in px
            val displayMetrics = context.resources.displayMetrics
            val maxWidth = (displayMetrics.widthPixels * 0.8f).toInt()

            // 1) Don’t block touches outside the dialog frame…
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            // 2) …and remove the default dim so it’s obvious what’s behind
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            // 2) Apply that width, but keep height wrap_content
            setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)

            // 3) Then your existing styling
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setBackgroundDrawableResource(R.drawable.dialog_frame)
            setGravity(Gravity.CENTER)
        }

        // 3) Prevent the dialog from auto‐dismissing on outside-touch
        dialog.setCanceledOnTouchOutside(false)

        // now that the dialog window is actually attached, bump the banners on top
        timeUpListener?.onTimeUpDialogShown()
    }

    private fun showInstructionDialog() {
        sensorManager.unregisterListener(this)
        startDialogShowing = true

        // 1) Inflate and build
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_start, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }
        dialog.show()

        // 2) Grab your views
        val tvTitle   = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnStart  = dialogView.findViewById<Button>(R.id.btnPositive)
        // val btnPrivacy= dialogView.findViewById<Button>(R.id.btnPrivacy)
        // val btnTerms  = dialogView.findViewById<Button>(R.id.btnTerms)
        val btnOptions = dialogView.findViewById<Button>(R.id.btnOptions)

        // 3) Your existing customizations
        tvTitle.text = "Welcome to AimGame!"
        if (soundEnabled) {
            playSound("success")
        }
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tvMessage.setTextColor(Color.GREEN)

        // 4) Start button
        btnStart.apply {
            text = "Start"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("constantia", Typeface.ITALIC)
            setOnClickListener {
                dialog.dismiss()
                startDialogShowing = false
                startLevel(resume = false)
            }
        }
        btnOptions.apply {
            text = "Options"
            setTypeface(typeface, Typeface.ITALIC)  // second arg can be Typeface.NORMAL/BOLD/ITALIC
        }
        btnOptions.setOnClickListener {
            allOptions()
        }
    }



    private fun showLongTextDialog(context: Context, title: String, @StringRes textResId: Int) {
        // Inflate using the context instead of layoutInflater
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_long_text, null)
        // Use context.getString(...) instead of getString(...)
        dialogView.findViewById<TextView>(R.id.tvDialogContent)?.text =
            context.getString(textResId)

        // Build with AlertDialog.Builder(context) instead of this
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // resume the level when "OK" is tapped
                startLevel(resume = false)
                invalidate()
            }
            .show()
    }

    private fun showPrivacyPolicy() = showLongTextDialog(context, "Privacy Policy",
        R.string.privacy_policy
    )
    private fun showTermsOfUse()    = showLongTextDialog(context, "Terms of Use",
        R.string.terms_of_use
    )
    private fun showHowToPlay()     = showLongTextDialog(context, "How to Play",
        R.string.how_to_play
    )

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
            // Log.d("Sensor", "Raw: ($rawX, $rawY); Calibrated: ($calibratedX, $calibratedY); offset: $orientationOffset")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}
