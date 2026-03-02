package com.easybilling.billing.utils.listeners

import com.easybilling.billing.data.BillingError
import com.easybilling.billing.data.ProductDetail
import com.easybilling.billing.data.PurchaseDetail

// ─────────────────────────────────────────────────────────────────────────────
// Connection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callback for BillingClient connection events.
 */
interface BillingConnectionListener {
    /**
     * Called when the BillingClient has connected (or failed to connect).
     * @param isSuccess  true if connection was established
     * @param message    "Connected" or an error description
     */
    fun onBillingClientConnected(isSuccess: Boolean, message: String)
}

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Broad lifecycle listener. Attach this to get notified about all major billing states.
 */
interface BillingListener {

    /** Called when the billing client is fully ready (products fetched, purchases verified). */
    fun onClientReady() {}

    /** Called when the billing client is already connected (duplicate init call). */
    fun onClientAlreadyConnected() {}

    /** Called when the billing client fails to initialise. */
    fun onClientInitError() {}

    /**
     * Called when one or more purchases are successfully completed or restored.
     * @param purchases List of acknowledged/consumed purchases
     */
    fun onProductsPurchased(purchases: List<PurchaseDetail>) {}

    /**
     * Called when a purchase has been acknowledged (non-consumables / subscriptions).
     * @param purchase The purchase that was acknowledged
     */
    fun onPurchaseAcknowledged(purchase: PurchaseDetail) {}

    /**
     * Called when a consumable purchase has been consumed (allowing re-purchase).
     * @param purchase The purchase that was consumed
     */
    fun onPurchaseConsumed(purchase: PurchaseDetail) {}

    /** Called on any billing error. */
    fun onBillingError(error: BillingError) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Products
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callback for querying product details from the Play Store.
 */
interface BillingProductDetailsListener {
    /** Called with the full list of [ProductDetail] objects for all requested products. */
    fun onSuccess(productDetailList: List<ProductDetail>)

    /** Called when the query fails. */
    fun onError(error: BillingError)
}

// ─────────────────────────────────────────────────────────────────────────────
// Purchases / History
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callback for fetching active or historical purchases.
 */
interface BillingPurchaseHistoryListener {
    /** Called with a list of active (or past) [PurchaseDetail] objects. */
    fun onSuccess(purchaseList: List<PurchaseDetail>)

    /** Called when the query fails. */
    fun onError(error: BillingError)
}

// ─────────────────────────────────────────────────────────────────────────────
// Purchase Flow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callback for a purchase or subscription action initiated by the user.
 */
interface BillingPurchaseListener {
    /**
     * Called when the purchase was completed successfully.
     * @param purchase  The resulting [PurchaseDetail]
     */
    fun onPurchaseSuccess(purchase: PurchaseDetail)

    /** Called when the purchase flow results in an error. */
    fun onPurchaseError(error: BillingError)
}
