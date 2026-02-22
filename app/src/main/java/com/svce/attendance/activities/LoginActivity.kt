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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.svce.attendance.models.User


class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    private lateinit var tvRole: TextView
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView

    // SharedPreferences for persistent teacher login
    private lateinit var sharedPref: SharedPreferences
    private val PREF_NAME = "user_prefs"
    private val KEY_LOGGED_IN = "is_logged_in"
    private val KEY_EMAIL = "user_email"
    private val KEY_ROLL = "user_roll"
    private val KEY_ROLE = "user_role"  // Optional: Save role if needed

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = Firebase.auth
        db   = Firebase.firestore


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

        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)



        if (role == "student") {
            etEmail.hint = "Email or Roll Number"
        } else {
            etEmail.hint = "Email"
        }

        btnLogin.setOnClickListener {
            loginUser(role)
        }


        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java).apply {
                putExtra("role", role)
            }
            startActivity(intent)
        }
    }

    private fun loginUser(role: String?) {
        val emailOrRoll = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (emailOrRoll.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false

        if (role == "student") {
            // For students: check if input is email or roll number
            if (emailOrRoll.contains("@")) {    
                // It's an email
                loginWithEmail(emailOrRoll, password, role)
            } else {
                // It's a roll number, need to find the email first
                findEmailByRollNumber(emailOrRoll, password, role)
            }
        } else {
            // For teachers: only email login
            loginWithEmail(emailOrRoll, password, role)
        }
    }

    private fun findEmailByRollNumber(rollNumber: String, password: String, role: String?) {
        db.collection("users")
            .whereEqualTo("rollNumber", rollNumber)
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("LoginActivity", "Query for roll[$rollNumber] returned ${documents.size()} docs")
                if (documents.isEmpty) {
                    btnLogin.isEnabled = true
                    Toast.makeText(this, "Roll number not found", Toast.LENGTH_SHORT).show()
                } else {
                    val user = documents.first().toObject(User::class.java)
                    loginWithEmail(user.email, password, role)
                }
            }
            .addOnFailureListener { exception ->
                btnLogin.isEnabled = true
                Log.w("LoginActivity", "Error finding user by roll number", exception)
                Toast.makeText(this, "Error finding user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loginWithEmail(email: String, password: String, expectedRole: String?) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        verifyUserRole(user.uid, expectedRole)
                    }
                } else {
                    btnLogin.isEnabled = true
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun verifyUserRole(uid: String, expectedRole: String?) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                btnLogin.isEnabled = true
                if (document.exists()) {
                    val userData = document.toObject(User::class.java) ?: return@addOnSuccessListener

                    if (userData.role == expectedRole) {
                        // Correct role - save login state and proceed
                        saveLoginState(userData)
                        navigateToHome()
                    } else {
                        // Wrong role - show error and sign out
                        Toast.makeText(this,
                            "This account is registered as ${userData.role}. Please use the correct login option.",
                            Toast.LENGTH_LONG).show()
                        auth.signOut()
                    }
                } else {
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    auth.signOut()
                }
            }
            .addOnFailureListener { exception ->
                btnLogin.isEnabled = true
                Log.w("LoginActivity", "Error getting user document", exception)
                Toast.makeText(this, "Error verifying user", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun saveLoginState(userData: User) {
        with(sharedPref.edit()) {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_EMAIL, userData.email)
            putString(KEY_ROLE, userData.role)
            if (userData.role == "student") {
                putString(KEY_ROLL, userData.rollNumber)
            }
            apply()
        }
    }

    private fun navigateToHome() {
        val savedRole = sharedPref.getString(KEY_ROLE, "teacher") ?: "teacher"
        val savedEmail = sharedPref.getString(KEY_EMAIL, "") ?: ""
        val rollNumber = sharedPref.getString(KEY_ROLL, "") ?: ""

        val intent = Intent(this, HomeActivity::class.java).apply {
            putExtra("role", savedRole)
            putExtra("email", savedEmail)
            if (savedRole == "student") {
                putExtra("rollNumber", rollNumber)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }


    // Removed authenticateTeacher and Mentor class since they are not used anymore for teacher login bypass
}
