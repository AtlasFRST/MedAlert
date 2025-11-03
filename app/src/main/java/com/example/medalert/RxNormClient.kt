package com.example.medalert.data.rxnorm

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RxNormClient {
    private const val BASE_URL = "https://rxnav.nlm.nih.gov/REST/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val ok = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val api: RxNormService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(ok)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(RxNormService::class.java)
    }
}
