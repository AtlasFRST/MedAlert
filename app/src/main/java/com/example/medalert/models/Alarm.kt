// In com/example/medalert/models/Alarm.kt

package com.example.medalert.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

// This model does not change
@IgnoreExtraProperties
data class TakenRecord(
    val time: Timestamp = Timestamp.now()
)

// This model does not change
@IgnoreExtraProperties
data class Medication(
    val name: String = "",
    val pillsRemaining: Int = 0,
    val timesPerDay: Int = 1,
    val takenHistory: List<TakenRecord> = emptyList()
)

// --- THIS MODEL CHANGES SIGNIFICANTLY ---
@IgnoreExtraProperties
data class Alarm(
    val alarmTime: String = "",
    val requestCode: Int = 0,
    // We no longer store the full medication object
    val medicationNames: List<String> = emptyList()
)

// --- THIS MODEL CHANGES SIGNIFICANTLY ---
@IgnoreExtraProperties
data class UserProfile(
    val name: String? = null,
    val email: String? = null,
    // We now have two separate lists
    val medications: List<Medication> = emptyList(),
    val alarms: List<Alarm> = emptyList()
)
