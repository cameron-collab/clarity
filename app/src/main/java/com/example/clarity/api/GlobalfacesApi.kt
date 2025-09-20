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
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

data class FundraiserLoginIn(val fundraiser_id: String)
data class FundraiserLoginOut(
    val session_id: String,
    val fundraiser: Map<String, Any?>,
    val charity: Map<String, Any?>?,
    val campaign: Map<String, Any?>?
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


}
