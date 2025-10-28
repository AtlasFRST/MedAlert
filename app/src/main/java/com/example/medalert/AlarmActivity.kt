package com.example.medalert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class AlarmActivity : AppCompatActivity() {

    private lateinit var alarmManager: AlarmManager
    private lateinit var rvAlarms: RecyclerView
    private lateinit var tvNoAlarms: TextView
    private lateinit var alarmAdapter: AlarmAdapter
    private val alarmsList = mutableListOf<String>()


    private lateinit var groupNewAlarm: ConstraintLayout
    private lateinit var spinnerHour: Spinner
    private lateinit var spinnerMinute: Spinner
    private lateinit var spinnerAmPm: Spinner
    private lateinit var btnSetAlarm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Initialize all views
        rvAlarms = findViewById(R.id.rvAlarms)
        tvNoAlarms = findViewById(R.id.tvNoAlarms)
        spinnerAmPm = findViewById(R.id.spinnerAmPm)
        groupNewAlarm = findViewById(R.id.groupNewAlarm)
        spinnerHour = findViewById(R.id.spinnerHour)
        spinnerMinute = findViewById(R.id.spinnerMinute)
        btnSetAlarm = findViewById(R.id.SetAlarmB)
        val btnSaveAlarm = findViewById<Button>(R.id.btnSaveAlarm)
        val btnCancel = findViewById<Button>(R.id.btnCancel)


        // --- Setup for the drop-down menus ---
        setupSpinners()

        btnSetAlarm.setOnClickListener {
            // Show the drop-down menus and hide the "Set New Alarm" button
            groupNewAlarm.visibility = View.VISIBLE
            btnSetAlarm.visibility = View.GONE
        }

        btnCancel.setOnClickListener {
            // Hide the drop-down menus and show the "Set New Alarm" button
            groupNewAlarm.visibility = View.GONE
            btnSetAlarm.visibility = View.VISIBLE
        }

        btnSaveAlarm.setOnClickListener {
            if (checkPermission()) {
                val selectedHour12 = spinnerHour.selectedItem.toString().toInt()
                val selectedMinute = spinnerMinute.selectedItem.toString().toInt()
                val selectedAmPm = spinnerAmPm.selectedItem.toString()
                val hour24 = convertTo24Hour(selectedHour12, selectedAmPm);

                setAlarm(hour24, selectedMinute)
                // Hide the menus after saving
                groupNewAlarm.visibility = View.GONE
                btnSetAlarm.visibility = View.VISIBLE
            }
        }
        // --- End of drop-down menu setup ---


        setupRecyclerView()
        loadAlarms()
    }

    private fun setupSpinners() {
        // Use 1-12 for hours
        val hours = (1..12).map { it.toString() }
        val hourAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hours)
        spinnerHour.adapter = hourAdapter

        // Minutes (0-59)
        val minutes = (0..59).map { String.format("%02d", it) }
        val minuteAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, minutes)
        spinnerMinute.adapter = minuteAdapter

        // AM/PM
        val amPm = listOf("AM", "PM")
        val amPmAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, amPm)
        spinnerAmPm.adapter = amPmAdapter
    }

    private fun convertTo24Hour(hour12: Int, amPm: String): Int {
        return when {
            amPm == "AM" && hour12 == 12 -> 0 // 12 AM is 00:00
            amPm == "PM" && hour12 < 12 -> hour12 + 12 // 1 PM is 13:00, 11 PM is 23:00
            else -> hour12 // AM hours (1-11) and 12 PM are the same
        }
    }

    private fun setAlarm(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        // Create a 12-hour format string for display and saving
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12 // 00:xx is 12:xx AM
            hour > 12 -> hour - 12
            else -> hour
        }
        val displayTime = String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, amPm)

        if (alarmsList.contains(displayTime)) {
            Toast.makeText(this, "Alarm for $displayTime already exists.", Toast.LENGTH_SHORT).show()
            return
        }

        val requestCode = displayTime.hashCode() // Use the display string for a unique code
        val alarmIntent = Intent(this, AlarmReceiver::class.java).putExtra("ALARM_TIME", displayTime)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        Toast.makeText(this, "Alarm set for $displayTime", Toast.LENGTH_SHORT).show()
        Log.d("AlarmActivity", "Alarm set for $displayTime with request code $requestCode")

        saveAlarm(displayTime)
        loadAlarms()
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(alarmsList) { alarmString ->
            deleteAlarm(alarmString)
        }
        rvAlarms.adapter = alarmAdapter
        rvAlarms.layoutManager = LinearLayoutManager(this)
    }

    private fun deleteAlarm(alarmString: String) {
        val requestCode = alarmString.hashCode()

        val alarmIntent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, requestCode, alarmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmActivity", "Canceled alarm with request code $requestCode")
        }

        removeAlarm(alarmString)
        loadAlarms()
        Toast.makeText(this, "Alarm for $alarmString deleted", Toast.LENGTH_SHORT).show()
    }

    private fun saveAlarm(alarmTime: String) {
        val prefs = getSharedPreferences("MedAlertAlarms", Context.MODE_PRIVATE)
        val alarms = prefs.getStringSet("alarms", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        alarms.add(alarmTime)
        prefs.edit().putStringSet("alarms", alarms).apply()
    }

    private fun removeAlarm(alarmTime: String) {
        val prefs = getSharedPreferences("MedAlertAlarms", Context.MODE_PRIVATE)
        val alarms = prefs.getStringSet("alarms", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        alarms.remove(alarmTime)
        prefs.edit().putStringSet("alarms", alarms).apply()
    }

    private fun loadAlarms() {
        val prefs = getSharedPreferences("MedAlertAlarms", Context.MODE_PRIVATE)
        val alarms = prefs.getStringSet("alarms", setOf())?.sorted() ?: listOf()
        alarmsList.clear()
        alarmsList.addAll(alarms)
        alarmAdapter.notifyDataSetChanged()

        if (alarmsList.isEmpty()) {
            rvAlarms.visibility = View.VISIBLE
            tvNoAlarms.visibility = View.VISIBLE
        } else {
            rvAlarms.visibility = View.VISIBLE
            tvNoAlarms.visibility = View.GONE
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Permission required to set exact alarms", Toast.LENGTH_LONG).show()
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    startActivity(it)
                }
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d("AlarmActivity", "Permission for exact alarms is granted.")
            }
        }
    }
}
