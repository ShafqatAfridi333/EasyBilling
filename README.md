# EasyBilling 💳

[![](https://jitpack.io/v/yourusername/EasyBilling.svg)](https://jitpack.io/#yourusername/EasyBilling)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Billing](https://img.shields.io/badge/Billing%20Library-8.3.0-orange.svg)](https://developer.android.com/google/play/billing)

**EasyBilling** is a production-ready Android library that wraps [Google Play Billing Library 8.x](https://developer.android.com/google/play/billing) into a clean, fluent, and error-safe Kotlin API.

---

## ✨ Features

- ✅ **Google Play Billing 8.3.0** (latest)
- ✅ Consumable & non-consumable in-app purchases
- ✅ Subscriptions with multiple plans, base plans, and offers
- ✅ Free trials & introductory pricing phase parsing
- ✅ Subscription upgrade / downgrade with all proration modes
- ✅ Auto-acknowledgement & auto-consumption
- ✅ Automatic reconnection on service disconnect
- ✅ `StateFlow` for reactive premium status updates
- ✅ Sealed `BillingError` class for exhaustive error handling
- ✅ Debug logging (opt-in per build type)
- ✅ Zero boilerplate — set up in ~10 lines
- ✅ ProGuard / R8 rules included

---

## 📦 Installation

### Step 1 — Add JitPack to your repositories

In your **`settings.gradle.kts`** (or project-level `build.gradle`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2 — Add the dependency

In your **app-level `build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.github.appsstock:easybilling:<version>")
}
```

---

## 🚀 Quick Start

### Step 1 — Initialize

```kotlin
private val billing by lazy { EasyBillingManager(this) }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    billing
        .setNonConsumables(listOf("premium_forever"))
        .setConsumables(listOf("coins_100", "coins_500"))
        .setSubscriptions(listOf("sub_weekly", "sub_monthly", "sub_yearly"))
        .enableLogging(enable = BuildConfig.DEBUG)   // log only in debug
        .setBillingListener(billingListener)
        .startConnection()
}
```

> **Tip:** You can initialize in your `Application` class to share the same instance across activities.

---

### Step 2 — Set Up the Listener

```kotlin
private val billingListener = object : BillingListener {

    override fun onClientReady() {
        // ✅ Safe to call fetchProductDetails(), buy(), check premium, etc.
    }

    override fun onClientInitError() {
        // Show "billing unavailable" UI
    }

    override fun onProductsPurchased(purchases: List<PurchaseDetail>) {
        purchases.forEach {
            when (it.productId) {
                "premium_forever" -> unlockPremium()
                "coins_100"       -> addCoins(100)
            }
        }
    }

    override fun onPurchaseAcknowledged(purchase: PurchaseDetail) {
        // Confirmed delivery — safe to unlock
    }

    override fun onPurchaseConsumed(purchase: PurchaseDetail) {
        // Consumable is consumed — grant reward
    }

    override fun onBillingError(error: BillingError) {
        when (error) {
            is BillingError.UserCancelled      -> { /* no-op */ }
            is BillingError.ItemAlreadyOwned   -> showToast("Already owned!")
            is BillingError.ServiceUnavailable -> showToast("Try again later")
            else                               -> showToast(error.message)
        }
    }
}
```

---

### Step 3 — Load Product Prices

```kotlin
billing.fetchProductDetails(object : BillingProductDetailsListener {
    override fun onSuccess(productDetailList: List<ProductDetail>) {
        productDetailList.forEach { product ->

            if (product.productType == ProductType.SUBSCRIPTION) {
                // Subscription pricing phases
                val freeTrial  = product.freeTrialPhase   // e.g. 7 days free
                val discounted = product.discountedPhase  // e.g. $0.99/month for 3 months
                val original   = product.originalPhase    // e.g. $4.99/month

                Log.d("Billing", "Product: ${product.productId} / Plan: ${product.planId}")
                Log.d("Billing", "Display price: ${product.displayPrice}")
                Log.d("Billing", "Has free trial: ${product.hasFreeTrial}")

            } else {
                // In-App pricing
                val price = product.originalPhase?.price
                Log.d("Billing", "${product.productId}: $price")
            }
        }
    }
    override fun onError(error: BillingError) { }
})
```

#### Get price for a specific product directly:

```kotlin
// In-App
val coinsPrice = billing.getInAppPrice("coins_100")
Log.d("Billing", "100 coins: ${coinsPrice?.price}")

// Subscription (base plan)
val monthlyPrice = billing.getSubscriptionPrice("sub_monthly", "plan-monthly")
Log.d("Billing", "Monthly: ${monthlyPrice?.price} ${monthlyPrice?.currencyCode}")

// Subscription with offer
val offerPrice = billing.getSubscriptionPrice("sub_monthly", "plan-monthly", "offer-trial")
```

---

### Step 4 — Make Purchases

#### In-App (non-consumable)

```kotlin
billing.purchaseInApp(
    activity = this,
    productId = "premium_forever",
    listener = object : BillingPurchaseListener {
        override fun onPurchaseSuccess(purchase: PurchaseDetail) {
            unlockPremiumFeatures()
        }
        override fun onPurchaseError(error: BillingError) {
            if (error !is BillingError.UserCancelled)
                showToast("Purchase failed: ${error.message}")
        }
    }
)
```

#### Consumable In-App

```kotlin
billing.purchaseInApp(
    activity = this,
    productId = "coins_100",
    listener = object : BillingPurchaseListener {
        override fun onPurchaseSuccess(purchase: PurchaseDetail) {
            // Handled in BillingListener.onPurchaseConsumed
        }
        override fun onPurchaseError(error: BillingError) { }
    }
)
```

#### Subscribe (base plan)

```kotlin
billing.purchaseSubscription(
    activity = this,
    productId = "sub_monthly",
    planId = "plan-monthly",
    listener = purchaseListener
)
```

#### Subscribe with Offer (e.g. free trial)

```kotlin
// First check if the offer is available for this user
val hasFreeTrial = billing.isOfferAvailable("sub_monthly", "plan-monthly", "offer-trial")

billing.purchaseSubscription(
    activity = this,
    productId = "sub_monthly",
    planId = "plan-monthly",
    offerId = if (hasFreeTrial) "offer-trial" else null,
    listener = purchaseListener
)
```

#### EU Personalized Pricing

```kotlin
billing.purchaseSubscription(
    activity = this,
    productId = "sub_monthly",
    planId = "plan-monthly",
    isPersonalizedOffer = true,   // Required disclosure for EU users
    listener = purchaseListener
)
```

---

### Step 5 — Upgrade / Downgrade Subscription

```kotlin
billing.updateSubscription(
    activity = this,
    oldProductId = "sub_monthly",
    newProductId = "sub_yearly",
    newPlanId = "plan-yearly",
    prorationMode = EasyProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE,
    listener = object : BillingPurchaseListener {
        override fun onPurchaseSuccess(purchase: PurchaseDetail) {
            showToast("Upgraded to Yearly 🎉")
        }
        override fun onPurchaseError(error: BillingError) {
            showToast("Upgrade failed: ${error.message}")
        }
    }
)
```

#### Proration Modes

| Mode | Description |
|---|---|
| `DEFERRED` | New plan starts when old plan expires. Good for downgrades. |
| `IMMEDIATE_AND_CHARGE_FULL_PRICE` | Switch now, charge full price + remaining time credited. |
| `IMMEDIATE_AND_CHARGE_PRORATED_PRICE` | Switch now, charge prorated amount. Good for upgrades. |
| `IMMEDIATE_WITHOUT_PRORATION` | Switch now, charge new price at next renewal. |
| `IMMEDIATE_WITH_TIME_PRORATION` | Switch now, credit remaining time. Default. |

---

### Step 6 — Check Premium Status

#### Reactive (recommended)

```kotlin
lifecycleScope.launch {
    billing.isPremiumFlow.collect { isPremium ->
        binding.proButton.isVisible = !isPremium
        binding.premiumBadge.isVisible = isPremium
    }
}
```

#### Synchronous checks

```kotlin
// Any active purchase or subscription
billing.isUserPremium()

// In-App only
billing.isInAppPremiumUser()
billing.isInAppPremiumUserByProductId("premium_forever")

// Subscription only
billing.isSubscriptionPremiumUser()
billing.isSubscriptionPremiumByProductId("sub_monthly")
billing.isSubscriptionPremiumByPlanId("plan-monthly")
```

---

### Step 7 — Cancel / Manage Subscription

You cannot cancel a subscription programmatically — this must be done by the user via the Play Store. Use this helper to deep-link them there:

```kotlin
billing.openSubscriptionManagement(activity, "sub_monthly")
```

---

### Step 8 — Release

Always release the billing client when it's no longer needed to avoid memory leaks:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    billing.release()
}
```

If you're using it in a `ViewModel`, call `release()` in `onCleared()`.

---

## 📐 Data Classes

### `ProductDetail`

```kotlin
data class ProductDetail(
    val productId: String,       // Play Console product ID
    val planId: String,          // Base plan ID (subs only)
    val offerId: String,         // Offer ID (optional)
    val productTitle: String,    // Display title
    val productType: ProductType,
    val pricingPhases: List<PricingPhase>
) {
    val freeTrialPhase: PricingPhase?   // Convenience: the FREE phase
    val discountedPhase: PricingPhase?  // Convenience: the DISCOUNTED phase
    val originalPhase: PricingPhase?    // Convenience: the full-price ORIGINAL phase
    val displayPrice: String            // Best available price string to show user
    val hasFreeTrial: Boolean
    val hasDiscount: Boolean
}
```

### `PricingPhase`

```kotlin
data class PricingPhase(
    val recurringMode: RecurringMode,    // FREE, DISCOUNTED, or ORIGINAL
    val price: String,                   // e.g. "$4.99" or "Free"
    val currencyCode: String,            // e.g. "USD"
    val currencySymbol: String,          // e.g. "$"
    val planTitle: String,               // e.g. "Monthly", "Yearly"
    val billingPeriod: String,           // e.g. "P1M", "P1Y"
    val billingCycleCount: Int,          // 0 = infinite
    val priceAmountMicros: Long,         // price × 1,000,000
    val freeTrialDays: Int               // 0 if not free trial
)
```

### `PurchaseDetail`

```kotlin
data class PurchaseDetail(
    val productId: String,
    val planId: String,
    val purchaseToken: String,       // Use for server-side verification
    val productType: ProductType,
    val purchaseTime: String,        // Human-readable
    val purchaseTimeMillis: Long,    // Unix timestamp
    val isAutoRenewing: Boolean,
    val isAcknowledged: Boolean,
    val isSuspended: Boolean,        // Subscription paused / payment issue
    val orderId: String,
    val quantity: Int
)
```

---

## 🛡️ Error Handling

All billing errors are delivered as sealed `BillingError` subclasses:

```kotlin
sealed class BillingError {
    ClientNotReady          // Not yet connected
    ClientDisconnected      // Lost connection to Play Store
    ProductNotFound         // Product ID missing in Play Console
    BillingUnavailable      // Play billing not available on device
    UserCancelled           // User closed the purchase dialog
    ServiceUnavailable      // Play Store temporarily down
    ItemUnavailable         // Product not available for purchase
    ItemAlreadyOwned        // Non-consumable already purchased
    ItemNotOwned            // Token mismatch on upgrade/downgrade
    DeveloperError          // Wrong product ID or bad configuration
    AcknowledgeError        // Failed to acknowledge within 3 days
    ConsumeError            // Failed to consume a consumable
    OldPurchaseTokenNotFound // Can't find token for subscription update
    InitializationError     // BillingClient setup failed
    Unknown                 // Anything else
}
```

---

## 🏗️ Play Console Setup Guide

### Subscription Structure

**Option A — One product per period (simpler)**
```
Product ID: sub_weekly    →  Plan ID: plan-weekly
Product ID: sub_monthly   →  Plan ID: plan-monthly
Product ID: sub_yearly    →  Plan ID: plan-yearly
```

**Option B — One product, multiple plans (advanced)**
```
Product ID: premium_subscription
  ├── Plan ID: plan-weekly
  ├── Plan ID: plan-monthly
  └── Plan ID: plan-yearly
        └── Offer ID: offer-free-trial  (7-day free trial)
```

> ⚠️ If you use Option B and need to identify which plan a historical purchase was for,
> you **must** save the planId server-side at purchase time. Option A avoids this issue.

### Supported Billing Periods (ISO 8601)

| Code | Period |
|---|---|
| `P1W` | Weekly |
| `P4W` | Every 4 Weeks |
| `P1M` | Monthly |
| `P2M` | Every 2 Months |
| `P3M` | Quarterly |
| `P6M` | Every 6 Months |
| `P1Y` | Yearly |

---

## 🧪 Testing

1. Add your Google account as a **License Tester** in Play Console → Setup → License Testing
2. Use **Static Responses** for initial testing (`android.test.purchased`)
3. Create a **Closed Testing track** and upload a signed APK for real product testing
4. Test subscription scenarios: purchase → acknowledge → cancel → refund

---

## 📋 Changelog

| Version | Notes |
|---|---|
| 1.0.0 | Initial release — Billing Library 8.3.0, full in-app & subs support |

---

## 📄 License

```
Copyright 2025 Your Name

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
