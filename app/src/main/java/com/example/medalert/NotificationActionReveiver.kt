package com.example.medalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medalert.models.TakenRecord
import com.example.medalert.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra("USER_ID")
        val alarmRequestCode = intent.getIntExtra("REQUEST_CODE", -1)

        if (userId == null || alarmRequestCode == -1) {
            Log.e("ActionReceiver", "User ID or Request Code missing in intent.")
            return
        }

        Log.d("ActionReceiver", "Received 'Mark as Taken' for user $userId and alarm $alarmRequestCode")

        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>()
            if (userProfile == null) {
                Log.e("ActionReceiver", "User profile not found for ID: $userId")
                return@runTransaction
            }

            val existingAlarms = userProfile.alarms.toMutableList()
            val alarmIndex = existingAlarms.indexOfFirst { it.requestCode == alarmRequestCode }

            if (alarmIndex != -1) {
                val alarmToUpdate = existingAlarms[alarmIndex]
                val updatedMedications = alarmToUpdate.medications.map { med ->
                    // Decrement pills and add to history
                    med.copy(
                        pillsRemaining = if (med.pillsRemaining > 0) med.pillsRemaining - 1 else 0,
                        takenHistory = med.takenHistory + TakenRecord() // Add new timestamp
                    )
                }

                // Update the alarm with the modified medications list
                existingAlarms[alarmIndex] = alarmToUpdate.copy(medications = updatedMedications)
                transaction.set(userDocRef, mapOf("alarms" to existingAlarms), SetOptions.merge())
            } else {
                Log.w("ActionReceiver", "Alarm with request code $alarmRequestCode not found.")
            }
            null
        }.addOnSuccessListener {
            Log.d("ActionReceiver", "Successfully marked as taken and updated pills remaining.")
            // Optionally, dismiss the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(alarmRequestCode)

        }.addOnFailureListener { e ->
            Log.e("ActionReceiver", "Failed to mark as taken.", e)
        }
    }
}
