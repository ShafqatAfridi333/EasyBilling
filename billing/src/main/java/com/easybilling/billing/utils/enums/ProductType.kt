package com.easybilling.billing.utils.enums

/**
 * Represents the type of a billing product.
 */
enum class ProductType {
    /** One-time in-app purchase (non-consumable) */
    INAPP_NON_CONSUMABLE,

    /** One-time in-app purchase (consumable - can be bought multiple times) */
    INAPP_CONSUMABLE,

    /** Recurring subscription */
    SUBSCRIPTION
}
