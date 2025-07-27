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

    // --- Data Properties ---
    // For teacher
    private lateinit var attendanceCsvFile: File
    private lateinit var mentorEmail: String
    private lateinit var assignedRolls: List<String>
    private lateinit var attendanceMatrix: List<Array<String>>

    // For student (to hold data from LoginActivity)
    private var studentRollNumber: String? = null
    private var studentBleCode: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // --- 1. Read All Intent Data ---
        val role = intent.getStringExtra("role") ?: "Unknown"
        mentorEmail = intent.getStringExtra("email") ?: ""
        // Receive and store student-specific data with exact key names
        studentRollNumber = intent.getStringExtra("ROLL_NUMBER")
        studentBleCode = intent.getIntExtra("BLE_CODE", -1)

        Log.d("HomeActivity", "Loaded role: $role")
        if (role == "teacher") Log.d("HomeActivity", "Teacher email: $mentorEmail")
        if (role == "student") Log.d("HomeActivity", "Student Roll: $studentRollNumber, BLE Code: $studentBleCode")

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
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("role", role)
            // Only pass the email if it's a teacher
            if (role == "teacher") {
                intent.putExtra("email", mentorEmail)
            } else if (role == "student") {
                intent.putExtra("ROLL_NUMBER", studentRollNumber)
                intent.putExtra("BLE_CODE", studentBleCode)
            }

            // --- Debug Logging (for your troubleshooting) ---
            Log.d(
                "ATTEND_DEBUG",
                "Launching AttendanceActivity with ROLL_NUMBER=$studentRollNumber, BLE_CODE=$studentBleCode"
            )

            startActivity(intent)
        }

        ivProfile.setOnClickListener {
            Toast.makeText(this, "Profile: $mentorEmail", Toast.LENGTH_SHORT).show()
        }

        // --- 4. Role-Specific Logic (Unchanged) ---
        if (role == "teacher") {
            recycler.visibility = View.VISIBLE
            if (mentorEmail.isEmpty()) {
                Toast.makeText(this, "Error: Could not load teacher data.", Toast.LENGTH_LONG).show()
                btnAttendance.isEnabled = false
                return
            }
            prepareCsvFiles()
            loadAttendanceCsv()
            findViewById<Button>(R.id.btnLogout).setOnClickListener {
                val sharedPref = getSharedPreferences("teacher_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    clear()
                    apply()
                }
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
        } else {
            recycler.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        if (role == "teacher" && ::attendanceCsvFile.isInitialized) {
            displayAttendanceBlocks()
        }
    }

    // --- All teacher-specific CSV functions remain unchanged ---

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
        if (!::attendanceMatrix.isInitialized || attendanceMatrix.isEmpty()) return
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
