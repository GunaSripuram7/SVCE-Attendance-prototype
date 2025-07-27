package com.svce.attendance.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.svce.attendance.R
import com.svce.attendance.ble.BleAdvertiserHelper
import com.svce.attendance.ble.BleScannerHelper
import com.svce.attendance.model.Student
import com.svce.attendance.utils.AttendanceSession
import com.svce.attendance.utils.SessionStore
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var bleAdvertiserHelper: BleAdvertiserHelper? = null
    private var bleScannerHelper: BleScannerHelper? = null

    private var studentBleCode: Int = -1
    private var studentRollNumber: String? = null
    private val bleCodeToRollNumberMap = mutableMapOf<Int, String>()
    private lateinit var assignedRolls: Set<String>

    private val liveRollNumbers = mutableSetOf<String>()
    private val finalRollNumbers = mutableSetOf<String>()
    private var recordFinalList = false
    private val confirmations = mutableMapOf<String, Boolean>()

    private var rollAdapter: ArrayAdapter<String>? = null
    private lateinit var btnStop: Button
    private lateinit var btnStart: Button
    private lateinit var tvGrace: TextView
    private lateinit var tvBroadcastCountdown: TextView

    private var broadcastTimer: CountDownTimer? = null
    private var advertiseIndex = 0
    private val advertiseHandler = Handler(Looper.getMainLooper())
    private val advertiseInterval = 300L

    private val advertiseRunnable = object : Runnable {
        override fun run() {
            if (finalRollNumbers.isEmpty()) return
            val rollList = finalRollNumbers.toList()
            val currentRoll = rollList[advertiseIndex % rollList.size]
            startAdvertisingRollNumber(currentRoll)
            advertiseIndex++
            advertiseHandler.postDelayed(this, advertiseInterval)
        }
    }

    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    private val bleRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        if (!checkAndRequestBLEPermissions()) return

        val role = intent.getStringExtra("role") ?: "Unknown"
        val tvAttendanceRole = findViewById<TextView>(R.id.tvAttendanceRole)
        tvAttendanceRole.text = getString(R.string.attendance_as, role)

        // --- Teacher setup: assign rolls/csv and map codes using the per-teacher JSON ---
        if (role == "teacher") {
            loadAssignedRollsCsv()
            val mentorEmail = intent.getStringExtra("email")
            if (mentorEmail.isNullOrEmpty()) {
                Toast.makeText(this, "No teacher email provided!", Toast.LENGTH_LONG).show()
                return
            }
            loadStudentCredentialsForTeacher(mentorEmail)
        }

        // --- Student setup ---
        if (role == "student") {
            studentBleCode = intent.getIntExtra("BLE_CODE", -1)
            studentRollNumber = intent.getStringExtra("ROLL_NUMBER")
            Log.d("ATTEND_DEBUG", "Student BLE_CODE=$studentBleCode, ROLL=$studentRollNumber")
            if (studentBleCode == -1 || studentRollNumber.isNullOrEmpty()) {
                Toast.makeText(this, "Login error: Could not get student details.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvGrace = findViewById(R.id.tvGrace)
        tvBroadcastCountdown = findViewById(R.id.tvBroadcastCountdown)
        tvGrace.text = ""
        tvBroadcastCountdown.visibility = View.GONE

        findViewById<Button>(R.id.btnPing)?.visibility = View.GONE
        findViewById<Button>(R.id.btnConfirm)?.visibility = View.GONE

        val listRolls = findViewById<ListView>(R.id.listRolls)
        rollAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listRolls.adapter = rollAdapter

        btnStart.setOnClickListener {
            if (!checkAndRequestBLEPermissions()) return@setOnClickListener
            if (role == "teacher") {
                Toast.makeText(this, "Attendance started (teacher)", Toast.LENGTH_SHORT).show()
                if (bleScannerHelper == null) bleScannerHelper = BleScannerHelper(this, serviceUuid)
                liveRollNumbers.clear()
                finalRollNumbers.clear()
                recordFinalList = false
                confirmations.clear()
                Log.d("ATTEND_DEBUG","Teacher assignedRolls=$assignedRolls, codeMapSize=${bleCodeToRollNumberMap.size}")

                bleScannerHelper?.startScan(
                    onDeviceFound = { bleCode ->
                        val rollNumber = bleCodeToRollNumberMap[bleCode]
                        Log.d("ATTEND_DEBUG","Teacher received code $bleCode → $rollNumber")
                        if (rollNumber != null && assignedRolls.contains(rollNumber)) {
                            runOnUiThread {
                                liveRollNumbers.add(rollNumber)
                                if (recordFinalList) finalRollNumbers.add(rollNumber)
                                updateRollList()
                            }
                        } else {
                            Log.d("ATTEND_DEBUG","Received BLE code $bleCode not mapped or not assigned")
                        }
                    },
                    onPayloadFound = { /* Unused for teacher */ },
                    onScanFailure = { code ->
                        runOnUiThread { Toast.makeText(this, "Scan failed: $code", Toast.LENGTH_SHORT).show() }
                    }
                )
            } else { // Student
                Toast.makeText(this, "Broadcasting attendance...", Toast.LENGTH_SHORT).show()
                if (bleScannerHelper == null) bleScannerHelper = BleScannerHelper(this, serviceUuid)
                bleScannerHelper?.startScan(
                    onDeviceFound = { /* Student ignores other students' integer codes */ },
                    onPayloadFound = { payload ->
                        runOnUiThread {
                            if (payload == studentRollNumber && confirmations[studentRollNumber!!] != true) {
                                confirmations[studentRollNumber!!] = true
                                showStudentConfirmationUI()
                            }
                        }
                    },
                    onScanFailure = { code ->
                        runOnUiThread { Toast.makeText(this, "Scan failed: $code", Toast.LENGTH_SHORT).show() }
                    }
                )
                if (bleAdvertiserHelper == null) bleAdvertiserHelper = BleAdvertiserHelper(this, serviceUuid)
                Log.d("ATTEND_DEBUG", "Student advertising BLE_CODE=$studentBleCode, ROLL=$studentRollNumber")
                bleAdvertiserHelper?.startAdvertising(
                    studentBleCode,
                    onSuccess = { Log.d("ATTEND_DEBUG", "Advertising started") },
                    onFailure = { code -> runOnUiThread { Toast.makeText(this, "Advertise failed: $code", Toast.LENGTH_SHORT).show() } }
                )
                liveRollNumbers.clear()
                liveRollNumbers.add(studentRollNumber!!)
                updateRollList()
            }
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        btnStop.setOnClickListener {
            if (role == "teacher") {
                showGraceDialog(5) {
                    bleScannerHelper?.stopScan()
                    recordFinalList = false
                    tvGrace.text = ""
                    liveRollNumbers.clear()
                    liveRollNumbers.addAll(finalRollNumbers)
                    updateRollList()
                    Toast.makeText(this, "Grace period ended. Starting confirmation broadcast.", Toast.LENGTH_LONG).show()
                    startBroadcastConfirmation()
                }
                recordFinalList = true
                finalRollNumbers.clear()
            } else { // Student
                bleAdvertiserHelper?.stopAdvertising()
                bleScannerHelper?.stopScan()
                Toast.makeText(this, "Operations stopped", Toast.LENGTH_SHORT).show()
            }
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateRollList()
    }

    // ---- Per-teacher JSON mapping function ----
    private fun loadStudentCredentialsForTeacher(email: String) {
        val fileName = "teacher_credentials/$email.json"
        bleCodeToRollNumberMap.clear()
        Log.d("ATTEND_DEBUG", "Loading per-teacher credentials from $fileName")
        try {
            assets.open(fileName).use { stream ->
                val students: List<Student> = Gson().fromJson(
                    InputStreamReader(stream),
                    object : TypeToken<List<Student>>() {}.type
                )
                students.forEach { bleCodeToRollNumberMap[it.bleCode] = it.rollNumber }
                Log.d("ATTEND_DEBUG", "Loaded ${students.size} students for BLE code mapping from $fileName.")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot load $fileName", Toast.LENGTH_LONG).show()
            Log.e("AttendanceActivity", "Error loading $fileName", e)
        }
    }

    private fun loadAssignedRollsCsv() {
        val mentorEmail = intent.getStringExtra("email") ?: return
        val file = File(filesDir, "rolls/$mentorEmail.csv")
        assignedRolls = try {
            if (!file.exists()) {
                Toast.makeText(this, "No assigned rolls file found.", Toast.LENGTH_SHORT).show()
                emptySet()
            } else {
                CSVReader(FileReader(file)).use { reader ->
                    reader.readAll().drop(1).mapNotNull { it.getOrNull(0) }.toSet()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading assigned rolls file.", Toast.LENGTH_LONG).show()
            emptySet()
        }
        Log.d("ATTEND_DEBUG", "Assigned rolls from CSV: $assignedRolls")
    }

    // --- All your other unchanged helper functions below ---
    private fun startBroadcastConfirmation() {
        if (bleAdvertiserHelper == null) bleAdvertiserHelper = BleAdvertiserHelper(this, serviceUuid)
        tvBroadcastCountdown.visibility = View.VISIBLE
        advertiseIndex = 0
        advertiseHandler.post(advertiseRunnable)
        broadcastTimer = object : CountDownTimer(30_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1_000).toInt()
                findViewById<ProgressBar>(R.id.progressBroadcast)?.let {
                    it.progress = 30 - secondsRemaining
                    it.visibility = View.VISIBLE
                }
                tvBroadcastCountdown.text = getString(R.string.broadcast_countdown, secondsRemaining)
            }
            override fun onFinish() {
                advertiseHandler.removeCallbacks(advertiseRunnable)
                stopAdvertising()
                tvBroadcastCountdown.visibility = View.GONE
                findViewById<ProgressBar>(R.id.progressBroadcast)?.visibility = View.GONE
                saveAttendanceSession()
                val mentorEmail = intent.getStringExtra("email")
                if (mentorEmail != null) {
                    appendAttendanceColumn(mentorEmail, confirmations.keys)
                }
                Toast.makeText(this@AttendanceActivity, "Attendance session saved!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun startAdvertisingRollNumber(rollNumber: String) {
        bleAdvertiserHelper?.stopAdvertising()
        bleAdvertiserHelper?.startAdvertising(
            rollNumber,
            onSuccess = { /* Logging optional */ },
            onFailure = { code -> Log.e("BLE", "Advertising failed for roll $rollNumber: $code") }
        )
    }

    private fun stopAdvertising() {
        bleAdvertiserHelper?.stopAdvertising()
    }

    private fun saveAttendanceSession() {
        if (finalRollNumbers.isEmpty()) return
        val now = System.currentTimeMillis()
        val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        confirmations.clear()
        finalRollNumbers.forEach { confirmations[it] = true }
        val session = AttendanceSession(
            timestamp = now,
            formattedTime = format.format(Date(now)),
            rollNumbers = finalRollNumbers.sorted(),
            confirmations = confirmations.toMap()
        )
        SessionStore.saveSession(this@AttendanceActivity, session)
    }

    private fun showGraceDialog(durationSeconds: Int, onFinish: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_grace_countdown, null)
        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Grace Period")
            .setView(dialogView)
            .setCancelable(false)
            .create()
        alertDialog.show()
        object : CountDownTimer((durationSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSeconds = (millisUntilFinished / 1000).toInt() + 1
                tvCountdown.text = getString(R.string.finalizing_in_seconds, remainingSeconds)
            }
            override fun onFinish() {
                alertDialog.dismiss()
                onFinish()
            }
        }.start()
    }

    private fun checkAndRequestBLEPermissions(): Boolean {
        val toAsk = blePermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        return if (toAsk.isEmpty()) true else {
            ActivityCompat.requestPermissions(this, toAsk.toTypedArray(), bleRequestCode)
            false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bleRequestCode) {
            if (!grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Bluetooth permissions are required to continue.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun updateRollList() {
        rollAdapter?.clear()
        val toShow = if (recordFinalList) finalRollNumbers else liveRollNumbers
        rollAdapter?.addAll(toShow.sorted())
        rollAdapter?.notifyDataSetChanged()
    }

    private fun showStudentConfirmationUI() {
        val roll = studentRollNumber ?: return
        AlertDialog.Builder(this)
            .setTitle("Attendance Confirmed")
            .setMessage("Your attendance has been confirmed!\nRoll Number: $roll")
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun appendAttendanceColumn(mentorEmail: String, confirmedList: Set<String>) {
        try {
            val file = File(filesDir, "rolls/$mentorEmail.csv")
            val all = CSVReader(FileReader(file)).readAll()
            CSVWriter(FileWriter(file)).use { writer ->
                val now = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
                all[0] = all[0] + arrayOf(now)
                val updated = (1 until all.size).map { i ->
                    val roll = all[i][0]
                    val status = if (confirmedList.contains(roll)) "P" else "A"
                    all[i] + arrayOf(status)
                }
                writer.writeAll(listOf(all[0]) + updated)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving attendance to CSV", Toast.LENGTH_SHORT).show()
        }
    }
}
