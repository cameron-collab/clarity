package com.example.clarity.api

import com.example.clarity.api.model.DonorUpsertIn
import com.example.clarity.api.model.DonorUpsertOut
import com.example.clarity.api.model.PaymentIntentIn
import com.example.clarity.api.model.PaymentIntentOut
import com.example.clarity.api.model.SetupIntentIn
import com.example.clarity.api.model.SetupIntentOut
import com.example.clarity.api.model.SubscriptionCreateIn
import com.example.clarity.api.model.SubscriptionCreateOut
import com.example.clarity.api.model.CustomerUpsertIn
import com.example.clarity.api.model.CustomerUpsertOut
import com.example.clarity.api.model.PaymentMethodAttachIn
import com.example.clarity.api.model.PaymentMethodAttachOut
import com.example.clarity.api.model.SendSmsIn
import com.example.clarity.api.model.SendSmsOut
import com.example.clarity.api.model.SmsStatusOut
import com.example.clarity.api.model.DonorConsentIn
import com.example.clarity.api.model.TerminalPaymentIntentIn
import com.example.clarity.api.model.TerminalPaymentIntentOut
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

data class FundraiserLoginIn(val fundraiser_id: String)
data class FundraiserLoginOut(
    val session_id: String,
    val fundraiser: Map<String, Any?>,
    val charity: Map<String, Any?>?,
    val campaign: Map<String, Any?>?
)
data class ProductLookupOut(
    val stripe_price_id: String,
    val product_id: String,
    val display_name: String
)

data class DonorOut(
    val email: String,
    val name: String,
    val phone: String?
)

data class CampaignProductsOut(
    val products: List<ProductOut>
)

data class ProductOut(
    val product_id: String,
    val product_type: String,
    val amount_cents: Int,
    val currency: String,
    val display_name: String,
    val stripe_price_id: String?,
    val active: Boolean
)

data class DeviceRegistrationIn(
    val device_code: String,
    val location_id: String
)

data class DeviceRegistrationOut(
    val reader_id: String,
    val status: String,
    val device_type: String
)

interface GlobalfacesApi {
    @POST("fundraiser/login")
    suspend fun login(@Body body: FundraiserLoginIn): FundraiserLoginOut

    @POST("donor/upsert")
    suspend fun donorUpsert(@Body body: DonorUpsertIn): DonorUpsertOut

    // NEW
    @POST("verification/sms/send")
    suspend fun sendSms(@Body body: SendSmsIn): SendSmsOut

    @GET("verification/sms/status")
    suspend fun getSmsStatus(
        @Query("session_id") sessionId: String,
        @Query("donor_id") donorId: String
    ): SmsStatusOut

    @POST("/payment_intent")
    suspend fun createPaymentIntent(@Body body: PaymentIntentIn): PaymentIntentOut

    @POST("/setup_intent")
    suspend fun createSetupIntent(@Body body: SetupIntentIn): SetupIntentOut

    @POST("/subscriptions/create")
    suspend fun createSubscription(@Body body: SubscriptionCreateIn): SubscriptionCreateOut

    @POST("/customer/upsert")
    suspend fun upsertCustomer(@Body body: CustomerUpsertIn): CustomerUpsertOut

    @POST("/payment_method/attach")
    suspend fun attachPaymentMethod(@Body body: PaymentMethodAttachIn): PaymentMethodAttachOut

    @POST("log-event")
    suspend fun logEvent(@Body body: com.example.clarity.api.model.LogEventIn): Map<String, Any?>

    @POST("donor/consent")
    suspend fun updateDonorConsent(@Body body: DonorConsentIn): Map<String, Any?>

    @POST("/terminal/connection_token")
    suspend fun createTerminalConnectionToken(): ConnectionTokenOut
    data class ConnectionTokenOut(val secret: String)

    @POST("/terminal/payment_intent")
    suspend fun createTerminalPaymentIntent(@Body body: TerminalPaymentIntentIn): TerminalPaymentIntentOut


    @GET("/products/lookup")
    suspend fun lookupProduct(
        @Query("campaign_id") campaignId: String,
        @Query("amount_cents") amountCents: Int,
        @Query("currency") currency: String,
        @Query("product_type") productType: String = "MONTHLY"
    ): ProductLookupOut

    @GET("/donor/{donor_id}")
    suspend fun getDonor(@Path("donor_id") donorId: String): DonorOut

    @GET("/products/campaign/{campaign_id}")
    suspend fun getCampaignProducts(@Path("campaign_id") campaignId: String): CampaignProductsOut

    @POST("/terminal/register_device")
    suspend fun registerDevice(@Body body: DeviceRegistrationIn): DeviceRegistrationOut


}
