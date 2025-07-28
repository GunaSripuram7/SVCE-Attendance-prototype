package com.svce.attendance.activities

import android.Manifest
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.svce.attendance.R
import com.svce.attendance.ble.BleAdvertiserHelper
import com.svce.attendance.ble.BleScannerHelper
import org.json.JSONObject
import java.io.InputStream
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var scannerHelper: BleScannerHelper? = null
    private var advertiserHelper: BleAdvertiserHelper? = null


    private lateinit var bleCodeContainer: LinearLayout
    private lateinit var etBleCode: EditText
    private lateinit var btnSaveCode: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvRole: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var isInGracePeriod = false
    private val gracePeriodRolls = mutableSetOf<String>()


    // Set of currently present roll numbers
    private val presentRolls = mutableSetOf<String>()

    // Map to track last seen time of each roll number (millis)
    private val lastSeenMap = mutableMapOf<String, Long>()

    private val handler = Handler(Looper.getMainLooper())
    private val cleanupIntervalMillis = 2000L  // run cleanup every 2 sec
    private val timeoutMillis = 5000L          // remove entries if no seen for 5 sec

    private val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private lateinit var codeToRoll: Map<Int, String>

    private var savedBleCode: Int? = null
    private lateinit var role: String

    private val cleanupRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            var listChanged = false
            val iterator = lastSeenMap.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > timeoutMillis) {
                    iterator.remove()
                    if (presentRolls.remove(entry.key)) {
                        listChanged = true
                    }
                }
            }
            if (listChanged) {
                runOnUiThread {
                    adapter.clear()
                    adapter.addAll(presentRolls.sorted())
                    adapter.notifyDataSetChanged()
                }
            }
            handler.postDelayed(this, cleanupIntervalMillis)
        }
    }

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

        role = intent.getStringExtra("role") ?: ""
        tvRole.text = getString(R.string.attendance_as, role)

        loadMapping()

        if (role == "teacher") {
            // Teacher UI: No BLE input
            bleCodeContainer.visibility = View.GONE
            btnStart.isEnabled = true
        } else {
            // Student UI: show BLE input, disable start until BLE code saved
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

                if (advertiserHelper == null) {
                    advertiserHelper = BleAdvertiserHelper(this, serviceUuid)
                }
                advertiserHelper?.startAdvertising(
                    payloadInt = code,
                    onSuccess = {
                        runOnUiThread {
                            tvRole.text = getString(R.string.advertising_code, code)
                        }
                        Toast.makeText(this, "Advertising started for code $code", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorCode ->
                        runOnUiThread {
                            Toast.makeText(this, "Advertising failed with error code $errorCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else if (role == "teacher") {
                startScanning()
            }

            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        var isInGracePeriod = false
        btnStop.setOnClickListener {
            if (role == "teacher") {
                // Log start
                Log.d("Attendance", "Stop clicked. Entering grace period.")

                // Disable UI buttons
                btnStart.isEnabled = false
                btnStop.isEnabled = false
                bleCodeContainer.isEnabled = false

                // Reset gracePeriodRolls and enable grace period flag


                isInGracePeriod = true
                gracePeriodRolls.clear()

                Toast.makeText(this, "Grace period started, collecting final attendance...", Toast.LENGTH_SHORT).show()

                // Start 5-second countdown timer for grace period
                object : CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        Log.d("Attendance", "Grace period countdown: ${millisUntilFinished / 1000} seconds")
                    }

                    override fun onFinish() {
                        Log.d("Attendance", "Grace period ended, final attendance: $gracePeriodRolls")

                        // Stop scanning and cleanup
                        scannerHelper?.stopScanning()
                        stopCleanup()

                        isInGracePeriod = false  // reset flag

                        // Optional: Save or export gracePeriodRolls here

                        // Update UI
                        runOnUiThread {
                            btnStart.isEnabled = true
                            btnStop.isEnabled = false
                            bleCodeContainer.isEnabled = true
                        }

                        Toast.makeText(this@AttendanceActivity, "Attendance finalized", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            } else {
                advertiserHelper?.stopAdvertising()
                Toast.makeText(this, "Student stopped broadcasting.", Toast.LENGTH_SHORT).show()
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                tvRole.text = getString(R.string.attendance_as, role)
            }
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
            val code = key.toIntOrNull()
            if (code != null) {
                map[code] = obj.getString(key)
            }
        }
        codeToRoll = map
    }

    private fun startScanning() {
        presentRolls.clear()
        lastSeenMap.clear()
        adapter.clear()

        scannerHelper = BleScannerHelper(
            context = this,
            serviceUuid = serviceUuid,
            onDeviceFound = { code: Int ->
                val roll = codeToRoll[code]
                if (roll != null) {
                    lastSeenMap[roll] = System.currentTimeMillis()

                    if (isInGracePeriod) {
                        gracePeriodRolls.add(roll)
                    } else {
                        val isNew = presentRolls.add(roll)
                        if (isNew) {
                            runOnUiThread {
                                adapter.add(roll)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            },
            onScanFailure = { err: Int ->
                runOnUiThread {
                    Toast.makeText(this, "Scan failed: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
        scannerHelper?.startScanning()
        startCleanup()
    }

    private fun startCleanup() {
        handler.post(cleanupRunnable)
    }

    private fun stopCleanup() {
        handler.removeCallbacks(cleanupRunnable)
    }

    private fun checkPermissions(): Boolean {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (missing.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 200)
            false
        }
    }
}
