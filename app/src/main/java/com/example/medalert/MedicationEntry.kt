package com.example.medalert

data class MedicationEntry(
    val patientName: String? = null,
    val drugName: String,
    val directions: String,
    val rxNumber: String? = null,
    val strength: String? = null,
    val form: String? = null
)

// super-light “storage” helpers (works for Firestore later)
fun MedicationEntry.toMap(): Map<String, Any?> = mapOf(
    "patientName" to patientName,
    "drugName" to drugName,
    "directions" to directions,
    "rxNumber" to rxNumber,
    "strength" to strength,
    "form" to form,
    "createdAt" to System.currentTimeMillis()
)

// quick JSON without extra libraries (android org.json)
fun MedicationEntry.toJsonString(): String =
    org.json.JSONObject(this.toMap()).toString(2)
