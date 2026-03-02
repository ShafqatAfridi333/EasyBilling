package com.easybilling.billing.model

/**
 * Detailed information about a product fetched from the Play Store.
 *
 * For IN-APP products, [pricingPhases] will contain a single [PricingPhase] with
 * [RecurringMode.ORIGINAL]. For subscriptions, multiple phases are possible.
 *
 * @property productId       The product ID as set in Play Console
 * @property planId          The base plan ID (subscriptions only, empty for in-app)
 * @property offerId         The offer ID if fetched via an offer (optional)
 * @property productTitle    Display title from Play Console (e.g. "Gold Membership")
 * @property productType     Whether this is an in-app purchase or subscription
 * @property pricingPhases   Ordered list of pricing phases (free → discounted → full price)
 */
data class ProductDetail(
    val productId: String = "",
    val planId: String = "",
    val offerId: String = "",
    val productTitle: String = "",
    val productType: ProductType = ProductType.SUBSCRIPTION,
    val pricingPhases: List<PricingPhase> = emptyList()
) {
    /** Convenience: returns the free trial phase, if any */
    val freeTrialPhase: PricingPhase?
        get() = pricingPhases.firstOrNull { it.recurringMode == RecurringMode.FREE }

    /** Convenience: returns the discounted introductory phase, if any */
    val discountedPhase: PricingPhase?
        get() = pricingPhases.firstOrNull { it.recurringMode == RecurringMode.DISCOUNTED }

    /** Convenience: returns the standard full-price phase */
    val originalPhase: PricingPhase?
        get() = pricingPhases.firstOrNull { it.recurringMode == RecurringMode.ORIGINAL }

    /** Convenience: the display price (discounted if available, else original) */
    val displayPrice: String
        get() = discountedPhase?.price ?: originalPhase?.price ?: ""

    /** Whether this product has an active free trial offer */
    val hasFreeTrial: Boolean
        get() = freeTrialPhase != null

    /** Whether this product has an introductory discount offer */
    val hasDiscount: Boolean
        get() = discountedPhase != null
}
