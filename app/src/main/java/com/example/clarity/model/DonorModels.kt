package com.example.clarity.api.model

// Existing:
data class DonorUpsertIn(
    val title: String? = null,
    val first_name: String,
    val middle_name: String? = null,
    val last_name: String,
    val dob_iso: String,
    val mobile_e164: String,
    val email: String,
    val address1: String,
    val address2: String? = null,
    val city: String,
    val region: String,
    val postal_code: String,
    val country: String = "CA",
    val fundraiser_id: String,
    val session_id: String
)

data class DonorUpsertOut(val donor_id: String)

// NEW: send-sms payloads
data class SendSmsIn(
    val to_e164: String,
    val session_id: String,
    val donor_id: String,
    val charity_name: String,
    val gift_type: String = "MONTHLY",     // "MONTHLY" | "OTG"
    val amount_cents: Int,                 // e.g. 2000 for $20.00
    val currency: String = "CAD",
    val preview_message: String? = null
)

data class SendSmsOut(
    val ok: Boolean,
    val sid: String
)
