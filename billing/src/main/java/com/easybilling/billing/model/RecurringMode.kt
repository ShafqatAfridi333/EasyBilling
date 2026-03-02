package com.easybilling.billing.model

/**
 * Describes the nature of a subscription pricing phase.
 */
enum class RecurringMode {
    /** Free trial phase — user pays nothing */
    FREE,

    /** Introductory / discounted price phase */
    DISCOUNTED,

    /** The regular, full-price recurring phase */
    ORIGINAL
}
