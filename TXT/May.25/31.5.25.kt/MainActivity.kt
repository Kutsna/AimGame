package com.example.aimgame

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import androidx.webkit.WebViewCompat


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

    // Reference to GameView.
    private lateinit var gameView: GameView
    private var interstitialAd: InterstitialAd? = null

    private lateinit var frameLayout: FrameLayout
    private val handler = Handler(Looper.getMainLooper())

    // Global storage for each ring's final rotation.
    private var finalRotationViolet = 0f
    private var finalRotationGold = 0f
    private var finalRotationGreen = 0f
    private var finalRotationYellow = 0f
    private var finalRotationRed = 0f
    // For red ring, we won’t change its rotation.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val testDeviceIds = listOf(
            AdRequest.DEVICE_ID_EMULATOR,
            "30AB8CD9F183E6686ED425A1383C9785"
        )
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        // ─── 2) Initialize Mobile Ads SDK ────────────────────────────────────────
        MobileAds.initialize(this) { status ->
            Log.d("AdMob", "SDK Init complete: $status")
            // NOTE: We are NOT calling loadInterstitialAd() here anymore
            // because we need to first ensure a WebView (JS engine) is spun up.
        }

        // ─── 3) Build the full-screen FrameLayout container ──────────────────────
        frameLayout = FrameLayout(this).apply {
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

        // ─── 6) Top layer: GameView ──────────────────────────────────────────────
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

        // ─── 7) Attach the container as the Content View ─────────────────────────
        setContentView(frameLayout)

        // ─── 8) After the view is laid out, align & animate rings ─────────────────
        gameView.post {
            // 8a) Check which WebView package is in use (for debugging)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val wvInfo = WebView.getCurrentWebViewPackage()
                if (wvInfo != null) {
                    Log.d(
                        "WebViewCheck",
                        "Default WebView pkg: ${wvInfo.packageName}, v${wvInfo.versionName}"
                    )
                } else {
                    Log.e("WebViewCheck", "No WebView implementation found on this device!")
                }
            } else {
                Log.d("WebViewCheck", "API < 26, cannot query default WebView pkg.")
            }

            // 8b) Create the “bootstrap” WebView, attach it (1×1 px), then load HTML
            val bootstrapWebView = WebView(this).apply {
                // Make it invisible and 1×1 pixel so it doesn’t affect layout
                visibility = android.view.View.INVISIBLE
                layoutParams = FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START)
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d("WebViewBootstrap", "onPageFinished: JS engine is ready.")

                        // Now that we’ve confirmed the JS engine spun up, load both ads:
                        loadBannerAd()
                        loadInterstitialAd()
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        Log.e("WebViewBootstrap", "JS engine test failed: ${error.description}")
                        // If this happens, ad loads will also fail.
                    }
                }
            }
            // Add it to the FrameLayout so it becomes part of the view hierarchy:
            frameLayout.addView(bootstrapWebView)

            // Load a trivial HTML string that immediately executes JavaScript:
            val htmlString = """
                <html>
                  <body>
                    <script>
                      // This line runs as soon as the page loads
                      window.myTestVar = "JS engine is alive";
                    </script>
                  </body>
                </html>
            """.trimIndent()
            bootstrapWebView.loadDataWithBaseURL(
                null,
                htmlString,
                "text/html",
                "utf-8",
                null
            )
            positionRings()
            animateRings()
        }

        // ─── 10) Finally, overlay and load the Banner Ad ──────────────────────────
        val adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            // ← Replace with your banner unit ID (test or production)
            adUnitId = "ca-app-pub-5967101454574410/8876366550"
        }
        val adParams = FrameLayout.LayoutParams(
            AdSize.BANNER.getWidthInPixels(this),
            AdSize.BANNER.getHeightInPixels(this),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        frameLayout.addView(adView, adParams)
        adView.bringToFront()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            adView.elevation = 100f
        }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob", "✅ Banner loaded")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob", "❌ Banner failed: ${error.code} – ${error.message}")
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    // Position rings based on their target fractional positions.
    private fun positionRings() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()
        val ringSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, dm)
        fun computePosition(fractionX: Float, fractionY: Float): Pair<Float, Float> {
            val x = screenWidth * fractionX - ringSizePx / 2f
            val y = screenHeight * fractionY - ringSizePx / 2f
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
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()
        val ringSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, dm)

        // Helper function that now accepts a ring identifier.
        fun animateRing(view: ImageView, finalX: Float, finalY: Float, ringId: String) {
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


    // ─── Loads (and attaches) the Banner AdView at the bottom ─────────────────
    private fun loadBannerAd() {
        val adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            // ← Replace with your real/test Banner Ad Unit ID
            adUnitId = "ca-app-pub-5967101454574410/8876366550"
        }
        val adParams = FrameLayout.LayoutParams(
            AdSize.BANNER.getWidthInPixels(this),
            AdSize.BANNER.getHeightInPixels(this),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        // Add the banner on top of all other views
        frameLayout.addView(adView, adParams)
        adView.bringToFront()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            adView.elevation = 100f
        }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob", "✅ Banner loaded")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdMob", "❌ Banner failed to load: ${error.code} – ${error.message}")
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }


    // ─── Loads an interstitial *after* a WebView has been initialized ───────────
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            // ← Replace with your interstitial unit ID (test or production)
            "ca-app-pub-5967101454574410/6939101371",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdMob", "Interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e("AdMob", "Interstitial failed: ${error.message}")
                }
            }
        )
    }

    // ─── Show an interstitial every 3 levels (for example) ─────────────────────
    override fun onLevelStarted(currentLevel: Int) {
        if (currentLevel > 1 && currentLevel % 3 == 0) {
            interstitialAd?.let { ad ->
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "Interstitial shown")
                    }
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "Interstitial dismissed")
                        loadInterstitialAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.e("AdMob", "Interstitial show failed: ${error.message}")
                        loadInterstitialAd()
                    }
                }
                ad.show(this)
                interstitialAd = null
            } ?: run {
                Log.d("AdMob", "Interstitial not ready; will retry later")
                // Optionally retry loading immediately or wait until next level
                loadInterstitialAd()
            }
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
}