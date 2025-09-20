package com.example.clarity.api.model

/* ---- One-time (OTG) PaymentIntent ---- */
data class PaymentIntentIn(
    val amount: Int,          // cents, e.g. 2000
    val currency: String,     // "cad"
    val session_id: String?,
    val donor_id: String?
)

data class PaymentIntentOut(
    val client_secret: String,
    val id: String,
    val status: String
)

/* ---- SetupIntent for saving card (monthly) ---- */
data class SetupIntentIn(
    val customer_id: String,
    val usage: String = "off_session",
    val session_id: String? = null,
    val donor_id: String? = null
)

data class SetupIntentOut(
    val client_secret: String,
    val id: String,
    val status: String
)

/* ---- Subscription creation (monthly) ---- */
data class SubscriptionCreateIn(
    val customer_id: String,
    val price_id: String,
    val cancel_after_years: Int = 50,
    val metadata: Map<String, Any?> = emptyMap(),
    val session_id: String? = null,
    val donor_id: String? = null
)

data class SubscriptionCreateOut(
    val id: String,
    val status: String,
    val cancel_at: Long?,
    val latest_invoice: String?,
    val payment_intent: String?
)

/* ---- Stripe customer upsert ---- */
data class CustomerUpsertIn(
    val email: String,
    val name: String,
    val phone: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

data class CustomerUpsertOut(
    val customer_id: String
)

/* ---- Attach payment method to customer ---- */
data class PaymentMethodAttachIn(
    val customer_id: String,
    val payment_method_id: String,
    val session_id: String? = null,
    val donor_id: String? = null,
    val save_row: Boolean = true
)

data class PaymentMethodAttachOut(
    val ok: Boolean,
    val customer_id: String,
    val payment_method_id: String,
    val default_payment_method: String?
)

data class LogEventIn(
    val event_type: String,
    val session_id: String? = null,
    val donor_id: String? = null,
    val fundraiser_id: String? = null,
    val attributes: Map<String, Any?> = emptyMap()
)