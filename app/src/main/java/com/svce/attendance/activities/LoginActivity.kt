package com.svce.attendance.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.svce.attendance.R
import java.io.InputStreamReader

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var tvRole: TextView
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView

    // SharedPreferences for persistent teacher login
    private lateinit var sharedPref: SharedPreferences
    private val PREF_NAME = "teacher_prefs"
    private val KEY_LOGGED_IN = "is_logged_in"
    private val KEY_EMAIL = "teacher_email"
    private val KEY_ROLE = "user_role"  // Optional: Save role if needed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize SharedPreferences
        sharedPref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Check if teacher is already logged in
        if (sharedPref.getBoolean(KEY_LOGGED_IN, false)) {
            val savedRole = sharedPref.getString(KEY_ROLE, "teacher") ?: "teacher"
            val savedEmail = sharedPref.getString(KEY_EMAIL, "") ?: ""
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("role", savedRole)
                putExtra("email", savedEmail)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finishAffinity()
            return  // Skip the rest of onCreate
        }

        // Determine role passed from RoleSelectionActivity
        val role = intent.getStringExtra("role") // "teacher" or "student"

        tvRole = findViewById(R.id.tvRole)
        tvRole.text = getString(R.string.login_as, role ?: "Unknown")

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etRollNumber = findViewById(R.id.etRollNumber)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)

        // Show/hide UI elements based on the role
        if (role == "student") {
            etRollNumber.visibility = android.view.View.VISIBLE
            // Optional: You could hide email/password for students if they are not needed at all
            // etEmail.visibility = android.view.View.GONE
            // etPassword.visibility = android.view.View.GONE
        } else {
            etRollNumber.visibility = android.view.View.GONE
        }

        // --- UPDATED OnClickListener ---
        btnLogin.setOnClickListener {
            if (role == "teacher") {
                // --- TEACHER LOGIN LOGIC ---
                val email = etEmail.text.toString().trim()
                val pwd = etPassword.text.toString().trim()

                // 1. Validate inputs for teacher
                if (email.isEmpty() || pwd.isEmpty()) {
                    Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 2. Authenticate teacher
                if (!authenticateTeacher(email, pwd)) {
                    Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 3. Save login state in SharedPreferences
                with(sharedPref.edit()) {
                    putBoolean(KEY_LOGGED_IN, true)
                    putString(KEY_EMAIL, email)
                    putString(KEY_ROLE, "teacher")
                    apply()  // Asynchronous save
                }

                // 4. On success, navigate
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("role", "teacher")
                    putExtra("email", email)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()

            } else {
                // --- STUDENT LOGIN LOGIC ---
                // No validation needed. Just navigate to HomeActivity.
                val rollNumber = etRollNumber.text.toString().trim() // Still capture roll if entered
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("role", "student")
                    putExtra("rollNumber", rollNumber) // Pass the roll number
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()
            }
        }

        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java).apply {
                putExtra("role", role)
            }
            startActivity(intent)
        }
    }

    /**
     * Reads mentors.json from assets and checks credentials.
     * This version includes helpful debug logging.
     */
    private fun authenticateTeacher(email: String, password: String): Boolean {
        Log.d("LoginAttempt", "Attempting to authenticate user: '$email' with password of length: ${password.length}")
        return try {
            assets.open("mentors.json").use { input ->
                val reader = InputStreamReader(input)
                val listType = object : TypeToken<List<Mentor>>() {}.type
                val mentors: List<Mentor> = Gson().fromJson(reader, listType)
                Log.d("LoginAttempt", "Successfully parsed mentors.json. Found ${mentors.size} entries.")

                // Trim password from JSON during comparison to handle hidden whitespace
                mentors.any { it.email_ID.equals(email, ignoreCase = true) && it.password.trim() == password }
            }
        } catch (e: Exception) {
            Log.e("LoginAttempt", "CRITICAL FAILURE: Could not read or parse mentors.json.", e)
            Toast.makeText(this, "Error: Could not load mentor data.", Toast.LENGTH_LONG).show()
            false
        }
    }

    data class Mentor(
        val email_ID: String,
        val password: String
    )
}
