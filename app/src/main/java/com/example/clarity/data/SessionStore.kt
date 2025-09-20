package com.example.clarity.data

data class CampaignCfg(
    val campaignId: String?,
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

            val currency = ((get("CURRENCY") as? String)
                ?: (get("currency") as? String) ?: "CAD").uppercase()

            // PRESET_AMOUNTS may be a real array or a string like "[20,30,40,50]"
            val presetsCents = anyToList(get("PRESET_AMOUNTS") ?: get("preset_amounts"))
                .mapNotNull { dollarsToCentsOrNull(it) }
                .sorted()

            // MIN_AMOUNT may be 10 (dollars) → convert to 1000 (cents). Default $10 if missing.
            val minCents = dollarsToCentsOrNull(get("MIN_AMOUNT") ?: get("min_amount")) ?: 1000

            return CampaignCfg(
                campaignId = id,
                currency = currency,
                presetAmounts = if (presetsCents.isNotEmpty()) presetsCents else listOf(2000, 3000, 4000, 5000),
                minAmount = minCents
            )
        }
    }
}

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

data class SelectedGift(
    val type: String,      // "MONTHLY" or "OTG"
    val amountCents: Int,
    val currency: String   // e.g., "CAD"
)

object SessionStore {
    // Campaign configuration (parsed from API)
    var campaign: CampaignCfg? = null

    // Fundraiser info
    var fundraiserId: String? = null
    var fundraiserDisplayName: String? = null
    var fundraiserFirst: String? = null

    // Charity info
    var charityId: String? = null
    var charityName: String = "Your Charity"
    var charityLogoUrl: String? = null
    var charityBlurb: String? = null   // <- add this
    var brandPrimaryHex: String? = null // <- already referenced

    var charityTermsUrl: String? = null

    var donorForm: DonorForm? = null

    var selectedGift: SelectedGift? = null

}

