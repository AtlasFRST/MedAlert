package com.example.medalert.data.rxnorm

data class ApproximateResponse(
    val approximateGroup: ApproximateGroup?
)

data class ApproximateGroup(
    val inputTerm: String?,
    val candidate: List<Candidate>?
)

data class Candidate(
    val rxcui: String?,
    val score: String?,   // higher is better (string from API)
    val rank: String?,
    val name: String?
)
