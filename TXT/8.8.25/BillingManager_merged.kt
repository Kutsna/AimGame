package com.veryeasygames.aimgame

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.android.billingclient.api.*

/**
 * Drop-in Billing manager that:
 * - Uses ProductDetails (no deprecated SkuDetails)
 * - Restores purchases on startup
 * - Persists entitlements to SharedPreferences("billing_prefs")
 * - Pushes entitlements to MainActivity + GameView (and hides ads immediately)
 */
class BillingManager(
    private val activity: Activity,
    private val gameView: GameView
) : PurchasesUpdatedListener {

    companion object {
        const val SKU_REMOVE_ADS = "remove_ads"
        const val SKU_REMOVE_TIMER = "remove_timer"
        private const val PREFS = "billing_prefs"
        private const val KEY_REMOVE_ADS = "remove_ads"
        private const val KEY_REMOVE_TIMER = "remove_timer"
        private const val TAG = "BillingManager"
    }

    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    private var removeAds: Boolean = false
    private var removeTimeLimit: Boolean = false

    private val billingClient: BillingClient = BillingClient
        .newBuilder(activity)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    init {
        // read persisted entitlements as a baseline (so UI starts correct even before billing connects)
        removeAds = prefs.getBoolean(KEY_REMOVE_ADS, false)
        removeTimeLimit = prefs.getBoolean(KEY_REMOVE_TIMER, false)
        pushEntitlements()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                // optional: retry
                Log.w(TAG, "Billing service disconnected")
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndCacheProductDetails()
                    restoreExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }
        })
    }

    private fun queryAndCacheProductDetails() {
        val products = listOf(SKU_REMOVE_ADS, SKU_REMOVE_TIMER).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { br, list ->
            if (br.responseCode == BillingClient.BillingResponseCode.OK && list.isNotEmpty()) {
                productDetailsMap.clear()
                list.forEach { pd -> productDetailsMap[pd.productId] = pd }
            } else {
                Log.w(TAG, "ProductDetails query failed: ${br.debugMessage}")
            }
        }
    }

    private fun restoreExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchasesAsync failed: ${br.debugMessage}")
                return@queryPurchasesAsync
            }
            var changed = false
            purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (p.products.contains(SKU_REMOVE_ADS)) {
                        if (!removeAds) changed = true
                        removeAds = true
                    }
                    if (p.products.contains(SKU_REMOVE_TIMER)) {
                        if (!removeTimeLimit) changed = true
                        removeTimeLimit = true
                    }
                }
            }
            if (changed) persistEntitlements()
            pushEntitlements()
        }
    }

    /** Launch purchase flow for a given inapp product id. */
    private fun launchPurchase(productId: String) {
        val pd = productDetailsMap[productId]
        if (pd == null) {
            Toast.makeText(activity, "Store info not ready, try again", Toast.LENGTH_SHORT).show()
            queryAndCacheProductDetails()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun launchRemoveAdsPurchase() = launchPurchase(SKU_REMOVE_ADS)
    fun launchRemoveTimerPurchase() = launchPurchase(SKU_REMOVE_TIMER)

    /**
     * Optional helper to show a tiny purchase dialog.
     * Make sure the strings EXACTLY match what you check in the click handler.
     */
    fun showRemoveOptionsDialog() {
        val items = mutableListOf<String>()
        if (!removeAds) items += "Remove Ads: ${getPrice(SKU_REMOVE_ADS)}"
        if (!removeTimeLimit) items += "Remove Timer from Lv11: ${getPrice(SKU_REMOVE_TIMER)}"
        if (items.isEmpty()) {
            Toast.makeText(activity, "All options already unlocked", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Unlock Premium")
            .setItems(items.toTypedArray()) { d, which ->
                when (items[which]) {
                    "Remove Ads: ${getPrice(SKU_REMOVE_ADS)}" -> launchRemoveAdsPurchase()
                    "Remove Timer from Lv11: ${getPrice(SKU_REMOVE_TIMER)}" -> launchRemoveTimerPurchase()
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { w ->
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val lp = w.attributes
                lp.gravity = Gravity.CENTER
                w.attributes = lp
            }
        }
        dialog.show()
    }

    private fun getPrice(productId: String): String {
        val pd = productDetailsMap[productId] ?: return "..."
        // INAPP one-time products: use oneTimePurchaseOfferDetails
        return pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "..."
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            var changed = false
            purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (p.products.contains(SKU_REMOVE_ADS) && !removeAds) {
                        removeAds = true
                        changed = true
                    }
                    if (p.products.contains(SKU_REMOVE_TIMER) && !removeTimeLimit) {
                        removeTimeLimit = true
                        changed = true
                    }
                    if (!p.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ack) { ar ->
                            Log.d(TAG, "acknowledge result: ${ar.responseCode}")
                        }
                    }
                }
            }
            if (changed) persistEntitlements()
            pushEntitlements()
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Purchase failed: ${result.debugMessage}", Toast.LENGTH_LONG).show()
        }
        // If you have a dialog on the GameView, you can dismiss it here:
        try { gameView.onDialogCancel() } catch (_: Throwable) {}
    }

    private fun persistEntitlements() {
        prefs.edit()
            .putBoolean(KEY_REMOVE_ADS, removeAds)
            .putBoolean(KEY_REMOVE_TIMER, removeTimeLimit)
            .apply()
    }

    private fun pushEntitlements() {
        // Push into GameView
        gameView.removeAds = removeAds
        gameView.removeTimeLimit = removeTimeLimit
        gameView.postInvalidateOnAnimation()
        // Push into Activity (and hide currently loaded ads)
        (activity as? MainActivity)?.onEntitlementsUpdated(removeAds, removeTimeLimit)
        if (removeAds) (activity as? MainActivity)?.hideAllAds()
    }

    fun destroy() {
        billingClient.endConnection()
    }
}
