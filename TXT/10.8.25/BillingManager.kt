package com.veryeasygames.aimgame

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.android.billingclient.api.*

/**
 * BillingManager with a single "Premium" entitlement:
 *  - Premium removes the timer from Lv11+ and disables interstitial ads.
 *  - Banner ads remain enabled.
 *  - Refunds/restore are handled via resyncEntitlements() (call onResume).
 *
 * IMPORTANT
 *  - For now we reuse the old timer product id ("remove_timer") as the Premium SKU.
 *    When you create a real premium SKU (e.g., "unlock_premium"), update SKU_PREMIUM.
 */
class BillingManager(
    private val activity: Activity,
    private val gameView: GameView
) : PurchasesUpdatedListener {

    companion object {
        // Use ONE product for premium. Replace with your real premium product id later.
        const val SKU_PREMIUM = "remove_timer"

        // Backward compatibility (treat any legacy purchase as premium as well)
        const val SKU_REMOVE_ADS = "remove_ads"
        const val SKU_REMOVE_TIMER = "remove_timer"

        private const val PREFS = "billing_prefs"
        private const val KEY_REMOVE_ADS = "remove_ads"       // means: NO interstitials
        private const val KEY_REMOVE_TIMER = "remove_timer"   // means: NO timer from Lv11+
    }

    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    // Entitlement flags
    private var removeAds: Boolean = false        // we interpret as "no interstitials"
    private var removeTimeLimit: Boolean = false  // "no timer from Lv11+"

    private val billingClient: BillingClient = BillingClient
        .newBuilder(activity)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    /**
     * Call AFTER Activity views (banners/gameView) have been created.
     * - Loads saved flags immediately (so UI starts correct)
     * - Connects to Play Billing
     * - Queries products and re-syncs current purchases
     */
    fun startConnection() {
        // 1) Apply last-known state instantly (prevents UI flicker and works offline)
        removeAds = prefs.getBoolean(KEY_REMOVE_ADS, false)
        removeTimeLimit = prefs.getBoolean(KEY_REMOVE_TIMER, false)
        pushEntitlementsToUi()

        // 2) Connect to Billing and refresh from server
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                // You may retry later
            }
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndCacheProductDetails()
                    resyncEntitlements()
                }
            }
        })
    }

    private fun queryAndCacheProductDetails() {
        val products = listOf(SKU_PREMIUM).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { br, list ->
            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsMap.clear()
                list.forEach { pd -> productDetailsMap[pd.productId] = pd }
            }
        }
    }


    // Helper
    private fun evalOwnership(purchases: List<Purchase>) {
        val ownsPremium = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                it.products.contains(SKU_PREMIUM) }   // premium is ONLY this

        val ownsLegacyInterstitialOnly = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                it.products.contains(SKU_REMOVE_ADS) } // old product

        // Apply:
        removeTimeLimit = ownsPremium                     // Timer off only with Premium
        removeAds = ownsPremium || ownsLegacyInterstitialOnly   // Interstitials off with either
    }

    /** Call this on Activity.onResume() to handle refunds/restores. */
    fun resyncEntitlements(onDone: (() -> Unit)? = null) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) { onDone?.invoke(); return@queryPurchasesAsync }

            val oldRemoveAds = removeAds
            val oldRemoveTimer = removeTimeLimit

            evalOwnership(purchases)

            if (removeAds != oldRemoveAds || removeTimeLimit != oldRemoveTimer) {
                persistEntitlements()
                activity.runOnUiThread { pushEntitlementsToUi() }
            }
            onDone?.invoke()
        }
    }

    fun checkAndShowUnlockDialog() {
        // Always re-query Play first (handles refunds/restores)
        resyncEntitlements {
            activity.runOnUiThread {
                // After resync, these booleans reflect the truth
                val owned = removeAds && removeTimeLimit
                if (owned) {
                    Toast.makeText(activity, "Premium already unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    showRemoveOptionsDialog()
                }
            }
        }
    }

    /** Show a single-option dialog to unlock Premium (if not owned). */
    private fun showRemoveOptionsDialog() {
        val price = getPrice(SKU_PREMIUM)
        val label = "Unlock Premium (No Timer Lv11+, No Interstitials): $price"

        AlertDialog.Builder(activity)
            .setTitle("Unlock Premium")
            .setItems(arrayOf(label)) { dialog, _ ->
                launchPurchaseFlow(SKU_PREMIUM)
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss(); gameView.onDialogCancel() }
            .create()
            .apply {
                setCanceledOnTouchOutside(true)
                setOnCancelListener { gameView.onDialogCancel() }
                show()
                window?.apply {
                    setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
                    setGravity(Gravity.CENTER)
                }
            }
    }

    private fun getPrice(productId: String): String {
        val pd = productDetailsMap[productId] ?: return "..."
        return pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "..."
    }

    private fun launchPurchaseFlow(productId: String) {
        val pd = productDetailsMap[productId]
        if (pd == null) {
            Toast.makeText(activity, "Store not ready. Try again in a moment.", Toast.LENGTH_SHORT).show()
            queryAndCacheProductDetails()
            return
        }
        val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(pdParams))
            .build()
        billingClient.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // acknowledge as you already do...
            evalOwnership(purchases)
            persistEntitlements()
            activity.runOnUiThread { pushEntitlementsToUi() }
            Toast.makeText(activity, "Purchase updated", Toast.LENGTH_SHORT).show()
            gameView.onDialogCancel()
            return
        }
        if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Purchase failed: ${result.debugMessage}", Toast.LENGTH_LONG).show()
        }
        gameView.onDialogCancel()
    }

    private fun persistEntitlements() {
        prefs.edit()
            .putBoolean(KEY_REMOVE_ADS, removeAds)
            .putBoolean(KEY_REMOVE_TIMER, removeTimeLimit)
            .apply()
    }

    private fun pushEntitlementsToUi() {
        // Push to GameView
        gameView.removeAds = removeAds              // interpreted as: NO interstitials
        gameView.removeTimeLimit = removeTimeLimit  // interpreted as: NO timer from Lv11+

        // Inform Activity (to gate interstitials and keep banners)
        (activity as? MainActivity)?.onEntitlementsUpdated(removeAds, removeTimeLimit)
    }

    fun destroy() {
        billingClient.endConnection()
    }
}
