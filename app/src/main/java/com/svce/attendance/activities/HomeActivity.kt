package com.svce.attendance.activities

import com.onesignal.OneSignal
import org.json.JSONObject


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.svce.attendance.services.ScanningAccessManager // <-- NEW: import the manager class
import java.io.File
import java.io.FileReader
import androidx.core.content.edit
import android.os.CountDownTimer












class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var btnAttendance: Button
    private lateinit var tvHomeRole: TextView
    private lateinit var ivProfile: ImageView

    private lateinit var attendanceCsvFile: File
    private var mentorEmail: String = "" // Changed to non-lateinit for safe checks
    private var assignedRolls: List<String> = emptyList()
    private var attendanceMatrix: List<Array<String>> = emptyList()

    // BEGIN SCAN ACCESS BANNER
    private lateinit var tvScanningStatus: TextView
    private var studentRollNumber: String = ""
    private lateinit var scanningAccessManager: ScanningAccessManager
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateScanningStatus()
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    // Helper access timer variables (NEW)
    private var helperAccessTimer: CountDownTimer? = null
    private var hasHelperAccess = false
    private lateinit var btnHelperScan: Button

    // END SCAN ACCESS BANNER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val role = intent.getStringExtra("role") ?: "Unknown"
        mentorEmail = intent.getStringExtra("email") ?: "" // May be empty for test teachers
        studentRollNumber = intent.getStringExtra("rollNumber") ?: ""
        Log.d("HomeActivity", "Loaded role: $role, mentorEmail: '$mentorEmail', rollNumber: '$studentRollNumber'")

        tvHomeRole = findViewById(R.id.tvHomeRole)
        ivProfile = findViewById(R.id.ivProfile)
        btnAttendance = findViewById(R.id.btnAttendance)
        recycler = findViewById(R.id.sessionList)

        tvHomeRole.text = getString(R.string.home_as, role)
        recycler.layoutManager = LinearLayoutManager(this)

        // --- ADD HERE: OneSignal notification handler ---
        // Inside onCreate() after OneSignal initialization:
        // After proper OneSignal initialization
        // In onCreate() or Application init
        // … inside onCreate():
        // After OneSignal.init/appId calls
        // ...in onCreate() or wherever appropriate...
        // --- Handle OneSignal notification open payload ---
      //  handleOpenedNotificationIfPresent(intent)






        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java).apply {
                putExtra("role", role)
                if (role == "teacher" && mentorEmail.isNotEmpty()) {
                    putExtra("email", mentorEmail)
                } else if (role == "student") {
                    putExtra("rollNumber", studentRollNumber)
                }
            }
            startActivity(intent)
        }

        ivProfile.setOnClickListener {
            Toast.makeText(this, "Profile: $mentorEmail", Toast.LENGTH_SHORT).show()
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
                val sharedPref = getSharedPreferences("teacher_prefs", MODE_PRIVATE)
                sharedPref.edit {
                    clear()
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

        // BEGIN SCAN ACCESS BANNER: Setup student scan status
        tvScanningStatus = findViewById(R.id.tvScanningStatus) // Add this to your XML

        scanningAccessManager = ScanningAccessManager.getInstance(this)
        if (role == "student") {
            tvScanningStatus.visibility = View.VISIBLE
            handler.post(updateRunnable)
        } else {
            tvScanningStatus.visibility = View.GONE
        }

        // Initialize helper scan button (NEW)
        btnHelperScan = findViewById(R.id.btnHelperScan) // Add this button to activity_home.xml
        btnHelperScan.visibility = View.GONE

        btnHelperScan.setOnClickListener {
            if (hasHelperAccess) {
                startHelperScanningMode()
            }
        }

        // In onCreate()
        // Initialize OneSignal






        // END SCAN ACCESS BANNER
    }

    /* override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleOpenedNotificationIfPresent(intent)
    }

    // Helper function:
    private fun handleOpenedNotificationIfPresent(intent: Intent?) {
        val onesignalData = intent?.getStringExtra("ONESIGNAL_DATA")
        Log.d("OneSignal", "Launch intent onesignal data: $onesignalData")
        if (onesignalData != null) {
            try {
                val notifyObj = JSONObject(onesignalData)
                val additionalData = notifyObj.optJSONObject("additionalData")
                val type = additionalData?.optString("type")
                val expiresAt = additionalData?.optString("expires_at")?.toLongOrNull() ?: 0L

                if (type == "helper_access") {
                    val timeLeft = expiresAt - System.currentTimeMillis()
                    if (timeLeft > 0) {
                        // Set helper flag and expiration
                        val prefs = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("is_helper_access_granted", true)
                            .putLong("helper_access_expires_at", expiresAt)
                            .apply()

                        // Broadcast to instantly notify AttendanceActivity
                        sendBroadcast(Intent("com.svce.attendance.HELPER_ACCESS_GRANTED"))

                        // (Optional: If you still want, you can launch AttendanceActivity here)
                        // val intent = Intent(this, AttendanceActivity::class.java)
                        // intent.putExtra("role", "student")
                        // intent.putExtra("rollNumber", studentRollNumber)
                        // startActivity(intent)

                        // (Optional: Any UI updates for HomeActivity)
                        runOnUiThread {
                            startHelperAccessTimer(timeLeft)
                            updateScanningStatus()
                        }
                    }
                }



            } catch (e: Exception) {
                Log.e("OneSignal", "Failed to parse notification data", e)
            }
        }
    }
*/

    // BEGIN SCAN ACCESS: Student status update logic
    private fun updateScanningStatus() {
        if (studentRollNumber.isEmpty()) return

        val activeAccess = scanningAccessManager.getActiveScanningAccess(studentRollNumber)
        Log.d("DEBUG", "Access count=${activeAccess.size}, access entries=$activeAccess")
        if (activeAccess.isNotEmpty()) {
            val access = activeAccess.first()
            val now = System.currentTimeMillis()
            val remainingMillis = (access.expires_at - now).coerceAtLeast(0L)
            val remainingMinutes = remainingMillis / 60000
            val remainingSeconds = (remainingMillis % 60000) / 1000

            tvScanningStatus.text = getString(R.string.status_scanning_active, remainingMinutes, remainingSeconds)
            tvScanningStatus.visibility = View.VISIBLE
            btnAttendance.text = "Start Scanning"
            btnAttendance.isEnabled = true

            // --- NEW: Show scan button with timer; allow scan anytime during access window
            hasHelperAccess = true
            btnHelperScan.visibility = View.VISIBLE
            btnHelperScan.isEnabled = true
            btnHelperScan.text = "Helper Scan (${remainingMinutes}:${remainingSeconds.toString().padStart(2, '0')})"
        } else {
            tvScanningStatus.text = "No active scanning sessions"
            tvScanningStatus.visibility = View.VISIBLE
            btnAttendance.text = "Give Attendance"
            btnAttendance.isEnabled = true

            hasHelperAccess = false
            btnHelperScan.visibility = View.GONE
            btnHelperScan.isEnabled = false
        }
        scanningAccessManager.cleanupExpiredSessions()
    }


    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        if (role == "teacher" && attendanceMatrix.isNotEmpty()) {
            displayAttendanceBlocks()
        } else if (role == "student") {
            updateScanningStatus()
        }
        if (hasHelperAccess && ::btnHelperScan.isInitialized) {
            btnHelperScan.visibility = View.VISIBLE
            btnHelperScan.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // BEGIN SCAN ACCESS: Remove handler updates
        handler.removeCallbacks(updateRunnable)
        // NEW: Clean up helper timer
        helperAccessTimer?.cancel()
        // END SCAN ACCESS
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
                    present += attendanceMatrix[rowIdx]
                }
            }
            sessions += SessionBlock(timestamp, present)
        }
        recycler.adapter = SessionBlockAdapter(this, sessions, mentorEmail)
        Log.d("HomeActivity", "Displayed ${sessions.size} session blocks.")
    }
    private fun startHelperAccessTimer(timeLeftMillis: Long) {
        hasHelperAccess = true
        btnHelperScan.visibility = View.VISIBLE
        btnHelperScan.isEnabled = true

        helperAccessTimer?.cancel()
        helperAccessTimer = object : CountDownTimer(timeLeftMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                btnHelperScan.text = "Helper Scan (${minutes}:${seconds.toString().padStart(2, '0')})"
            }

            override fun onFinish() {
                hasHelperAccess = false
                btnHelperScan.visibility = View.GONE
                btnHelperScan.isEnabled = false
                Toast.makeText(this@HomeActivity, "Helper access expired", Toast.LENGTH_SHORT).show()
            }
        }
        helperAccessTimer?.start()
    }

    private fun startHelperScanningMode() {
        val intent = Intent(this, AttendanceActivity::class.java).apply {
            putExtra("role", "helper")
            putExtra("rollNumber", studentRollNumber)
        }
        startActivity(intent)
    }





    data class SessionBlock(val timestamp: String, val presentRolls: List<String>)
}
