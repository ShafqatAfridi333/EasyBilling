package com.easybilling.billing.model

/**
 * A generic result wrapper for billing operations.
 *
 * Use this to pass results through callbacks or Flows.
 */
sealed class EasyBillingResult<out T> {
    data class Success<T>(val data: T) : EasyBillingResult<T>()
    data class Error(val error: BillingError) : EasyBillingResult<Nothing>()
}
