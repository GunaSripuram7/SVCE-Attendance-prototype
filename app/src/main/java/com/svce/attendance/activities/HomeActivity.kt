package com.svce.attendance.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opencsv.CSVReader
import com.svce.attendance.R
import java.io.File
import java.io.FileReader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var btnAttendance: Button
    private lateinit var tvHomeRole: TextView
    private lateinit var ivProfile: ImageView

    private lateinit var attendanceCsvFile: File
    private var mentorEmail: String = ""   // Changed to non-lateinit for safe checks
    private var assignedRolls: List<String> = emptyList()
    private var attendanceMatrix: List<Array<String>> = emptyList()

    private lateinit var auth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialize Firebase Auth
        auth = Firebase.auth


        val role = intent.getStringExtra("role") ?: "Unknown"
        mentorEmail = intent.getStringExtra("email") ?: ""  // May be empty for test teachers
        Log.d("HomeActivity", "Loaded role: $role, mentorEmail: '$mentorEmail'")

        tvHomeRole = findViewById(R.id.tvHomeRole)
        ivProfile = findViewById(R.id.ivProfile)
        btnAttendance = findViewById(R.id.btnAttendance)
        recycler = findViewById(R.id.sessionList)
        val btnLogout = findViewById<Button>(R.id.btnLogout)


        if (role == "student") {
            val rollNumber = intent.getStringExtra("rollNumber") ?: "N/A"
            tvHomeRole.text = "Student: $rollNumber"
        } else {
            tvHomeRole.text = getString(R.string.home_as, role)
        }

        // Load persisted user data in case Intent extras are missing
        loadUserDataFromPreferences()

        recycler.layoutManager = LinearLayoutManager(this)

        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java).apply {
                putExtra("role", role)
                if (role == "teacher" && mentorEmail.isNotEmpty()) {
                    putExtra("email", mentorEmail)
                } else if (role == "student") {
                    // Try Intent first, then SharedPreferences as fallback
                    val rollNumber = this@HomeActivity.intent.getStringExtra("rollNumber")
                        ?: getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_roll", "")
                        ?: ""
                    putExtra("rollNumber", rollNumber)
                }
            }
            startActivity(intent)
        }




        ivProfile.setOnClickListener {
            val role = this@HomeActivity.intent.getStringExtra("role") ?: "Unknown"
            if (role == "student") {
                // Try Intent first, then SharedPreferences as fallback
                val rollNumber = this@HomeActivity.intent.getStringExtra("rollNumber")
                    ?: getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_roll", "")
                    ?: "N/A"
                Toast.makeText(this, "Student: $rollNumber, Email: $mentorEmail", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Profile: $mentorEmail", Toast.LENGTH_SHORT).show()
            }
        }




        btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    // Sign out
                    auth.signOut()
                    // Clear preferences
                    getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
                    // Navigate back
                    startActivity(Intent(this, RoleSelectionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }


        if (role == "teacher") {
            recycler.visibility = View.VISIBLE

            // Only attempt CSV load if mentorEmail is provided
            if (mentorEmail.isNotEmpty()) {
                prepareCsvFiles()
                loadAttendanceCsv()
            } else {
                Log.w("HomeActivity", "Teacher mentorEmail is empty, skipping CSV load")
            }

            findViewById<Button>(R.id.btnLogout).setOnClickListener {
                auth.signOut()
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()

                startActivity(Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finishAffinity()
            }

        } else {
            recycler.visibility = View.GONE
        }
    }

    private fun loadUserDataFromPreferences() {
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val savedRole = sharedPref.getString("user_role", "") ?: ""
        val savedEmail = sharedPref.getString("user_email", "") ?: ""
        val savedRoll = sharedPref.getString("user_roll", "") ?: ""

        if (savedRole == "student" && savedRoll.isNotEmpty()) {
            tvHomeRole.text = "Student: $savedRoll"
        }
    }


    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        if (role == "teacher" && attendanceMatrix.isNotEmpty()) {
            displayAttendanceBlocks()
        }
    }

    private fun prepareCsvFiles() {
        if (mentorEmail.isEmpty()) return

        val assetPath = "rolls/$mentorEmail.csv"
        attendanceCsvFile = File(filesDir, "rolls/$mentorEmail.csv")
        try {
            if (!attendanceCsvFile.exists()) {
                attendanceCsvFile.parentFile?.mkdirs()
                assets.open(assetPath).use { inp ->
                    attendanceCsvFile.outputStream().use { out -> inp.copyTo(out) }
                }
                Log.d("HomeActivity", "Copied asset $assetPath to ${attendanceCsvFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to copy CSV for '$mentorEmail'", e)
            Toast.makeText(this, "Error: Data file not found for this user.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAttendanceCsv() {
        if (!::attendanceCsvFile.isInitialized || !attendanceCsvFile.exists()) return

        try {
            CSVReader(FileReader(attendanceCsvFile)).use { reader ->
                val all = reader.readAll()
                if (all.isEmpty()) {
                    Log.w("HomeActivity", "CSV for $mentorEmail is empty!")
                    attendanceMatrix = emptyList()
                    assignedRolls = emptyList()
                    return
                }
                assignedRolls = all.drop(1).mapNotNull { it.getOrNull(0) }
                attendanceMatrix = all
                Log.d("HomeActivity", "Loaded ${assignedRolls.size} rolls from ${attendanceCsvFile.name}")
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to read CSV for $mentorEmail", e)
        }
    }

    private fun displayAttendanceBlocks() {
        loadAttendanceCsv()
        if (attendanceMatrix.isEmpty()) return

        val sessions = mutableListOf<SessionBlock>()
        val header = attendanceMatrix[0]
        for (colIdx in 1 until header.size) {
            val timestamp = header[colIdx]
            val present = mutableListOf<String>()
            for (rowIdx in 1 until attendanceMatrix.size) {
                if (attendanceMatrix[rowIdx].getOrNull(colIdx) == "P") {
                    present += attendanceMatrix[rowIdx][0]
                }
            }
            sessions += SessionBlock(timestamp, present)
        }

        recycler.adapter = SessionBlockAdapter(this, sessions, mentorEmail)
        Log.d("HomeActivity", "Displayed ${sessions.size} session blocks.")
    }

    data class SessionBlock(val timestamp: String, val presentRolls: List<String>)
}
