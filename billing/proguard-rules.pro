# ──────────────────────────────────────────────────────────────────────────────
# EasyBilling ProGuard Rules
# ──────────────────────────────────────────────────────────────────────────────

# Keep all public EasyBilling API classes
-keep class com.easybilling.billing.** { *; }

# Keep model classes (required for serialization)
-keep class com.easybilling.billing.data.** { *; }
-keep class com.easybilling.billing.utils.** { *; }
-keep class com.easybilling.billing.utils.extensions.** { *; }
-keep class com.easybilling.billing.utils.listeners.** { *; }
-keep class com.easybilling.billing.utils.logger.** { *; }
-keep class com.easybilling.billing.utils.enums.** { *; }

# Keep listener interfaces
-keep interface com.easybilling.billing.manager.** { *; }

# Google Play Billing — keep all Billing classes and methods
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep data classes (prevent field stripping)
-keepclassmembers class com.easybilling.billing.data.** {
    <fields>;
    <init>(...);
    public *;
}
