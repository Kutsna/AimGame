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

    @Suppress("DEPRECATION")
    private val billingClient: BillingClient = BillingClient
        .newBuilder(activity)
        .enablePendingPurchases()    // deprecated, but required for one-time products
        .setListener(this)
        .build()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    var removeAds = false
    var removeTimeLimit = false

    companion object {
        const val SKU_REMOVE_ADS = "remove_ads"
        const val SKU_REMOVE_TIMER = "remove_timer"
        const val SKU_REMOVE_ALL = "remove_all"
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndCacheProductDetails()
                    queryExistingPurchases()
                    showRemoveOptionsDialog()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Optional: Retry connection
            }
        })
    }

    private fun queryAndCacheProductDetails() {
        val productList = listOf(SKU_REMOVE_ADS, SKU_REMOVE_TIMER, SKU_REMOVE_ALL).map {
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

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            purchasesList.forEach { purchase ->
                val product = purchase.products.firstOrNull() ?: return@forEach
                when (product) {
                    SKU_REMOVE_ADS -> removeAds = true
                    SKU_REMOVE_TIMER -> removeTimeLimit = true
                    SKU_REMOVE_ALL -> {
                        removeAds = true
                        removeTimeLimit = true
                    }
                }
            }
        }
    }

    fun showRemoveOptionsDialog() {
        if (removeAds && removeTimeLimit) return

        val items = mutableListOf<String>()
        if (!removeAds) items += "Remove Ads — ${getPrice(SKU_REMOVE_ADS)}"
        if (!removeTimeLimit) items += "Remove Timer from Lv 11 — ${getPrice(SKU_REMOVE_TIMER)}"
        if (!removeAds || !removeTimeLimit) items += "Remove Both — ${getPrice(SKU_REMOVE_ALL)}"

        val builder = AlertDialog.Builder(activity)
            .setTitle("Unlock Premium")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Remove Ads — ${getPrice(SKU_REMOVE_ADS)}" -> launchPurchaseFlow(SKU_REMOVE_ADS)
                    "Remove Timer from Lv 11 — ${getPrice(SKU_REMOVE_TIMER)}" -> launchPurchaseFlow(
                        SKU_REMOVE_TIMER
                    )
                    "Remove Both — ${getPrice(SKU_REMOVE_ALL)}" -> launchPurchaseFlow(SKU_REMOVE_ALL)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                gameView.onDialogCancel()
            }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { gameView.onDialogCancel() }
        dialog.show()
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(R.drawable.dialog_frame)
            setGravity(Gravity.CENTER)
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
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ackParams) {}
                    }

                    when (purchase.products.firstOrNull()) {
                        SKU_REMOVE_ADS -> removeAds = true
                        SKU_REMOVE_TIMER -> removeTimeLimit = true
                        SKU_REMOVE_ALL -> {
                            removeAds = true
                            removeTimeLimit = true
                        }
                    }

                    Toast.makeText(activity, "Purchase successful!", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Error: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
        }
        Log.d("Billing", "onPurchasesUpdated: $billingResult")
        Toast.makeText(activity, "Purchase result: ${billingResult.responseCode}", LENGTH_SHORT).show()
    }

    fun destroy() {
        billingClient.endConnection()
    }
}