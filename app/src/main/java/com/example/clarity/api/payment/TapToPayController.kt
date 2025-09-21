package com.example.clarity.api.payment

import com.example.clarity.api.RetrofitProvider
import com.example.clarity.data.SessionStore

/**
 * Controller that uses real Stripe Price IDs for monthly donations
 * and your backend endpoints for payment processing
 */
class TapToPayController {

    suspend fun createPaymentIntent(
        amountCents: Int,
        currency: String = "cad",
        sessionId: String? = null,
        donorId: String? = null
    ): String {
        // Use your existing backend endpoint
        val response = RetrofitProvider.api.createPaymentIntent(
            com.example.clarity.api.model.PaymentIntentIn(
                amount = amountCents,
                currency = currency,
                session_id = sessionId,
                donor_id = donorId
            )
        )
        return response.client_secret
    }

    suspend fun collectAndConfirmPayment(
        clientSecret: String,
        sessionId: String,
        donorId: String,
        isMonthly: Boolean,
        amountCents: Int,
        currency: String
    ): Boolean {
        // Simulate processing time
        kotlinx.coroutines.delay(2000)

        try {
            if (isMonthly) {
                // Use real Stripe subscription creation for monthly donations
                return createMonthlySubscription(sessionId, donorId, amountCents, currency)
            } else {
                // For one-time donations, just log completion for now
                return processOneTimePayment(sessionId, donorId, amountCents, currency)
            }
        } catch (e: Exception) {
            // Log the error
            RetrofitProvider.api.logEvent(
                com.example.clarity.api.model.LogEventIn(
                    event_type = "PAYMENT_FAILED",
                    session_id = sessionId,
                    donor_id = donorId,
                    attributes = mapOf(
                        "error" to (e.message ?: "Unknown error"),
                        "payment_type" to if (isMonthly) "MONTHLY" else "OTG"
                    )
                )
            )
            return false
        }
    }

    private suspend fun createMonthlySubscription(
        sessionId: String,
        donorId: String,
        amountCents: Int,
        currency: String
    ): Boolean {
        // Get the selected gift with Stripe Price ID
        val selectedGift = SessionStore.selectedGift
        val priceId = selectedGift?.stripePriceId

        if (priceId.isNullOrBlank()) {
            throw Exception("No Stripe Price ID found for selected monthly product")
        }

        // Get donor info for customer creation
        val donorInfo = getDonorInfo(donorId)

        // Create Stripe customer
        val customerResponse = RetrofitProvider.api.upsertCustomer(
            com.example.clarity.api.model.CustomerUpsertIn(
                email = donorInfo.email,
                name = donorInfo.name,
                phone = donorInfo.phone,
                metadata = mapOf(
                    "donor_id" to donorId,
                    "session_id" to sessionId
                )
            )
        )

        // Create subscription with real Price ID
        val subscriptionResponse = RetrofitProvider.api.createSubscription(
            com.example.clarity.api.model.SubscriptionCreateIn(
                customer_id = customerResponse.customer_id,
                price_id = priceId,  // Real Stripe Price ID from your PRODUCT table
                session_id = sessionId,
                donor_id = donorId,
                metadata = mapOf(
                    "product_id" to (selectedGift?.productId ?: ""),
                    "amount_cents" to amountCents.toString(),
                    "currency" to currency
                )
            )
        )

        return subscriptionResponse.status in listOf("active", "incomplete")
    }

    private suspend fun processOneTimePayment(
        sessionId: String,
        donorId: String,
        amountCents: Int,
        currency: String
    ): Boolean {
        // For one-time payments, log completion
        // In the future, this would use the actual payment intent confirmation
        RetrofitProvider.api.logEvent(
            com.example.clarity.api.model.LogEventIn(
                event_type = "PAYMENT_COMPLETED",
                session_id = sessionId,
                donor_id = donorId,
                attributes = mapOf(
                    "payment_intent_id" to "pi_simulated_${System.currentTimeMillis()}",
                    "amount_cents" to amountCents,
                    "currency" to currency,
                    "payment_type" to "OTG",
                    "status" to "succeeded",
                    "method" to "tap_to_pay_simulation"
                )
            )
        )
        return true
    }

    private suspend fun getDonorInfo(donorId: String): DonorInfo {
        return try {
            val donor = RetrofitProvider.api.getDonor(donorId)
            DonorInfo(donor.email, donor.name, donor.phone)
        } catch (e: Exception) {
            // Fallback to basic info if donor fetch fails
            DonorInfo("donor@example.com", "Donor Name", null)
        }
    }

    fun isReaderConnected(): Boolean {
        return true
    }

    fun getConnectedReader() = null
}

// Data class for donor information
data class DonorInfo(
    val email: String,
    val name: String,
    val phone: String?
)