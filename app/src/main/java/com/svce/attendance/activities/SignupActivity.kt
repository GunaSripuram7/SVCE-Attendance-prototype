package com.svce.attendance.activities

import com.svce.attendance.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.svce.attendance.models.User

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.util.Log

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val role = intent.getStringExtra("role") // "teacher" or "student"
        // Initialize Firebase
        auth = Firebase.auth
        db   = Firebase.firestore


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
            createAccount(role)
        }


    }

    private fun createAccount(role: String?) {
        val email = findViewById<EditText>(R.id.etSignupEmail).text.toString().trim()
        val password = findViewById<EditText>(R.id.etSignupPassword).text.toString().trim()
        val confirm = findViewById<EditText>(R.id.etSignupConfirmPassword).text.toString().trim()
        val name = findViewById<EditText>(R.id.etSignupName).text.toString().trim()
        val roll = findViewById<EditText>(R.id.etSignupRollNumber).text.toString().trim()

        // Validation
        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (role == "student" && roll.isEmpty()) {
            Toast.makeText(this, "Roll number is required for students", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirm) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSignup = findViewById<Button>(R.id.btnSignup)
        btnSignup.isEnabled = false

        // For students, check if roll number already exists
        if (role == "student") {
            checkRollNumberExists(roll) { exists ->
                if (exists) {
                    btnSignup.isEnabled = true
                    Toast.makeText(this, "Roll number already exists", Toast.LENGTH_SHORT).show()
                } else {
                    createFirebaseAccount(email, password, name, roll, role)
                }
            }
        } else {
            createFirebaseAccount(email, password, name, "", role)
        }
    }

    private fun checkRollNumberExists(rollNumber: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo("rollNumber", rollNumber)
            .get()
            .addOnSuccessListener { documents ->
                callback(!documents.isEmpty)
            }
            .addOnFailureListener {
                callback(false) // Allow creation if check fails
            }
    }

    private fun createFirebaseAccount(email: String, password: String, name: String, rollNumber: String, role: String?) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser!!
                    val user = User(
                        uid = firebaseUser.uid,
                        email = email,
                        role = role ?: "student",
                        rollNumber = rollNumber,
                        name = name
                    )

                    saveUserToFirestore(user)
                } else {
                    val btnSignup = findViewById<Button>(R.id.btnSignup)
                    btnSignup.isEnabled = true
                    Log.w("SignupActivity", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(this, "Account creation failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(user: User) {
        db.collection("users").document(user.uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Signed up! Please login", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java)
                    .putExtra("role", user.role)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
            .addOnFailureListener {
                auth.currentUser?.delete()
                Toast.makeText(this, "Error saving user", Toast.LENGTH_SHORT).show()
                val btnSignup = findViewById<Button>(R.id.btnSignup)
                btnSignup.isEnabled = true
            }
    }

}
