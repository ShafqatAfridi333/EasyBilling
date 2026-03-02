package com.easybilling.billing.utils.logger

import android.util.Log

/**
 * Internal logging utility for EasyBilling.
 * Logs are emitted only when [isEnabled] is true.
 */
internal object BillingLogger {

    private const val TAG = "EasyBilling"
    var isEnabled: Boolean = false

    fun d(message: String) {
        if (isEnabled) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (isEnabled) Log.i(TAG, message)
    }

    fun w(message: String) {
        if (isEnabled) Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(TAG, message, throwable)
    }
}