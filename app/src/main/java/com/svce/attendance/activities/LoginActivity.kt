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
        etRollNumber = findViewById(R.id.etRollNumber)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)

        if (role == "student") {
            etRollNumber.visibility = android.view.View.VISIBLE
        } else {
            etRollNumber.visibility = android.view.View.GONE
        }

        btnLogin.setOnClickListener {
            if (role == "teacher") {
                // Bypass all validation and authentication for teachers
                val dummyEmail = etEmail.text.toString().trim().ifEmpty { "teacher@test.com" }
                with(sharedPref.edit()) {
                    putBoolean(KEY_LOGGED_IN, true)
                    putString(KEY_EMAIL, dummyEmail)
                    putString(KEY_ROLE, "teacher")
                    apply()
                }
                Toast.makeText(this, "Teacher logged in (no validation)", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("role", "teacher")
                    putExtra("email", dummyEmail)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()
            } else {
                // Student login: keep existing logic (no validation)
                val rollNumber = etRollNumber.text.toString().trim() // optional
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("role", "student")
                    putExtra("rollNumber", rollNumber)
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

    // Removed authenticateTeacher and Mentor class since they are not used anymore for teacher login bypass
}
