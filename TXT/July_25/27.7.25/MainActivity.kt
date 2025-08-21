package com.veryeasygames.aimgame

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Color.YELLOW
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

import android.widget.Button
import androidx.core.animation.addListener
import com.google.android.gms.ads.RequestConfiguration

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
// import com.google.firebase.ktx.Firebase

class MainActivity :
    AppCompatActivity(),
    GameView.LevelStartListener, GameView.LevelFailedListener, GameView.OnTimeUpListener {   // GameView.OnTimeUpListener
    // The red ring’s hole position remains fixed.
    private val redHolePosFraction = Pair(0.5f, 0.483f)

    // Target fractional positions for the rings (used for positioning the views).
    /*
    private val violetPos = Pair(0.21f, 0.342f)
    private val goldPos = Pair(0.318f, 0.394f)
    private val greenPos = Pair(0.408f, 0.438f)
    private val yellowPos = Pair(0.558f/1.14815f, 0.512f/1.07563f)
    private val redPos = Pair(0.558f, 0.512f)
    // private val redPos = Pair(0.558f, 0.512f)

     */


    private val violetPos = Pair(0.5f, 0.483f)
    private val goldPos = Pair(0.5f, 0.483f)
    private val greenPos = Pair(0.5f, 0.483f)
    private val yellowPos = Pair(0.5f, 0.483f)
    // private val redPos = Pair(0.5f, 0.483f)
    private val redPos = Pair(0.5f, 0.483f)



    // For computing new hole positions we use the following initial side fractions.
    // (These correspond to the initial unrotated positions of the holes in the ring images.)
    private val violetInitial = Pair(0.573f, 0.52f)
    private val goldInitial   = Pair(0.632f, 0.55f)
    private val greenInitial  = Pair(0.69f, 0.578f)
    private val yellowInitial = Pair(0.75f, 0.607f)
    // The vertex (pivot) for the rotation of the holes is:
    private val vertexFraction = Pair(0.5f, 0.483f)

    // References to the ring ImageViews.
    private lateinit var ivRingViolet: RingView
    private lateinit var ivRingGold:   RingView
    private lateinit var ivRingGreen:  RingView
    private lateinit var ivRingYellow: RingView
    private lateinit var ivRingRed:    RingView

    private val DESIGN_HEIGHT_DP   = 110f    // the 100 dp you baked into your RingView
    private val DESIGN_WIDTH_DP  = 1080f    // your A55 design width in dp


    /**
     * Converts BASE_RING_DP→px and scales by (screenWidth / DESIGN_WIDTH_DP).

    private fun calculateRingSizePx(): Int {
        val dm           = resources.displayMetrics
        val screenWpx    = dm.widthPixels.toFloat()
        val designWpx    = DESIGN_WIDTH_DP * dm.density
        val scale        = screenWpx / designWpx
        return (BASE_RING_DP * dm.density * scale).toInt()
    }

    private fun baseRingDpFor(type: RingView.Type) = when(type) {
        RingView.Type.VIOLET -> 308f
        RingView.Type.GOLD   -> 258f
        RingView.Type.GREEN  -> 208f
        RingView.Type.YELLOW -> 158f
        RingView.Type.RED    -> 108f
    }
    */

    /**
     * Just size the RingView’s box (in px) from its base-dp, scaled by the
     * SAME viewport→px scale you use for holes (min(width,height)/110).
     */
    private fun ringLp(
        type: RingView.Type
    ): FrameLayout.LayoutParams {
        val dm       = resources.displayMetrics
        val screenW  = dm.widthPixels.toFloat()
        val screenH  = dm.heightPixels.toFloat()
        val density  = dm.density

        // pick the dp you had in your XML for each ring
        val baseDp = when (type) {
            RingView.Type.VIOLET -> 308f
            RingView.Type.GOLD   -> 230f
            RingView.Type.GREEN  -> 166f
            RingView.Type.YELLOW -> 110f
            RingView.Type.RED    ->  58f
        }

        // compute the SAME scale factor your GameView uses for holes:
        // viewport=110 units → min(screenW,screenH) px
        // val scale = minOf(screenW, screenH) / (110f * density) // *3.2f is added here
        // ring diameter in px:
        // val sizePx = ((baseDp * density * scale) / 3.25f ).toInt()


         // 1) convert dp→px baseline
        val designWpx = DESIGN_WIDTH_DP  * density  // 1080dp → px
        val designHpx = DESIGN_HEIGHT_DP * density  //  110dp → px

        // 2) separate scale factors
        val scaleX    = screenW / designWpx
        val scaleY    = screenH / designHpx

        // 3) pick the smaller so circles stay round & fit both dims
        var scale     = minOf(scaleX, scaleY) * 3f

        // 3) Shrink 10% on tablets (>=600dp width) or very small phones (<=360dp width)
        val screenWdp = screenW / density
        if (screenWdp >= 600f ) {
            scale *= 0.9f
        }
        /*
        if ( screenWdp <= 320f) {
            scale *= 0.8f
        }
         */

        // 4) now size the ring box
        val sizePx    = (baseDp * density * scale).toInt()

        return FrameLayout.LayoutParams(sizePx, sizePx)
    }




    // Reference to GameView and ads.
    private lateinit var gameView: GameView
    private lateinit var billingManager: BillingManager
    private val handler = Handler(Looper.getMainLooper())

    private var interstitialAd: InterstitialAd? = null
    private var interstitialShowCount = 0

    // Two banners for cross‐fade
    lateinit var bannerA: AdView
    lateinit var bannerB: AdView
    private var showingA = true

    private lateinit var frameLayout: FrameLayout

    // Global storage for each ring's final rotation.
    private var finalRotationViolet = 0f
    private var finalRotationGold = 0f
    private var finalRotationGreen = 0f
    private var finalRotationYellow = 0f
    private var finalRotationRed = 0f
    // For red ring, we won’t change its rotation.

    private lateinit var btnSound: Button

    // simple prefs to remember the user’s choice
    private val prefs by lazy {
        getSharedPreferences("game_settings", MODE_PRIVATE)
    }
    private var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(value) = prefs.edit().putBoolean("sound_on", value).apply()


    // Runnable that kicks off loading the *hidden* banner every 30s
    private val reloadBannerRunnable = object : Runnable {
        override fun run() {
            // Decide which one is hidden
            val toLoad = if (showingA) bannerB else bannerA
            toLoad.loadAd(AdRequest.Builder().build())
            // Schedule next reload
            handler.postDelayed(this, 65_000)
        }
    }

    companion object {
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5967101454574410/6260381850"
        private const val BANNER_AD_UNIT_ID       = "ca-app-pub-5967101454574410/2297086115"

    }

    var removeAds: Boolean = false
    var removeTimeLimit: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // setContentView(R.layout.activity_main)
        // gameView = findViewById(R.id.gameView)


        // 1) Configure test devices and use real unit IDs only in production.
        //    Here, remove all test‐device configuration so we get real ads:
/*
                val testDeviceIds = listOf(
                    AdRequest.DEVICE_ID_EMULATOR,
                    "30AB8CD9F183E6686ED425A1383C9785"
                )
                val config = RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()
                MobileAds.setRequestConfiguration(config)
 */


        // ─── Initialize the Mobile Ads SDK ───────────────────────────────────
        MobileAds.initialize(this) { status ->
            Log.d("AdMob", "SDK Initialization complete: $status")
        }


        // 3) Build a full-screen FrameLayout container
        frameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT
            )
        }

// 4) Bottom Layer: Background image
        val ivBackground = ImageView(this).apply {
            setImageResource(R.drawable.wood_wall)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        frameLayout.addView(ivBackground)

        val (cx, cy) = redPos

// Middle Layer: Ring overlays
        ivRingViolet = RingView(this).apply { type = RingView.Type.VIOLET }
        frameLayout.addView(ivRingViolet, ringLp(RingView.Type.VIOLET))

        ivRingGold   = RingView(this).apply { type = RingView.Type.GOLD }
        frameLayout.addView(ivRingGold,   ringLp(RingView.Type.GOLD))

        ivRingGreen  = RingView(this).apply { type = RingView.Type.GREEN }
        frameLayout.addView(ivRingGreen,  ringLp(RingView.Type.GREEN))

        ivRingYellow = RingView(this).apply { type = RingView.Type.YELLOW }
        frameLayout.addView(ivRingYellow, ringLp(RingView.Type.YELLOW))

        ivRingRed    = RingView(this).apply { type = RingView.Type.RED }
        frameLayout.addView(ivRingRed,    ringLp(RingView.Type.RED))



// 6) Top Layer: GameView
        gameView = GameView(this).also { gv ->
            gv.levelStartListener  = this
            gv.levelFailedListener = this
        }
        frameLayout.addView(
            gameView,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )

// 7) Show it!
        // setContentView(frameLayout)





// 2. Create BillingManager and inject gameView
        billingManager = BillingManager(this, gameView)

// 3. Assign BillingManager to GameView
        gameView.billingManager = billingManager

// 4. Configure GameView and add to layout
        gameView.levelStartListener = this
        gameView.levelFailedListener = this
        (gameView.parent as? ViewGroup)?.removeView(gameView)
        frameLayout.addView(
            gameView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

// 5. Start Billing connection
        billingManager.startConnection()

// convert 16 dp → px
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        // dp → px helper
        val dp0 = 0

        // helper values
        val popScale = 1.5f
        val popDuration = 150L

        fun View.popText() {
            // scale up…
            animate()
                .scaleX(popScale)
                .scaleY(popScale)
                .setDuration(popDuration)
                .withEndAction {
                    // …then scale back down
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(popDuration)
                        .start()
                }
                .start()
        }



// Helper to convert dp→px
        fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
        val dm = resources.displayMetrics
// pick a fraction of screen-height (e.g. 0.03 = 3%)
        val textPx = dm.heightPixels * 0.025f

// 1) Sound On/Off on the left
        val btnSound = Button(this).apply {
            text = if (soundOn) "Sound On" else "Sound Off"
            isAllCaps = false
            typeface = Typeface.create("constantia", Typeface.ITALIC)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(YELLOW)
            // rotate around its center once it's laid out
            post {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                pivotX = width  * 0.2f
                pivotY = height * 0.8f
                rotation = -90f
            }
            setOnClickListener { view ->
                view.popText()
                soundOn = !soundOn
                gameView.soundEnabled = soundOn
                text = if (soundOn) "Sound On" else "Sound Off"
            }
        }
        frameLayout.addView(
            btnSound,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dpToPx(16)
            }
        )
        btnSound.bringToFront()

// 2) Set Tilt on the right
        val btnTilt = Button(this).apply {
            text = "Set Tilt"
            isAllCaps = false
            typeface = Typeface.create("constantia", Typeface.ITALIC)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(YELLOW)
            post {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                pivotX = width  * 0.8f
                pivotY = height * 0.8f
                rotation = 90f
            }
            setOnClickListener { view ->
                if (gameView.gamePaused) return@setOnClickListener
                view.popText()
                gameView.orientationOffset = -45
                gameView.calibrateBaseline()
                gameView.resumeGame()
            }
        }
        frameLayout.addView(
            btnTilt,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dpToPx(16)
            }
        )
        btnTilt.bringToFront()


        // ------------------ SHOW BANNERS --------------
// 1) Create Banner A (initially visible)
        bannerA = AdView(this).apply {
            setAdSize(AdSize.LARGE_BANNER)
            // TODO: replace with your real Banner unit ID
            adUnitId = "ca-app-pub-5967101454574410/2297086115"
            // adUnitId = "ca-app-pub-3940256099942544/6300978111" // your test unit ID
            alpha = 1f
            visibility = View.VISIBLE    // show A immediately
        }

// 2) Create Banner B (initially invisible)
        bannerB = AdView(this).apply {
            setAdSize(AdSize.LARGE_BANNER)
            adUnitId = "ca-app-pub-5967101454574410/2297086115"
            // adUnitId = "ca-app-pub-3940256099942544/6300978111" // your test unit ID
            alpha = 0f
            visibility = View.VISIBLE
        }

        // 10% opaque white = 26/255 ≈ 0.1 alpha - THIS DOES NOT WORK
        val semiTransparentWhite = Color.argb(
            (255 * 0.1f).toInt(),  // alpha = 10%
            255, 255, 255          // white
        )

        // apply as the AdView’s background
        bannerA.setBackgroundColor(semiTransparentWhite)
        bannerB.setBackgroundColor(semiTransparentWhite)


        // 3) Let the AdView size itself: MATCH_PARENT x WRAP_CONTENT
        val bannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )

// Add B first, then A on top
        frameLayout.addView(bannerB, bannerParams)
        frameLayout.addView(bannerA, bannerParams)

        bannerA.setBackgroundColor(Color.GREEN)
        bannerB.setBackgroundColor(Color.BLUE)

        // Elevation so nothing ever covers them
        bannerB.bringToFront()
        bannerA.bringToFront()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bannerB.elevation = 100f
            bannerA.elevation = 101f
        }


        // AdListeners for cross‐fade logic
        bannerA.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob","✅ BannerA loaded")

                if (!showingA) {
                    // Cross‐fade A onto B
                    crossfade(bannerA, bannerB)
                    showingA = true
                }
                Log.d("AdMob","bannerA is visible")
            }

            override fun onAdImpression() {
                // 1) Log an Analytics event so you can see, by placement/level/country,
                //    which banners are viewed most and earn the most.
                // val analytics = Firebase.analytics
                Firebase.analytics.logEvent("banner_impression", Bundle().apply {
                    putString("ad_unit", bannerA.adUnitId)
                    putInt("level", gameView.currentLevel)
                })
                // 2) If you see certain levels or screens get high impressions but low clicks,
                //    try moving the banner or switching to an adaptive size there.
            }

            override fun onAdClicked() {
                // 1) Log click-through events to Analytics for CTR and revenue analysis:
                Firebase.analytics.logEvent("banner_click", Bundle().apply {
                    putString("ad_unit", bannerA.adUnitId)
                    putInt("level", gameView.currentLevel)
                })
                // 2) Immediately reload (or delay reload) so the user sees a fresh ad next time:
                bannerA.loadAd(AdRequest.Builder().build())
                bannerB.loadAd(AdRequest.Builder().build())
            }

            override fun onAdOpened() {
                // Pause the game or mute sounds so the user isn’t interrupted when the ad overlay appears.
                gameView.pauseGame()
            }
            override fun onAdClosed() {
                // 1) Resume the game cleanly:
                gameView.resumeGame()
                // 2) Load a new ad immediately (or after a small delay) to capture the returning user:
                handler.postDelayed({
                    bannerA.loadAd(AdRequest.Builder().build())
                    bannerB.loadAd(AdRequest.Builder().build())
                }, 5_000)  // e.g. 5s buffer so you don’t reload too aggressively
            }

            private var retryDelayMs = 10_000
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob","❌ BannerA failed: ${error.code} ${error.message}")
                handler.postDelayed({
                    bannerA.loadAd(AdRequest.Builder().build())
                    bannerB.loadAd(AdRequest.Builder().build())
                    // increase delay up to a max, e.g. 5→10→20→40s
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000)
                }, retryDelayMs.toLong())
            }
        }

        bannerB.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob","✅ BannerB loaded")
                if (showingA) {
                    // Cross‐fade B onto A
                    crossfade(bannerB, bannerA)
                    showingA = false
                }
                Log.d("AdMob","bannerB is visible")
            }
            override fun onAdImpression() {
                // 1) Log an Analytics event so you can see, by placement/level/country,
                //    which banners are viewed most and earn the most.
                // val analytics = Firebase.analytics
                Firebase.analytics.logEvent("banner_impression", Bundle().apply {
                    putString("ad_unit", bannerB.adUnitId)
                    putInt("level", gameView.currentLevel)
                })
                // 2) If you see certain levels or screens get high impressions but low clicks,
                //    try moving the banner or switching to an adaptive size there.
            }

            override fun onAdClicked() {
                // 1) Log click-through events to Analytics for CTR and revenue analysis:
                Firebase.analytics.logEvent("banner_click", Bundle().apply {
                    putString("ad_unit", bannerB.adUnitId)
                    putInt("level", gameView.currentLevel)
                })
                // 2) Immediately reload (or delay reload) so the user sees a fresh ad next time:
                bannerB.loadAd(AdRequest.Builder().build())
            }

            override fun onAdOpened() {
                // Pause the game or mute sounds so the user isn’t interrupted when the ad overlay appears.
                gameView.pauseGame()
            }
            override fun onAdClosed() {
                // 1) Resume the game cleanly:
                gameView.resumeGame()
                // 2) Load a new ad immediately (or after a small delay) to capture the returning user:
                handler.postDelayed({
                    bannerB.loadAd(AdRequest.Builder().build())
                }, 5_000)  // e.g. 5s buffer so you don’t reload too aggressively
            }

            private var retryDelayMs = 10_000
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob","❌ BannerA failed: ${error.code} ${error.message}")
                handler.postDelayed({
                    bannerB.loadAd(AdRequest.Builder().build())
                    // increase delay up to a max, e.g. 5→10→20→40s
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000)
                }, retryDelayMs.toLong())
            }
        }

        gameView.levelStartListener = this
        gameView.levelFailedListener = this

        // Finish setup
        setContentView(frameLayout)
        positionRings()
        animateRings()

        // Load first banner into A and start the reload cycle
        bannerB.loadAd(AdRequest.Builder().build())
        bannerA.loadAd(AdRequest.Builder().build())
        handler.postDelayed(reloadBannerRunnable, 65_000)
    }

    override fun onTimeUpDialogShown() {
        // run on UI thread just in case
        runOnUiThread {
            // now we can bring both banners to the top:
            bannerA.bringToFront()
            bannerB.bringToFront()
            // if you’re on Lollipop+ you might also re-elevate:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bannerA.elevation = 101f
                bannerB.elevation = 100f
            }
        }
    }



    // Smooth cross‐fade from viewIn to viewOut
    private fun crossfade(viewIn: View, viewOut: View) {
        // Make viewIn visible at alpha=0, then animate to alpha=1
        viewIn.alpha = 0f
        viewIn.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(viewIn, "alpha", 0f, 1f).apply {
            duration = 1000
            start()
        }
        // Animate viewOut to alpha=0 then make it INVISIBLE
        ObjectAnimator.ofFloat(viewOut, "alpha", 1f, 0f).apply {
            duration = 1000
            start()
            addListener(onEnd = {
                viewOut.visibility = View.INVISIBLE
            })
        }
        gameView.levelStartListener = this
        gameView.levelFailedListener = this

// -------------------- BILLING is moved to BillingManager--------------------
    }


    // ------------------ RINGS -------------------
    // This is exactly how your black holes get placed
    private fun positionRings() {
        val dm      = resources.displayMetrics
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        // Exactly the black-hole technique: center = frac*dimension
        fun computePosition(view: RingView, frac: Pair<Float,Float>) {
            val cx = screenW * frac.first
            val cy = screenH * frac.second
            // drop the view so its center lands at (cx,cy)
            view.x = cx - view.width  / 2f
            view.y = cy - view.height / 2f
        }

        computePosition(ivRingViolet,  Pair(redHolePosFraction.first,
            redHolePosFraction.second))
        computePosition(ivRingGold,    Pair(redHolePosFraction.first,
            redHolePosFraction.second))
        computePosition(ivRingGreen,   Pair(redHolePosFraction.first,
            redHolePosFraction.second))
        computePosition(ivRingYellow,  Pair(redHolePosFraction.first,
            redHolePosFraction.second))
        computePosition(ivRingRed,     Pair(redHolePosFraction.first,
            redHolePosFraction.second))
    }


    // Animate rings with a starting rotation of 0.
    private fun animateRings() {
        gameView.showHoles = false
        val dm           = resources.displayMetrics
        val screenW      = dm.widthPixels.toFloat()
        val screenH      = dm.heightPixels.toFloat()

        // 1) Animate helper stays the same
        fun animateRing(view: View, finalX: Float, finalY: Float, ringId: String) {
            gameView.showHoles = false
            val randomEdge = (0..3).random()
            var startX = finalX
            var startY = finalY
            when (randomEdge) {
                0 -> startX = -view.width.toFloat()
                1 -> startY = -view.height.toFloat()
                2 -> startX = screenW + view.width
                3 -> startY = screenH + view.height
            }
            view.x = startX
            view.y = startY
            view.rotation = 0f
            val extraRotation = (360..720).random().toFloat()
            view.animate()
                .x(finalX)
                .y(finalY)
                .rotationBy(extraRotation)
                .setDuration(3000)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    val finalRotation = view.rotation
                    Log.d("RingAnimation", "Final rotation for ring $ringId: $finalRotation")
                    // Compute turnDeg = finalRotation; if > 360 subtract 360.
                    var turnDeg = finalRotation
                    if (turnDeg > 360f) {
                        turnDeg -= 360f
                    }
                    Log.d("RingAnimation", "turnDeg for ring $ringId: $turnDeg")
                    // Store the rotation for each ring.
                    when (ringId) {
                        "violet" -> finalRotationYellow = turnDeg
                        "gold" ->  finalRotationGreen = turnDeg
                        "green" ->  finalRotationGold = turnDeg
                        "yellow" ->  finalRotationViolet = turnDeg
                        "red" -> finalRotationRed = turnDeg
                        // For red, we leave it as is.
                    }
                }
                .start()
        }

        // 2) New: compute where each ring should end up by using your hole‐style fractions
        fun getFinalPos(view: View, frac: Pair<Float, Float>): Pair<Float, Float> {
            // centerX = frac.first * screenW, then back up half the view’s width
            val x = screenW * frac.first - view.measuredWidth  / 2f
            val y = screenH * frac.second - view.measuredHeight / 2f
            return x to y
        }

        // 3) Make sure each view is measured before animating
        listOf(
            ivRingViolet  to redHolePosFraction ,
            ivRingGold    to redHolePosFraction ,
            ivRingGreen   to redHolePosFraction ,
            ivRingYellow  to redHolePosFraction ,
            ivRingRed     to redHolePosFraction  // or redInitial if you have one
        ).forEach { (view, frac) ->
            view.post {
                val (finalX, finalY) = getFinalPos(view, frac)
                // animate all
                    animateRing(view, finalX, finalY, view.type.name.lowercase())
                    view.x = finalX
                    view.y = finalY
                    view.rotation = 0f
                }
            }


        // 4) If you need to update the hole positions after the rings land,
        //    call your update function here, perhaps using view.measuredWidth/2 as radius:
        handler.postDelayed({
            val radiusPx = ivRingRed.measuredWidth / 2f
            updateAndSendHolePositions(radiusPx)
        }, 3200)
    }



    // Compute the final hole positions using the computed turnDeg for each ring.
    private fun updateAndSendHolePositions(ringSizePx: Float) {
        // Get screen dimensions.
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()
        // The pivot for rotation in screen coordinates.
        val vertexX = screenWidth * vertexFraction.first
        val vertexY = screenHeight * vertexFraction.second

        // Define a helper to rotate a point around the vertex by an angle (in degrees).
        fun rotatePoint(initial: Pair<Float, Float>, angleDeg: Float): Pair<Float, Float> {
            // Convert initial fraction to screen coordinates.
            val initX = screenWidth * initial.first
            val initY = screenHeight * initial.second
            // Translate to origin.
            val relX = initX - vertexX
            val relY = initY - vertexY
            // Convert angle to radians.
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = Math.cos(rad)
            val sinA = Math.sin(rad)
            val rotatedX = (relX * cosA - relY * sinA).toFloat()
            val rotatedY = (relX * sinA + relY * cosA).toFloat()
            // Translate back.
            return Pair(vertexX + rotatedX, vertexY + rotatedY)
        }

        // For each ring, compute its new hole position based on its turnDeg.
        // Note: For each ring, if no rotation was computed (default 0) then new position equals initial.
        val violetNew = rotatePoint(violetInitial, finalRotationViolet)
        val goldNew = rotatePoint(goldInitial, finalRotationGold)
        val greenNew = rotatePoint(greenInitial, finalRotationGreen)
        val yellowNew = rotatePoint(yellowInitial, finalRotationYellow)
        // Red ring hole remains fixed (vertex).
        val redNew = Pair(screenWidth * redHolePosFraction.first, screenHeight * redHolePosFraction.second)

        val newPositions = arrayOf(redNew, violetNew, goldNew, greenNew, yellowNew)
        newPositions.forEachIndexed { index, pos ->
            Log.d("HolePositions", "New hole $index: (${pos.first}, ${pos.second})")
        }
        gameView.updateHolePositions(newPositions, ringFinished = true) // "ringFinished = true" is recently added
        gameView.showHoles = true
        gameView.invalidate()
    }

    // 4) Now the Activity is fully created and views are laid out.
    //    We can safely load the interstitial in onStart().
    override fun onStart() {
        super.onStart()
        // Only load if we haven’t already loaded one:
        if (interstitialAd == null) {
            if (removeAds) return
            loadInterstitialAd()
        }
    }

    // ─── 4) LOAD AN INTERSTITIAL (TEST ID SHOWN BELOW) ─────────────────────────
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            //  This is YOUR real Interstitial ID.
            "ca-app-pub-5967101454574410/6260381850",
            // "ca-app-pub-3940256099942544/1033173712", // Google’s TEST Interstitial ID.
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.setFullScreenContentCallback(object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            // Pause the game while the ad is visible
                            gameView.pauseGame()
                            // Prevent closing the ad for 5 seconds:
                            Handler(Looper.getMainLooper()).postDelayed({
                                interstitialAd?.setImmersiveMode(false)
                            }, 5_000)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            // Resume the game now that the ad is gone
                            // gameView.resumeGame()
                            // Let “PLAY NEXT LEVEL” appear
                            gameView.showPlayNextUI = false
                            gameView.invalidate()

                            // Bring both banners back on top
                            bannerB.bringToFront()
                            bannerA.bringToFront()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                bannerB.elevation = 100f
                                bannerA.elevation = 101f
                            }

                            // Finally, clear this ad reference so next level loads fresh
                            interstitialAd = null
                            // Preload the interstitial for the next level completion
                            if (removeAds) return
                            loadInterstitialAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            gameView.resumeGame()
                            interstitialAd = null
                            // Retry loading a new interstitial after a short delay
                            if (removeAds) return
                            Handler(Looper.getMainLooper()).postDelayed({
                                loadInterstitialAd()
                            }, 5_000)
                        }
                    })
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    // Retry after a delay if it fails
                    if (removeAds) return
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadInterstitialAd()
                    }, 5_000)
                }
            }
        )
    }


    // ─── 3) SHOW INTERSTITIAL WHEN LEVEL STARTS ────────────────────────────────
    override fun onLevelStarted(currentLevel: Int) {
        Log.d("AdMob", "onLevelStarted($currentLevel) called")
        // Example: show on every even level >= 2
        if (currentLevel >= 1 && currentLevel % 1 == 1) {
            if (removeAds) return
            else {
                interstitialAd?.let { ad ->
                    Log.d("AdMob", "▶️ Showing interstitial at level $currentLevel")
                    // Before showing, force immersive‐mode for the first 5 seconds
                    // ad.setImmersiveMode(true)
                    // ad.show(this)
                } ?: run {
                    Log.w("AdMob", "⚠️ Interstitial not ready at level $currentLevel; loading one")
                    loadInterstitialAd()
                }

                bannerA.visibility = View.VISIBLE
                bannerA.bringToFront()
            }
            bannerB.visibility = View.VISIBLE
            bannerB.bringToFront()
            positionRings()
            animateRings()
            // Also reset the “PLAY NEXT LEVEL” flag so it doesn’t show prematurely:
            gameView.showPlayNextUI = false
        }


        when (currentLevel) {
            in 3..6 -> {
                ivRingYellow .setColorFilter(Color.parseColor("#9FB95E"), PorterDuff.Mode.SRC_ATOP)
                ivRingGreen  .setColorFilter(Color.parseColor("#799A41"), PorterDuff.Mode.SRC_ATOP)
                ivRingGold   .setColorFilter(Color.parseColor("#597032"), PorterDuff.Mode.SRC_ATOP)
                ivRingViolet .setColorFilter(Color.parseColor("#425227"), PorterDuff.Mode.SRC_ATOP)
                addVioletEdge("#293619")
            }
            in 7..10 -> {
                ivRingYellow .setColorFilter(Color.parseColor("#75C2D4"), PorterDuff.Mode.SRC_ATOP)
                ivRingGreen  .setColorFilter(Color.parseColor("#54AEC4"), PorterDuff.Mode.SRC_ATOP)
                ivRingGold   .setColorFilter(Color.parseColor("#3294AB"), PorterDuff.Mode.SRC_ATOP)
                ivRingViolet .setColorFilter(Color.parseColor("#267F94"), PorterDuff.Mode.SRC_ATOP)
                addVioletEdge("#00474F")
            }
            in 11..15 -> {
                ivRingYellow .setColorFilter(Color.parseColor("#CAA597"), PorterDuff.Mode.SRC_ATOP)
                ivRingGreen  .setColorFilter(Color.parseColor("#AF796D"), PorterDuff.Mode.SRC_ATOP)
                ivRingGold   .setColorFilter(Color.parseColor("#8E4E46"), PorterDuff.Mode.SRC_ATOP)
                ivRingViolet .setColorFilter(Color.parseColor("#6D3831"), PorterDuff.Mode.SRC_ATOP)
                // addVioletEdge("#462419")
            }
            in 16..19 -> {
                ivRingYellow .setColorFilter(Color.parseColor("#ffd491"), PorterDuff.Mode.SRC_ATOP)
                ivRingGreen  .setColorFilter(Color.parseColor("#ffc262"), PorterDuff.Mode.SRC_ATOP)
                ivRingGold   .setColorFilter(Color.parseColor("#ffaf32"), PorterDuff.Mode.SRC_ATOP)
                ivRingViolet .setColorFilter(Color.parseColor("#e78d00"), PorterDuff.Mode.SRC_ATOP)
                addVioletEdge("#8f5700")
            }
            else -> {
                ivRingYellow .clearColorFilter()
                ivRingGreen  .clearColorFilter()
                ivRingGold   .clearColorFilter()
                ivRingViolet .clearColorFilter()
                ivRingViolet.overlay.clear()
            }
        }


        // 2. Continue with your usual ring positioning & animation
        positionRings()
        animateRings()
    }

    override fun onLevelFailed(currentLevel: Int) {
        // 1) show the banners
        bannerA.visibility = View.VISIBLE
        bannerA.loadAd(AdRequest.Builder().build())
        bannerB.visibility = View.VISIBLE
        bannerB.loadAd(AdRequest.Builder().build())

        // 2) show your in-view “Play Next Level” UI
        gameView.showPlayNextUI = true
        gameView.invalidate()

        // 3) bring banners above the GameView (and its Play-Next overlay)
        bannerA.bringToFront()
        bannerB.bringToFront()


        // 2) if we have an interstitial ready, show it
        interstitialAd?.let { ad ->
            ad.setImmersiveMode(true)
            ad.show(this)
        }
        // 3) otherwise, immediately fall back and queue the next one
            ?: run {
                gameView.showPlayNextUI = false
                gameView.invalidate()
                loadInterstitialAd()    // make sure the next ad is loading
            }
    }

    // Helper to draw the dark edge around just the violet ring
    private fun addVioletEdge(edgeColorHex: String) {
        ivRingViolet.post {
            val w = ivRingViolet.width
            val h = ivRingViolet.height
            val fullSize = minOf(w, h)
            val scale = 0.95f
            val size = (fullSize * scale).toInt()
            val strokePx = (8f * resources.displayMetrics.density).toInt()
            val outerRadius = size / 2f
            val innerRadius = (outerRadius - strokePx / 2f).toInt()

            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(strokePx, Color.parseColor(edgeColorHex))
                this.innerRadius = innerRadius
                thickness = strokePx
                useLevel = false
            }

            val dx = (w - size) / 2
            val dy = (h - size) / 2
            ring.setBounds(dx, dy, dx + size, dy + size)

            ivRingViolet.overlay.clear()
            ivRingViolet.overlay.add(ring)
        }
    }


    override fun onLevelCompleted(currentLevel: Int) {
        bannerA.visibility = View.VISIBLE
        bannerA.loadAd(AdRequest.Builder().build())
        bannerA.bringToFront()

        if (currentLevel > 1) {
            if (interstitialAd != null) {
                interstitialAd!!.setImmersiveMode(true)
                interstitialAd!!.show(this)
            } else {
                // If no ad is ready, immediately show “PLAY NEXT LEVEL”:
                gameView.showPlayNextUI = false
                gameView.invalidate()
                bannerA.bringToFront()
                bannerB.bringToFront()
                // And queue the next interstitial for the next level
                loadInterstitialAd()
            }
        }
    }

    // ─── Pass flags into your GameView ────────────────────────────────
    override fun onResume() {
        super.onResume()
        // val gameView = findViewById<GameView>(R.id.gameView) ?: return
        gameView.removeAds = removeAds
        gameView.removeTimeLimit = removeTimeLimit
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
        handler.removeCallbacksAndMessages(null)
    }
}