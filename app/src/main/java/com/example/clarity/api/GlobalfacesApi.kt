package com.example.clarity.api

import retrofit2.Call
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
    fun login(@Body body: FundraiserLoginIn): Call<FundraiserLoginOut>
}
