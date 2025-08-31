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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.svce.attendance.services.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import com.svce.attendance.R
import android.text.InputType
import android.widget.ImageButton

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var tvRole: TextView
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView

    // SharedPreferences for persistent teacher login
    private lateinit var sharedPref: SharedPreferences
    private val PREF_NAME = "user_prefs"
    private val KEY_LOGGED_IN = "is_logged_in"
    private val KEY_EMAIL = "user_email"
    private val KEY_ROLE = "user_role"
    private val KEY_USER_ID = "user_id"
    private val KEY_ROLL_NUMBER = "roll_number"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sharedPref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

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
            return
        }

        val role = intent.getStringExtra("role")

        tvRole = findViewById(R.id.tvRole)
        tvRole.text = getString(R.string.login_as, role ?: "Unknown")

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        // ── NEW: Password show/hide toggle ──
        val btnTogglePassword = findViewById<ImageButton>(R.id.btnTogglePassword)
        var isPasswordVisible = false
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_visibility_on else R.drawable.ic_visibility_off
            )
            etPassword.setSelection(etPassword.text.length)
        }

        etRollNumber = findViewById(R.id.etRollNumber)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)

        if (role == "student") {
            etRollNumber.visibility = android.view.View.VISIBLE
        } else {
            etRollNumber.visibility = android.view.View.GONE
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val rollNumber = etRollNumber.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "Password is required"
                return@setOnClickListener
            }
            if (role == "student" && rollNumber.isEmpty()) {
                etRollNumber.error = "Roll number is required for students"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            lifecycleScope.launch {
                try {
                    SupabaseConfig.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    val session = SupabaseConfig.client.auth.currentSessionOrNull()
                    if (session != null) {

                        val user = SupabaseConfig.client.auth.retrieveUser(session.accessToken)

                        // ── NEW: Prevent cross-role login ──
                        // --- BEGIN Updated Role Check Block ---
                        val userRole = user.userMetadata?.get("role")?.toString()?.trim('"')?.trim()?.lowercase()
                        val expectedRole = role?.trim()?.lowercase()


                        Log.d("ROLECHECK", "userRole: '$userRole' from metadata, expectedRole: '$expectedRole'")

                        if (userRole != expectedRole) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Access denied: you signed up as $userRole, not $expectedRole",
                                    Toast.LENGTH_LONG
                                ).show()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Login"
                            }
                            SupabaseConfig.client.auth.signOut()
                            return@launch
                        }
// --- END Updated Role Check Block ---

                        with(sharedPref.edit()) {
                            putBoolean(KEY_LOGGED_IN, true)
                            putString(KEY_EMAIL, email)
                            putString(KEY_ROLE, role)
                            putString(KEY_USER_ID, user.id)
                            if (role == "student") {
                                putString(KEY_ROLL_NUMBER, rollNumber)
                            }
                            apply()
                        }

                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply {
                                putExtra("role", role)
                                putExtra("email", email)
                                putExtra("rollNumber", rollNumber)
                                putExtra("userId", user.id)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finishAffinity()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"
                    }
                }
            }

        }

        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java).apply {
                putExtra("role", role)
            }
            startActivity(intent)
        }
    }
}
