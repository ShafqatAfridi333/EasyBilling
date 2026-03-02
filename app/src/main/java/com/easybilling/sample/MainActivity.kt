package com.easybilling.sample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.easybilling.billing.manager.BillingListener
import com.easybilling.billing.manager.BillingProductDetailsListener
import com.easybilling.billing.manager.BillingPurchaseListener
import com.easybilling.billing.manager.EasyBillingManager
import com.easybilling.billing.model.BillingError
import com.easybilling.billing.model.EasyProrationMode
import com.easybilling.billing.model.ProductDetail
import com.easybilling.billing.model.PurchaseDetail
import com.easybilling.billing.model.RecurringMode
import com.easybilling.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Sample Activity demonstrating all EasyBilling features.
 *
 * Replace the product/plan IDs below with your actual Play Console IDs.
 */
class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    companion object {
        private const val TAG = "EasyBillingSample"

        // ── Replace these with your real product IDs from Play Console ──────
        private const val PRODUCT_PREMIUM_FOREVER = "premium_forever"     // Non-consumable
        private const val PRODUCT_COINS_100 = "coins_100"           // Consumable
        private const val PRODUCT_COINS_500 = "coins_500"           // Consumable

        private const val SUB_WEEKLY = "sub_weekly"
        private const val SUB_MONTHLY = "sub_monthly"
        private const val SUB_YEARLY = "sub_yearly"

        private const val PLAN_WEEKLY = "plan-weekly"
        private const val PLAN_MONTHLY = "plan-monthly"
        private const val PLAN_YEARLY = "plan-yearly"

        // Optional offer IDs (leave empty to use base plan pricing)
        private const val OFFER_TRIAL = "offer-free-trial"
        // ─────────────────────────────────────────────────────────────────────
    }

    private lateinit var billing: EasyBillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupBilling()
        binding.btnLifetimePurchase.setOnClickListener {
            buyPremiumForever()
        }
        binding.btnSubscription.setOnClickListener {
            subscribeMonthlyWithTrial()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupBilling() {
        billing = EasyBillingManager(this)
            .setNonConsumables(listOf(PRODUCT_PREMIUM_FOREVER))
            .setConsumables(listOf(PRODUCT_COINS_100, PRODUCT_COINS_500))
            .setSubscriptions(listOf(SUB_WEEKLY, SUB_MONTHLY, SUB_YEARLY))
            .enableLogging(enable = BuildConfig.DEBUG)
            .setBillingListener(object : BillingListener {

                override fun onClientReady() {
                    Log.i(TAG, "✓ Billing client ready")
                    // Now safe to call fetchProductDetails, buy, check premium, etc.
                    observePremiumStatus()
                    loadProductPrices()
                }

                override fun onClientAlreadyConnected() {
                    Log.i(TAG, "Billing client already connected")
                }

                override fun onClientInitError() {
                    Log.e(TAG, "✗ Billing init error")
                    showToast("Billing unavailable — check your Play Store connection")
                }

                override fun onProductsPurchased(purchases: List<PurchaseDetail>) {
                    Log.i(TAG, "🎉 ${purchases.size} product(s) purchased!")
                    purchases.forEach {
                        Log.i(TAG, "  → ${it.productId} | token: ${it.purchaseToken}")
                    }
                    // Unlock features here
                }

                override fun onPurchaseAcknowledged(purchase: PurchaseDetail) {
                    Log.i(TAG, "✓ Purchase acknowledged: ${purchase.productId}")
                }

                override fun onPurchaseConsumed(purchase: PurchaseDetail) {
                    Log.i(TAG, "✓ Purchase consumed: ${purchase.productId}")
                    // Add coins/gems to user account
                    when (purchase.productId) {
                        PRODUCT_COINS_100 -> addCoins(100)
                        PRODUCT_COINS_500 -> addCoins(500)
                    }
                }

                override fun onBillingError(error: BillingError) {
                    Log.e(TAG, "Billing error: ${error.message}")
                    when (error) {
                        is BillingError.UserCancelled -> { /* no-op */
                        }

                        is BillingError.ItemAlreadyOwned -> showToast("You already own this!")
                        is BillingError.ServiceUnavailable -> showToast("Play Store unavailable, try later")
                        is BillingError.DeveloperError -> Log.e(
                            TAG,
                            "Developer config error! Check product IDs"
                        )

                        else -> showToast("Purchase error: ${error.message}")
                    }
                }
            })
            .startConnection()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Premium status observer
    // ─────────────────────────────────────────────────────────────────────────

    private fun observePremiumStatus() {
        lifecycleScope.launch {
            billing.isPremiumFlow.collect { isPremium ->
                Log.i(TAG, "Premium status: $isPremium")
                updatePremiumUI(isPremium)
            }
        }
    }

    private fun updatePremiumUI(isPremium: Boolean) {
        // Update your UI: show/hide ads, unlock features, etc.
        Log.d(TAG, "UI update — premium: $isPremium")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Product Prices
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadProductPrices() {
        billing.fetchProductDetails(object : BillingProductDetailsListener {
            override fun onSuccess(productDetailList: List<ProductDetail>) {
                productDetailList.forEach { product ->
                    Log.d(TAG, "─────────────────────────────")
                    Log.d(TAG, "Product:  ${product.productId}")
                    Log.d(TAG, "Plan:     ${product.planId}")
                    Log.d(TAG, "Type:     ${product.productType}")

                    product.pricingPhases.forEach { phase ->
                        Log.d(TAG, "  Phase: ${phase.recurringMode}")
                        Log.d(TAG, "  Price: ${phase.price} (${phase.currencyCode})")
                        Log.d(TAG, "  Period: ${phase.planTitle}")
                        if (phase.recurringMode == RecurringMode.FREE) {
                            Log.d(TAG, "  FreeTrial: ${phase.freeTrialDays} days")
                        }
                    }
                }

                // Example: get price for specific items
                val weeklyPrice = billing.getSubscriptionPrice(SUB_WEEKLY, PLAN_WEEKLY)
                Log.i(TAG, "Weekly subscription price: ${weeklyPrice?.price}")

                val coinsPrice = billing.getInAppPrice(PRODUCT_COINS_100)
                Log.i(TAG, "100 coins price: ${coinsPrice?.price}")
            }

            override fun onError(error: BillingError) {
                Log.e(TAG, "Failed to load product prices: ${error.message}")
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Purchase – Non-consumable In-App
    // ─────────────────────────────────────────────────────────────────────────

    fun buyPremiumForever() {
        billing.purchaseInApp(
            activity = this,
            productId = PRODUCT_PREMIUM_FOREVER,
            listener = object : BillingPurchaseListener {
                override fun onPurchaseSuccess(purchase: PurchaseDetail) {
                    showToast("🎉 Premium unlocked forever!")
                }

                override fun onPurchaseError(error: BillingError) {
                    if (error !is BillingError.UserCancelled)
                        showToast("Purchase failed: ${error.message}")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Purchase – Consumable In-App
    // ─────────────────────────────────────────────────────────────────────────

    fun buyCoins100() {
        billing.purchaseInApp(
            activity = this,
            productId = PRODUCT_COINS_100,
            listener = object : BillingPurchaseListener {
                override fun onPurchaseSuccess(purchase: PurchaseDetail) {
                    showToast("Purchased 100 coins!")
                }

                override fun onPurchaseError(error: BillingError) {
                    if (error !is BillingError.UserCancelled)
                        showToast("Purchase failed: ${error.message}")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subscribe – Monthly with free trial offer
    // ─────────────────────────────────────────────────────────────────────────

    fun subscribeMonthlyWithTrial() {
        // Check if offer is available first
        val hasOffer = billing.isOfferAvailable(SUB_MONTHLY, PLAN_MONTHLY, OFFER_TRIAL)
        Log.d(TAG, "Free trial offer available: $hasOffer")

        billing.purchaseSubscription(
            activity = this,
            productId = SUB_MONTHLY,
            planId = PLAN_MONTHLY,
            offerId = if (hasOffer) OFFER_TRIAL else null,
            listener = object : BillingPurchaseListener {
                override fun onPurchaseSuccess(purchase: PurchaseDetail) {
                    showToast("🎉 Monthly subscription started!")
                }

                override fun onPurchaseError(error: BillingError) {
                    if (error !is BillingError.UserCancelled)
                        showToast("Subscription failed: ${error.message}")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Upgrade – Monthly → Yearly (immediate, charge prorated)
    // ─────────────────────────────────────────────────────────────────────────

    fun upgradeToYearly() {
        billing.updateSubscription(
            activity = this,
            oldProductId = SUB_MONTHLY,
            newProductId = SUB_YEARLY,
            newPlanId = PLAN_YEARLY,
            prorationMode = EasyProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE,
            listener = object : BillingPurchaseListener {
                override fun onPurchaseSuccess(purchase: PurchaseDetail) {
                    showToast("Upgraded to Yearly!")
                }

                override fun onPurchaseError(error: BillingError) {
                    showToast("Upgrade failed: ${error.message}")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Premium Check Utilities
    // ─────────────────────────────────────────────────────────────────────────

    fun checkPremiumStatus() {
        val isPremium = billing.isUserPremium()
        val isSubsPremium = billing.isSubscriptionPremiumUser()
        val hasCoins100 = billing.isInAppPremiumUserByProductId(PRODUCT_COINS_100)
        val isMonthly = billing.isSubscriptionPremiumByPlanId(PLAN_MONTHLY)
        val isYearly = billing.isSubscriptionPremiumByPlanId(PLAN_YEARLY)

        Log.d(
            TAG, """
            ── Premium Status ──────────────────
            Any premium:     $isPremium
            Any subscription: $isSubsPremium
            Monthly sub:     $isMonthly
            Yearly sub:      $isYearly
            Has coins_100:   $hasCoins100
            ────────────────────────────────────
        """.trimIndent()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel Subscription
    // ─────────────────────────────────────────────────────────────────────────

    fun openCancelSubscription() {
        billing.openSubscriptionManagement(this, SUB_MONTHLY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun addCoins(amount: Int) {
        Log.i(TAG, "Adding $amount coins to user account")
        // Update your database / SharedPreferences here
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        billing.release()
    }
}
