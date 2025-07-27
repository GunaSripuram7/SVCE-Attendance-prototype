package com.svce.attendance.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.svce.attendance.R
import com.svce.attendance.model.Student
import java.io.InputStreamReader
import android.view.View


class LoginActivity : AppCompatActivity() {

    // --- Views ---
    private lateinit var etPassword: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var tvRole: TextView
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView

    private lateinit var sharedPref: SharedPreferences

    companion object {
        private const val PREF_NAME = "teacher_prefs"
        private const val KEY_LOGGED_IN = "is_logged_in"
        private const val KEY_EMAIL = "teacher_email"
        private const val KEY_ROLE = "user_role"
    }

    private lateinit var students: List<Student>

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Log.d("LOGIN_DEBUG", "LoginActivity onCreate called, role=${intent.getStringExtra("role")}")
        setContentView(R.layout.activity_login)


        sharedPref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // If teacher is already logged in, skip login
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

        // Bind views
        tvRole = findViewById(R.id.tvRole)
        etPassword = findViewById(R.id.etPassword)
        etRollNumber = findViewById(R.id.etRollNumber)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)
        val etEmail = findViewById<EditText>(R.id.etEmail)

        tvRole.text = getString(R.string.login_as, role ?: "Unknown")
        if (role == "student") {
            Log.d("LOGIN_DEBUG", "Student role detected, about to loadStudentsFromJson")
            etRollNumber.visibility = View.VISIBLE
            etEmail.visibility = View.GONE
            students = loadStudentsFromJson()
        } else { // Teacher
            etRollNumber.visibility = android.view.View.GONE
            etEmail.visibility = android.view.View.VISIBLE
        }

        btnLogin.setOnClickListener {
            if (role == "teacher") {
                handleTeacherLogin()
            } else {
                handleStudentLogin()
            }
        }

        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java).apply {
                putExtra("role", role)
            }
            startActivity(intent)
        }
    }

    private fun handleTeacherLogin() {
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val email = etEmail.text.toString().trim()
        val pwd = etPassword.text.toString().trim()

        if (email.isEmpty() || pwd.isEmpty()) {
            Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!authenticateTeacher(email, pwd)) {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPref.edit {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_EMAIL, email)
            putString(KEY_ROLE, "teacher")
        }

        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, HomeActivity::class.java).apply {
            putExtra("role", "teacher")
            putExtra("email", email)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    private fun handleStudentLogin() {
        val rollInput = etRollNumber.text.toString().trim()
        val passInput = etPassword.text.toString().trim()

        if (rollInput.isEmpty() || passInput.isEmpty()) {
            Toast.makeText(this, "Roll number and password required", Toast.LENGTH_SHORT).show()
            return
        }

        val student = students.find {
            it.rollNumber.equals(rollInput, ignoreCase = true) && it.password == passInput
        }

        if (student == null) {
            Toast.makeText(this, "Invalid roll number or password", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Welcome! BLE Code: ${student.bleCode}", Toast.LENGTH_LONG).show()
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("role", "student")
                putExtra("ROLL_NUMBER", student.rollNumber)
                putExtra("BLE_CODE", student.bleCode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finishAffinity()
        }
    }

    private fun authenticateTeacher(email: String, password: String): Boolean {
        Log.d("LoginAttempt", "Attempting to authenticate teacher: '$email'")
        return try {
            assets.open("mentors.json").use { input ->
                val reader = InputStreamReader(input)
                val listType = object : TypeToken<List<Mentor>>() {}.type
                val mentors: List<Mentor> = Gson().fromJson(reader, listType)
                mentors.any {
                    it.emailId.equals(email, ignoreCase = true) && it.password.trim() == password
                }
            }
        } catch (e: Exception) {
            Log.e("LoginAttempt", "Failed to load mentors.json", e)
            Toast.makeText(this, "Error loading mentor data", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun loadStudentsFromJson(): List<Student> {
        Log.d("LOGIN_DEBUG", "loadStudentsFromJson CALLED")
        val teacherEmail = intent.getStringExtra("email")
            ?: getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_EMAIL, null)

        Log.d("LOGIN_DEBUG", "teacherEmail resolved as $teacherEmail")

        if (teacherEmail == null) return emptyList()
        val assetName = "teacher_credentials/$teacherEmail.json"

        return try {
            assets.open(assetName).use { input ->
                val reader = InputStreamReader(input)
                val type = object : TypeToken<List<Student>>(){}.type
                val list = Gson().fromJson<List<Student>>(reader, type)
                Log.d("LOGIN_DEBUG", "Read ${list.size} students from $assetName")
                list
            }
        } catch(e: Exception) {
            Log.e("Login", "Error loading $assetName", e)
            Toast.makeText(this,"Error loading student data", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }



    // Mentor data class for teacher authentication
    data class Mentor(
        @SerializedName("email_ID")
        val emailId: String,
        val password: String
    )
}
