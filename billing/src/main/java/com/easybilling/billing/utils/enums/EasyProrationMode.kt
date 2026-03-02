package com.easybilling.billing.utils.enums

import com.android.billingclient.api.BillingFlowParams

/**
 * Defines how proration is handled when a user upgrades or downgrades a subscription.
 *
 * Maps directly to [BillingFlowParams.SubscriptionUpdateParams.ReplacementMode].
 */
enum class EasyProrationMode(internal val playValue: Int) {

    /**
     * Replacement takes effect when the old plan expires; the new price is charged at that time.
     * Recommended for downgrading.
     */
    DEFERRED(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED),

    /**
     * Replacement takes effect immediately. The user is charged the full price of the new plan
     * and receives a full billing cycle, plus any remaining prorated time from the old plan.
     * Best for upgrading to a higher-tier plan.
     */
    IMMEDIATE_AND_CHARGE_FULL_PRICE(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE),

    /**
     * Replacement takes effect immediately; the billing cycle remains the same.
     * The user is charged the prorated price immediately.
     */
    IMMEDIATE_AND_CHARGE_PRORATED_PRICE(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE),

    /**
     * Replacement takes effect immediately; no extra charge is made immediately.
     * The new price is charged on the next renewal date.
     */
    IMMEDIATE_WITHOUT_PRORATION(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION),

    /**
     * Replacement takes effect immediately; the remaining time is prorated and credited.
     */
    IMMEDIATE_WITH_TIME_PRORATION(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION),

    /**
     * Unknown or unspecified proration mode.
     */
    UNKNOWN(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.UNKNOWN_REPLACEMENT_MODE)
}
