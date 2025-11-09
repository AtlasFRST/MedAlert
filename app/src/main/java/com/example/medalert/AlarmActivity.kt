package com.example.medalert

// Required imports for models, Firestore, and dialogs
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
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.widget.LinearLayout
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
    private val alarmsList = mutableListOf<Alarm>() // List of Alarm objects

    // Firestore and Auth instances
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

        // Load existing alarms from Firestore. This "scans the document".
        loadAlarmsFromFirestore()
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(alarmsList) { alarm: Alarm ->
            deleteAlarm(alarm)
        }
        rvAlarms.adapter = alarmAdapter
        rvAlarms.layoutManager = LinearLayoutManager(this)
    }


    private fun showAddAlarmDialog() {
        // This part remains the same
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

                val newMed = Medication(medName, pillsRemaining, timesPerDay)

                // --- NEW LOGIC TO FIND AND PROMPT FOR MATCHING SCHEDULES ---
                checkForMatchingSchedules(newMed) { userWantsToCreateNew ->
                    if (userWantsToCreateNew) {
                        // User either said "No" or no matches were found.
                        // Proceed with the original flow of asking for each time.
                        askForAlarmTime(newMed, 1, newMed.timesPerDay) { medAlarms ->
                            if (medAlarms.isNotEmpty()) {
                                saveAllMedicationAlarms(medAlarms)
                            }
                        }
                    }
                    // If the user said "Yes", the new medication is already saved
                    // by the `checkForMatchingSchedules` function, so we do nothing here.
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // In AlarmActivity.kt, add this new function

    /**
     * Checks if there are existing alarm schedules that match the 'timesPerDay'
     * of the new medication. If so, it prompts the user to join one of them.
     * @param onComplete A callback that returns 'true' if a new schedule should be created,
     *                   or 'false' if the medication was added to an existing schedule.
     */
    private fun checkForMatchingSchedules(newMed: Medication, onComplete: (Boolean) -> Unit) {
        if (userId == null) {
            onComplete(true) // No user, proceed to create new
            return
        }

        // 1. Find all medications that have the same 'timesPerDay' value
        val matchingSchedules = alarmsList
            .flatMap { it.medications } // Get a flat list of all medications
            .filter { it.timesPerDay == newMed.timesPerDay } // Filter by timesPerDay
            .map { it.name } // Get their names
            .distinct() // Get unique medication names

        if (matchingSchedules.isEmpty()) {
            onComplete(true) // No matches found, proceed to create a new schedule
            return
        }

        // 2. A match is found! Prompt the user.
        val scheduleNames = matchingSchedules.joinToString(", ")
        val message = "This medication is taken ${newMed.timesPerDay} time(s) per day, " +
                "just like '$scheduleNames'.\n\nWould you like to add it to the same schedule?"

        AlertDialog.Builder(this)
            .setTitle("Join Existing Schedule?")
            .setMessage(message)
            .setPositiveButton("Yes, Join Schedule") { _, _ ->
                // User said YES. Add the new medication to the existing alarms.
                addMedicationToExistingSchedule(newMed)
                onComplete(false) // Don't create a new schedule
            }
            .setNegativeButton("No, Create New Schedule") { _, _ ->
                onComplete(true) // User said NO. Proceed to create a new schedule.
            }
            .show()
    }

    // In AlarmActivity.kt, add this new helper function

    private fun addMedicationToExistingSchedule(newMed: Medication) {
        if (userId == null) return

        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: UserProfile()
            val existingAlarms = userProfile.alarms.toMutableList()

            // Find all alarms that contain a medication with the matching 'timesPerDay'
            val alarmsToUpdate = existingAlarms.filter { alarm ->
                alarm.medications.any { med -> med.timesPerDay == newMed.timesPerDay }
            }

            for (alarmToUpdate in alarmsToUpdate) {
                val alarmIndex = existingAlarms.indexOfFirst { it.requestCode == alarmToUpdate.requestCode }
                if (alarmIndex != -1) {
                    val updatedMeds = alarmToUpdate.medications.toMutableList().apply { add(newMed) }
                    existingAlarms[alarmIndex] = alarmToUpdate.copy(medications = updatedMeds)
                }
            }

            transaction.set(userDocRef, mapOf("alarms" to existingAlarms), SetOptions.merge())
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "'${newMed.name}' was added to the existing schedule.", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    // --- NEW: A recursive function to ask for each alarm time ---
    private fun askForAlarmTime(medication: Medication, currentAlarmNum: Int, totalAlarms: Int, onComplete: (List<Pair<Medication, String>>) -> Unit) {
        if (currentAlarmNum > totalAlarms) {
            // Base case: We've collected all alarm times.
            // The 'onComplete' lambda will now be called with an empty list,
            // signaling the end. The real data is collected in the 'else' block.
            onComplete(emptyList())
            return
        }

        val timeInput = EditText(this).apply { hint = "e.g., 8:30 AM" }
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(timeInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Time for Alarm $currentAlarmNum of $totalAlarms")
            .setView(dialogView)
            .setCancelable(false) // Prevent user from dismissing
            .setPositiveButton("Set Time") { _, _ ->
                val alarmTime = timeInput.text.toString()
                if (alarmTime.isNotBlank()) {
                    // We have the time for the current alarm, now ask for the next one
                    askForAlarmTime(medication, currentAlarmNum + 1, totalAlarms) { remainingAlarms ->
                        // This builds the list of alarms backwards as the recursion unwinds
                        val allAlarms = mutableListOf(Pair(medication, alarmTime))
                        allAlarms.addAll(remainingAlarms)
                        onComplete(allAlarms)
                    }
                } else {
                    Toast.makeText(this, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                    // Ask for the same alarm time again
                    askForAlarmTime(medication, currentAlarmNum, totalAlarms, onComplete)
                }
            }
            .show()
    }


    // In AlarmActivity.kt
// REPLACE your old saveMedicationAlarm function with this new one.

    private fun saveAllMedicationAlarms(medAlarms: List<Pair<Medication, String>>) {
        if (userId == null || medAlarms.isEmpty()) return

        val userDocRef = db.collection("users").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val userProfile = snapshot.toObject<UserProfile>() ?: UserProfile()
            val existingAlarms = userProfile.alarms.toMutableList()

            // Loop through each medication and time the user entered
            for ((medication, alarmTime) in medAlarms) {
                // Check if an alarm for this specific time already exists
                val alarmIndex = existingAlarms.indexOfFirst { it.alarmTime == alarmTime }

                if (alarmIndex != -1) {
                    // Alarm exists, add medication to it (avoiding duplicates)
                    val existingAlarm = existingAlarms[alarmIndex]
                    if (!existingAlarm.medications.any { it.name == medication.name }) {
                        val updatedMeds = existingAlarm.medications.toMutableList().apply { add(medication) }
                        existingAlarms[alarmIndex] = existingAlarm.copy(medications = updatedMeds)
                    }
                } else {
                    // No alarm for this time, create a new one
                    val newAlarm = Alarm(
                        alarmTime = alarmTime,
                        requestCode = (System.currentTimeMillis() + alarmTime.hashCode()).toInt(), // Unique code
                        medications = listOf(medication)
                    )
                    existingAlarms.add(newAlarm)
                }
            }

            // Set the final updated list back to Firestore
            transaction.set(userDocRef, mapOf("alarms" to existingAlarms), SetOptions.merge())
            null // Return null for success
        }.addOnSuccessListener {
            Log.d("AlarmActivity", "Transaction success! All user-defined alarms have been saved.")
            Toast.makeText(this, "Medication alarms saved successfully!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Log.w("AlarmActivity", "Transaction failure.", e)
            Toast.makeText(this, "Failed to save alarms: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }



    private fun deleteAlarm(alarmToDelete: Alarm) {
        if (userId == null) return

        // Also cancel the Android AlarmManager alarm
        cancelAndroidAlarm(alarmToDelete.requestCode)

        // Use FieldValue.arrayRemove to delete the object from the Firestore array
        val userDocRef = db.collection("users").document(userId)
        userDocRef.update("alarms", FieldValue.arrayRemove(alarmToDelete))
            .addOnSuccessListener { Log.d("AlarmActivity", "Alarm document successfully deleted!") }
            .addOnFailureListener { e -> Log.w("AlarmActivity", "Error deleting document", e) }
    }

    // This function "scans" the user's document for alarms
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

                updateUiWithAlarms(alarms)

                // Set the actual Android alarms
                alarms.forEach { setAndroidAlarm(it) }
            }
    }

    private fun updateUiWithAlarms(alarms: List<Alarm>) {
        alarmsList.clear()
        alarmsList.addAll(alarms.sortedBy { it.alarmTime })
        alarmAdapter.notifyDataSetChanged()

        tvNoAlarms.visibility = if (alarmsList.isEmpty()) View.VISIBLE else View.GONE
        rvAlarms.visibility = if (alarmsList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Permission is not granted, request it from the user.
                Toast.makeText(
                    this,
                    "Permission required to set exact alarms",
                    Toast.LENGTH_LONG
                ).show()
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    startActivity(it)
                }
                false // Return false because we don't have permission yet
            } else {
                true // Permission is already granted
            }
        } else {
            true // No special permission needed for older Android versions
        }
    }


    private fun setAndroidAlarm(alarm: Alarm) {
        if (checkPermission()) {
            val alarmTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val calendar = Calendar.getInstance()

            try {
                // Parse the time string (e.g., "8:30 AM") into a Date object
                val date = alarmTimeFormat.parse(alarm.alarmTime)
                if (date != null) {
                    val alarmCalendar = Calendar.getInstance()
                    alarmCalendar.time = date

                    // Set the calendar to today's date but with the alarm's time
                    calendar.set(Calendar.HOUR_OF_DAY, alarmCalendar.get(Calendar.HOUR_OF_DAY))
                    calendar.set(Calendar.MINUTE, alarmCalendar.get(Calendar.MINUTE))
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    // If the alarm time is in the past for today, schedule it for tomorrow
                    if (calendar.before(Calendar.getInstance())) {
                        calendar.add(Calendar.DATE, 1)
                    }

                    // Create the intent to be fired when the alarm goes off
                    val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
                        // Pass data to the receiver, like the list of medications
                        putExtra("ALARM_TIME", alarm.alarmTime)
                        putExtra("REQUEST_CODE", alarm.requestCode)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        this,
                        alarm.requestCode, // Use the unique request code from Firestore
                        alarmIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

                    Log.d("AlarmActivity", "SUCCESS: Android alarm set for ${alarm.alarmTime} with code ${alarm.requestCode}")

                }
            } catch (e: Exception) {
                Log.e("AlarmActivity", "Failed to parse alarm time: ${alarm.alarmTime}", e)
                Toast.makeText(this, "Invalid time format for alarm: ${alarm.alarmTime}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // In AlarmActivity.kt

    private fun cancelAndroidAlarm(requestCode: Int) {
        val alarmIntent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
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
