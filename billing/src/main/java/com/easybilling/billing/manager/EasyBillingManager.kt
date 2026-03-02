package com.easybilling.billing.manager

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.easybilling.billing.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EasyBillingManager(private val context: Context) {

    // ─── Coroutine scope ──────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ─── Product ID lists ─────────────────────────────────────────────────────
    private var nonConsumableIds: List<String> = emptyList()
    private var consumableIds: List<String> = emptyList()
    private var subscriptionIds: List<String> = emptyList()

    // ─── Cache ────────────────────────────────────────────────────────────────
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()
    private val activePurchases = mutableListOf<PurchaseDetail>()

    // ─── StateFlow for premium status ─────────────────────────────────────────
    private val _isPremium = MutableStateFlow(false)
    val isPremiumFlow: StateFlow<Boolean> = _isPremium.asStateFlow()
    val isPremiumUser: Boolean get() = _isPremium.value

    // ─── Listeners ────────────────────────────────────────────────────────────
    private var billingListener: BillingListener? = null
    private var connectionListener: BillingConnectionListener? = null
    private var pendingPurchaseListener: BillingPurchaseListener? = null

    // ─── Retry state ──────────────────────────────────────────────────────────
    private var retryCount = 0
    private val maxRetries = 3
    private var isConnecting = false

    // ─── Google BillingClient ─────────────────────────────────────────────────
    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchasesUpdated(billingResult, purchases)
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder-style configuration
    // ─────────────────────────────────────────────────────────────────────────

    fun setNonConsumables(ids: List<String>) = apply { nonConsumableIds = ids }
    fun setConsumables(ids: List<String>) = apply { consumableIds = ids }
    fun setSubscriptions(ids: List<String>) = apply { subscriptionIds = ids }
    fun setBillingListener(listener: BillingListener) = apply { billingListener = listener }
    fun setConnectionListener(listener: BillingConnectionListener) = apply { connectionListener = listener }
    fun enableLogging(enable: Boolean = true) = apply { BillingLogger.isEnabled = enable }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection  ← THE FIX: apply {} so startConnection() returns `this`
    // ─────────────────────────────────────────────────────────────────────────

    fun startConnection() = apply {
        if (billingClient.isReady) {
            BillingLogger.i("BillingClient already connected")
            billingListener?.onClientAlreadyConnected()
            return@apply
        }
        if (isConnecting) {
            BillingLogger.w("Connection already in progress")
            return@apply
        }
        isConnecting = true
        BillingLogger.d("Starting billing connection...")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                isConnecting = false
                retryCount = 0
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    BillingLogger.i("Billing connected ✓")
                    connectionListener?.onBillingClientConnected(true, "Connected")
                    scope.launch { initialiseAfterConnect() }
                } else {
                    val error = result.responseCode.toEasyBillingError(result.debugMessage)
                    BillingLogger.e("Billing setup failed: ${result.debugMessage}")
                    connectionListener?.onBillingClientConnected(false, result.debugMessage)
                    billingListener?.onClientInitError()
                    billingListener?.onBillingError(error)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                BillingLogger.w("Billing service disconnected")
                billingListener?.onBillingError(BillingError.ClientDisconnected())
                retryConnection()
            }
        })
    }

    private fun retryConnection() {
        if (retryCount >= maxRetries) {
            BillingLogger.e("Max reconnection attempts reached")
            return
        }
        retryCount++
        val delayMs = retryCount * 2000L
        BillingLogger.d("Retrying connection in ${delayMs}ms (attempt $retryCount/$maxRetries)")
        scope.launch {
            delay(delayMs)
            startConnection()
        }
    }

    private suspend fun initialiseAfterConnect() {
        fetchAndCacheProductDetails()
        queryActivePurchases()
        billingListener?.onClientReady()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Product Details
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchAndCacheProductDetails() {
        val inAppIds = (nonConsumableIds + consumableIds).distinct()
        if (inAppIds.isNotEmpty()) {
            val inAppProducts = inAppIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val (result, details) = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(inAppProducts).build()
            )
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details?.forEach { cachedProductDetails[it.productId] = it }
                BillingLogger.d("Fetched ${details?.size} in-app products")
            } else {
                BillingLogger.e("Failed to fetch in-app products: ${result.debugMessage}")
            }
        }

        if (subscriptionIds.isNotEmpty()) {
            val subsProducts = subscriptionIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val (result, details) = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(subsProducts).build()
            )
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details?.forEach { cachedProductDetails[it.productId] = it }
                BillingLogger.d("Fetched ${details?.size} subscription products")
            } else {
                BillingLogger.e("Failed to fetch subscriptions: ${result.debugMessage}")
            }
        }
    }

    fun fetchProductDetails(listener: BillingProductDetailsListener) {
        if (!guardReady { listener.onError(it) }) return
        scope.launch {
            fetchAndCacheProductDetails()
            val allDetails = cachedProductDetails.values.flatMap { it.toProductDetailList(consumableIds.toSet()) }
            if (allDetails.isEmpty()) listener.onError(BillingError.ProductNotFound())
            else listener.onSuccess(allDetails)
        }
    }

    fun getSubscriptionProductDetail(
        productId: String,
        planId: String,
        offerId: String? = null,
        listener: BillingProductDetailsListener
    ) {
        if (!guardReady { listener.onError(it) }) return
        scope.launch {
            val pd = cachedProductDetails[productId]
                ?: return@launch listener.onError(BillingError.ProductNotFound("Product '$productId' not found"))
            val filtered = pd.toProductDetailList(consumableIds.toSet())
                .filter { it.planId == planId && (offerId.isNullOrBlank() || it.offerId == offerId) }
            if (filtered.isEmpty()) listener.onError(BillingError.ProductNotFound("Plan '$planId' not found"))
            else listener.onSuccess(filtered)
        }
    }

    fun getInAppProductDetail(productId: String, listener: BillingProductDetailsListener) {
        if (!guardReady { listener.onError(it) }) return
        scope.launch {
            val pd = cachedProductDetails[productId]
                ?: return@launch listener.onError(BillingError.ProductNotFound("'$productId' not found"))
            listener.onSuccess(pd.toProductDetailList(consumableIds.toSet()))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Purchase Queries
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun queryActivePurchases() {
        activePurchases.clear()
        queryActivePurchasesForType(BillingClient.ProductType.INAPP)
        queryActivePurchasesForType(BillingClient.ProductType.SUBS)
        updatePremiumStatus()
    }

    private suspend fun queryActivePurchasesForType(type: String) {
        val (result, purchases) = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(type).build()
        )
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLogger.d("Active $type purchases: ${purchases.size}")
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    processAndAcknowledge(purchase, type)
                }
            }
        }
    }

    fun fetchActivePurchases(listener: BillingPurchaseHistoryListener) {
        if (!guardReady { listener.onError(it) }) return
        scope.launch {
            queryActivePurchases()
            listener.onSuccess(activePurchases.toList())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Purchase Flow
    // ─────────────────────────────────────────────────────────────────────────

    fun purchaseInApp(
        activity: Activity,
        productId: String,
        isPersonalizedOffer: Boolean = false,
        listener: BillingPurchaseListener? = null
    ) {
        if (!guardReady { listener?.onPurchaseError(it) }) return

        val productDetails = cachedProductDetails[productId]
            ?: return reportError(BillingError.ProductNotFound("'$productId' not found in cache"), listener)

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            ))
            .setIsOfferPersonalized(isPersonalizedOffer)
            .build()

        pendingPurchaseListener = listener
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            reportError(result.responseCode.toEasyBillingError(result.debugMessage), listener)
        }
    }

    fun purchaseSubscription(
        activity: Activity,
        productId: String,
        planId: String,
        offerId: String? = null,
        isPersonalizedOffer: Boolean = false,
        listener: BillingPurchaseListener? = null
    ) {
        if (!guardReady { listener?.onPurchaseError(it) }) return

        val productDetails = cachedProductDetails[productId]
            ?: return reportError(BillingError.ProductNotFound("'$productId' not found in cache"), listener)

        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == planId && (offerId.isNullOrBlank() || it.offerId == offerId) }
            ?.offerToken
            ?: return reportError(BillingError.ProductNotFound("Offer token not found for plan '$planId'"), listener)

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            ))
            .setIsOfferPersonalized(isPersonalizedOffer)
            .build()

        pendingPurchaseListener = listener
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            reportError(result.responseCode.toEasyBillingError(result.debugMessage), listener)
        }
    }

    fun updateSubscription(
        activity: Activity,
        oldProductId: String,
        newProductId: String,
        newPlanId: String,
        newOfferId: String? = null,
        prorationMode: EasyProrationMode = EasyProrationMode.IMMEDIATE_WITH_TIME_PRORATION,
        isPersonalizedOffer: Boolean = false,
        listener: BillingPurchaseListener? = null
    ) {
        if (!guardReady { listener?.onPurchaseError(it) }) return

        val oldToken = activePurchases.firstOrNull { it.productId == oldProductId }?.purchaseToken
            ?: return reportError(BillingError.OldPurchaseTokenNotFound(), listener)

        val newProductDetails = cachedProductDetails[newProductId]
            ?: return reportError(BillingError.ProductNotFound("'$newProductId' not found in cache"), listener)

        val offerToken = newProductDetails.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == newPlanId && (newOfferId.isNullOrBlank() || it.offerId == newOfferId) }
            ?.offerToken
            ?: return reportError(BillingError.ProductNotFound("Offer token not found for plan '$newPlanId'"), listener)

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(newProductDetails)
                    .setOfferToken(offerToken)
                    .build()
            ))
            .setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldToken)
                    .setSubscriptionReplacementMode(prorationMode.playValue)
                    .build()
            )
            .setIsOfferPersonalized(isPersonalizedOffer)
            .build()

        pendingPurchaseListener = listener
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            reportError(result.responseCode.toEasyBillingError(result.debugMessage), listener)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Purchase Result Handling
    // ─────────────────────────────────────────────────────────────────────────

    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) return
                val purchased = mutableListOf<PurchaseDetail>()
                scope.launch {
                    purchases.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            val type = if (subscriptionIds.any { it in purchase.products })
                                BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
                            processAndAcknowledge(purchase, type)?.let { purchased.add(it) }
                        }
                    }
                    if (purchased.isNotEmpty()) {
                        updatePremiumStatus()
                        billingListener?.onProductsPurchased(purchased)
                        pendingPurchaseListener?.onPurchaseSuccess(purchased.first())
                        pendingPurchaseListener = null
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                BillingLogger.d("User cancelled purchase")
                pendingPurchaseListener?.onPurchaseError(BillingError.UserCancelled())
                pendingPurchaseListener = null
            }
            else -> {
                val error = billingResult.responseCode.toEasyBillingError(billingResult.debugMessage)
                BillingLogger.e("Purchase error: ${billingResult.debugMessage}")
                pendingPurchaseListener?.onPurchaseError(error)
                billingListener?.onBillingError(error)
                pendingPurchaseListener = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Acknowledge / Consume
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun processAndAcknowledge(purchase: Purchase, productType: String): PurchaseDetail? {
        val productId = purchase.products.firstOrNull() ?: return null
        val isConsumable = productId in consumableIds

        val detail = PurchaseDetail(
            productId = productId,
            purchaseToken = purchase.purchaseToken,
            productType = when {
                productType == BillingClient.ProductType.SUBS -> ProductType.SUBSCRIPTION
                isConsumable -> ProductType.INAPP_CONSUMABLE
                else -> ProductType.INAPP_NON_CONSUMABLE
            },
            purchaseTimeMillis = purchase.purchaseTime,
            purchaseTime = purchase.purchaseTime.toFormattedDate(),
            isAutoRenewing = purchase.isAutoRenewing,
            isAcknowledged = purchase.isAcknowledged,
            orderId = purchase.orderId ?: "",
            quantity = purchase.quantity
        )

        return when {
            isConsumable             -> consumePurchase(purchase, detail)
            !purchase.isAcknowledged -> acknowledgePurchase(purchase, detail)
            else -> {
                if (activePurchases.none { it.purchaseToken == detail.purchaseToken })
                    activePurchases.add(detail)
                detail
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase, detail: PurchaseDetail): PurchaseDetail? {
        val result = billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        )
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                BillingLogger.d("Acknowledged: ${detail.productId}")
                val acknowledged = detail.copy(isAcknowledged = true)
                activePurchases.removeAll { it.purchaseToken == acknowledged.purchaseToken }
                activePurchases.add(acknowledged)
                billingListener?.onPurchaseAcknowledged(acknowledged)
                acknowledged
            }
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                BillingLogger.w("Already acknowledged: ${detail.productId}")
                activePurchases.removeAll { it.purchaseToken == detail.purchaseToken }
                activePurchases.add(detail)
                detail
            }
            else -> {
                val error = BillingError.AcknowledgeError("Failed to acknowledge ${detail.productId}: ${result.debugMessage}")
                BillingLogger.e(error.message)
                billingListener?.onBillingError(error)
                null
            }
        }
    }

    private suspend fun consumePurchase(purchase: Purchase, detail: PurchaseDetail): PurchaseDetail? {
        val (result, _) = billingClient.consumePurchase(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLogger.d("Consumed: ${detail.productId}")
            billingListener?.onPurchaseConsumed(detail)
            detail
        } else {
            val error = BillingError.ConsumeError("Failed to consume ${detail.productId}: ${result.debugMessage}")
            BillingLogger.e(error.message)
            billingListener?.onBillingError(error)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Premium Status
    // ─────────────────────────────────────────────────────────────────────────

    private fun updatePremiumStatus() {
        _isPremium.value = activePurchases.isNotEmpty()
        BillingLogger.d("Premium: ${_isPremium.value} (${activePurchases.size} active)")
    }

    fun isUserPremium(): Boolean = activePurchases.isNotEmpty()
    fun isInAppPremiumUser(): Boolean = activePurchases.any { it.productType == ProductType.INAPP_NON_CONSUMABLE }
    fun isInAppPremiumUserByProductId(productId: String): Boolean =
        activePurchases.any { it.productId == productId && it.productType != ProductType.SUBSCRIPTION }
    fun isSubscriptionPremiumUser(): Boolean = activePurchases.any { it.productType == ProductType.SUBSCRIPTION }
    fun isSubscriptionPremiumByProductId(productId: String): Boolean =
        activePurchases.any { it.productId == productId && it.productType == ProductType.SUBSCRIPTION }
    fun isSubscriptionPremiumByPlanId(planId: String): Boolean =
        activePurchases.any { it.planId == planId && it.productType == ProductType.SUBSCRIPTION }
    fun getActivePurchases(): List<PurchaseDetail> = activePurchases.toList()

    // ─────────────────────────────────────────────────────────────────────────
    // Price Getters
    // ─────────────────────────────────────────────────────────────────────────

    fun getInAppPrice(productId: String): PricingPhase? =
        cachedProductDetails[productId]
            ?.toProductDetailList(consumableIds.toSet())
            ?.firstOrNull()
            ?.originalPhase

    fun getSubscriptionPrice(productId: String, planId: String, offerId: String? = null): PricingPhase? =
        cachedProductDetails[productId]
            ?.toProductDetailList(consumableIds.toSet())
            ?.firstOrNull { it.planId == planId && (offerId.isNullOrBlank() || it.offerId == offerId) }
            ?.originalPhase

    fun getAllProductDetails(): List<ProductDetail> =
        cachedProductDetails.values.flatMap { it.toProductDetailList(consumableIds.toSet()) }

    // ─────────────────────────────────────────────────────────────────────────
    // Offer / Feature checks
    // ─────────────────────────────────────────────────────────────────────────

    fun isOfferAvailable(productId: String, planId: String, offerId: String): Boolean =
        cachedProductDetails[productId]
            ?.subscriptionOfferDetails
            ?.any { it.basePlanId == planId && it.offerId == offerId } == true

    fun areSubscriptionsSupported(): Boolean =
        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            .responseCode == BillingClient.BillingResponseCode.OK

    fun isBillingClientReady(): Boolean = billingClient.isReady

    // ─────────────────────────────────────────────────────────────────────────
    // Subscription management
    // ─────────────────────────────────────────────────────────────────────────

    fun openSubscriptionManagement(activity: Activity, productId: String) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "https://play.google.com/store/account/subscriptions?sku=$productId&package=${activity.packageName}"
            )
        )
        activity.startActivity(intent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun guardReady(onError: (BillingError) -> Unit): Boolean {
        if (!billingClient.isReady) {
            val error = BillingError.ClientNotReady()
            BillingLogger.e(error.message)
            billingListener?.onBillingError(error)
            onError(error)
            return false
        }
        return true
    }

    private fun reportError(error: BillingError, listener: BillingPurchaseListener?) {
        BillingLogger.e(error.message)
        listener?.onPurchaseError(error)
        billingListener?.onBillingError(error)
        pendingPurchaseListener = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    fun release() {
        BillingLogger.i("Releasing billing client")
        scope.cancel()
        if (billingClient.isReady) billingClient.endConnection()
    }
}