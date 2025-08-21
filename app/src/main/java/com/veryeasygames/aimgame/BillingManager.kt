package com.veryeasygames.aimgame

import android.app.Activity
import android.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.android.billingclient.api.*

class BillingManager(
    private val activity: Activity,
    private val gameView: GameView,
) : PurchasesUpdatedListener {

    data class RefundInfo(
        val refunded: Boolean,       // previously owned but not owned now
        val ownedNow: Boolean,       // current ownership (remove_ads)
        val previouslyOwned: Boolean, // seen in purchase history
    )

    private val TAG = "BillingManager"

    @Suppress("DEPRECATION")
    private val billingClient: BillingClient = BillingClient
        .newBuilder(activity)
        .enablePendingPurchases()     // required for in-app products
        .setListener(this)
        .build()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    // Only ads entitlement remains
    var removeAds = false
    var removeTimeLimit = false

    // Timer is no longer managed by BillingManager; always false from here.
    //private val removeTimeLimit: Boolean
    //    get() = false

    private fun applyEntitlements() {
        // Copy flags into GameView
        gameView.removeAds = removeAds
        gameView.removeTimeLimit = removeTimeLimit // always false from billing
    }

    companion object {
        const val SKU_REMOVE_ALL = "remove_all"
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndCacheProductDetails()
                    resyncEntitlements()
                }
            }
            override fun onBillingServiceDisconnected() {
                // optional: reconnect on demand
            }
        })
    }

    /** Re-query current ownership and update flags/prefs/UI. */
    fun resyncEntitlements() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            val ownsAds = purchases.any { p ->
                p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it == SKU_REMOVE_ALL }
            }

            val newRemoveAds       = ownsAds
            val newRemoveTimeLimit = ownsAds   // <-- true when purchased; false when not/refunded

            val changed = (newRemoveAds != removeAds) || (newRemoveTimeLimit != removeTimeLimit)
            if (changed) {
                removeAds = newRemoveAds
                removeTimeLimit = newRemoveTimeLimit

                activity.getSharedPreferences("billing_prefs", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("remove_ads", removeAds)
                    .putBoolean("remove_timer", removeTimeLimit)
                    .apply()

                (activity as? MainActivity)?.onEntitlementsUpdated(removeAds, removeTimeLimit)
                applyEntitlements()
                gameView.postInvalidateOnAnimation()
            }
        }
    }

    /** Refund probe (ads only). Flips removeAds to false if ownership is gone. */
    fun getRefundInfo(onResult: (RefundInfo) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
// Query current ownership
        billingClient.queryPurchasesAsync(params) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(RefundInfo(refunded = false, ownedNow = removeAds, previouslyOwned = false))
                return@queryPurchasesAsync
            }

            val ownedNow = purchases.any { p ->
                p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it == SKU_REMOVE_ALL }
            }

            val histParams = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
// Query purchase history
            billingClient.queryPurchaseHistoryAsync(histParams) { hr, records ->
                if (hr.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "getRefundInfo: history failed: ${hr.responseCode} ${hr.debugMessage}")
                }
                val previouslyOwned = records?.any { r -> r.products.any { it == SKU_REMOVE_ALL } } == true
                val refunded = previouslyOwned && !ownedNow

                Log.i(TAG, "getRefundInfo[ADS]: ownedNow=$ownedNow, previouslyOwned=$previouslyOwned, refunded=$refunded")

                if (refunded) {
                    val changed = removeAds || removeTimeLimit
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

                activity.runOnUiThread {
                    onResult(RefundInfo(refunded = refunded, ownedNow = ownedNow, previouslyOwned = previouslyOwned))
                }
            }
        }
    }

    /** Load pricing for remove_ads. */
    private fun queryAndCacheProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_REMOVE_ALL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (pd in productDetailsList) productDetailsMap[pd.productId] = pd
            } else {
                Log.e(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun showRemoveOptionsDialog() {
        // Optional: quick resync so state is fresh
        resyncEntitlements()

        // If already owned, just toast and bail
            if (removeAds && removeTimeLimit) {
                Toast.makeText(activity, "Premium already unlocked", Toast.LENGTH_SHORT).show()
                gameView.onDialogCancel()
                return
            }


        val label = "Remove Timer Bar and\nInterstitial Ads:  ${getPrice(SKU_REMOVE_ALL)}"
        val spannableTitle = SpannableString(label)
        spannableTitle.setSpan(
            ForegroundColorSpan(android.graphics.Color.CYAN),
            0, // Start of the text
            label.length, // End of the text
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        AlertDialog.Builder(activity)
            .setTitle(spannableTitle)
            .setPositiveButton("Purchase") { _, _ ->
                launchPurchaseFlow(SKU_REMOVE_ALL)
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
            Toast.makeText(activity, "Purchase not possible\nthis time", Toast.LENGTH_SHORT).show()
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

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.any { it == SKU_REMOVE_ALL }) {

                    if (!purchase.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                        billingClient.acknowledgePurchase(ack) { }
                    }

                    removeAds = true
                    removeTimeLimit = true

                    activity.getSharedPreferences("billing_prefs", Activity.MODE_PRIVATE)
                        .edit()
                        .putBoolean("remove_ads", true)
                        .putBoolean("remove_timer", true)
                        .apply()

                        (activity as? MainActivity)?.onEntitlementsUpdated(removeAds, removeTimeLimit)
                        applyEntitlements()
                        gameView.removeAds = removeAds
                        gameView.removeTimeLimit = removeTimeLimit
                        gameView.postInvalidateOnAnimation()

                        // 5) Play the buy‐sound if enabled
                        if (gameView.soundEnabled) {
                            gameView.playSound("coins_m")
                        }
                        Toast.makeText(activity, "Purchase successful!", Toast.LENGTH_SHORT).show()
                    }
            }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Error: ${result.debugMessage}", Toast.LENGTH_LONG).show()
        }
        gameView.onDialogCancel()
    }

    fun destroy() {
        billingClient.endConnection()
    }
}
