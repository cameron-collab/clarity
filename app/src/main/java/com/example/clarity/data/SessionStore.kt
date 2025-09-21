package com.example.clarity.data

/* ---------- Campaign config parsed from /fundraiser/login ---------- */
data class CampaignCfg(
    val campaignId: String?,
    val name: String?,
    val currency: String,         // e.g., "CAD"
    val presetAmounts: List<Int>, // IN CENTS
    val minAmount: Int            // IN CENTS
) {
    companion object {
        fun fromMap(m: Map<String, Any?>?): CampaignCfg? {
            if (m == null) return null

            fun get(key1: String, key2: String? = null): Any? {
                val v1 = m[key1]
                if (v1 != null) return v1
                return if (key2 != null) m[key2] else null
            }

            fun anyToList(a: Any?): List<Any?> = when (a) {
                is List<*> -> a
                is String -> {
                    val s = a.trim()
                    val inner = s.removePrefix("[").removeSuffix("]")
                    if (inner.isBlank()) emptyList()
                    else inner.split(',', ';').map { it.trim() }
                }
                else -> emptyList()
            }

            fun toDoubleOrNull(x: Any?): Double? = when (x) {
                is Number -> x.toDouble()
                is String -> x.trim().toDoubleOrNull()
                else -> null
            }

            fun dollarsToCentsOrNull(x: Any?): Int? =
                toDoubleOrNull(x)?.let { (it * 100.0).toInt() }

            val id = (get("CAMPAIGN_ID") as? String) ?: (get("campaign_id") as? String)
            val name = (get("NAME") as? String) ?: (get("name") as? String)

            val currency = ((get("CURRENCY") as? String)
                ?: (get("currency") as? String) ?: "CAD").uppercase()

            val presetsCents = anyToList(get("PRESET_AMOUNTS") ?: get("preset_amounts"))
                .mapNotNull { dollarsToCentsOrNull(it) }
                .sorted()

            val minCents = dollarsToCentsOrNull(get("MIN_AMOUNT") ?: get("min_amount")) ?: 1000

            return CampaignCfg(
                campaignId = id,
                name = name,
                currency = currency,
                presetAmounts = if (presetsCents.isNotEmpty()) presetsCents else listOf(2000, 3000, 4000, 5000),
                minAmount = minCents
            )
        }
    }
}

/* ---------- Donor form snapshot (for prefill/restore) ---------- */
data class DonorForm(
    val title: String? = null,
    val first: String = "",
    val middle: String = "",
    val last: String = "",
    val dobIso: String = "",
    val phoneRaw: String = "",
    val email: String = "",
    val addr1: String = "",
    val addr2: String = "",
    val city: String = "",
    val region: String = "",
    val postal: String = "",
    val country: String = "CA"
)

/* ---------- Chosen gift for this donor ---------- */
data class SelectedGift(
    val type: String,      // "MONTHLY" or "OTG"
    val amountCents: Int,
    val currency: String   // e.g., "CAD"
)

/* ---------- Global session/cache ---------- */
object SessionStore {
    // Session
    var sessionId: String? = null

    // Fundraiser
    var fundraiserId: String? = null
    var fundraiserDisplayName: String? = null
    var fundraiserFirst: String? = null

    // Charity
    var charityId: String? = null
    var charityName: String = "Your Charity"
    var charityLogoUrl: String? = null
    var charityBlurb: String? = null
    var charityTermsUrl: String? = null
    var brandPrimaryHex: String? = null

    // Campaign
    var campaign: CampaignCfg? = null

    // Donor form (for prefill)
    var donorForm: DonorForm? = null

    // Chosen gift for this donor
    var selectedGift: SelectedGift? = null

    // Donor details cache used across screens (for SMS confirm → NO path)
    var donorPhoneE164: String? = null
    var donorEmail: String? = null
    var donorFullName: String? = null
    var donorDobIso: String? = null
    var donorAddressLine: String? = null

    // Communication consents (auto opt-in by default, as requested)
    var consentSms: Boolean = true
    var consentEmail: Boolean = true
    var consentMail: Boolean = true

    /** Clear *only donor-specific* state between donations; keep session/campaign/charity. */
    fun resetForNextDonor() {
        // Clear the donor form (so the next donor starts fresh)
        donorForm = null

        // Clear gift choice
        selectedGift = null

        // Clear donor caches used for prefill on SMS “NO” route
        donorPhoneE164 = null
        donorEmail = null
        donorFullName = null
        donorDobIso = null
        donorAddressLine = null

        // Reset consents back to default on each new donor
        consentSms = true
        consentEmail = true
        consentMail = true
    }
}
