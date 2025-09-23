package com.example.clarity.api.payment

import com.example.clarity.api.*
import com.example.clarity.data.SessionStore
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.stripe.stripeterminal.external.models.CollectConfiguration
import com.stripe.stripeterminal.external.models.AllowRedisplay
import com.example.clarity.api.model.*

/**
 * Real Stripe Terminal controller based on Stripe sample code
 */
class TapToPayController {

    companion object {
        private val discoveryConfig = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(isSimulated = false)
    }

    private suspend fun getLocationId(): String {
        return try {
            val response = RetrofitProvider.api.getTerminalLocation()
            println("=== DEBUG: Got location ID from backend: ${response.location_id} ===")
            response.location_id
        } catch (e: Exception) {
            println("=== DEBUG: Failed to get location ID from backend, using fallback: ${e.message} ===")
            "tml_GMwgTw8OHAJtnR" // fallback to test location
        }
    }

    suspend fun initializeTapToPay(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val discoveryListener = object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    println("=== DEBUG: Found ${readers.size} readers ===")
                    readers.forEach { reader ->
                        println("=== DEBUG: Reader - Type: ${reader.deviceType}, ID: ${reader.id} ===")
                    }

                    if (readers.isNotEmpty()) {
                        connectToReader(readers.first()) { success ->
                            continuation.resume(success)
                        }
                    } else {
                        continuation.resumeWithException(Exception("No Tap to Pay readers found - ensure NFC is enabled"))
                    }
                }
            }

            val discoveryCallback = object : Callback {
                override fun onSuccess() {
                    println("=== DEBUG: Discovery process completed ===")
                }

                override fun onFailure(e: TerminalException) {
                    println("=== DEBUG: Discovery failed: ${e.errorMessage} ===")
                    continuation.resumeWithException(e)
                }
            }

            println("=== DEBUG: Starting Tap to Pay discovery (real mode) ===")
            Terminal.getInstance().discoverReaders(discoveryConfig, discoveryListener, discoveryCallback)
        }
    }

    private fun connectToReader(reader: Reader, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationId = getLocationId()
                println("=== DEBUG: Connecting to reader with location ID: $locationId ===")

                val connectionConfig = ConnectionConfiguration.TapToPayConnectionConfiguration(
                    locationId = locationId, // Now dynamic from backend
                    autoReconnectOnUnexpectedDisconnect = true,
                    tapToPayReaderListener = object : TapToPayReaderListener {
                        override fun onDisconnect(reason: DisconnectReason) {
                            println("=== DEBUG: Reader disconnected: $reason ===")
                        }
                    }
                )

                val readerCallback = object : ReaderCallback {
                    override fun onSuccess(reader: Reader) {
                        println("=== DEBUG: Reader connected successfully ===")
                        callback(true)
                    }

                    override fun onFailure(e: TerminalException) {
                        println("=== DEBUG: Reader connection failed: ${e.errorMessage} ===")
                        callback(false)
                    }
                }

                Terminal.getInstance().connectReader(
                    reader = reader,
                    config = connectionConfig,
                    connectionCallback = readerCallback
                )
            } catch (e: Exception) {
                println("=== DEBUG: Exception in connectToReader: ${e.message} ===")
                callback(false)
            }
        }
    }

    suspend fun createTerminalPaymentIntent(
        amountCents: Int,
        currency: String,
        sessionId: String? = null,
        donorId: String? = null
    ): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitProvider.api.createTerminalPaymentIntent(
                        com.example.clarity.api.model.TerminalPaymentIntentIn(
                            amount = amountCents,
                            currency = currency,
                            session_id = sessionId,
                            donor_id = donorId
                        )
                    )

                    // Retrieve the PaymentIntent from Stripe Terminal
                    Terminal.getInstance().retrievePaymentIntent(
                        response.client_secret,
                        object : PaymentIntentCallback {
                            override fun onSuccess(paymentIntent: PaymentIntent) {
                                continuation.resume(paymentIntent)
                            }

                            override fun onFailure(e: TerminalException) {
                                continuation.resumeWithException(e)
                            }
                        }
                    )
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            // Create configuration with allow_redisplay for saving payment methods
            val config = CollectConfiguration.Builder()
                .setAllowRedisplay(AllowRedisplay.ALWAYS)
                .build()

            Terminal.getInstance().collectPaymentMethod(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }

                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                },
                config  // Config as third parameter
            )
        }
    }

    suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().confirmPaymentIntent(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }

                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                }
            )
        }
    }

    suspend fun processCompletePayment(
        amountCents: Int,
        currency: String,
        sessionId: String,
        donorId: String,
        isMonthly: Boolean
    ): PaymentResult {
        try {
            println("=== DEBUG: Starting payment, isMonthly: $isMonthly ===")

            // Step 0: Initialize Tap to Pay (connect to phone's NFC reader) if not connected
            if (!isReaderConnected()) {
                initializeTapToPay()
            }

            // Step 1: Create payment intent
            val paymentIntent = createTerminalPaymentIntent(
                amountCents = amountCents,
                currency = currency,
                sessionId = sessionId,
                donorId = donorId
            )
            println("=== DEBUG: Created payment intent, status: ${paymentIntent.status} ===")

            // Step 2: Collect payment method (this is where tap-to-pay happens)
            val collectedIntent = collectPaymentMethod(paymentIntent)
            println("=== DEBUG: Collected payment method, status: ${collectedIntent.status} ===")

            // Step 3: Confirm payment
            val confirmedIntent = confirmPaymentIntent(collectedIntent)
            println("=== DEBUG: Confirmed payment, status: ${confirmedIntent.status} ===")

            // Step 4: Handle post-payment processing (subscriptions, etc.)
            if (isMonthly && confirmedIntent.status == PaymentIntentStatus.SUCCEEDED) {
                println("=== DEBUG: Processing as MONTHLY subscription ===")
                return handleMonthlySubscription(sessionId, donorId, amountCents, currency, confirmedIntent)
            } else if (confirmedIntent.status == PaymentIntentStatus.SUCCEEDED) {
                println("=== DEBUG: Processing as ONE-TIME payment ===")
                return handleOneTimePayment(sessionId, donorId, amountCents, currency, confirmedIntent)
            } else {
                println("=== DEBUG: Payment not successful, status: ${confirmedIntent.status} ===")
                return PaymentResult.Failed("Payment was not successful: ${confirmedIntent.status}")
            }

        } catch (e: TerminalException) {
            println("=== DEBUG: TerminalException: ${e.errorMessage} ===")
            return PaymentResult.Failed("Terminal error: ${e.errorMessage}")
        } catch (e: Exception) {
            println("=== DEBUG: Exception: ${e.message} ===")
            return PaymentResult.Failed("Payment failed: ${e.message}")
        }
    }

    private suspend fun handleMonthlySubscription(
        sessionId: String,
        donorId: String,
        amountCents: Int,
        currency: String,
        paymentIntent: PaymentIntent
    ): PaymentResult {
        return try {
            println("=== DEBUG: handleMonthlySubscription started ===")

            val selectedGift = SessionStore.selectedGift
            val priceId = selectedGift?.stripePriceId

            if (priceId.isNullOrBlank()) {
                return PaymentResult.Failed("No Stripe Price ID found for monthly product")
            }

            // Get payment method ID from backend
            val paymentIntentId = paymentIntent.id ?: return PaymentResult.Failed("Missing payment intent ID")
            println("=== DEBUG: About to call getPaymentMethodFromIntent with ID: $paymentIntentId ===")

            val paymentMethodResponse = RetrofitProvider.api.getPaymentMethodFromIntent(paymentIntentId)
            println("=== DEBUG: Raw payment method response = $paymentMethodResponse ===")

            val paymentMethodId = if (!paymentMethodResponse.generated_card_id.isNullOrBlank()) {
                println("=== DEBUG: Using generated_card_id: ${paymentMethodResponse.generated_card_id} ===")
                paymentMethodResponse.generated_card_id
            } else {
                println("=== DEBUG: generated_card_id is null/blank, falling back to payment_method_id: ${paymentMethodResponse.payment_method_id} ===")
                paymentMethodResponse.payment_method_id
            }
            println("=== DEBUG: Using paymentMethodId = $paymentMethodId ===")


            if (paymentMethodId.isNullOrBlank()) {
                return PaymentResult.Failed("No payment method found for payment intent")
            }

            println("=== DEBUG: Payment method response = $paymentMethodResponse ===")
            println("=== DEBUG: Using paymentMethodId = $paymentMethodId ===")
            println("=== DEBUG: generated_card_id = ${paymentMethodResponse.generated_card_id} ===")
            println("=== DEBUG: original payment_method_id = ${paymentMethodResponse.payment_method_id} ===")

            // Get donor info for customer creation
            val donorInfo = getDonorInfo(donorId)

            val customerResponse = RetrofitProvider.api.upsertCustomer(
                com.example.clarity.api.model.CustomerUpsertIn(
                    email = donorInfo.email,
                    name = donorInfo.name,
                    phone = donorInfo.phone,
                    metadata = mapOf(
                        "donor_id" to donorId,
                        "session_id" to sessionId,
                        "payment_intent_id" to paymentIntentId
                    )
                )
            )

            // Attach payment method to customer
            RetrofitProvider.api.attachPaymentMethod(
                com.example.clarity.api.model.PaymentMethodAttachIn(
                    customer_id = customerResponse.customer_id,
                    payment_method_id = paymentMethodId,
                    session_id = sessionId,
                    donor_id = donorId
                )
            )
            println("=== DEBUG: Successfully attached payment method to customer ===")


            // Create subscription
            val subscriptionResponse = RetrofitProvider.api.createSubscription(
                com.example.clarity.api.model.SubscriptionCreateIn(
                    customer_id = customerResponse.customer_id,
                    price_id = priceId,
                    session_id = sessionId,
                    donor_id = donorId,
                    metadata = mapOf(
                        "product_id" to (selectedGift?.productId ?: ""),
                        "amount_cents" to amountCents.toString(),
                        "currency" to currency,
                        "initial_payment_intent_id" to paymentIntentId,
                        "payment_method" to "terminal_payment"
                    )
                )
            )

            if (subscriptionResponse.status in listOf("active", "incomplete")) {
                PaymentResult.Success(
                    paymentIntentId = paymentIntentId,
                    subscriptionId = subscriptionResponse.id
                )
            } else {
                PaymentResult.Failed("Subscription creation failed: ${subscriptionResponse.status}")
            }

        } catch (e: Exception) {
            println("=== DEBUG: Exception in handleMonthlySubscription: ${e.message} ===")
            PaymentResult.Failed("Monthly subscription setup failed: ${e.message}")
        }
    }

    private suspend fun handleOneTimePayment(
        sessionId: String,
        donorId: String,
        amountCents: Int,
        currency: String,
        paymentIntent: PaymentIntent
    ): PaymentResult {
        return try {
            // Log successful one-time payment
            RetrofitProvider.api.logEvent(
                com.example.clarity.api.model.LogEventIn(
                    event_type = "PAYMENT_COMPLETED",
                    session_id = sessionId,
                    donor_id = donorId,
                    attributes = mapOf(
                        "payment_intent_id" to (paymentIntent.id ?: ""),
                        "amount_cents" to amountCents,
                        "currency" to currency,
                        "payment_type" to "OTG",
                        "status" to paymentIntent.status.toString(),
                        "method" to "tap_to_pay"
                    )
                )
            )

            PaymentResult.Success(paymentIntentId = paymentIntent.id ?: return PaymentResult.Failed("Missing payment intent ID"))
        } catch (e: Exception) {
            PaymentResult.Failed("One-time payment logging failed: ${e.message}")
        }
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
        return Terminal.getInstance().connectedReader != null
    }

    fun getConnectedReader(): Reader? {
        return Terminal.getInstance().connectedReader
    }

    fun getConnectionStatus(): ConnectionStatus {
        return Terminal.getInstance().connectionStatus
    }

    suspend fun registerDevice(context: android.content.Context): Boolean {
        return try {
            // Check if device is already registered
            if (isDeviceRegistered(context)) {
                return true
            }

            // Get device identifier and dynamic location ID
            val deviceCode = getDeviceIdentifier(context)
            val locationId = getLocationId()

            val response = RetrofitProvider.api.registerDevice(
                DeviceRegistrationIn(
                    device_code = deviceCode,
                    location_id = locationId // Use dynamic location ID
                )
            )

            // Log successful registration
            RetrofitProvider.api.logEvent(
                com.example.clarity.api.model.LogEventIn(
                    event_type = "DEVICE_REGISTERED",
                    attributes = mapOf(
                        "reader_id" to response.reader_id,
                        "device_type" to response.device_type
                    )
                )
            )

            // Mark device as registered locally
            markDeviceAsRegistered(context, response.reader_id)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isDeviceRegistered(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("stripe_terminal", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("device_registered", false)
    }

    private fun markDeviceAsRegistered(context: android.content.Context, readerId: String) {
        val prefs = context.getSharedPreferences("stripe_terminal", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("device_registered", true)
            .putString("reader_id", readerId)
            .putLong("registration_timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun getDeviceIdentifier(context: android.content.Context): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
}
// Data class for donor information
data class DonorInfo(
    val email: String,
    val name: String,
    val phone: String?
)