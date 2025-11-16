package com.example.medalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medalert.models.TakenRecord
import com.example.medalert.models.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra("USER_ID")
        val alarmRequestCode = intent.getIntExtra("REQUEST_CODE", -1)

        if (userId == null || alarmRequestCode == -1) {
            Log.e("ActionReceiver", "User ID or Request Code missing.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: return@runTransaction

            // 1. Find the alarm that was triggered.
            val triggeredAlarm = userProfile.alarms.find { it.requestCode == alarmRequestCode }
            if (triggeredAlarm == null) {
                Log.w("ActionReceiver", "Triggered alarm with code $alarmRequestCode not found.")
                return@runTransaction
            }

            // 2. Get the names of the medications for that alarm.
            val medNamesToUpdate = triggeredAlarm.medicationNames

            // 3. Update each of those medications in the master list.
            val updatedMasterMeds = userProfile.medications.map { med ->
                if (med.name in medNamesToUpdate) {
                    // This is one of the meds we need to update
                    med.copy(
                        pillsRemaining = if (med.pillsRemaining > 0) med.pillsRemaining - 1 else 0,
                        takenHistory = med.takenHistory + TakenRecord() // Add new timestamp
                    )
                } else {
                    med // This medication was not in the alarm, return it unchanged.
                }
            }

            // 4. Save the updated master medications list back to Firestore.
            transaction.set(userDocRef, mapOf("medications" to updatedMasterMeds), SetOptions.merge())

        }.addOnSuccessListener {
            Log.d("ActionReceiver", "Successfully marked as taken and updated pills remaining.")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(alarmRequestCode)
        }.addOnFailureListener { e ->
            Log.e("ActionReceiver", "Failed to mark as taken.", e)
        }
    }
}
