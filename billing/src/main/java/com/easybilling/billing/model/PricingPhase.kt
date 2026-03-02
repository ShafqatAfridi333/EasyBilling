package com.easybilling.billing.model

/**
 * Represents a single pricing phase in a subscription plan.
 *
 * A subscription offer may contain multiple phases, e.g.:
 *  - Phase 1: FREE trial for 7 days
 *  - Phase 2: DISCOUNTED price for 1 month
 *  - Phase 3: Full ORIGINAL price indefinitely
 *
 * @property recurringMode    The nature of this phase (FREE, DISCOUNTED, ORIGINAL)
 * @property price            Formatted price string (e.g. "$4.99", "Free")
 * @property currencyCode     ISO 4217 currency code (e.g. "USD", "PKR")
 * @property planTitle        Human-readable period label (e.g. "Weekly", "Monthly", "Yearly")
 * @property billingPeriod    ISO 8601 duration (e.g. "P1W", "P1M", "P1Y")
 * @property billingCycleCount  Number of billing cycles. 0 = infinite (original phase)
 * @property priceAmountMicros  Price in micros (price * 1,000,000). 0 for free trial
 * @property freeTrialDays    Number of free-trial days. 0 if not a free trial phase
 * @property currencySymbol   Currency symbol (e.g. "$", "£", "₨")
 */
data class PricingPhase(
    val recurringMode: RecurringMode = RecurringMode.ORIGINAL,
    val price: String = "",
    val currencyCode: String = "",
    val planTitle: String = "",
    val billingPeriod: String = "",
    val billingCycleCount: Int = 0,
    val priceAmountMicros: Long = 0L,
    val freeTrialDays: Int = 0,
    val currencySymbol: String = ""
)
