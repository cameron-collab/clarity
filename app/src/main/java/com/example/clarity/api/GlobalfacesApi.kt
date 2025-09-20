package com.example.clarity.api

import com.example.clarity.api.model.DonorUpsertIn
import com.example.clarity.api.model.DonorUpsertOut
import com.example.clarity.api.model.SendSmsIn
import com.example.clarity.api.model.SendSmsOut
import com.example.clarity.api.model.SmsStatusOut
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
}
