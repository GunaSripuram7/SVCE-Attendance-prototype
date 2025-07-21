package com.svce.attendance.activities

import com.svce.attendance.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val role = intent.getStringExtra("role") // "teacher" or "student"

        val tvSignupRole = findViewById<TextView>(R.id.tvSignupRole)
        tvSignupRole.text = getString(R.string.signup_as, role ?: "Unknown")

        val etRollNumber = findViewById<EditText>(R.id.etSignupRollNumber)
        if (role == "student") {
            etRollNumber.visibility = android.view.View.VISIBLE
        } else {
            etRollNumber.visibility = android.view.View.GONE
        }

        val btnSignup = findViewById<Button>(R.id.btnSignup)
        btnSignup.setOnClickListener {
            // For now, show a toast (replace with real registration logic later)
            Toast.makeText(this, "Sign up clicked for $role", Toast.LENGTH_SHORT).show()

            // Go to HomeActivity after sign up
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
            finish() // Optional: remove SignupActivity from back stack
        }
    }
}
