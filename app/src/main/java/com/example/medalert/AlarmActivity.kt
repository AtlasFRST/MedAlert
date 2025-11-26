package com.example.medalert

import android.app.AlarmManager
import com.example.medalert.models.Alarm
import com.example.medalert.models.Medication
import com.example.medalert.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

class AlarmActivity : AppCompatActivity() {

    private lateinit var alarmManager: AlarmManager
    private lateinit var rvAlarms: RecyclerView
    private lateinit var tvNoAlarms: TextView
    private lateinit var alarmAdapter: AlarmAdapter

    // We now need to hold both lists locally to reconstruct the display
    private val masterMedicationList = mutableListOf<Medication>()
    private val alarmsList = mutableListOf<Alarm>()

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        rvAlarms = findViewById(R.id.rvAlarms)
        tvNoAlarms = findViewById(R.id.tvNoAlarms)

        setupRecyclerView()

        findViewById<Button>(R.id.SetAlarmB).setOnClickListener {
            showAddAlarmDialog()
        }

        loadAlarmsFromFirestore()

        handleScannedData() //new
    }

    private fun setupRecyclerView() {
        // The adapter now needs both lists to link medication details to alarm names
        alarmAdapter = AlarmAdapter(alarmsList, masterMedicationList) { alarm ->
            deleteAlarm(alarm)
        }
        rvAlarms.adapter = alarmAdapter
        rvAlarms.layoutManager = LinearLayoutManager(this)
    }

    // --- DATA LOADING & UI ---


    private fun handleScannedData() {
        // Check if the intent that started this activity has needed data
        if (intent.hasExtra("SCANNED_DRUG_NAME") && intent.hasExtra("SCANNED_TIMES_PER_DAY")) {
            val drugName = intent.getStringExtra("SCANNED_DRUG_NAME")
            val timesPerDay = intent.getIntExtra("SCANNED_TIMES_PER_DAY", 1)
            val pillsRemaining = intent.getIntExtra("SCANNED_PILLS_REMAINING", 0) // Optional

            if (drugName != null) {
                // If this medication already exists, don't re-add it
                // Prevents duplicates
                if (masterMedicationList.any { it.name.equals(drugName, ignoreCase = true) }) {
                    Toast.makeText(this, "'$drugName' is already in your list.", Toast.LENGTH_LONG).show()
                    return
                }

                // Create a new Medication object from the scanned data
                val newMed = Medication(
                    name = drugName,
                    pillsRemaining = pillsRemaining,
                    timesPerDay = timesPerDay
                )

                // Check for matching schedules
                checkForMatchingSchedules(newMed) { userWantsToCreateNew ->
                    if (userWantsToCreateNew) {
                        // User wants a new schedule, so we ask for the times.
                        askForAlarmTime(newMed, 1, newMed.timesPerDay) { alarmTimes ->
                            if (alarmTimes.isNotEmpty()) {
                                saveNewMedicationAndAlarms(newMed, alarmTimes)
                            }
                        }
                    }
                    // If 'false', the medication was added to an existing schedule and we're done.
                }

                // Clear the extras from the intent so this doesn't re-trigger on configuration changes
                intent.removeExtra("SCANNED_DRUG_NAME")
                intent.removeExtra("SCANNED_TIMES_PER_DAY")
            }
        }
    }

    private fun loadAlarmsFromFirestore() {
        if (userId == null) return

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("AlarmActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val userProfile = snapshot?.toObject<UserProfile>()
                val alarms = userProfile?.alarms ?: emptyList()
                val medications = userProfile?.medications ?: emptyList()

                // Update both master lists
                masterMedicationList.clear()
                masterMedicationList.addAll(medications)

                updateUiWithAlarms(alarms)

                // Schedule all device alarms
                alarms.forEach { setAndroidAlarm(it) }
            }
    }

    private fun updateUiWithAlarms(alarms: List<Alarm>) {
        alarmsList.clear()
        // Here, we pass the full medication list to the adapter so it can find details
        alarmsList.addAll(alarms.sortedBy { get24HourString(it.alarmTime) })
        alarmAdapter.notifyDataSetChanged() // The adapter will now have both new lists

        tvNoAlarms.visibility = if (alarmsList.isEmpty()) View.VISIBLE else View.GONE
        rvAlarms.visibility = if (alarmsList.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- ADD MEDICATION FLOW (REFACTORED) ---

    private fun showAddAlarmDialog() {
        val medNameInput = EditText(this).apply { hint = "Medication Name" }
        val pillsRemainingInput = EditText(this).apply { hint = "Pills Remaining"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val timesPerDayInput = EditText(this).apply { hint = "Times Per Day"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        val initialDialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(medNameInput)
            addView(pillsRemainingInput)
            addView(timesPerDayInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Medication Details")
            .setView(initialDialogView)
            .setPositiveButton("Next") { _, _ ->
                val medName = medNameInput.text.toString()
                val pillsRemaining = pillsRemainingInput.text.toString().toIntOrNull() ?: 0
                val timesPerDay = timesPerDayInput.text.toString().toIntOrNull() ?: 1

                if (medName.isBlank() || timesPerDay < 1) {
                    Toast.makeText(this, "Medication name and valid times per day are required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Important: Check if this medication already exists in our master list.
                // For this normalized structure, we treat adding a medication and setting its alarms as distinct steps.
                if (masterMedicationList.any { it.name.equals(medName, ignoreCase = true) }) {
                    Toast.makeText(this, "'$medName' already exists. To change its schedule, please delete it and add it again.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val newMed = Medication(medName, pillsRemaining, timesPerDay)

                checkForMatchingSchedules(newMed) { userWantsToCreateNew ->
                    if (userWantsToCreateNew) {
                        askForAlarmTime(newMed, 1, newMed.timesPerDay) { alarmTimes ->
                            if (alarmTimes.isNotEmpty()) {
                                // This function now saves the medication to the master list AND creates the new alarms
                                saveNewMedicationAndAlarms(newMed, alarmTimes)
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun get24HourString(time: String): String {
        return try {
            val inputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(time)
            if (date != null) outputFormat.format(date) else time
        } catch (e: Exception) {

            time
        }
    }

    private fun askForAlarmTime(medication: Medication, currentAlarmNum: Int, totalAlarms: Int, onComplete: (List<String>) -> Unit) {
        if (currentAlarmNum > totalAlarms) {
            onComplete(emptyList()) // Base case
            return
        }

        val timeInput = EditText(this).apply { hint = "e.g., 8:30 AM" }
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(50, 50, 50, 50); addView(timeInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Time for Alarm $currentAlarmNum of $totalAlarms")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Set Time") { _, _ ->
                val alarmTime = timeInput.text.toString()
                if (alarmTime.isNotBlank()) {
                    askForAlarmTime(medication, currentAlarmNum + 1, totalAlarms) { remainingAlarms ->
                        val allAlarms = mutableListOf(alarmTime)
                        allAlarms.addAll(remainingAlarms)
                        onComplete(allAlarms)
                    }
                } else {
                    Toast.makeText(this, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                    askForAlarmTime(medication, currentAlarmNum, totalAlarms, onComplete)
                }
            }
            .show()
    }

    // --- "JOIN SCHEDULE" FLOW (REFACTORED) ---

    private fun checkForMatchingSchedules(newMed: Medication, onComplete: (Boolean) -> Unit) {
        if (userId == null) {
            onComplete(true)
            return
        }

        // Find existing medications with the same timesPerDay
        val matchingMeds = masterMedicationList
            .filter { it.timesPerDay == newMed.timesPerDay }
            .map { it.name }
            .distinct()

        if (matchingMeds.isEmpty()) {
            onComplete(true) // No matches, create a new schedule
            return
        }

        val scheduleNames = matchingMeds.joinToString(", ")
        val message = "This medication is taken ${newMed.timesPerDay} time(s) per day, " +
                "just like '$scheduleNames'.\n\nWould you like to add it to the same schedule?"

        AlertDialog.Builder(this)
            .setTitle("Join Existing Schedule?")
            .setMessage(message)
            .setPositiveButton("Yes, Join Schedule") { _, _ ->
                addMedicationToExistingSchedule(newMed)
                onComplete(false) // Don't create a new schedule
            }
            .setNegativeButton("No, Create New Schedule") { _, _ ->
                onComplete(true) // Proceed to create a new schedule
            }
            .show()
    }

    private fun addMedicationToExistingSchedule(newMed: Medication) {
        if (userId == null) return

        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: UserProfile()

            // First, add the new medication to the master list
            val updatedMasterMeds = userProfile.medications.toMutableList().apply { add(newMed) }

            val existingAlarms = userProfile.alarms.toMutableList()
            // Find the names of meds with the same frequency
            val matchingMedNames = userProfile.medications
                .filter { it.timesPerDay == newMed.timesPerDay }
                .map { it.name }

            // Find all alarms that contain any of those medications
            val alarmsToUpdate = existingAlarms.filter { alarm ->
                alarm.medicationNames.any { medName -> medName in matchingMedNames }
            }

            for (alarmToUpdate in alarmsToUpdate) {
                val alarmIndex = existingAlarms.indexOfFirst { it.requestCode == alarmToUpdate.requestCode }
                if (alarmIndex != -1) {
                    val updatedMedNames = alarmToUpdate.medicationNames.toMutableList().apply { add(newMed.name) }
                    existingAlarms[alarmIndex] = alarmToUpdate.copy(medicationNames = updatedMedNames)
                }
            }

            // Save both updated lists to Firestore
            transaction.set(userDocRef, mapOf(
                "medications" to updatedMasterMeds,
                "alarms" to existingAlarms
            ), SetOptions.merge())
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "'${newMed.name}' was added to the existing schedule.", Toast.LENGTH_LONG).show()
            // A reload will refresh UI and set alarms correctly
            loadAlarmsFromFirestore()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- DATA SAVING (REFACTORED) ---

    private fun saveNewMedicationAndAlarms(medication: Medication, alarmTimes: List<String>) {
        if (userId == null) return

        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: UserProfile()

            // 1. Add the new medication to the master medication list
            val updatedMasterMeds = userProfile.medications.toMutableList().apply { add(medication) }

            // 2. Create new alarms or add to existing ones
            val existingAlarms = userProfile.alarms.toMutableList()
            for (time in alarmTimes) {
                val alarmIndex = existingAlarms.indexOfFirst { get24HourString(it.alarmTime) == get24HourString(time) }
                if (alarmIndex != -1) {
                    // An alarm for this time already exists, just add the medication name
                    val alarmToUpdate = existingAlarms[alarmIndex]
                    val updatedNames = alarmToUpdate.medicationNames.toMutableList().apply { add(medication.name) }
                    existingAlarms[alarmIndex] = alarmToUpdate.copy(medicationNames = updatedNames)
                } else {
                    // No alarm for this time, create a brand new one
                    val newAlarm = Alarm(
                        alarmTime = time,
                        requestCode = (System.currentTimeMillis() + time.hashCode()).toInt(),
                        medicationNames = listOf(medication.name)
                    )
                    existingAlarms.add(newAlarm)
                }
            }

            // 3. Save both updated lists back to Firestore
            transaction.set(userDocRef, mapOf(
                "medications" to updatedMasterMeds,
                "alarms" to existingAlarms
            ), SetOptions.merge())

        }.addOnSuccessListener {
            Log.d("AlarmActivity", "Transaction success! New medication and alarms saved.")
            Toast.makeText(this, "Medication alarms saved successfully!", Toast.LENGTH_SHORT).show()
            loadAlarmsFromFirestore() // Reload to update UI and set device alarms
        }.addOnFailureListener { e ->
            Log.w("AlarmActivity", "Transaction failure.", e)
            Toast.makeText(this, "Failed to save alarms: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --- DELETE ALARM (REFACTORED) ---

    private fun deleteAlarm(alarmToDelete: Alarm) {
        if (userId == null) return

        // This function becomes much more complex now.
        // For simplicity, a full medication is deleted from all alarms it appears in.
        // A more advanced implementation might ask the user if they want to remove just from this alarm.

        // Let's assume we delete the ENTIRE medication schedule for simplicity
        val medsInThisAlarm = alarmToDelete.medicationNames

        AlertDialog.Builder(this)
            .setTitle("Delete Medication Schedule?")
            .setMessage("This will delete '${medsInThisAlarm.joinToString()}' and all of its associated alarms. Are you sure?")
            .setPositiveButton("Yes, Delete") { _, _ ->
                performDelete(medsInThisAlarm)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(medNamesToDelete: List<String>) {
        if (userId == null) return
        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: return@runTransaction

            // 1. Remove the medications from the master list
            val updatedMasterMeds = userProfile.medications.filter { it.name !in medNamesToDelete }

            // 2. Remove the medication names from all alarms, and delete alarms that become empty
            val updatedAlarms = userProfile.alarms.mapNotNull { alarm ->
                // Cancel the Android alarm before deleting
                cancelAndroidAlarm(alarm.requestCode)

                val remainingMeds = alarm.medicationNames.filter { it !in medNamesToDelete }
                if (remainingMeds.isEmpty()) {
                    null // This alarm is now empty, so delete it by returning null
                } else {
                    alarm.copy(medicationNames = remainingMeds) // Keep the alarm with the remaining meds
                }
            }

            transaction.set(userDocRef, mapOf(
                "medications" to updatedMasterMeds,
                "alarms" to updatedAlarms
            ), SetOptions.merge())

        }.addOnSuccessListener {
            Log.d("AlarmActivity", "Medication schedule deleted successfully.")
            loadAlarmsFromFirestore() // Reload everything
        }.addOnFailureListener { e ->
            Log.w("AlarmActivity", "Error deleting medication schedule", e)
        }
    }


    // --- ANDROID ALARM SCHEDULING (NO CHANGES NEEDED HERE) ---

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Permission required to set exact alarms", Toast.LENGTH_LONG).show()
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { startActivity(it) }
                false
            } else { true }
        } else { true }
    }

    private fun setAndroidAlarm(alarm: Alarm) {
        if (checkPermission()) {
            val alarmTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val calendar = Calendar.getInstance()

            try {
                val date = alarmTimeFormat.parse(alarm.alarmTime)
                if (date != null) {
                    val alarmCalendar = Calendar.getInstance().apply { time = date }
                    calendar.set(Calendar.HOUR_OF_DAY, alarmCalendar.get(Calendar.HOUR_OF_DAY))
                    calendar.set(Calendar.MINUTE, alarmCalendar.get(Calendar.MINUTE))
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    if (calendar.before(Calendar.getInstance())) {
                        calendar.add(Calendar.DATE, 1)
                    }

                    // The intent only needs to know about the alarm, not the full med objects
                    val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
                        putExtra("ALARM_TIME", alarm.alarmTime)
                        putExtra("REQUEST_CODE", alarm.requestCode)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        this,
                        alarm.requestCode,
                        alarmIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("AlarmActivity", "SUCCESS: Android alarm set for ${alarm.alarmTime} with code ${alarm.requestCode}")
                }
            } catch (e: Exception) {
                Log.e("AlarmActivity", "Failed to parse alarm time: ${alarm.alarmTime}", e)
            }
        }
    }

    private fun cancelAndroidAlarm(requestCode: Int) {
        val alarmIntent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, alarmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmActivity", "SUCCESS: Canceled Android alarm with code $requestCode")
        } else {
            Log.w("AlarmActivity", "Could not find alarm to cancel with code $requestCode")
        }
    }
}

