package com.veryeasygames.aimgame

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * MainActivity revised for a single "Premium" entitlement:
 *  - Premium removes timer from Lv11+ and disables interstitials.
 *  - Banners remain active in both free and premium.
 *  - Refunds are handled by resyncEntitlements() in onResume().
 *
 * NOTE: This version builds banners programmatically into a FrameLayout.
 * If you already have banners in XML, you can keep those and simply gate interstitials
 * and timer with the flags; no need to use these programmatic banners.
 */
class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var root: FrameLayout
    lateinit var gameView: GameView

    // Ads
    private var bannerTop: AdView? = null
    private var bannerBottom: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private val handler = Handler(Looper.getMainLooper())

    // Entitlements (removeAds == NO interstitials; banners stay)
    private var removeAds: Boolean = false
    private var removeTimeLimit: Boolean = false

    // Billing
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root container
        root = FrameLayout(this).apply { foregroundGravity = Gravity.CENTER }
        setContentView(root)

        // Game view
        gameView = GameView(this).also {
            it.removeAds = removeAds
            it.removeTimeLimit = removeTimeLimit
        }
        root.addView(
            gameView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Initialize Mobile Ads
        MobileAds.initialize(this)

        // Banners (kept even for premium)
        createAndAttachBanners()
        if (!removeAds) {
            loadBanners()
            scheduleBannerReload()
        }

        // Billing after views exist
        billingManager = BillingManager(this, gameView)
        gameView.billingManager = billingManager      // <<< IMPORTANT
        billingManager.startConnection()

        // Optionally load an interstitial at start if allowed
        if (!removeAds) {
            loadInterstitialAd()
        }
    }

    // ===== Entitlement updates from Billing =====
    fun onEntitlementsUpdated(removeAds: Boolean, removeTimer: Boolean) {
        this.removeAds = removeAds
        this.removeTimeLimit = removeTimer

        // Push to GameView
        gameView.removeAds = removeAds
        gameView.removeTimeLimit = removeTimer

        // Interstitials only: disable in premium
        if (removeAds) {
            interstitialAd = null
            // Leave banners alone (keep showing)
        } else {
            if (interstitialAd == null) loadInterstitialAd()
        }
    }

    // ===== Interstitial helpers (always gate by !removeAds) =====
    private fun loadInterstitialAd() {
        if (removeAds) return
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            getString(R.string.admob_interstitial_id),
            request,
            object : InterstitialAdLoadCallback() {}
        )
    }

    fun maybeShowInterstitial() {
        if (!removeAds) {
            interstitialAd?.show(this)
        }
    }

    // ===== Banners (kept in premium) =====
    private fun createAndAttachBanners() {
        bannerTop = AdView(this).apply {
            adUnitId = getString(R.string.admob_bannerA_id)
            setAdSize(AdSize.BANNER)
        }
        bannerBottom = AdView(this).apply {
            adUnitId = getString(R.string.admob_bannerB_id)
            setAdSize(AdSize.LARGE_BANNER)
        }

        root.addView(
            bannerTop,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )
        root.addView(
            bannerBottom,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
    }

    private fun loadBanners() {
        bannerTop?.loadAd(AdRequest.Builder().build())
        bannerBottom?.loadAd(AdRequest.Builder().build())
    }

    private val reloadRunnable = object : Runnable {
        override fun run() {
            loadBanners()
            handler.postDelayed(this, 65_000L)
        }
    }

    private fun scheduleBannerReload() {
        handler.removeCallbacks(reloadRunnable)
        handler.postDelayed(reloadRunnable, 65_000L)
    }

    override fun onResume() {
        super.onResume()
        // Re-sync to detect refunds/restores
        billingManager.resyncEntitlements()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
        handler.removeCallbacks(reloadRunnable)
        bannerTop?.destroy()
        bannerBottom?.destroy()
    }
}
