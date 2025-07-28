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

class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var btnAttendance: Button
    private lateinit var tvHomeRole: TextView
    private lateinit var ivProfile: ImageView

    private lateinit var attendanceCsvFile: File
    private lateinit var mentorEmail: String
    private lateinit var assignedRolls: List<String>
    private lateinit var attendanceMatrix: List<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val role = intent.getStringExtra("role") ?: "Unknown"
        mentorEmail = intent.getStringExtra("email") ?: ""
        Log.d("HomeActivity", "Loaded role: $role, mentorEmail: '$mentorEmail'")

        tvHomeRole = findViewById(R.id.tvHomeRole)
        ivProfile = findViewById(R.id.ivProfile)
        btnAttendance = findViewById(R.id.btnAttendance)
        recycler = findViewById(R.id.sessionList)

        tvHomeRole.text = getString(R.string.home_as, role)
        recycler.layoutManager = LinearLayoutManager(this)

        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java).apply {
                putExtra("role", role)
                if (role == "teacher") {
                    putExtra("email", mentorEmail)
                }
            }
            startActivity(intent)
        }

        ivProfile.setOnClickListener {
            Toast.makeText(this, "Profile: $mentorEmail", Toast.LENGTH_SHORT).show()
        }

        if (role == "teacher") {
            recycler.visibility = View.VISIBLE
            // Removed mentorEmail check to allow teacher access without login details

            prepareCsvFiles()
            loadAttendanceCsv()

            findViewById<Button>(R.id.btnLogout).setOnClickListener {
                val sharedPref = getSharedPreferences("teacher_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    clear()
                    apply()
                }

                val logoutIntent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(logoutIntent)
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
