package com.example.medalert

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore

class PrimaryActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_USERNAME = "username"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_primary)

        val  userName = intent.getStringExtra(EXTRA_USERNAME)
        findViewById<TextView>(R.id.textView2).text = "$userName"

    }
}