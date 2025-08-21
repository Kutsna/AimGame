package com.veryeasygames.aimgame

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.android.billingclient.api.*

class BillingManager(
    private val activity: Activity,
    private val gameView: GameView
) : PurchasesUpdatedListener {

    data class RefundInfo(
        // val status: String,
        val refunded: Boolean,          // inferred: previously owned, not owned now
        val ownedNow: Boolean,          // currently owned according to Play
        val previouslyOwned: Boolean    // seen in purchase history
    )

    private val TAG = "BillingManager"

    @Suppress("DEPRECATION")
    private val billingClient: BillingClient = BillingClient
        .newBuilder(activity)
        .enablePendingPurchases()    // deprecated, but required for one-time products
        .setListener(this)
        .build()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    var removeAds = false
    var removeTimeLimit = false

    private fun applyEntitlements() {
        // 1) Copy your flags into the GameView
        gameView.removeAds       = removeAds
        gameView.removeTimeLimit = removeTimeLimit
    }

    companion object {
        const val SKU_REMOVE_ADS = "remove_ads"
        const val SKU_REMOVE_TIMER = "remove_timer"
        val PREMIUM_SKUS = setOf(SKU_REMOVE_TIMER, SKU_REMOVE_ADS)
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndCacheProductDetails()
                    queryExistingPurchases()
                    // showRemoveOptionsDialog()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Optional: Retry connection
            }
        })
    }

    fun getRefundInfo(onResult: (RefundInfo) -> Unit) {
        // 1) What is owned *now*?
        val purchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(purchasesParams) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "getRefundInfo: queryPurchasesAsync failed: ${br.responseCode} ${br.debugMessage}")
                // Fall back to our current flags to produce a reasonable status
                val fallbackOwned = removeAds && removeTimeLimit
                val status = if (fallbackOwned) "Purchased" else "Available"
                onResult(RefundInfo(refunded = false, ownedNow = fallbackOwned, previouslyOwned = false))
                return@queryPurchasesAsync
            }

            val premiumSkus = setOf(SKU_REMOVE_ADS, SKU_REMOVE_TIMER)
            val ownedNow = purchases.any { p ->
                p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it in premiumSkus }
            }

            // 2) Did we ever own it? (history can include refunded/canceled)
            val histParams = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchaseHistoryAsync(histParams) { hr, records ->
                if (hr.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "getRefundInfo: queryPurchaseHistoryAsync failed: ${hr.responseCode} ${hr.debugMessage}")
                }

                val previouslyOwned = records?.any { r -> r.products.any { it in premiumSkus } } == true
                val refunded = previouslyOwned && !ownedNow

                val status = when {
                    ownedNow      -> "Purchased"
                    refunded      -> "Refunded"
                    else          -> "Available"
                }

                Log.d(TAG, "getRefundInfo: status=$status, ownedNow=$ownedNow, previouslyOwned=$previouslyOwned")

                // If refunded, flip entitlements back to false, persist, and update UI
                if (refunded) {
                    val changed = removeAds || removeTimeLimit
                    if (changed) {
                        Log.i(TAG, "getRefundInfo: refund detected → clearing entitlements (removeAds=false, removeTimeLimit=false)")
                    }
                    removeAds = false
                    removeTimeLimit = false
                    activity.getSharedPreferences("billing_prefs", Activity.MODE_PRIVATE)
                        .edit()
                        .putBoolean("remove_ads", false)
                        .putBoolean("remove_timer", false)
                        .apply()

                    if (changed) {
                        activity.runOnUiThread {
                            (activity as? MainActivity)?.onEntitlementsUpdated(removeAds, removeTimeLimit)
                            gameView.removeAds = removeAds
                            gameView.removeTimeLimit = removeTimeLimit
                            gameView.postInvalidateOnAnimation()
                        }
                    }
                }

                // Deliver result (UI thread is nice for callers)
                activity.runOnUiThread {
                    onResult(RefundInfo(refunded = refunded, ownedNow = ownedNow, previouslyOwned = previouslyOwned))
                }
            }
        }
    }

    private fun queryAndCacheProductDetails() {
        //  SKU_REMOVE_ALL was also here
        val productList = listOf(SKU_REMOVE_ADS, SKU_REMOVE_TIMER).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (productDetails in productDetailsList) {
                    productDetailsMap[productDetails.productId] = productDetails
                }
            } else {
                Log.e("Billing", "Query failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryExistingPurchases() {   // ← make it public (remove 'private')
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            // Own any of your premium SKUs?
            val ownedNow = purchases.any { p ->
                p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it == SKU_REMOVE_TIMER || it == SKU_REMOVE_ADS }
            }

            val changed = (ownedNow != removeAds) || (ownedNow != removeTimeLimit)

            removeAds       = ownedNow           // premium ⇒ NO interstitials
            removeTimeLimit = ownedNow           // premium ⇒ NO timer Lv11+

            if (changed) {
                // Persist and push to UI
                activity.getSharedPreferences("billing_prefs", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("remove_ads", removeAds)
                    .putBoolean("remove_timer", removeTimeLimit)
                    .apply()

                applyEntitlements()  // copies into gameView
                gameView.postInvalidateOnAnimation()
            }
        }
    }
    fun showRemoveOptionsDialog() {
        // Re-sync first so refunds/restores are reflected
        queryExistingPurchases()

        // If already owned, just toast and bail
    /*    if (removeAds && removeTimeLimit) {
            Toast.makeText(activity, "Premium already unlocked", Toast.LENGTH_SHORT).show()
            gameView.onDialogCancel()
            return
        }

     */

        val label = "Unlock Premium (No Timer Lv11+, No Interstitials): ${getPrice(SKU_REMOVE_TIMER)}"
        AlertDialog.Builder(activity)
            .setTitle(" ")
            .setItems(arrayOf(label)) { _, _ ->
                launchPurchaseFlow(SKU_REMOVE_TIMER) // treat as your Premium
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                gameView.onDialogCancel()
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(true)
                setOnCancelListener { gameView.onDialogCancel() }
                show()
                window?.apply {
                    setLayout(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundDrawableResource(R.drawable.dialog_frame)
                    setGravity(Gravity.CENTER)
                }
            }
    }

    private fun getPrice(skuId: String): String {
        val details = productDetailsMap[skuId]
        return details?.oneTimePurchaseOfferDetails?.formattedPrice ?: "—"
    }

    private fun launchPurchaseFlow(skuId: String) {
        val productDetails = productDetailsMap[skuId] ?: run {
            Toast.makeText(activity, "Product not available", Toast.LENGTH_SHORT).show()
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // 1) Acknowledge if needed
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ackParams) { /*…*/ }
                    }

                    // 2) Flip your BillingManager flags based on SKU
                    when (purchase.products.firstOrNull()) {
                        SKU_REMOVE_ADS, SKU_REMOVE_TIMER -> {
                            removeAds = true
                            removeTimeLimit = true
                        }
                    }
                    applyEntitlements()

                    // 5) Play the buy‐sound if enabled
                    if (gameView.soundEnabled) {
                        gameView.playSound("coins_m")
                    }

                    // 6) Show confirmation toast
                    Toast
                        .makeText(activity, "Purchase successful!", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast
                .makeText(activity, "Error: ${billingResult.debugMessage}", Toast.LENGTH_LONG)
                .show()
        }

        // close any open purchase dialog
        gameView.onDialogCancel()
    }

    fun destroy() {
        billingClient.endConnection()
    }
}