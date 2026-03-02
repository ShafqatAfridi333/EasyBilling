package com.easybilling.billing.model

/**
 * Represents a verified, active purchase.
 *
 * @property productId          Product ID (same as Play Console)
 * @property planId             Base plan ID (subscriptions only; empty for in-app)
 * @property purchaseToken      Unique token for this purchase (use for server-side verification)
 * @property productType        Whether this is an in-app purchase or subscription
 * @property purchaseTime       Human-readable purchase date (e.g. "2024-01-15 10:30:00")
 * @property purchaseTimeMillis Unix timestamp in milliseconds since epoch
 * @property isAutoRenewing     True if subscription will auto-renew (subscriptions only)
 * @property isAcknowledged     Whether the purchase has been acknowledged
 * @property isSuspended        True if the subscription is currently suspended (billing issue / paused)
 * @property orderId            Google Play Order ID (may be empty for free trials)
 * @property quantity           Purchase quantity (usually 1, unless multi-quantity enabled)
 */
data class PurchaseDetail(
    val productId: String = "",
    val planId: String = "",
    val purchaseToken: String = "",
    val productType: ProductType = ProductType.SUBSCRIPTION,
    val purchaseTime: String = "",
    val purchaseTimeMillis: Long = 0L,
    val isAutoRenewing: Boolean = false,
    val isAcknowledged: Boolean = false,
    val isSuspended: Boolean = false,
    val orderId: String = "",
    val quantity: Int = 1
)
