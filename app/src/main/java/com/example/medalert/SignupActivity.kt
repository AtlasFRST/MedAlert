package com.example.medalert

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore





class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signup)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.CreateAcctB).setOnClickListener {
            Log.d("dbfirebase", "Button Clicked")
            val firstName = findViewById<EditText>(R.id.FirstNameET).text.toString()
            val lastName = findViewById<EditText>(R.id.LastNameET).text.toString()
            val username = findViewById<EditText>(R.id.NewUsernameET).text.toString()
            val password = findViewById<EditText>(R.id.NewPasswordET).text.toString()
            Log.d("dbfirebase", "Values stored")
            val db = FirebaseFirestore.getInstance()
            Log.d("dbfirebase", "db instance")
            val user: MutableMap<String, Any> = HashMap()
            user["firstName"] = firstName
            user["lastName"] = lastName
            user["username"] = username
            user["password"] = password
            Log.d("dbfirebase", "map created")
            db.collection("users")
                .add(user)
                .addOnSuccessListener {
                    Log.d("dbfirebase", "save: ${user}")
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    Log.d("dbfirebase Failed", "${user}")
                }
        }
    }
}
