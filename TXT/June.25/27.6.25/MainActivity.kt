package com.example.aimgame

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Color.YELLOW
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
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
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
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
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.addListener

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
// import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.ktx.analytics

class MainActivity : AppCompatActivity(), GameView.LevelStartListener {
    // The red ring’s hole position remains fixed.
    private val redHolePosFraction = Pair(0.5f, 0.483f)

    // Target fractional positions for the rings (used for positioning the views).
    private val violetPos = Pair(0.141f, 0.051f)
    private val goldPos = Pair(0.139f, 0.05f)
    private val greenPos = Pair(0.139f, 0.05f)
    private val yellowPos = Pair(0.139f, 0.05f)
    private val redPos = Pair(0.139f, 0.05f)

    // For computing new hole positions we use the following initial side fractions.
    // (These correspond to the initial unrotated positions of the holes in the ring images.)
    private val violetInitial = Pair(0.573f, 0.52f)
    private val goldInitial   = Pair(0.632f, 0.55f)
    private val greenInitial  = Pair(0.69f, 0.578f)
    private val yellowInitial = Pair(0.75f, 0.607f)
    // The vertex (pivot) for the rotation of the holes is:
    private val vertexFraction = Pair(0.5f, 0.483f)

    // References to the ring ImageViews.
    private lateinit var ivRingViolet: ImageView
    private lateinit var ivRingGold: ImageView
    private lateinit var ivRingGreen: ImageView
    private lateinit var ivRingYellow: ImageView
    private lateinit var ivRingRed: ImageView

    // Reference to GameView and ads.
    private lateinit var gameView: GameView
    private lateinit var bannerView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var interstitialShowCount = 0

    // Two banners for cross‐fade
    private lateinit var bannerA: AdView
    // private lateinit var bannerB: AdView
    private var showingA = true

    private lateinit var frameLayout: FrameLayout
    private val handler = Handler(Looper.getMainLooper())

    // Global storage for each ring's final rotation.
    private var finalRotationViolet = 0f
    private var finalRotationGold = 0f
    private var finalRotationGreen = 0f
    private var finalRotationYellow = 0f
    private var finalRotationRed = 0f
    // For red ring, we won’t change its rotation.


    // Runnable that kicks off loading the *hidden* banner every 30s
    private val reloadBannerRunnable = object : Runnable {
        override fun run() {
            // Decide which one is hidden
            val toLoad =  bannerA // if (showingA) bannerB else
            toLoad.loadAd(AdRequest.Builder().build())
            // Schedule next reload
            handler.postDelayed(this, 65_000)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1) Configure test devices and use real unit IDs only in production.
        //    Here, remove all test‐device configuration so we get real ads:

        val testDeviceIds = listOf(
            AdRequest.DEVICE_ID_EMULATOR,
            "30AB8CD9F183E6686ED425A1383C9785"
        )
        val config = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(config)


        // ─── Initialize the Mobile Ads SDK ───────────────────────────────────
        MobileAds.initialize(this) { status ->
            Log.d("AdMob", "SDK Initialization complete: $status")
        }


        // 3) Build a full‐screen FrameLayout container
        val frameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Measure the banner height (in px) so we can pad the game area above it:
        // val bannerHeightPx = AdSize.BANNER.getHeightInPixels(this)

        // Bottom Layer: Background image.
        val ivBackground = ImageView(this).apply {
            setImageResource(R.drawable.wood_wall)
            scaleType = ImageView.ScaleType.CENTER_CROP
            // translationY = -bannerHeightPx.toFloat()
        }
        frameLayout.addView(ivBackground)


        // Middle Layer: Ring overlays.
        ivRingViolet = ImageView(this).apply {
            setImageResource(R.drawable.ring_violet)
            scaleType = ImageView.ScaleType.CENTER
        }
        ivRingGold = ImageView(this).apply {
            setImageResource(R.drawable.ring_gold)
            scaleType = ImageView.ScaleType.CENTER
        }
        ivRingGreen = ImageView(this).apply {
            setImageResource(R.drawable.ring_green)
            scaleType = ImageView.ScaleType.CENTER
        }
        ivRingYellow = ImageView(this).apply {
            setImageResource(R.drawable.ring_yellow)
            scaleType = ImageView.ScaleType.CENTER
        }
        ivRingRed = ImageView(this).apply {
            setImageResource(R.drawable.ring_red)
            scaleType = ImageView.ScaleType.CENTER
        }
        frameLayout.addView(ivRingViolet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameLayout.addView(ivRingGold, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameLayout.addView(ivRingGreen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameLayout.addView(ivRingYellow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameLayout.addView(ivRingRed, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 6) Add the GameView on top
        gameView = GameView(this).also { gv ->
            gv.levelStartListener = this
            frameLayout.addView(
                gv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

// convert 16 dp → px
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        // dp → px helper
        val dp0 = 0

// 1) “Set Flat” on left center
        val btnFlat = Button(this).apply {
            text = "Set Flat"
            isAllCaps = false
            rotation = -90f                   // rotate CCW 90°
            typeface = Typeface.create("constantia", Typeface.BOLD_ITALIC)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(YELLOW)
            setOnClickListener {view: View ->
                gameView.orientationOffset = 0
                //gameView.baselineSet       = true
                //gameView.baselineX         = 0f
                //gameView.baselineY         = 0f
                gameView.resumeGame()
            }
        }
        frameLayout.addView(
            btnFlat,
            FrameLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dp0
            }
        )

// 2) “Set Tilt” on right center
        val btnTilt = Button(this).apply {
            text = "Set Tilt"
            isAllCaps = false
            rotation = 90f                  // rotate CCW 90°
            typeface = Typeface.create("constantia", Typeface.BOLD_ITALIC)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(YELLOW)
            setOnClickListener {
                gameView.orientationOffset = -45
                gameView.calibrateBaseline()
                gameView.resumeGame()
            }
        }
        frameLayout.addView(
            btnTilt,
            FrameLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dp0
            }
        )
        btnFlat.setPadding(50, 0, 0, 180)
        btnTilt.setPadding(0, 0, 30, 180)


        // ─── Banner A ─────────────────────────────────────────────────────────
        bannerA = AdView(this).apply {
            setAdSize(AdSize.LARGE_BANNER)
            // TODO: replace with your real Banner unit ID
            adUnitId = "ca-app-pub-5967101454574410/8876366550"
            // adUnitId = "ca-app-pub-3940256099942544/6300978111" // your test unit ID
            alpha = 1f
        }
        /*
        // ─── Banner B (initially invisible) ───────────────────────────────────
        bannerB = AdView(this).apply {
            setAdSize(AdSize.LARGE_BANNER)
            adUnitId = "ca-app-pub-5967101454574410/8876366550"
            // adUnitId = "ca-app-pub-3940256099942544/6300978111" // your test unit ID
            alpha = 0f
            visibility = View.INVISIBLE
        }

         */

        val bannerParams = FrameLayout.LayoutParams(
            AdSize.LARGE_BANNER.getWidthInPixels(this),
            AdSize.LARGE_BANNER.getHeightInPixels(this),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )

        // Add both to the layout (A on top of B initially)
        // frameLayout.addView(bannerB, bannerParams)
        frameLayout.addView(bannerA, bannerParams)

        // Always bring them above everything
        // bannerB.bringToFront()
        bannerA.bringToFront()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bannerA.elevation = 100f
            // bannerB.elevation = 101f
        }
        /*
                // Bring banner to front and bump its elevation so nothing can cover it
                bannerView.bringToFront()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bannerView.elevation = 100f
                }

         */


        // AdListeners for cross‐fade logic
        bannerA.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob","✅ BannerA loaded")
                /*
                if (!showingA) {
                    // Cross‐fade A onto B
                    crossfade(bannerA, bannerB)
                    showingA = true
                }
                 */
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
                }, 5_000)  // e.g. 5s buffer so you don’t reload too aggressively
            }

            private var retryDelayMs = 10_000
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob","❌ BannerA failed: ${error.code} ${error.message}")
                handler.postDelayed({
                    bannerA.loadAd(AdRequest.Builder().build())
                    // increase delay up to a max, e.g. 5→10→20→40s
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000)
                }, retryDelayMs.toLong())
            }
        }
        /*
        bannerB.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob","✅ BannerB loaded")
                if (showingA) {
                    // Cross‐fade B onto A
                    crossfade(bannerB, bannerA)
                    showingA = false
                }
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob","❌ BannerB failed: ${error.code} ${error.message}")
            }
        }

         */
        gameView.levelStartListener = this

        // Finish setup
        setContentView(frameLayout)
        positionRings()
        animateRings()

        // Load first banner into A and start the reload cycle
        bannerA.loadAd(AdRequest.Builder().build())
        handler.postDelayed(reloadBannerRunnable, 65_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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
    }


    // Position rings based on their target fractional positions.
    private fun positionRings() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()

    // 2) Compute scaleFactor off your “design” width (here: 1080px baseline)
        val designW = 1080f
        val scaleFactor = screenWidth / designW

        // 3) Base ring size in dp → px, then scale
        val baseRingDp = 100f
        val ringSizePx = TypedValue
            .applyDimension(COMPLEX_UNIT_DIP, baseRingDp, dm)
            .times(scaleFactor)

        fun computePosition(fx: Float, fy: Float): Pair<Float, Float> {
            val x = screenWidth * fx - ringSizePx/2f
            val y = screenHeight * fy - ringSizePx/2f
            return Pair(x, y)
        }
        computePosition(goldPos.first, goldPos.second).apply {
            ivRingGold.x = first
            ivRingGold.y = second
        }
        computePosition(greenPos.first, greenPos.second).apply {
            ivRingGreen.x = first
            ivRingGreen.y = second
        }
        computePosition(redPos.first, redPos.second).apply {
            ivRingRed.x = first
            ivRingRed.y = second
        }
        computePosition(violetPos.first, violetPos.second).apply {
            ivRingViolet.x = first
            ivRingViolet.y = second
        }
        computePosition(yellowPos.first, yellowPos.second).apply {
            ivRingYellow.x = first
            ivRingYellow.y = second
        }
    }

    // Animate rings with a starting rotation of 0.
    private fun animateRings() {
        gameView.showHoles = false
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat() // * 1.25f // was 1
        // val ringSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, dm)

        // same scaleFactor logic
        val designW = 1080f
        val scaleFactor = screenWidth / designW
        val baseRingDp = 100f // was 100f
        val ringSizePx = TypedValue
            .applyDimension(COMPLEX_UNIT_DIP, baseRingDp, dm)
            .times(scaleFactor)


        // Helper function that now accepts a ring identifier.
        fun animateRing(view: ImageView, finalX: Float, finalY: Float, ringId: String) {
            gameView.showHoles = false
            // Random starting edge.
            val randomEdge = (0..3).random()
            var startX = finalX
            var startY = finalY
            when (randomEdge) {
                0 -> { startX = -ringSizePx; startY = (0..screenHeight.toInt()).random().toFloat() }
                1 -> { startY = -ringSizePx; startX = (0..screenWidth.toInt()).random().toFloat() }
                2 -> { startX = screenWidth + ringSizePx; startY = (0..screenHeight.toInt()).random().toFloat() }
                3 -> { startY = screenHeight + ringSizePx; startX = (0..screenWidth.toInt()).random().toFloat() }
            }
            view.x = startX
            view.y = startY
            // Set starting rotation explicitly to 0.
            view.rotation = 0f
            Log.d("RingAnimation", "Starting rotation for ring $ringId: ${view.rotation}")

            // Choose a random extra rotation.
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

        // Compute final positions for each ring view.
        fun getFinalPos(fraction: Pair<Float, Float>): Pair<Float, Float> {
            val x = screenWidth * fraction.first - ringSizePx / 2f
            val y = screenHeight * fraction.second - ringSizePx / 2f
            return Pair(x, y)
        }
        val finalGold = getFinalPos(goldPos)
        val finalGreen = getFinalPos(greenPos)
        val finalRed = getFinalPos(redPos)
        val finalViolet = getFinalPos(violetPos)
        val finalYellow = getFinalPos(yellowPos)

        animateRing(ivRingViolet, finalViolet.first, finalViolet.second, "violet")
        animateRing(ivRingGold, finalGold.first, finalGold.second, "gold")
        animateRing(ivRingGreen, finalGreen.first, finalGreen.second, "green")
        animateRing(ivRingYellow, finalYellow.first, finalYellow.second, "yellow")
        animateRing(ivRingRed, finalRed.first, finalRed.second, "red") // red ring is now animated

        // For red ring, no rotation is needed.
        ivRingRed.x = finalRed.first
        ivRingRed.y = finalRed.second
        ivRingRed.rotation = 0f
        Log.d("RingAnimation", "Red ring rotation remains 0.")

        // After animations finish (~3200ms), update hole positions.
        handler.postDelayed({
            updateAndSendHolePositions(ringSizePx)
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
            loadInterstitialAd()
        }
    }

    // ─── 4) LOAD AN INTERSTITIAL (TEST ID SHOWN BELOW) ─────────────────────────
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            //  This is YOUR real Interstitial ID.
            "ca-app-pub-5967101454574410/6939101371",
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
                            // Finally, clear this ad reference so next level loads fresh
                            interstitialAd = null
                            // Preload the interstitial for the next level completion
                            loadInterstitialAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            gameView.resumeGame()
                            interstitialAd = null
                            // Retry loading a new interstitial after a short delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                loadInterstitialAd()
                            }, 5_000)
                        }
                    })
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    // Retry after a delay if it fails
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadInterstitialAd()
                    }, 10_000)
                }
            }
        )
    }


    // ─── 3) SHOW INTERSTITIAL WHEN LEVEL STARTS ────────────────────────────────
    override fun onLevelStarted(currentLevel: Int) {
        Log.d("AdMob", "onLevelStarted($currentLevel) called")
        // Example: show on every even level >= 2
        if (currentLevel >= 1 && currentLevel % 1 == 1) {
            interstitialAd?.let { ad ->
                Log.d("AdMob", "▶️ Showing interstitial at level $currentLevel")
                // Before showing, force immersive‐mode for the first 5 seconds
                // ad.setImmersiveMode(true)
                // ad.show(this)
            } ?: run {
                Log.w("AdMob", "⚠️ Interstitial not ready at level $currentLevel; loading one")
                // loadInterstitialAd()
            }
            bannerA.visibility = View.VISIBLE
            // bannerB.visibility = View.VISIBLE
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
                addVioletEdge("#462419")
            }
            in 16..19 -> {
                ivRingYellow .setColorFilter(Color.parseColor("#9FB95E"), PorterDuff.Mode.SRC_ATOP)
                ivRingGreen  .setColorFilter(Color.parseColor("#799A41"), PorterDuff.Mode.SRC_ATOP)
                ivRingGold   .setColorFilter(Color.parseColor("#597032"), PorterDuff.Mode.SRC_ATOP)
                ivRingViolet .setColorFilter(Color.parseColor("#425227"), PorterDuff.Mode.SRC_ATOP)
                addVioletEdge("#293619")
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

    // Helper to draw the dark edge around just the violet ring
    private fun addVioletEdge(edgeColorHex: String) {
        ivRingViolet.post {
            val w = ivRingViolet.width
            val h = ivRingViolet.height
            val fullSize = minOf(w, h)
            val scale = 0.85f
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

        if (interstitialAd != null) {
            interstitialAd!!.setImmersiveMode(true)
            interstitialAd!!.show(this)
        } else {
            // If no ad is ready, immediately show “PLAY NEXT LEVEL”:
            gameView.showPlayNextUI = false
            gameView.invalidate()
            // And queue the next interstitial for the next level
            loadInterstitialAd()
        }
    }


}