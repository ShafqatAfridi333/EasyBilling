package com.easybilling.billing.utils.extensions

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.easybilling.billing.data.BillingError
import com.easybilling.billing.data.PricingPhase
import com.easybilling.billing.data.ProductDetail
import com.easybilling.billing.utils.enums.ProductType
import com.easybilling.billing.utils.enums.RecurringMode
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Google BillingResult → BillingError conversion
// ─────────────────────────────────────────────────────────────────────────────

internal fun Int.toEasyBillingError(defaultMsg: String = ""): BillingError {
    return when (this) {
        BillingClient.BillingResponseCode.USER_CANCELED ->
            BillingError.UserCancelled()
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
            BillingError.ServiceUnavailable()
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            BillingError.BillingUnavailable()
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            BillingError.ItemUnavailable()
        BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
            BillingError.DeveloperError()
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            BillingError.ItemAlreadyOwned()
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
            BillingError.ItemNotOwned()
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            BillingError.ClientDisconnected()
        else -> BillingError.Unknown(
            defaultMsg.ifBlank { "Response code: $this" }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProductDetails → ProductDetail mapping
// ─────────────────────────────────────────────────────────────────────────────

internal fun ProductDetails.toProductDetailList(
    consumableIds: Set<String>
): List<ProductDetail> {
    return when (productType) {
        BillingClient.ProductType.INAPP -> {
            val oneTimePurchaseOfferDetails = oneTimePurchaseOfferDetails
            val phase = PricingPhase(
                recurringMode = RecurringMode.ORIGINAL,
                price = oneTimePurchaseOfferDetails?.formattedPrice ?: "",
                currencyCode = oneTimePurchaseOfferDetails?.priceCurrencyCode ?: "",
                priceAmountMicros = oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L,
                currencySymbol = oneTimePurchaseOfferDetails?.priceCurrencyCode
                    ?.toCurrencySymbol() ?: "",
                billingPeriod = "ONE_TIME",
                planTitle = "One-time"
            )
            val type = if (productId in consumableIds)
                ProductType.INAPP_CONSUMABLE else ProductType.INAPP_NON_CONSUMABLE
            listOf(
                ProductDetail(
                    productId = productId,
                    productTitle = title,
                    productType = type,
                    pricingPhases = listOf(phase)
                )
            )
        }

        BillingClient.ProductType.SUBS -> {
            subscriptionOfferDetails?.map { offer ->
                val phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                    val isFree = phase.priceAmountMicros == 0L
                    val isDiscounted = !isFree && phase.billingCycleCount > 0
                    PricingPhase(
                        recurringMode = when {
                            isFree -> RecurringMode.FREE
                            isDiscounted -> RecurringMode.DISCOUNTED
                            else -> RecurringMode.ORIGINAL
                        },
                        price = if (isFree) "Free" else phase.formattedPrice,
                        currencyCode = phase.priceCurrencyCode,
                        currencySymbol = phase.priceCurrencyCode.toCurrencySymbol(),
                        billingPeriod = phase.billingPeriod,
                        planTitle = phase.billingPeriod.toPlanTitle(),
                        billingCycleCount = phase.billingCycleCount,
                        priceAmountMicros = phase.priceAmountMicros,
                        freeTrialDays = if (isFree) phase.billingPeriod.toDays() else 0
                    )
                }
                ProductDetail(
                    productId = productId,
                    planId = offer.basePlanId,
                    offerId = offer.offerId ?: "",
                    productTitle = title,
                    productType = ProductType.SUBSCRIPTION,
                    pricingPhases = phases
                )
            } ?: emptyList()
        }

        else -> emptyList()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Billing period parsing helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Converts ISO 8601 duration string to a human-readable plan title */
internal fun String.toPlanTitle(): String = when (this.uppercase(Locale.ROOT)) {
    "P1W"  -> "Weekly"
    "P2W"  -> "Every 2 Weeks"
    "P4W"  -> "Every 4 Weeks"
    "P1M"  -> "Monthly"
    "P2M"  -> "Every 2 Months"
    "P3M"  -> "Quarterly"
    "P4M"  -> "Every 4 Months"
    "P6M"  -> "Every 6 Months"
    "P8M"  -> "Every 8 Months"
    "P1Y"  -> "Yearly"
    "P3D"  -> "3 Days"
    "P7D"  -> "7 Days"
    "P14D" -> "14 Days"
    "P30D" -> "30 Days"
    else   -> this
}

/** Best-effort conversion of an ISO 8601 duration to a number of days (for free trial display) */
internal fun String.toDays(): Int {
    val upper = this.uppercase(Locale.ROOT)
    return when {
        upper.endsWith("D") -> upper.removePrefix("P").removeSuffix("D").toIntOrNull() ?: 0
        upper == "P1W"      -> 7
        upper == "P2W"      -> 14
        upper == "P3W"      -> 21
        upper == "P4W"      -> 28
        upper == "P1M"      -> 30
        upper == "P3M"      -> 90
        upper == "P6M"      -> 180
        upper == "P1Y"      -> 365
        else                -> 0
    }
}

/** Converts a currency code to its symbol using Java's Currency class */
internal fun String.toCurrencySymbol(): String = try {
    Currency.getInstance(this).symbol
} catch (e: Exception) {
    this
}

// ─────────────────────────────────────────────────────────────────────────────
// Date formatting
// ─────────────────────────────────────────────────────────────────────────────

internal fun Long.toFormattedDate(): String = try {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(this))
} catch (e: Exception) {
    this.toString()
}
