package com.easybilling.billing.model

/**
 * Sealed class representing all possible billing error states.
 *
 * Use when statements on this class to exhaustively handle every billing error.
 */
sealed class BillingError(open val message: String) {

    /** BillingClient was not yet set up when an operation was attempted */
    data class ClientNotReady(override val message: String = "Billing client is not ready") : BillingError(message)

    /** BillingClient disconnected from the Play Store service */
    data class ClientDisconnected(override val message: String = "Billing client disconnected") : BillingError(message)

    /** The requested product ID does not exist in Play Console */
    data class ProductNotFound(override val message: String = "Product not found") : BillingError(message)

    /** A generic Play Billing error occurred */
    data class BillingUnavailable(override val message: String = "Billing unavailable") : BillingError(message)

    /** The user cancelled the purchase flow */
    data class UserCancelled(override val message: String = "User cancelled the purchase") : BillingError(message)

    /** Google Play service is temporarily unavailable */
    data class ServiceUnavailable(override val message: String = "Service unavailable, try again later") : BillingError(message)

    /** The requested item is currently unavailable for purchase */
    data class ItemUnavailable(override val message: String = "Item unavailable") : BillingError(message)

    /** The item is already owned by this user */
    data class ItemAlreadyOwned(override val message: String = "Item already owned") : BillingError(message)

    /** The item is not owned by this user (upgrade/downgrade issue) */
    data class ItemNotOwned(override val message: String = "Item not owned") : BillingError(message)

    /** A developer configuration error (e.g. wrong product IDs) */
    data class DeveloperError(override val message: String = "Developer error - check product configuration") : BillingError(message)

    /** Purchase acknowledgement failed */
    data class AcknowledgeError(override val message: String = "Failed to acknowledge purchase") : BillingError(message)

    /** Consuming a consumable product failed */
    data class ConsumeError(override val message: String = "Failed to consume purchase") : BillingError(message)

    /** Old purchase token not found during upgrade/downgrade */
    data class OldPurchaseTokenNotFound(override val message: String = "Old purchase token not found for upgrade/downgrade") : BillingError(message)

    /** Failed to initialize the billing client */
    data class InitializationError(override val message: String = "Failed to initialize billing") : BillingError(message)

    /** Unknown or unexpected error */
    data class Unknown(override val message: String = "An unknown error occurred") : BillingError(message)
}
