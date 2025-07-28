package com.svce.attendance.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.svce.attendance.R
import com.svce.attendance.ble.BleScannerHelper
import org.json.JSONObject
import java.io.InputStream
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var scannerHelper: BleScannerHelper? = null

    private lateinit var bleCodeContainer: LinearLayout
    private lateinit var etBleCode: EditText
    private lateinit var btnSaveCode: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvRole: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val presentRolls = mutableSetOf<String>()

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private lateinit var codeToRoll: Map<Int, String>

    // For student BLE code entered and saved during runtime
    private var savedBleCode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        bleCodeContainer = findViewById(R.id.bleCodeContainer)
        etBleCode = findViewById(R.id.etBleCode)
        btnSaveCode = findViewById(R.id.btnSaveCode)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvRole = findViewById(R.id.tvAttendanceRole)
        listView = findViewById(R.id.listRolls)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter

        val role = intent.getStringExtra("role") ?: ""
        tvRole.text = getString(R.string.attendance_as, role)

        loadMapping()

        if (role == "teacher") {
            // Hide BLE code input and show only start/stop buttons for teacher
            bleCodeContainer.visibility = View.GONE
            btnStart.isEnabled = true // teacher can start scanning immediately

        } else {
            // Show BLE code input for student and disable start until saved
            bleCodeContainer.visibility = View.VISIBLE
            btnStart.isEnabled = false

            btnSaveCode.setOnClickListener {
                val codeText = etBleCode.text.toString().trim()
                val codeInt = codeText.toIntOrNull()
                if (codeInt == null) {
                    Toast.makeText(this, "Please enter a valid integer BLE code", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                savedBleCode = codeInt
                btnStart.isEnabled = true
                Toast.makeText(this, "BLE code saved: $codeInt", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener

            if (role == "student") {
                val code = savedBleCode
                if (code == null) {
                    Toast.makeText(this, "Please save your BLE code first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // TODO: Add BLE advertising start logic here
                Toast.makeText(this, "Starting advertising with code $code", Toast.LENGTH_SHORT).show()

            } else if (role == "teacher") {
                startScanning()
            }

            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        btnStop.setOnClickListener {
            if (role == "teacher") {
                scannerHelper?.stopScanning()
                Toast.makeText(this, "Teacher scanning stopped", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: Add stop advertising logic for student if needed
                Toast.makeText(this, "Student broadcasting stopped", Toast.LENGTH_SHORT).show()
            }

            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }

        btnStop.isEnabled = false
    }

    private fun loadMapping() {
        val jsonStream: InputStream = assets.open("BLEcode_rollnumber.json")
        val text = jsonStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val map = mutableMapOf<Int, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            key.toIntOrNull()?.let { code ->
                map[code] = obj.getString(key)
            }
        }
        codeToRoll = map
    }

    private fun startScanning() {
        presentRolls.clear()
        adapter.clear()
        scannerHelper = BleScannerHelper(
            context = this,
            serviceUuid = serviceUuid,
            onCodeFound = { code ->
                android.util.Log.d("AttendanceActivity", "Scanned BLE code: $code")
                codeToRoll[code]?.let { roll ->
                    if (presentRolls.add(roll)) {
                        runOnUiThread {
                            adapter.add(roll)
                            adapter.notifyDataSetChanged()
                        }
                    }
                } ?: run {
                    // Optional: show a debug toast or log for unmapped codes
                    android.util.Log.d("AttendanceActivity", "Unmapped BLE code scanned: $code")
                }
            },
            onScanFailure = { err ->
                runOnUiThread {
                    Toast.makeText(this, "Scan failed: $err", Toast.LENGTH_SHORT).show()
                }
            })
        scannerHelper?.startScanning()
    }

    private fun checkPermissions(): Boolean {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (missing.isEmpty()) true else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 200)
            false
        }
    }
}
