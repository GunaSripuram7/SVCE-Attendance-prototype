package com.svce.attendance.activities

import android.content.Intent
import android.content.SharedPreferences
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
import com.opencsv.CSVWriter
import com.svce.attendance.R
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var recycler: RecyclerView
    private lateinit var btnAttendance: Button
    private lateinit var tvHomeRole: TextView
    private lateinit var ivProfile: ImageView

    // Data Properties (mostly for teachers)
    private lateinit var attendanceCsvFile: File
    private lateinit var mentorEmail: String
    private lateinit var assignedRolls: List<String>
    private lateinit var attendanceMatrix: List<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // --- 1. Read Intent Data ---
        val role = intent.getStringExtra("role") ?: "Unknown"
        mentorEmail = intent.getStringExtra("email") ?: ""
        Log.d("HomeActivity", "Loaded role: $role, mentorEmail: '$mentorEmail'")

        // --- 2. Initialize UI Views ---
        tvHomeRole = findViewById(R.id.tvHomeRole)
        ivProfile = findViewById(R.id.ivProfile)
        btnAttendance = findViewById(R.id.btnAttendance)
        recycler = findViewById(R.id.sessionList)

        tvHomeRole.text = getString(R.string.home_as, role)
        recycler.layoutManager = LinearLayoutManager(this)

        // --- 3. Set Up Click Listeners ---
        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java).apply {
                putExtra("role", role)
                // Only pass the email if it's a teacher
                if (role == "teacher") {
                    putExtra("email", mentorEmail)
                }
                // Pass roll number for student if available from LoginActivity
                if (role == "student") {
                    putExtra("rollNumber", getIntent().getStringExtra("rollNumber"))
                }
            }
            startActivity(intent)
        }

        ivProfile.setOnClickListener {
            Toast.makeText(this, "Profile: $mentorEmail", Toast.LENGTH_SHORT).show()
        }

        // --- 4. Role-Specific Logic (CRITICAL FIX) ---
        if (role == "teacher") {
            // This block only runs for teachers, preventing crashes for students
            recycler.visibility = View.VISIBLE

            // Safety check: ensure mentor email was passed correctly
            if (mentorEmail.isEmpty()) {
                Toast.makeText(this, "Error: Could not load teacher data.", Toast.LENGTH_LONG).show()
                btnAttendance.isEnabled = false // Disable features if data is missing
                return
            }

            // Prepare and load the teacher's specific CSV file
            prepareCsvFiles()
            loadAttendanceCsv()

            // Add Logout button (assume you have a Button with id btnLogout in activity_home.xml)
            findViewById<Button>(R.id.btnLogout).setOnClickListener {
                // Clear SharedPreferences
                val sharedPref = getSharedPreferences("teacher_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    clear()  // Removes all saved data
                    apply()
                }

                // Redirect to LoginActivity
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }

        } else {
            // Student-specific setup: hide the session list
            recycler.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        // Refresh the list only if it's a teacher and their data has been successfully initialized
        if (role == "teacher" && ::attendanceCsvFile.isInitialized) {
            displayAttendanceBlocks()
        }
    }

    /** Ensures a working copy of mentor's CSV is present in private storage. */
    private fun prepareCsvFiles() {
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

    /** Loads the assigned rolls and attendance block matrix from the mentor CSV. */
    private fun loadAttendanceCsv() {
        // Safety check in case prepareCsvFiles failed
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
                // Safely map roll numbers, handling potential nulls
                assignedRolls = all.drop(1).mapNotNull { it.getOrNull(0) }
                attendanceMatrix = all
                Log.d("HomeActivity", "Loaded ${assignedRolls.size} rolls from ${attendanceCsvFile.name}")
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to read CSV for $mentorEmail", e)
        }
    }

    /** Extracts block sessions by column from attendanceMatrix and supplies them to the adapter. */
    private fun displayAttendanceBlocks() {
        // First, reload data from the CSV file to get the latest state
        loadAttendanceCsv()

        // Don't proceed if the CSV data couldn't be loaded
        if (!::attendanceMatrix.isInitialized || attendanceMatrix.isEmpty()) return

        val sessions = mutableListOf<SessionBlock>()
        val header = attendanceMatrix[0]
        // Loop through each attendance column (starting from the second column)
        for (colIdx in 1 until header.size) {
            val timestamp = header[colIdx]
            val present = mutableListOf<String>()
            // Check each student's status for that column
            for (rowIdx in 1 until attendanceMatrix.size) {
                // Safely get the status, check if it's "P"
                if (attendanceMatrix[rowIdx].getOrNull(colIdx) == "P") {
                    present += attendanceMatrix[rowIdx][0] // Add the roll number
                }
            }
            sessions += SessionBlock(timestamp, present)
        }

        recycler.adapter = SessionBlockAdapter(
            this,      // Context
            sessions,  // List<SessionBlock>
            mentorEmail  // String (mentorEmail)
        )
        Log.d("HomeActivity", "Displayed ${sessions.size} session blocks.")
    }

    /** Data class for one session block. */
    data class SessionBlock(val timestamp: String, val presentRolls: List<String>)
}
