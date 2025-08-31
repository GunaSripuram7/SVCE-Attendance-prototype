package com.svce.attendance.activities

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

import com.svce.attendance.models.AttendanceSession

import io.github.jan.supabase.postgrest.from

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions



import android.os.Looper
import android.content.Context
import android.content.IntentFilter
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
import kotlin.text.toIntOrNull

import kotlinx.coroutines.Dispatchers


// NEW: Advanced multi-scanner imports (add these when service classes are created)
// TODO: Uncomment these when you create the service classes
/*
import com.svce.attendance.models.AttendanceRecord
import com.svce.attendance.models.AttendanceSession
import com.svce.attendance.models.SessionHelper
import com.svce.attendance.services.ScanningAccessManager
import com.svce.attendance.services.SocketIOService
import com.svce.attendance.services.SupabaseService
*/

class AttendanceActivity : AppCompatActivity() {

    // BLE and scanning components (PRESERVED)
    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var scannerHelper: BleScannerHelper? = null
    private var advertiserHelper: BleAdvertiserHelper? = null
    private var hasSubmittedCodeThisSession = false

    // UI components (PRESERVED + NEW)
    private lateinit var tvRollCount: TextView
    private lateinit var bleCodeContainer: LinearLayout
    private lateinit var etBleCode: EditText
    private lateinit var btnSaveCode: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvRole: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    // NEW: Advanced UI components (add these to your layout when ready)
    private lateinit var tvStatus: TextView // TODO: Add to layout
    private lateinit var tvHelperList: TextView // TODO: Add to layout for teacher
    private lateinit var btnStartGroupScanning: Button // TODO: Add to layout for teacher

    private lateinit var btnStopGroupScanning: Button

    // App state (PRESERVED + NEW)
    private var isInGracePeriod = false
    private val gracePeriodRolls = mutableSetOf<String>()
    private lateinit var codeToRollMap: Map<Int, String>
    private val presentRolls = mutableSetOf<String>()
    private val lastSeenMap = mutableMapOf<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private val cleanupIntervalMillis = 2000L
    private val timeoutMillis = 5000L

    // NEW: Advanced session state (for future multi-scanner functionality)
    private var savedBleCode: Int? = null
    private lateinit var role: String
    private var studentRollNumber: String = ""
    private var teacherEmail: String = ""
    private val selectedHelpers = mutableSetOf<String>()
    private val connectedHelpers = mutableSetOf<String>()
    private val scannedStudents = mutableSetOf<String>()

    // NEW: Helper selection functionality

    private var isSelectingHelpers = false

    // NEW: Service placeholders (TODO: Initialize when service classes are ready)
    // private lateinit var socketService: SocketIOService
    // private lateinit var supabaseService: SupabaseService
    // private lateinit var scanningAccessManager: ScanningAccessManager
    // private var currentSession: AttendanceSession? = null

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

    private val helperAccessReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (role == "student") {
                setupStudentUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
        }

        // Initialize views (PRESERVED + NEW)
        initializeViews()

        // Get role and user data (ENHANCED)
        role = intent.getStringExtra("role") ?: ""
        teacherEmail = intent.getStringExtra("email") ?: ""
        studentRollNumber = intent.getStringExtra("rollNumber") ?: ""


        if (role == "student") {
            val filter = IntentFilter("com.svce.attendance.HELPER_ACCESS_GRANTED")

            // Use reflection to safely call the new API
            try {
                val method = this.javaClass.getMethod("registerReceiver",
                    android.content.BroadcastReceiver::class.java,
                    IntentFilter::class.java,
                    Int::class.java)
                method.invoke(this, helperAccessReceiver, filter, 2) // 2 = RECEIVER_NOT_EXPORTED
            } catch (e: Exception) {
                // Fallback to old method
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(helperAccessReceiver, filter)
            }
        }






        tvRole.text = getString(R.string.attendance_as, role)

        if (hasSubmittedCodeThisSession) {
            btnSaveCode.isEnabled = false
        }

        // Load mappings (PRESERVED)
        loadMapping()
        loadBleCodeRollMap()

        // Setup UI based on role (ENHANCED)
        setupRoleSpecificUI()

        // Setup event handlers (ENHANCED)
        setupEventHandlers()

        // TODO: Initialize services when ready
        // initializeServices()
        // connectToSocket()

        // Add this inside onCreate (replace "your_table_name" with a real table!)
// No need to create a new CoroutineScope: use lifecycleScope in Activity.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = com.svce.attendance.services.SupabaseConfig.client
                    .from("attendance_sessions")
                    .select()
                    .decodeList<AttendanceSession>()  // Use your data class here!

                android.util.Log.d("SupabaseTest", "Sessions: $result")
            } catch (e: Exception) {
                android.util.Log.e("SupabaseTest", "Error fetching sessions", e)
            }
        }

        // Only for TESTING: Insert a new session on Activity start -- using real user UUID!
        if (role == "teacher") {
            // Get the teacher's UUID from SharedPreferences
            val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val teacherUUID = sharedPref.getString("user_id", null)

            if (teacherUUID != null) {
                val session = AttendanceSession(
                    session_code = "TEST${System.currentTimeMillis()}",
                    teacher_id = teacherUUID, // ✅ Use real UUID here!
                    class_id = "CS101"
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val insertedSession = com.svce.attendance.services.SupabaseConfig.client
                            .from("attendance_sessions")
                            .insert(session)
                            .decodeSingle<AttendanceSession>()
                        Log.d("SupabaseTest", "Inserted session: $insertedSession")
                    } catch (e: Exception) {
                        Log.e("SupabaseTest", "Insert failed", e)
                    }
                }
            } else {
                Log.e("SupabaseTest", "Teacher UUID not found in SharedPreferences!")
            }
        }


        // Only for TESTING: Insert a new session on Activity start
        /*val session = AttendanceSession(
            session_code = "TEST001",
            teacher_id = "teacher1@example.com",
            class_id = "CS101"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val insertedSession = com.svce.attendance.services.SupabaseConfig.client
                    .from("attendance_sessions")
                    .insert(session)
                    .decodeSingle<AttendanceSession>()
                android.util.Log.d("SupabaseTest", "Inserted: $insertedSession")
            } catch (e: Exception) {
                android.util.Log.e("SupabaseTest", "Insert failed", e)
            }
        } */

    }

    private fun initializeViews() {
        bleCodeContainer = findViewById(R.id.bleCodeContainer)
        etBleCode = findViewById(R.id.etBleCode)
        btnSaveCode = findViewById(R.id.btnSaveCode)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvRole = findViewById(R.id.tvAttendanceRole)
        listView = findViewById(R.id.listRolls)
        tvRollCount = findViewById(R.id.tvRollCount)

        // NEW: Initialize advanced UI (TODO: Add these to your layout)
        // tvStatus = findViewById(R.id.tvStatus)
         //tvHelperList = findViewById(R.id.tvHelperList)
        // btnStartGroupScanning = findViewById(R.id.btnStartGroupScanning)
        // --- ADD THIS for the Helper Scan button ---
        btnStartGroupScanning = findViewById(R.id.btnStartGroupScanning)
        btnStopGroupScanning = findViewById(R.id.btnStopGroupScanning) // <--- ADD THIS LINE HERE

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter
    }

    private fun setupRoleSpecificUI() {
        when (role) {
            "teacher" -> setupTeacherUI()
            "student" -> setupStudentUI()
            "helper" -> setupHelperUI()

        }

    }
    // Helper selection toggle, called on list item click
    private fun toggleHelperSelection(rollNumber: String) {
        if (selectedHelpers.contains(rollNumber)) {
            selectedHelpers.remove(rollNumber)
            Toast.makeText(this, "Removed helper: $rollNumber", Toast.LENGTH_SHORT).show()
        } else if (selectedHelpers.size < 7) {
            selectedHelpers.add(rollNumber)
            Toast.makeText(this, "Added helper: $rollNumber (${selectedHelpers.size}/7)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Maximum 7 helpers allowed", Toast.LENGTH_SHORT).show()
        }
        updateHelperDisplay()
    }

    // Update UI with selected helpers
    private fun updateHelperDisplay() {
        val helperList = selectedHelpers.joinToString(", ")
        tvRole.text = "Helpers (${selectedHelpers.size}/7): $helperList"
    }


    private fun setupTeacherUI() {
        // Hide student-specific UI
        bleCodeContainer.visibility = View.GONE

        // NEW: Show teacher-specific UI (TODO: Uncomment when UI elements are added)
        // tvHelperList.visibility = View.VISIBLE
        // btnStartGroupScanning.visibility = View.VISIBLE
        // tvStatus.text = "Ready to start attendance session"
        // tvHelperList.text = "Selected helpers: 0"

        btnStart.isEnabled = true
        btnStart.text = "Start Attendance"
        btnStop.isEnabled = false

        isSelectingHelpers = false
        selectedHelpers.clear()
    }

    private fun setupStudentUI() {
        // Hide teacher-specific UI (if present)
        // tvHelperList.visibility = View.GONE
        // btnStartGroupScanning.visibility = View.GONE

        // Show student-specific UI
        bleCodeContainer.visibility = View.VISIBLE
        btnStart.isEnabled = false

        // --- Show "Helper Scan" button if the student is a helper ---
        val prefs = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
        val isHelper = prefs.getBoolean("is_helper_access_granted", false)

        // Make sure btnStartGroupScanning is initialized (e.g., in initializeViews)
        if (isHelper) {
            btnStartGroupScanning.visibility = View.VISIBLE
            btnStartGroupScanning.isEnabled = true
        } else {
            btnStartGroupScanning.visibility = View.GONE
        }

        // (leave scanningAccessManager code as TODO if not using yet)
        // val hasAccess = studentRollNumber.isNotEmpty() &&
        //     scanningAccessManager.getActiveScanningAccess(studentRollNumber).isNotEmpty()
        // if (hasAccess) {
        //     setupScannerStudentUI()
        // } else {
        //     setupRegularStudentUI()
        // }
    }


    // Place this just after your setupTeacherUI/setupStudentUI definitions:
    private fun setupHelperUI() {
        tvRole.text = "Helper Scanning Mode"
        btnStart.text = "Scanning..."
        btnStart.isEnabled = false
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.VISIBLE


        btnStartGroupScanning.visibility = View.GONE // (optional, if not needed in this mode)

        btnStopGroupScanning.visibility = View.VISIBLE      // <-- add here
        btnStopGroupScanning.isEnabled = true

        // Start scanning immediately
        startHelperBleScanning()
    }



    private fun setupEventHandlers() {
        // Student code saving (PRESERVED)
        btnSaveCode.setOnClickListener {
            handleSaveCode()
        }

        // Start button (ENHANCED)
        btnStart.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener

            when (role) {
                "teacher" -> handleTeacherStart()
                "student" -> handleStudentStart()
            }
        }

        // Stop button (ENHANCED)
        btnStop.setOnClickListener {
            when (role) {
                "teacher" -> handleTeacherStop()
                "student" -> handleStudentStop()
            }
        }

        // Helper Group scanning button
        btnStartGroupScanning.setOnClickListener {
            Log.d("HelperScan", "Start Group Scanning button clicked!")
            if (!checkPermissions()) return@setOnClickListener
            startHelperBleScanning()
        }

        btnStopGroupScanning.setOnClickListener {
            stopHelperBleScanningAndSendRolls()
        }


        // NEW: List item clicks for helper selection (TODO: Implement when advanced mode is ready)
        // Enable list item click only in teacher mode
        if (role == "teacher") {
            listView.setOnItemClickListener { _, _, position, _ ->
                if (isSelectingHelpers) {
                    val rollNumber = adapter.getItem(position)
                    rollNumber?.let { toggleHelperSelection(it) }
                }
            }
        }

        // Back button handling (PRESERVED)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (role == "student") {
                    advertiserHelper?.stopAdvertising()
                    advertiserHelper = null
                    savedBleCode = null
                    btnSaveCode.isEnabled = true
                    hasSubmittedCodeThisSession = false
                    Toast.makeText(this@AttendanceActivity, "BLE advertising stopped", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        })

        btnStop.isEnabled = false
    }

    // STUDENT FUNCTIONALITY (PRESERVED + ENHANCED)

    private fun handleSaveCode() {
        if (hasSubmittedCodeThisSession) {
            Toast.makeText(this, "Code already submitted. Restart app to change.", Toast.LENGTH_SHORT).show()
            return
        }

        val codeText = etBleCode.text.toString().trim()
        val codeInt = codeText.toIntOrNull()

        if (codeInt == null) {
            Toast.makeText(this, "Please enter a valid integer BLE code", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate against mapping
        val roll = codeToRollMap[codeInt]
        if (roll == null) {
            Log.e("Attendance", "No roll mapping for BLE code $codeInt")
            Toast.makeText(this, "Unknown BLE code!", Toast.LENGTH_SHORT).show()
            return
        }

        // Save data (ENHANCED)
        savedBleCode = codeInt
        studentRollNumber = roll // NEW: Store roll number for advanced features
        btnStart.isEnabled = true
        hasSubmittedCodeThisSession = true
        btnSaveCode.isEnabled = false

        // OneSignal tagging (PRESERVED)
        OneSignal.User.addTag("roll", roll)

        Toast.makeText(this, "BLE code saved: $codeInt → Roll: $roll", Toast.LENGTH_SHORT).show()
        Log.d("Attendance", "Mapped BLE code $codeInt → roll $roll")
    }

    private fun handleStudentStart() {
        // NEW: Check if this is a scanner student (TODO: Implement when ScanningAccessManager is ready)
        // if (isScanner()) {
        //     startScannerMode()
        //     return
        // }

        // Regular advertising mode (PRESERVED)
        val code = savedBleCode
        if (code == null) {
            Toast.makeText(this, "Please save your BLE code first", Toast.LENGTH_SHORT).show()
            return
        }

        advertiserHelper?.stopAdvertising()
        advertiserHelper = null
        advertiserHelper = BleAdvertiserHelper(this, serviceUuid)

        advertiserHelper?.startAdvertising(
            payloadInt = code,
            onSuccess = {
                runOnUiThread {
                    tvRole.text = getString(R.string.advertising_code, code)
                    // TODO: Update tvStatus when UI is ready
                    // tvStatus.text = "Stay near scanning students - Signal active"
                }
                Toast.makeText(this, "Advertising started for code $code", Toast.LENGTH_SHORT).show()
            },
            onFailure = { errorCode ->
                runOnUiThread {
                    Toast.makeText(this, "Advertising failed with error code $errorCode", Toast.LENGTH_SHORT).show()
                    // TODO: Update tvStatus when UI is ready
                    // tvStatus.text = "Failed to start advertising - Try again"
                }
            }
        )

        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }

    private fun handleStudentStop() {
        // NEW: Check if this is a scanner student (TODO: Implement when services are ready)
        // if (isScanner()) {
        //     stopScannerMode()
        //     return
        // }

        // Regular advertising stop (PRESERVED)
        advertiserHelper?.stopAdvertising()
        advertiserHelper = null
        savedBleCode = null
        btnSaveCode.isEnabled = true
        hasSubmittedCodeThisSession = false

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        tvRole.text = getString(R.string.attendance_as, role)

        Toast.makeText(this, "Student stopped broadcasting.", Toast.LENGTH_SHORT).show()
    }

    // TEACHER FUNCTIONALITY (PRESERVED + ENHANCED)

    private fun handleTeacherStart() {
        if (!isSelectingHelpers) {
            // Phase 1: Select helpers - Start scanning to populate rolls!
            isSelectingHelpers = true
            btnStart.text = "Finish Helper Selection"
            tvRole.text = "Tap roll numbers to select helpers (max 7)"
            btnStop.isEnabled = false

            // Start scanning so roll numbers become available!
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
                },
                onScanFailure = { err: Int ->
                    runOnUiThread {
                        Toast.makeText(this, "Scan failed: $err", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            scannerHelper?.startScanning()
            startCleanup()
        } else {
            // Phase 2: End helper selection and continue scanning for attendance
            // Phase 2: End helper selection and continue scanning for attendance
            isSelectingHelpers = false
            btnStart.text = "Start Attendance"
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvRole.text = "Scanning in progress - Students should advertise now"
            sendHelperNotifications()


            // Optionally, store selected helpers to Supabase here
            // (Continue scanning, or restart if needed)
        }

    }


    private fun handleTeacherStop() {
        // Existing grace period logic (PRESERVED)
        if (isInGracePeriod) return

        Log.d("Attendance", "Teacher pressed STOP – entering grace period")

        btnStart.isEnabled = false
        btnStop.isEnabled = false
        bleCodeContainer.isEnabled = false

        gracePeriodRolls.clear()
        isInGracePeriod = true
        Toast.makeText(this, "Grace period started (5 s)… collecting final attendance", Toast.LENGTH_SHORT).show()

        object : CountDownTimer(5_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                tvRole.text = getString(R.string.grace_remaining, millisUntilFinished / 1_000)
                Log.d("Attendance", "Grace period countdown: ${millisUntilFinished / 1000}s")
            }

            override fun onFinish() {
                isInGracePeriod = false
                scannerHelper?.stopScanning()
                stopCleanup()

                val finalSet = presentRolls + gracePeriodRolls
                exportSessionCsv(finalSet)

                presentRolls += gracePeriodRolls
                adapter.clear()
                adapter.addAll(presentRolls.sorted())
                adapter.notifyDataSetChanged()
                updateRollCount()

                Log.d("Attendance", "Grace ended. Final rolls: $presentRolls")

                tvRole.text = getString(R.string.attendance_as, role)
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                bleCodeContainer.isEnabled = true

                Toast.makeText(this@AttendanceActivity, "Attendance finalised (${presentRolls.size} students)", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // CORE BLE FUNCTIONALITY (PRESERVED)

    private fun loadMapping() {
        val jsonStream: InputStream = assets.open("BLEcode_rollnumber.json")
        val text = jsonStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val map = mutableMapOf<Int, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val code = (key as? String)?.toIntOrNull() // FIXED
            if (code != null) {
                map[code] = obj.getString(key as String) // SAFE
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
                                adapter.clear()
                                adapter.addAll(presentRolls.sorted())
                                adapter.notifyDataSetChanged()
                                updateRollCount()
                                // TODO: Update status when advanced UI is ready
                                // tvStatus.text = "Found ${presentRolls.size} students"
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

    // CSV EXPORT AND NOTIFICATIONS (PRESERVED)

    private fun exportSessionCsv(rolls: Collection<String>) {
        val csvFile = createNextSessionCsv()

        FileWriter(csvFile).use { w ->
            w.appendLine("Roll Number")
            rolls.sorted().forEach { w.appendLine(it) }
        }

        Log.d("Attendance", "CSV saved at ${csvFile.absolutePath}")
        Toast.makeText(this, "CSV saved:\n${csvFile.absolutePath}", Toast.LENGTH_LONG).show()

        sendAttendancePush(csvFile)
    }

    private fun sendAttendancePush(csvFile: File) {
        val rolls = csvFile.readLines()
            .drop(1)
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        if (rolls.isEmpty()) {
            Log.w("AttendancePush", "No rolls to notify in CSV")
            return
        }

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

        val payload = mapOf(
            "app_id" to "5707627c-23d3-41da-8d32-309113db8718",
            "filters" to filters,
            "headings" to mapOf("en" to "Attendance Confirmed {{roll}}"),
            "contents" to mapOf("en" to "✅ Your attendance is confirmed")
        )

        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(Map::class.java)
        val jsonBody = jsonAdapter.toJson(payload)

        Log.d("AttendancePush", "Starting push: rolls=$rolls")

        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://onesignal.com/api/v1/notifications")
            .addHeader("Authorization", "os_v2_app_k4dwe7bd2na5vdjsgcirhw4hdcl4kaojncseofffamdip4qnke6ptolxwisrlfkiyueihhtokj6e5ar5ztnvzxebxjuvja3pbxdk7cy")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

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

    private fun createNextSessionCsv(): File {
        val sessionsDir = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }
        val datePart = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val existingToday = sessionsDir.listFiles { _, name ->
            name.startsWith(datePart) && name.endsWith(".csv")
        } ?: emptyArray()

        val highestOrdinal = existingToday
            .mapNotNull { file ->
                Regex("""${Regex.escape(datePart)}-(\d+)\.csv""")
                    .find(file.name)
                    ?.groupValues?.get(1)
                    ?.toInt()
            }
            .maxOrNull() ?: 0

        val nextOrdinal = highestOrdinal + 1
        val newFileName = "$datePart-$nextOrdinal.csv"

        return File(sessionsDir, newFileName)
    }

    // UTILITY FUNCTIONS (PRESERVED)

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
                (key as? String)?.toIntOrNull()?.let { code -> code to obj.getString(key as String) } // FIXED
            }
            .toMap()
    }


    private fun updateRollCount() {
        val visibleCount = presentRolls.size
        tvRollCount.text = "roll numbers $visibleCount visible"
    }

    // NEW: Advanced utility functions (TODO: Implement when services are ready)

    // private fun isScanner(): Boolean {
    //     return studentRollNumber.isNotEmpty() &&
    //            scanningAccessManager.getActiveScanningAccess(studentRollNumber).isNotEmpty()
    // }

    // private fun getDeviceId(): String {
    //     return android.provider.Settings.Secure.getString(
    //         contentResolver,
    //         android.provider.Settings.Secure.ANDROID_ID
    //     ) ?: "unknown_device"
    // }

    override fun onDestroy() {
        // Cleanup BLE (PRESERVED)
        scannerHelper?.stopScanning()
        advertiserHelper?.stopAdvertising()

        // Cleanup handlers (PRESERVED)
        stopCleanup()

        // NEW: Disconnect Socket.IO (TODO: Implement when SocketIOService is ready)
        // socketService.disconnect()

        // --- Unregister broadcast receiver (for helper access updates) ---
        try {
            unregisterReceiver(helperAccessReceiver)
        } catch (e: Exception) {
            // Ignore if receiver was not registered
        }

        Log.d("AttendanceActivity", "Activity destroyed - cleaned up resources")
        super.onDestroy()
    }


    private fun sendHelperNotifications() {
        if (selectedHelpers.isEmpty()) return

        val filters = mutableListOf<Map<String, Any>>()
        selectedHelpers.forEachIndexed { index, rollNumber ->
            if (index > 0) {
                filters.add(mapOf("operator" to "OR"))
            }
            filters.add(
                mapOf(
                    "field" to "tag",
                    "key" to "roll",
                    "relation" to "=",
                    "value" to rollNumber
                )
            )
        }

        val payload = mapOf(
            "app_id" to "5707627c-23d3-41da-8d32-309113db8718",
            "filters" to filters,
            "headings" to mapOf("en" to "You're a Helper! 👥"),
            "contents" to mapOf("en" to "You've been selected to help with attendance scanning for 5 minutes. Check your app!"),
            "data" to mapOf(
                "type" to "helper_access",
                "expires_at" to (System.currentTimeMillis() + (5 * 60 * 1000)).toString()
            )
        )

        Thread {
            try {
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(Map::class.java)
                val jsonBody = jsonAdapter.toJson(payload)

                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://onesignal.com/api/v1/notifications")
                    .addHeader("Authorization", "os_v2_app_k4dwe7bd2na5vdjsgcirhw4hdcl4kaojncseofffamdip4qnke6ptolxwisrlfkiyueihhtokj6e5ar5ztnvzxebxjuvja3pbxdk7cy")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("HelperNotifications", "Helper notifications sent: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("HelperNotifications", "Failed to send helper notifications", e)
            }
        }.start()
    }

    private fun saveHelpersToSupabase(sessionId: String) {
        // TODO: Replace with actual Supabase service calls when ready
        lifecycleScope.launch {
            selectedHelpers.forEach { rollNumber ->
                try {
                    // Create helper record in session_helpers table
                    val helperData = mapOf(
                        "session_id" to sessionId,
                        "student_roll" to rollNumber,
                        "access_granted_at" to System.currentTimeMillis(),
                        "access_expires_at" to (System.currentTimeMillis() + (5 * 60 * 1000)),
                        "status" to "active"
                    )

                    // supabaseService.insertSessionHelper(helperData)
                    Log.d("Attendance", "Helper saved to Supabase: $rollNumber")

                } catch (e: Exception) {
                    Log.e("Attendance", "Failed to save helper $rollNumber to Supabase", e)
                }
            }
        }
    }


    private fun startHelperBleScanning() {
        Log.d("HelperScan", "startHelperBleScanning invoked")

        scannedStudents.clear()
        adapter.clear()
        scannerHelper = BleScannerHelper(
            context = this,
            serviceUuid = serviceUuid,
            onDeviceFound = { code: Int ->
                val roll = codeToRoll[code]
                Log.d("HelperScan", "onDeviceFound code=$code → roll=$roll") // <--- ADD THIS LINE
                if (roll != null && !scannedStudents.contains(roll)) {
                    scannedStudents.add(roll)
                    runOnUiThread {
                        adapter.clear()
                        adapter.addAll(scannedStudents.sorted())
                        adapter.notifyDataSetChanged()
                        updateRollCount()
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

    private fun stopHelperBleScanningAndSendRolls() {
        scannerHelper?.stopScanning()
        btnStopGroupScanning.visibility = View.GONE
        btnStartGroupScanning.isEnabled = true // Allow restart if needed (optional)

        val rollList = scannedStudents.toList()
        val sessionBlockName = "TEST_ROOM_101" // TEMPORARY, replace later!
        sendHelperRollsToSupabase(sessionBlockName, rollList)
    }

    private fun sendHelperRollsToSupabase(sessionBlockName: String, rolls: List<String>) {
        // Use your Supabase SDK, REST, or HTTP client
        Log.d("SupabaseUpload", "Sending rolls: $rolls to block: $sessionBlockName")
        // TODO: Implement actual upload to Supabase!
        // E.g. supabaseService.uploadSessionBlock(sessionBlockName, rolls)
        Toast.makeText(this, "Sent ${rolls.size} rolls to Supabase (block=$sessionBlockName)", Toast.LENGTH_LONG).show()
    }


}
