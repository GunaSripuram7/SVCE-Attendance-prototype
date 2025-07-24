package com.svce.attendance.activities

import com.svce.attendance.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import android.content.Intent

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val role = intent.getStringExtra("role") // "teacher" or "student"

        val tvRole = findViewById<TextView>(R.id.tvRole)
        tvRole.text = getString(R.string.login_as, role ?: "Unknown")

        val etRollNumber = findViewById<EditText>(R.id.etRollNumber)
        if (role == "student") {
            etRollNumber.visibility = android.view.View.VISIBLE
        } else {
            etRollNumber.visibility = android.view.View.GONE
        }

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener {
            // For now, show a toast (replace with real authentication later)
            Toast.makeText(this, "Login clicked for $role", Toast.LENGTH_SHORT).show()

            // Go to HomeActivity after login, clearing back stack so user can't return to Login or RoleSelection
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("role", role)
            // Use intent flags AND finishAffinity for best navigation hygiene
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity() // This finishes the current and all parent activities (including Role Selection)
        }

        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
        }
    }
}
