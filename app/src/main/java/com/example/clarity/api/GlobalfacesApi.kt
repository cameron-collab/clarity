package com.example.clarity.api

import com.example.clarity.api.model.DonorUpsertIn
import com.example.clarity.api.model.DonorUpsertOut
import com.example.clarity.api.model.SendSmsIn
import com.example.clarity.api.model.SendSmsOut
import retrofit2.http.Body
import retrofit2.http.POST

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
}
