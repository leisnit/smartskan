package com.example.smartskan.data

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.smartskan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * The one-off "Remove ads" in-app purchase (Google Play Billing).
 *
 * Create a one-time product with ID [REMOVE_ADS] priced at £6.99 in the Play Console. The price
 * shown in the app comes from Play (localised for the user), with [FALLBACK_PRICE] until it loads.
 * The ad-free flag is cached so paying users never see an ad while Play is still connecting.
 */
class Purchases(context: Context) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences("purchases", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectLock = Mutex()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _adFree = MutableStateFlow(prefs.getBoolean(KEY_AD_FREE, false))
    val adFree: StateFlow<Boolean> = _adFree.asStateFlow()

    private val _price = MutableStateFlow(FALLBACK_PRICE)
    val price: StateFlow<String> = _price.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** One-off messages for the UI to show (purchase complete, pending, errors). */
    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Resolved by the UI, so it appears in the current language. */
    class Message(@StringRes private val text: Int, private val detail: String? = null) {
        fun resolve(context: Context): String =
            if (detail == null) context.getString(text) else context.getString(text, detail)
    }

    private var productDetails: ProductDetails? = null

    enum class RestoreResult { RESTORED, NOTHING_FOUND, FAILED }

    /** Silently checks ownership and loads the price; call at start-up. */
    fun refresh() {
        scope.launch {
            if (!connect()) return@launch
            loadProduct()
            syncOwned()
        }
    }

    /** Opens Google Play's purchase sheet for "Remove ads". */
    fun buyRemoveAds(activity: Activity) {
        scope.launch {
            if (!connect()) {
                _messages.tryEmit(Message(R.string.purchase_unavailable))
                return@launch
            }
            val details = productDetails ?: loadProduct()
            if (details == null) {
                _messages.tryEmit(Message(R.string.purchase_load_failed))
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
                )
                .build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
            else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _messages.tryEmit(Message(R.string.purchase_start_failed, result.debugMessage.ifBlank { "${result.responseCode}" }))
            }
        }
    }

    /** Re-checks Google Play for a previous "Remove ads" purchase on this account. */
    suspend fun restore(): RestoreResult {
        _busy.value = true
        try {
            if (!connect()) return RestoreResult.FAILED
            return when (syncOwned()) {
                true -> RestoreResult.RESTORED
                false -> RestoreResult.NOTHING_FOUND
                null -> RestoreResult.FAILED
            }
        } finally {
            _busy.value = false
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases?.forEach { handle(it, announce = true) } }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restore() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _messages.tryEmit(Message(R.string.purchase_failed))
        }
    }

    /** Returns true if owned, false if not, null if Play couldn't be queried. */
    private suspend fun syncOwned(): Boolean? {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        val owned = result.purchasesList.filter { REMOVE_ADS in it.products }
        owned.forEach { handle(it, announce = false) }
        val isOwned = owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Also clears the flag when a purchase has been refunded.
        setAdFree(isOwned)
        return isOwned
    }

    private suspend fun handle(purchase: Purchase, announce: Boolean) {
        if (REMOVE_ADS !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                setAdFree(true)
                if (!purchase.isAcknowledged) {
                    // Unacknowledged purchases are refunded by Google after 3 days.
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    )
                }
                if (announce) _messages.tryEmit(Message(R.string.purchase_thanks))
            }
            Purchase.PurchaseState.PENDING ->
                if (announce) _messages.tryEmit(Message(R.string.purchase_pending))
            else -> Unit
        }
    }

    private suspend fun loadProduct(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val details = client.queryProductDetails(params).productDetailsList?.firstOrNull()
        details?.oneTimePurchaseOfferDetails?.formattedPrice?.let { _price.value = it }
        productDetails = details
        return details
    }

    private fun setAdFree(value: Boolean) {
        _adFree.value = value
        prefs.edit().putBoolean(KEY_AD_FREE, value).apply()
    }

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    companion object {
        const val REMOVE_ADS = "remove_ads"
        const val FALLBACK_PRICE = "£6.99"
        private const val KEY_AD_FREE = "ad_free"
    }
}

val LocalPurchases = staticCompositionLocalOf<Purchases> { error("Purchases not provided") }
