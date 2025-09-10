package com.example.medalert

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.EditText


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //launches signup activity
        findViewById<Button>(R.id.MainSignupB).setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)

            startActivity(intent)
            finish()
        }
        //launches login activity
        findViewById<Button>(R.id.MainLoginB).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)

            startActivity(intent)
            finish()
        }
    }
}