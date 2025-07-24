package com.svce.attendance.activities

import com.svce.attendance.ble.BleAdvertiserHelper
import com.svce.attendance.ble.BleScannerHelper
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import com.svce.attendance.R
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothManager
import android.text.InputFilter
import java.util.*
import android.view.View
import android.util.Log

class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var bleAdvertiserHelper: BleAdvertiserHelper? = null
    private var bleScannerHelper: BleScannerHelper? = null

    private val liveRollNumbers = mutableSetOf<String>()
    private val finalRollNumbers = mutableSetOf<String>()
    private var recordFinalList = false

    private var rollAdapter: ArrayAdapter<String>? = null
    private lateinit var btnStop: Button
    private lateinit var btnStart: Button
    private lateinit var tvGrace: TextView
    private lateinit var btnPing: Button
    private lateinit var btnConfirm: Button
    private lateinit var tvBroadcastCountdown: TextView

    private var studentRollNumber: String? = null

    // --- TRACK CONFIRMATIONS (student side will maintain locally) ---
    private val confirmations = mutableMapOf<String, Boolean>()

    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private val bleRequestCode = 101

    // For teacher advertising cycle
    private var advertiseIndex = 0
    private val advertiseHandler = Handler(Looper.getMainLooper())
    private val advertiseInterval = 500L // 500ms per roll number

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        if (!checkAndRequestBLEPermissions()) {
            // Early exit until permissions are granted
            return
        }

        val role = intent.getStringExtra("role") ?: "Unknown"
        val tvAttendanceRole = findViewById<TextView>(R.id.tvAttendanceRole)
        tvAttendanceRole.text = getString(R.string.attendance_as, role)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvGrace = findViewById(R.id.tvGrace)
        btnPing = findViewById(R.id.btnPing)
        btnConfirm = findViewById(R.id.btnConfirm)
        tvBroadcastCountdown = findViewById(R.id.tvBroadcastCountdown)

        tvGrace.text = "" // hidden until grace starts
        tvBroadcastCountdown.visibility = View.GONE

        val listRolls = findViewById<ListView>(R.id.listRolls)
        rollAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listRolls.adapter = rollAdapter

        btnPing.visibility = View.GONE // Completely hide the ping button as per new design
        btnConfirm.visibility = View.GONE // No manual confirm

        if (role == "student" && studentRollNumber == null) {
            promptForRollNumber { roll ->
                Toast.makeText(this, "Roll Number Saved: $roll", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            if (!checkAndRequestBLEPermissions()) return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Bluetooth permissions required!", Toast.LENGTH_SHORT).show()
                checkAndRequestBLEPermissions()
                return@setOnClickListener
            }
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(enableBtIntent)
                Toast.makeText(this, "Please enable Bluetooth then press Start again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (role == "teacher") {
                Toast.makeText(this, "Attendance started via BLE (teacher)", Toast.LENGTH_SHORT).show()
                if (bleScannerHelper == null) bleScannerHelper = BleScannerHelper(this, serviceUuid)
                liveRollNumbers.clear()
                finalRollNumbers.clear()
                recordFinalList = false
                confirmations.clear()

                bleScannerHelper?.startScan(
                    onDeviceFound = { payload ->
                        runOnUiThread {
                            // Teacher listens for roll numbers - no CONFIRM processing as automatic now
                            liveRollNumbers.add(payload)
                            if (recordFinalList) finalRollNumbers.add(payload)
                            updateRollList()
                        }
                    },
                    onScanFailure = { code ->
                        runOnUiThread {
                            Toast.makeText(this, "Scan failed: $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                // STUDENT LOGIC: advertise own roll number and scan for teacher broadcasts of roll numbers for confirmation
                if (studentRollNumber.isNullOrBlank()) {
                    promptForRollNumber {}
                    return@setOnClickListener
                }
                Toast.makeText(this, "Attendance started via BLE (student)", Toast.LENGTH_SHORT).show()

                if (bleScannerHelper == null) bleScannerHelper = BleScannerHelper(this, serviceUuid)
                bleScannerHelper?.startScan(
                    onDeviceFound = { payload ->
                        runOnUiThread {
                            if (payload == studentRollNumber) {
                                if (confirmations[studentRollNumber!!] != true) {
                                    confirmations[studentRollNumber!!] = true
                                    showStudentConfirmationUI()
                                }
                            }
                        }
                    },
                    onScanFailure = { code ->
                        runOnUiThread {
                            Toast.makeText(this, "Scan failed: $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (bleAdvertiserHelper == null) bleAdvertiserHelper = BleAdvertiserHelper(this, serviceUuid)
                bleAdvertiserHelper?.startAdvertising(
                    studentRollNumber!!,
                    onSuccess = {
                        runOnUiThread { Toast.makeText(this, "BLE advertising started.", Toast.LENGTH_SHORT).show() }
                    },
                    onFailure = { code ->
                        runOnUiThread { Toast.makeText(this, "Advertise failed: $code", Toast.LENGTH_SHORT).show() }
                    }
                )
                liveRollNumbers.clear()
                liveRollNumbers.add(studentRollNumber!!)
                updateRollList()
            }
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        // Confirm button unused now, so hide and disable it
        btnConfirm.visibility = View.GONE
        btnConfirm.setOnClickListener(null)

        btnStop.setOnClickListener {
            if (role == "teacher") {
                // Hide ping button permanently since unused
                btnPing.visibility = View.GONE

                // 5-second grace period before starting 10-sec broadcast
                showGraceDialog(5) {
                    bleScannerHelper?.stopScan()
                    recordFinalList = false
                    tvGrace.text = ""
                    liveRollNumbers.clear()
                    liveRollNumbers.addAll(finalRollNumbers)
                    updateRollList()
                    Toast.makeText(this, "Grace period ended - starting confirmation broadcast!", Toast.LENGTH_LONG).show()
                    startBroadcastConfirmation()
                }
                recordFinalList = true
                finalRollNumbers.clear()
            } else {
                bleAdvertiserHelper?.stopAdvertising()
                bleScannerHelper?.stopScan()
                Toast.makeText(this, "Advertising stopped", Toast.LENGTH_SHORT).show()
            }
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }

        // Ping button removed from action since no longer needed
        btnPing.setOnClickListener(null)

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateRollList()
    }

    private fun startBroadcastConfirmation() {
        if (bleAdvertiserHelper == null) {
            bleAdvertiserHelper = BleAdvertiserHelper(this, serviceUuid)
        }

        tvBroadcastCountdown.visibility = View.VISIBLE
        advertiseIndex = 0
        advertiseHandler.post(advertiseRunnable)

        object : CountDownTimer(10_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000) + 1
                tvBroadcastCountdown.text = "Broadcasting confirmation: $secondsRemaining"
            }

            override fun onFinish() {
                advertiseHandler.removeCallbacks(advertiseRunnable)
                stopAdvertising()
                tvBroadcastCountdown.visibility = View.GONE
                saveAttendanceSession()
                Toast.makeText(this@AttendanceActivity, "Attendance session saved!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun startAdvertisingRollNumber(rollNumber: String) {
        stopAdvertising()

        bleAdvertiserHelper?.startAdvertising(
            rollNumber,
            onSuccess = { /* Advertising started for rollNumber */ },
            onFailure = { code -> Log.e("BLE", "Advertising failed for roll $rollNumber: $code") }
        )
    }

    private fun stopAdvertising() {
        bleAdvertiserHelper?.stopAdvertising()
    }

    private fun saveAttendanceSession() {
        if (finalRollNumbers.isEmpty()) return

        val now = System.currentTimeMillis()
        val format = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        confirmations.clear()
        finalRollNumbers.forEach { confirmations[it] = true } // All final rolls confirmed by implicit broadcast
        val session = com.svce.attendance.utils.AttendanceSession(
            timestamp = now,
            formattedTime = format.format(Date(now)),
            rollNumbers = finalRollNumbers.sorted(),
            confirmations = confirmations.toMap()
        )
        com.svce.attendance.utils.SessionStore.saveSession(this@AttendanceActivity, session)
    }

    private fun showGraceDialog(durationSeconds: Int = 5, onFinish: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_grace_countdown, null)
        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Grace Period")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        alertDialog.show()

        var secondsLeft = durationSeconds
        tvCountdown.text = getString(R.string.finalizing_in_seconds, secondsLeft)
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                secondsLeft--
                if (secondsLeft <= 0) {
                    alertDialog.dismiss()
                    onFinish()
                } else {
                    tvCountdown.text = getString(R.string.finalizing_in_seconds, secondsLeft)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun promptForRollNumber(onRollEntered: (String) -> Unit) {
        val input = EditText(this)
        input.hint = "Enter Roll Number"
        input.maxLines = 1
        input.filters = arrayOf(InputFilter.LengthFilter(10))
        AlertDialog.Builder(this)
            .setTitle("Your Roll Number")
            .setMessage("Please enter your 10-character roll number")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save") { d, _ ->
                val roll = input.text.toString().trim()
                if (roll.length == 10 && roll.matches(Regex("^[A-Za-z0-9]{10}$"))) {
                    studentRollNumber = roll
                    onRollEntered(roll)
                } else {
                    Toast.makeText(this, "Invalid roll number!", Toast.LENGTH_SHORT).show()
                    promptForRollNumber(onRollEntered)
                }
                d.dismiss()
            }
            .show()
    }

    private fun checkAndRequestBLEPermissions(): Boolean {
        val toAsk = blePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (toAsk.isEmpty()) true else {
            ActivityCompat.requestPermissions(this, toAsk.toTypedArray(), bleRequestCode)
            false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bleRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Bluetooth permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permissions needed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateRollList() {
        rollAdapter?.clear()
        val toShow = if (recordFinalList) finalRollNumbers else liveRollNumbers
        val displayList = toShow.sorted().map { roll ->
            // Since no manual confirmations, no tick marks on teacher side
            roll
        }
        rollAdapter?.addAll(displayList)
        rollAdapter?.notifyDataSetChanged()
    }

    private fun showStudentConfirmationUI() {
        // You can update your UI here for student confirmation, e.g., a Toast or green tick
        Toast.makeText(this, "Attendance Confirmed!", Toast.LENGTH_SHORT).show()
        // You can also update a UI component or trigger animation if you want
    }

}
