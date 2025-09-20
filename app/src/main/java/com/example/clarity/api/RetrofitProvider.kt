package com.example.clarity.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitProvider {
    // Emulator → host FastAPI
    private const val BASE_URL = "http://10.0.2.2:8000/"
    // For a physical device, swap to your ngrok URL.

    val api: GlobalfacesApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GlobalfacesApi::class.java)
}
