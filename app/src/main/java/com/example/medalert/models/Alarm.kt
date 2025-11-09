
package com.example.medalert.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

// NEW data class to store a record of when a medication was taken
@IgnoreExtraProperties
data class TakenRecord(
    val time: Timestamp = Timestamp.now() // Automatically stores the date and time
)

@IgnoreExtraProperties
data class Medication(
    val name: String = "",
    val pillsRemaining: Int = 0,
    val timesPerDay: Int = 1,
    val takenHistory: List<TakenRecord> = emptyList() // ADD THIS LINE
)

@IgnoreExtraProperties
data class Alarm(
    val alarmTime: String = "",
    val requestCode: Int = 0,
    // Note: The medications list now contains the enhanced Medication object
    val medications: List<Medication> = emptyList()
)

@IgnoreExtraProperties
data class UserProfile(
    val name: String? = null,
    val email: String? = null,
    val alarms: List<Alarm> = emptyList()
)
