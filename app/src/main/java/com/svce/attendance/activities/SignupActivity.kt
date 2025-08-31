package com.svce.attendance.activities

import com.svce.attendance.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.svce.attendance.services.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.text.InputType
import android.widget.ImageButton



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

        val etEmail = findViewById<EditText>(R.id.etSignupEmail)
        val etPassword = findViewById<EditText>(R.id.etSignupPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etSignupConfirmPassword)
        val btnToggleSignupPassword = findViewById<ImageButton>(R.id.btnToggleSignupPassword)
        val btnToggleConfirmPassword = findViewById<ImageButton>(R.id.btnToggleConfirmPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        // Toggle for main password
        var isSignupPasswordVisible = false
        btnToggleSignupPassword.setOnClickListener {
            isSignupPasswordVisible = !isSignupPasswordVisible
            etPassword.inputType = if (isSignupPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnToggleSignupPassword.setImageResource(
                if (isSignupPasswordVisible) R.drawable.ic_visibility_on else R.drawable.ic_visibility_off
            )
            etPassword.setSelection(etPassword.text.length)
        }

// Toggle for confirm password
        var isConfirmPasswordVisible = false
        btnToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            etConfirmPassword.inputType = if (isConfirmPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnToggleConfirmPassword.setImageResource(
                if (isConfirmPasswordVisible) R.drawable.ic_visibility_on else R.drawable.ic_visibility_off
            )
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }


        btnSignup.setOnClickListener {


            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val rollNumber = etRollNumber.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "Password is required"
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }
            if (confirmPassword != password) {
                etConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }
            if (role == "student" && rollNumber.isEmpty()) {
                etRollNumber.error = "Roll number is required for students"
                return@setOnClickListener
            }

            btnSignup.isEnabled = false
            btnSignup.text = "Creating Account..."

            lifecycleScope.launch {
                try {
                    SupabaseConfig.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        data = buildJsonObject {
                            put("role", (role ?: "unknown").trim().lowercase())

                            if (role == "student") put("roll_number", rollNumber)
                        }

                    }
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "Account created. Please check your email for verification.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@SignupActivity, LoginActivity::class.java).apply {
                            putExtra("role", role)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finishAffinity()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
                        btnSignup.isEnabled = true
                        btnSignup.text = "Sign Up"
                    }
                }
            }
        }

    }
}
