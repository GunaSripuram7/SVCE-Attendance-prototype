package com.svce.attendance.activities

import com.svce.attendance.ble.BleAdvertiserHelper
import com.svce.attendance.ble.BleScannerHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.svce.attendance.R
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.widget.ListView
import android.widget.ArrayAdapter
import com.svce.attendance.utils.AttendanceSession
import com.svce.attendance.utils.SessionStore
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.EditText

class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var bleAdvertiserHelper: BleAdvertiserHelper? = null
    private var bleScannerHelper: BleScannerHelper? = null

    // Store scanned roll numbers (teacher) or single own roll number (student)
    private val scannedRollNumbers = mutableSetOf<String>()
    private var rollAdapter: ArrayAdapter<String>? = null
    private lateinit var btnStop: Button

    // In-memory student roll (temporary, lost if app is killed)
    private var studentRollNumber: String? = null

    // BLE permissions array and request code (adapts for API level)
    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    private val bleRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        val role = intent.getStringExtra("role") ?: "Unknown"
        val tvAttendanceRole = findViewById<TextView>(R.id.tvAttendanceRole)
        tvAttendanceRole.text = getString(R.string.attendance_as, role)

        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        val listRolls = findViewById<ListView>(R.id.listRolls)
        rollAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listRolls.adapter = rollAdapter

        // Only prompt students for roll number on entry if not set for this session
        if (role == "student" && studentRollNumber == null) {
            promptForRollNumber { roll ->
                Toast.makeText(this, "Roll Number Saved: $roll", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            // 1. Check and request BLE permissions at runtime
            if (!checkAndRequestBLEPermissions()) return@setOnClickListener

            // 2. Check Bluetooth CONNECT permission before using BluetoothAdapter
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Bluetooth permissions required!", Toast.LENGTH_SHORT).show()
                checkAndRequestBLEPermissions()
                return@setOnClickListener
            }

            // 3. Check if Bluetooth is enabled, or prompt user
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, 100)
                return@setOnClickListener
            }

            // 4. Proceed with role-based BLE logic
            if (role == "teacher") {
                Toast.makeText(this, "Attendance started via BLE (teacher)", Toast.LENGTH_SHORT).show()
                // -- Real BLE SCAN --
                if (bleScannerHelper == null) bleScannerHelper = BleScannerHelper(this, serviceUuid)
                scannedRollNumbers.clear()
                bleScannerHelper?.startScan(
                    onDeviceFound = { roll ->
                        runOnUiThread { addRollNumber(roll) }
                    },
                    onScanFailure = { code ->
                        runOnUiThread {
                            Toast.makeText(this, "Scan failed: $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                // Students: must have a roll number to continue
                if (studentRollNumber.isNullOrBlank()) {
                    promptForRollNumber {}
                    return@setOnClickListener
                }
                Toast.makeText(this, "Attendance started via BLE (student)", Toast.LENGTH_SHORT).show()
                scannedRollNumbers.clear()
                // Only show student's own roll in their list
                scannedRollNumbers.add(studentRollNumber!!)
                updateRollList()
                // -- Real BLE ADVERTISE --
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
            }
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        btnStop.setOnClickListener {
            Toast.makeText(this, "Attendance stopped ($role)", Toast.LENGTH_SHORT).show()
            // Stop BLE logic
            if (role == "teacher") {
                bleScannerHelper?.stopScan()
            } else {
                bleAdvertiserHelper?.stopAdvertising()
            }

            if (role == "teacher" && scannedRollNumbers.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val session = AttendanceSession(
                    timestamp = now,
                    formattedTime = format.format(Date(now)),
                    rollNumbers = scannedRollNumbers.sorted()
                )
                SessionStore.saveSession(this, session)
            }
            finish()
        }

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateRollList()
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

    // Called from real BLE scan result
    private fun addRollNumber(roll: String) {
        if (scannedRollNumbers.add(roll)) {
            updateRollList()
        }
    }

    private fun updateRollList() {
        rollAdapter?.clear()
        rollAdapter?.addAll(scannedRollNumbers.sorted())
        rollAdapter?.notifyDataSetChanged()
    }

    // Demo only: not used anymore (kept for development reference)
    private fun simulateScanning() {
        scannedRollNumbers.clear()
        updateRollList()
        val simulated = listOf("22CS1001", "22CS1002", "22CS1003", "22CS1004", "22CS1005")
        var i = 0
        btnStop.postDelayed(object : Runnable {
            override fun run() {
                if (i < simulated.size) {
                    addRollNumber(simulated[i])
                    i++
                    btnStop.postDelayed(this, 800)
                }
            }
        }, 800)
    }
}
