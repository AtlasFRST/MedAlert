package com.example.medalert.data.rxnorm

import retrofit2.http.GET
import retrofit2.http.Query

interface RxNormService {
    // https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term=ibup&maxEntries=10
    @GET("approximateTerm.json")
    suspend fun approximateTerm(
        @Query("term") term: String,
        @Query("maxEntries") maxEntries: Int = 10
    ): ApproximateResponse
}
