package com.svce.attendance.activities


import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.transport.ControlMessage
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid

import com.svce.attendance.mesh.MeshProxyBleManager
import androidx.activity.OnBackPressedCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi
import com.onesignal.OneSignal
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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

import java.io.BufferedReader
import java.io.FileReader

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class AttendanceActivity : AppCompatActivity(),
    no.nordicsemi.android.mesh.MeshManagerCallbacks,
    no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks,
    no.nordicsemi.android.mesh.MeshStatusCallbacks {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
  //  private var scannerHelper: BleScannerHelper? = null
   // private var advertiserHelper: BleAdvertiserHelper? = null
  // Add mesh variables:
    private var meshProxyManager: MeshProxyBleManager? = null
    private var isTeacherProvisioner = false
    private val provisionedNodes = mutableListOf<ProvisionedMeshNode>()
    private var hasSubmittedCodeThisSession = false
    private lateinit var meshManagerApi: no.nordicsemi.android.mesh.MeshManagerApi
    private var meshNetwork: no.nordicsemi.android.mesh.MeshNetwork? = null

    private lateinit var tvRollCount: TextView
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

    private lateinit var codeToRollMap: Map<Int, String>
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
                    updateRollCount()
                }
            }
            handler.postDelayed(this, cleanupIntervalMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        meshManagerApi = no.nordicsemi.android.mesh.MeshManagerApi(this)
        meshManagerApi.setMeshManagerCallbacks(this)
        meshManagerApi.setProvisioningStatusCallbacks(this)
        meshManagerApi.setMeshStatusCallbacks(this)
        meshManagerApi.loadMeshNetwork()

        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
        }
        // Request notification permission on Android 13+ devices


        bleCodeContainer = findViewById(R.id.bleCodeContainer)
        etBleCode = findViewById(R.id.etBleCode)
        btnSaveCode = findViewById(R.id.btnSaveCode)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvRole = findViewById(R.id.tvAttendanceRole)
        listView = findViewById(R.id.listRolls)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter
        tvRollCount = findViewById(R.id.tvRollCount)


        role = intent.getStringExtra("role") ?: ""
        tvRole.text = getString(R.string.attendance_as, role)

        if (hasSubmittedCodeThisSession) {
            btnSaveCode.isEnabled = false
        }

        loadMapping()
        loadBleCodeRollMap()


        if (role == "teacher") {
            // Teacher UI: No BLE input
            bleCodeContainer.visibility = View.GONE
            btnStart.isEnabled = true
        } else {
            // Student UI: show BLE input, disable start until BLE code saved
            bleCodeContainer.visibility = View.VISIBLE
            btnStart.isEnabled = false

            btnSaveCode.setOnClickListener {
                // 1. Prevent duplicate submission in this session
                if (hasSubmittedCodeThisSession) {
                    Toast.makeText(this, "Code has already been submitted. Reopen app to change.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val codeText = etBleCode.text.toString().trim()
                val codeInt = codeText.toIntOrNull()
                if (codeInt == null) {
                    Toast.makeText(this, "Please enter a valid integer BLE code", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Save raw code for BLE advertising as before
                savedBleCode = codeInt
                btnStart.isEnabled = true
                Toast.makeText(this, "BLE code saved: $codeInt", Toast.LENGTH_SHORT).show()

                // Transform to roll number via JSON map
                val roll = codeToRollMap[codeInt]
                if (roll == null) {
                    Log.e("Attendance", "No roll mapping for BLE code $codeInt")
                    Toast.makeText(this, "Unknown BLE code!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Log.d("Attendance", "Mapped BLE code $codeInt → roll $roll")

                // OneSignal: Tag this device with the roll number
                OneSignal.User.addTag("roll", roll)

                Toast.makeText(this, "Code submitted. Reopen app to change.", Toast.LENGTH_LONG).show()
                btnSaveCode.isEnabled = false           // block further edits
                hasSubmittedCodeThisSession = true      // memory lock
            }





        }

        btnStart.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener

            if (role == "teacher") {
                // TEACHER: Start mesh provisioning
                Toast.makeText(this, "Ready to provision students into mesh!", Toast.LENGTH_SHORT).show()
                // Here, you could trigger a scan and connect to a student's device for provisioning,
                // then instantiate MeshProxyBleManager as needed.
                // Example:
                // val deviceAddress = ... // MAC address of student node from scan
                // meshProxyManager = MeshProxyBleManager(..., deviceAddress = deviceAddress, ...)
                // meshProxyManager?.connect()
            } else if (role == "student") {
                val code = savedBleCode
                if (code == null) {
                    Toast.makeText(this, "Please save your BLE code first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val roll = codeToRollMap[code]
                if (roll != null) {
                    // --- INITIATE CONNECTION TO TEACHER'S PROXY NODE ----
                    scanForMeshProxyDevices()


                    Toast.makeText(this, "Connecting via Mesh Proxy... ($roll)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Unknown BLE code—cannot send attendance.", Toast.LENGTH_SHORT).show()
                }
            }

            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }





        var isInGracePeriod = false
        btnStop.setOnClickListener {

            /********  TEACHER  *********/
            if (role == "teacher") {

                // Ignore double-taps while already in grace period
                if (isInGracePeriod) return@setOnClickListener

                Log.d("Attendance", "Teacher pressed STOP – entering grace period")

                // Lock down UI while we keep scanning for late joiners
                btnStart.isEnabled = false
                btnStop.isEnabled  = false
                bleCodeContainer.isEnabled = false

                gracePeriodRolls.clear()
                isInGracePeriod = true
                Toast.makeText(
                    this,
                    "Grace period started (5 s)… collecting final attendance",
                    Toast.LENGTH_SHORT
                ).show()

                /* Keep the scanner running during grace period.
                   When the countdown finishes we stop scanning and finalise the list. */
                object : CountDownTimer(5_000, 1_000) {

                    override fun onTick(millisUntilFinished: Long) {
                        // Update the subtitle so the teacher sees a live countdown
                        tvRole.text = getString(
                            R.string.grace_remaining,
                            millisUntilFinished / 1_000
                        )
                        Log.d("Attendance",
                            "Grace period countdown: ${millisUntilFinished / 1000}s")
                    }

                    override fun onFinish() {
                        isInGracePeriod = false

                        // Stop BLE scan and housekeeping timer
                        //scannerHelper?.stopScanning()
                        stopCleanup()

                        val finalSet = presentRolls + gracePeriodRolls
                        exportSessionCsv(finalSet)    // <- writes dd-MM-yyyy-N.csv

                        // Merge grace-period hits into the main set and refresh list
                        presentRolls += gracePeriodRolls
                        adapter.clear()
                        adapter.addAll(presentRolls.sorted())
                        adapter.notifyDataSetChanged()
                        updateRollCount()

                        Log.d("Attendance",
                            "Grace ended. Final rolls: $presentRolls")

                        // Restore UI
                        tvRole.text = getString(R.string.attendance_as, role)
                        btnStart.isEnabled = true
                        btnStop.isEnabled  = false
                        bleCodeContainer.isEnabled = true

                        Toast.makeText(
                            this@AttendanceActivity,
                            "Attendance finalised (${presentRolls.size} students)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.start()


                /********  STUDENT  *********/
            } else if (role == "student") {

                //advertiserHelper?.stopAdvertising()
                // advertiserHelper = null
                savedBleCode = null
                btnSaveCode.isEnabled = true
                hasSubmittedCodeThisSession = false
                Toast.makeText(
                    this,
                    "Student stopped broadcasting.",
                    Toast.LENGTH_SHORT
                ).show()

                btnStart.isEnabled = true
                btnStop.isEnabled  = false
                tvRole.text = getString(R.string.attendance_as, role)
            }
        }


        btnStop.isEnabled = false

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (role == "student") {

                    //advertiserHelper?.stopAdvertising()
                    //advertiserHelper = null
                    savedBleCode = null
                    btnSaveCode.isEnabled = true
                    hasSubmittedCodeThisSession = false
                    Toast.makeText(this@AttendanceActivity, "BLE advertising stopped", Toast.LENGTH_SHORT).show()
                }
                finish()  // Close the activity
            }
        })
    }


    private fun scanForMeshProxyDevices() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Bluetooth scan permission not granted", Toast.LENGTH_SHORT).show()
            // Prompt user for permission, or just return for now.
            return
        }

        val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
        val meshProxyUuid =
            ParcelUuid(UUID.fromString("00001828-0000-1000-8000-00805f9b34fb")) // Mesh Proxy Service UUID

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(meshProxyUuid)
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (ContextCompat.checkSelfPermission(
                            this@AttendanceActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d(
                            "MeshScan",
                            "Found mesh proxy: ${result.device.address} (${result.device.name})"
                        )
                        // Use result.device.address as needed for connection
                    } else {
                        Toast.makeText(
                            this@AttendanceActivity,
                            "No Bluetooth connect permission for device address",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("MeshScan", "Scan failed: $errorCode")
                }
            })
        } catch (e: SecurityException) {
            Toast.makeText(this, "Bluetooth scan permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    /*   override fun onBackPressed() {
           if (role == "student") {
               advertiserHelper?.stopAdvertising()
               advertiserHelper = null
               savedBleCode = null
               btnSaveCode.isEnabled = true
               hasSubmittedCodeThisSession = false
               Toast.makeText(this, "BLE advertising stopped", Toast.LENGTH_SHORT).show()
           }
           super.onBackPressed()
       } */

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

        /* scannerHelper = BleScannerHelper(
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
                                adapter.clear()
                                adapter.addAll(presentRolls.sorted())
                                adapter.notifyDataSetChanged()
                                updateRollCount()
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
        ) */
        // scannerHelper?.startScanning()
        startCleanup()
    }

    private fun startCleanup() {
        handler.post(cleanupRunnable)
    }

    private fun stopCleanup() {
        handler.removeCallbacks(cleanupRunnable)
    }


    private fun exportSessionCsv(rolls: Collection<String>) {
        val csvFile = createNextSessionCsv()

        FileWriter(csvFile).use { w ->
            w.appendLine("Roll Number")
            rolls.sorted().forEach { w.appendLine(it) }
        }

        Log.d("Attendance", "CSV saved at ${csvFile.absolutePath}")
        Toast.makeText(
            this,
            "CSV saved:\n${csvFile.absolutePath}",
            Toast.LENGTH_LONG
        ).show()

        // NEW: send push notifications to all rolls in this session
        sendAttendancePush(csvFile)
    }



    // Call this at the END of exportSessionCsv()
    private fun sendAttendancePush(csvFile: File) {

        // 1. Read all roll numbers from CSV (skip header)
        val rolls = csvFile.readLines()
            .drop(1) // remove "Roll Number" header
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        if (rolls.isEmpty()) {
            Log.w("AttendancePush", "No rolls to notify in CSV")
            return
        }

        // 2. Build OneSignal filters array
        val filters = mutableListOf<Map<String, Any>>()
        rolls.forEachIndexed { index, roll ->
            if (index > 0) {
                filters.add(mapOf("operator" to "OR"))
            }
            filters.add(
                mapOf(
                    "field" to "tag",
                    "key" to "roll",
                    "relation" to "=",
                    "value" to roll
                )
            )
        }

        // 3. Build JSON payload for OneSignal
        val payload = mapOf(
            "app_id" to "5707627c-23d3-41da-8d32-309113db8718",
            "filters" to filters,
            "headings" to mapOf("en" to "Attendance Confirmed {{roll}}"),
            "contents" to mapOf("en" to "✅ Your attendance is confirmed")
        )

        // 4. Serialize payload to JSON
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(Map::class.java)
        val jsonBody = jsonAdapter.toJson(payload)

        Log.d("AttendancePush", "Starting push: rolls=$rolls")
        // 5. Create HTTP request
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://onesignal.com/api/v1/notifications")
            .addHeader("Authorization", "os_v2_app_k4dwe7bd2na5vdjsgcirhw4hdcl4kaojncseofffamdip4qnke6ptolxwisrlfkiyueihhtokj6e5ar5ztnvzxebxjuvja3pbxdk7cy")  //Legacy REST Key
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        // 6. Execute request synchronously (or off the main thread)
        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: "no body"
                    Log.d("AttendancePush", "Response code: ${response.code}")
                    Log.d("AttendancePush", "Response body: $bodyString")

                    if (response.isSuccessful) {
                        Log.d("AttendancePush", "Push sent OK!")
                    } else {
                        Log.e("AttendancePush", "Error ${response.code}: $bodyString")
                    }
                }
            } catch (e: Exception) {
                Log.e("AttendancePush", "Failed to send push", e)
            }

        }.start()
    }


    /**
     * Create (and return) a new CSV file whose name follows
     *   dd-MM-yyyy-<ordinal>.csv   where <ordinal> starts at 1 every day
     */
    private fun createNextSessionCsv(): File {
        // 1)  App-private “sessions” directory on external storage
        val sessionsDir = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }

        // 2)  Date part  ->  "28-07-2025"
        val datePart = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

        // 3)  Find all files for today and read their trailing numbers
        val existingToday = sessionsDir.listFiles { _, name ->
            name.startsWith(datePart) && name.endsWith(".csv")
        } ?: emptyArray()

        val highestOrdinal = existingToday
            .mapNotNull { file ->
                // Pull the number between the last hyphen and ".csv"
                Regex("""${Regex.escape(datePart)}-(\d+)\.csv""")
                    .find(file.name)
                    ?.groupValues?.get(1)
                    ?.toInt()
            }
            .maxOrNull() ?: 0   // 0 if none found

        val nextOrdinal = highestOrdinal + 1        // auto-increment
        val newFileName = "$datePart-$nextOrdinal.csv"

        return File(sessionsDir, newFileName)       // NOT yet written
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

    private fun loadBleCodeRollMap() {
        val jsonStream: InputStream = assets.open("BLEcode_rollnumber.json")
        val text = jsonStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        codeToRollMap = obj.keys().asSequence()
            .mapNotNull { key ->
                key.toIntOrNull()?.let { code -> code to obj.getString(key) }
            }
            .toMap()
    }
    private fun updateRollCount() {
        val visibleCount = presentRolls.size
        tvRollCount.text = "roll numbers $visibleCount visible"
    }
    // MeshManagerCallbacks
    override fun onNetworkLoaded(meshNetwork: no.nordicsemi.android.mesh.MeshNetwork) {
        this.meshNetwork = meshNetwork
        Log.d("Mesh", "Network loaded")
    }
    override fun onNetworkUpdated(meshNetwork: no.nordicsemi.android.mesh.MeshNetwork) {
        this.meshNetwork = meshNetwork
    }
    override fun onNetworkLoadFailed(error: String?) { }
    override fun onNetworkImported(meshNetwork: no.nordicsemi.android.mesh.MeshNetwork) { }
    override fun onNetworkImportFailed(error: String?) { }

    override fun getMtu(): Int {
        // Return the MTU size for your BLE link, or a typical default.
        // 517 is maximum for Bluetooth 4.2+, 23 is Bluetooth default.
        return 517
    }

    // MeshProvisioningStatusCallbacks
    override fun onProvisioningStateChanged(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {}

    override fun onProvisioningFailed(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) { /* ... */ }

    override fun onProvisioningCompleted(
        meshNode: ProvisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) { /* ... */ }



    // MeshStatusCallbacks
    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) { }
    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray) { }
    override fun onBlockAcknowledgementProcessed(dst: Int, message: no.nordicsemi.android.mesh.transport.ControlMessage) { }
    override fun onBlockAcknowledgementReceived(src: Int, message: no.nordicsemi.android.mesh.transport.ControlMessage) { }
    // Typical meshSend callback, called when meshManagerApi wants you to send a PDU:
    override fun onMeshPduCreated(pdu: ByteArray) {
        meshProxyManager?.writeMeshPdu(pdu)
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
        // handle PDU send over BLE here, e.g. with your MeshProxyBleManager
        meshProxyManager?.writeMeshPdu(pdu)
    }

    override fun onHeartbeatMessageReceived(src: Int, message: ControlMessage) {
        Log.d("Mesh", "Mesh heartbeat received from $src: $message")
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: no.nordicsemi.android.mesh.transport.MeshMessage) { }

    override fun onMessageDecryptionFailed(meshLayer: String, errorMessage: String) {
        // Log or handle decryption failure here, optional for most apps
        Log.e("Mesh", "Message decryption failed at layer $meshLayer: $errorMessage")
    }

    override fun onMeshMessageReceived(src: Int, meshMessage: no.nordicsemi.android.mesh.transport.MeshMessage) {
        Log.d("Mesh", "Mesh message received from: $src")
        // Example: parse roll from message (or use src as identifier)
        // Here, you might use meshMessage (if it's GenericOnOffSet or vendor) to extract roll/state info!

        // For simple demo/prototype:
        val roll = "ROLL_$src" // (You would map src to roll number using a central record or included state!)

        runOnUiThread {
            if (presentRolls.add(roll)) {
                adapter.clear()
                adapter.addAll(presentRolls.sorted())
                adapter.notifyDataSetChanged()
                updateRollCount()
            }
        }
    }


}
