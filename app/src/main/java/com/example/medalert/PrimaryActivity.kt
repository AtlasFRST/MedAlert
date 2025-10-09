package com.example.medalert

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import com.google.firebase.auth.FirebaseAuth

class PrimaryActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_USERNAME = "username"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_primary)

        if(FirebaseAuth.getInstance().currentUser == null){
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            findViewById<TextView>(R.id.textView).text = "Welcome, ${FirebaseAuth.getInstance().currentUser?.displayName}!"
        }


        findViewById<Button>(R.id.ScannerB).setOnClickListener {
            Log.d("activity", "button clicked")
            val intent = Intent(this, ScannerActivity::class.java)

            startActivity(intent)

        }

        findViewById<Button>(R.id.SchedulerB).setOnClickListener {
            Log.d("activity", "button clicked")
            val intent = Intent(this, AlarmActivity::class.java)
            startActivity(intent)

        }

        findViewById<Button>(R.id.LogoutB).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }


    }
}