package com.example.medalert

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth


class MainActivity : AppCompatActivity() {

    private lateinit var signInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var signInIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //preps firebase login
        signInLauncher = registerForActivityResult(
            FirebaseAuthUIActivityResultContract()
        ) { res ->
            this.onSignInResult(res) // Call the class's onSignInResult method
        }

        providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
        )

        signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
        //launches signup/login activity
        findViewById<Button>(R.id.MainLoginB).setOnClickListener {
            Log.d("dbfirebase", "Create Account Button Clicked")

            signInLauncher.launch(signInIntent)
            Log.d("intent started", "FirebaseUI sign-in flow initiated")
        }

    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in or created account
            val user = FirebaseAuth.getInstance().currentUser
            Log.d("dbfirebase", "Sign in successful! User: ${user?.displayName}, UID: ${user?.uid}")
            Toast.makeText(this, "Welcome, ${user?.displayName ?: "User"}!", Toast.LENGTH_SHORT).show()

            // TODO: Navigate to your main app activity here
            val intent = Intent(this, PrimaryActivity::class.java)
            startActivity(intent)
            finish()

        } else {
            // Sign in failed.
            if (response == null) {
                Log.w("dbfirebase", "Sign in canceled by user")
                Toast.makeText(this, "Sign in canceled.", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("dbfirebase", "Sign in error: ${response.error?.errorCode}", response.error)
                Toast.makeText(this, "Sign in failed: ${response.error?.message}", Toast.LENGTH_LONG).show()

            }
            // TODO: Handle sign-in failure, perhaps show a detailed message or offer retry options
        }
    }

}