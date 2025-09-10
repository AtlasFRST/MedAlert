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

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //following lines establish which xml file to use
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //login button listener
        findViewById<Button>(R.id.LoginB).setOnClickListener {
            Log.d("dbfirebase", "Button Clicked")
            //saves edittext fields to variables
            val username = findViewById<EditText>(R.id.UsernameET).text.toString()
            val password = findViewById<EditText>(R.id.PasswordET).text.toString()
            Log.d("dbfirebase", "Values stored")
            //sets up firebase
            val db = FirebaseFirestore.getInstance()
            Log.d("dbfirebase", "db instance")
            //checks to see that user values exist in firebase
            db.collection("users")
                .whereEqualTo("username", username)
                .whereEqualTo("password", password)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        Log.d("dbfirebase", "${document.id} => ${document.data}")
                        val intent = Intent(this, PrimaryActivity::class.java)
                        intent.putExtra("username", username)
                        startActivity(intent)
                        finish()
                        }
                }
                .addOnFailureListener { exception ->
                    Log.w("dbfirebase", "Error getting documents: ", exception)
                }

        }
    }
}