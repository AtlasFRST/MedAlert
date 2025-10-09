package com.example.medalert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest


class AlarmActivity : AppCompatActivity() {

    private val timeRegex = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    private lateinit var alarmManager: AlarmManager

    override fun onResume() {
        super.onResume()
        // Check if the permission was granted while the user was in settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Permission required to set exact alarms", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_alarm)

        val etTime = findViewById<EditText>(R.id.TimeET)
        val bSetAlarm = findViewById<Button>(R.id.SetAlarmB)

        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.alarm)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bSetAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Permission is not granted, request it from the user.
                    Toast.makeText(this, "Permission required to set exact alarms", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                    return@setOnClickListener // Stop here until permission is granted
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Check if permission is already granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // If not granted, request it from the user
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            }

            val input = etTime.text.toString().trim()
            if (!timeRegex.matches(input)) {
                etTime.error = "Invalid time format. Please use HH:MM."
                return@setOnClickListener
            }

            Log.d("alarm", "button clicked")
            Log.d("alarm", input + "inputted time")

            // Parse the input string into hours and minutes
            val (h, m) = input.split(":").map { it.toInt() }

            val now = LocalDateTime.now()
            var trigger = LocalDateTime.of(LocalDate.now(), LocalTime.of(h,m))
            //sets trigger to go off tomorrow if it's already passed
            if(trigger.isBefore(now)){ trigger = trigger.plusDays(1) }
            Log.d("alarm", trigger.toString() + "trigger time")

            val triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()



            val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
                // You can add extras to tell the receiver what this alarm is about
                putExtra("ALARM_TIME", input)
            }

            val pi = PendingIntent.getBroadcast(
                this,
                1001, // request code
                alarmIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )



            val showIntent = PendingIntent.getActivity(
                this,
                1002,
                Intent(this, PrimaryActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)

            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showIntent)

            alarmManager.setAlarmClock(alarmClockInfo, pi)

            Toast.makeText(this, "Alarm set for $h:$m", Toast.LENGTH_SHORT).show()

        }

    }




}