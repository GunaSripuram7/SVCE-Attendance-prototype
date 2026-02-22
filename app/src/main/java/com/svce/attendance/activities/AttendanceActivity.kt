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
import com.svce.attendance.utils.CustomDeviceFingerprint
import com.svce.attendance.ble.StudentPayload
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await



import java.io.BufferedReader
import java.io.FileReader

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class AttendanceActivity : AppCompatActivity() {

    private val serviceUuid = UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb")
    private var scannerHelper: BleScannerHelper? = null
    private var advertiserHelper: BleAdvertiserHelper? = null


    private lateinit var tvRollCount: TextView

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvRole: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var isInGracePeriod = false


    private lateinit var codeToRollMap: Map<Int, String>
    // Set of currently present roll numbers

    // Add these new properties for proxy detection
    data class ScannedStudent(
        val payload: StudentPayload,
        val rollNumber: String,
        val timestamp: Long = System.currentTimeMillis(),
        var isProxy: Boolean = false,
        var proxyReason: String = ""
    )

    private val scannedStudents = mutableListOf<ScannedStudent>()
    private val seenAndroidIds = mutableSetOf<Int>()
    private val seenDeviceFingerprints = mutableSetOf<Int>()
    private val rollHashToRollMap = mutableMapOf<Int, String>()


    // Map to track last seen time of each roll number (millis)


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


    private lateinit var role: String

    private lateinit var btnRefreshRolls: Button
    private lateinit var db: FirebaseFirestore




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_attendance)
        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
        }
        // Request notification permission on Android 13+ devices



        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvRole = findViewById(R.id.tvAttendanceRole)
        listView = findViewById(R.id.listRolls)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter
        tvRollCount = findViewById(R.id.tvRollCount)

        btnRefreshRolls = findViewById(R.id.btnRefreshRolls)
        db = Firebase.firestore




        // Final list UI components
        val tvScanningLabel = findViewById<TextView>(R.id.tvScanningLabel)






        role = intent.getStringExtra("role") ?: ""
        tvRole.text = getString(R.string.attendance_as, role)



        loadMapping()
        loadBleCodeRollMap()

        // Auto-sync roll numbers for teachers (moved here after role is set)
        if (role == "teacher") {
            syncRollNumbersFromFirestore()
        }


        if (role == "teacher") {
            // Teacher UI: No BLE input
            btnStart.isEnabled = true
            btnRefreshRolls.visibility = View.VISIBLE

            btnRefreshRolls.setOnClickListener {
                syncRollNumbersFromFirestore()
            }
        } else {
            // Student UI
            btnStart.isEnabled = true
            btnRefreshRolls.visibility = View.GONE
        }


        btnStart.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener

            if (role == "teacher") {
                startScanning() // ONLY scan
            } else if (role == "student") {
                val rollNumber = intent.getStringExtra("rollNumber")
                    ?: getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_roll", "")
                    ?: ""

                if (rollNumber.isEmpty()) {
                    Toast.makeText(this, "Roll number not found. Please login again.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Set OneSignal tag immediately when student enters attendance
                OneSignal.User.addTag("roll", rollNumber)
                Log.d("OneSignal", "Set roll tag early: $rollNumber")


                // Always stop any existing advertiser before starting a new one
                advertiserHelper?.stopAdvertising()
                advertiserHelper = null
                advertiserHelper = BleAdvertiserHelper(this, serviceUuid)

                // Use new method with roll number and fingerprint
                advertiserHelper?.startAdvertisingWithRollAndFingerprint(
                    context = this,
                    rollNumber = rollNumber,
                    onSuccess = {
                        runOnUiThread {
                            tvRole.text = "Advertising roll: $rollNumber"
                        }
                        Toast.makeText(this, "Advertising started for roll $rollNumber", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorCode ->
                        runOnUiThread {
                            Toast.makeText(this, "Advertising failed with error code $errorCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }




            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }



        var isInGracePeriod = false
        btnStop.setOnClickListener {

            /********  TEACHER  *********/
            if (role == "teacher") {
                Log.d("Attendance", "Teacher pressed STOP – finalizing attendance")

                // Stop BLE scan immediately
                scannerHelper?.stopScanning()

                // Collect all non-proxy student roll numbers
                val validRolls = scannedStudents
                    .filter { !it.isProxy }
                    .map { it.rollNumber }

                // Launch the final attendance screen
                val intent = Intent(this, FinalAttendanceActivity::class.java).apply {
                    putStringArrayListExtra("finalRolls", ArrayList(validRolls))
                }
                startActivity(intent)
                finish()

                /********  STUDENT  *********/
            } else if (role == "student") {
                advertiserHelper?.stopAdvertising()
                advertiserHelper = null

                Toast.makeText(
                    this,
                    "Student stopped broadcasting.",
                    Toast.LENGTH_SHORT
                ).show()

                btnStart.isEnabled = true
                btnStop.isEnabled = false
                tvRole.text = getString(R.string.attendance_as, role)
            }
        }





        btnStop.isEnabled = false

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (role == "student") {
                    advertiserHelper?.stopAdvertising()
                    advertiserHelper = null

                    Toast.makeText(this@AttendanceActivity, "BLE advertising stopped", Toast.LENGTH_SHORT).show()
                }
                finish()  // Close the activity
            }
        })
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
        val localFile = File(filesDir, "BLEcode_rollnumber.json")

        if (localFile.exists()) {
            // Load from local file
            loadMappingFromFile(localFile)
        } else {
            // Load from assets and copy to local
            val jsonStream: InputStream = assets.open("BLEcode_rollnumber.json")
            val text = jsonStream.bufferedReader().use { it.readText() }
            localFile.writeText(text)
            loadMappingFromFile(localFile)
        }
    }





    private fun startScanning() {
        scannedStudents.clear()
        seenAndroidIds.clear()
        seenDeviceFingerprints.clear()
        adapter.clear()

        scannerHelper = BleScannerHelper(
            context = this,
            serviceUuid = serviceUuid,
            onStudentFound = { payload ->
                processStudentPayload(payload)
            },
            onScanFailure = { err: Int ->
                runOnUiThread {
                    Toast.makeText(this, "Scan failed: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )

        scannerHelper?.startScanning()

        if (role == "teacher") {
            findViewById<TextView>(R.id.tvScanningLabel).visibility = View.VISIBLE
        }
        // Remove startCleanup() call
    }


    private fun processStudentPayload(payload: StudentPayload) {
        // Get roll number from hash
        val rollNumber = rollHashToRollMap[payload.rollNumberHash] ?: "Unknown-${payload.rollNumberHash}"

        // Check for existing students with same Android ID or Fingerprint
        val existingStudentIndex = scannedStudents.indexOfFirst {
            it.payload.androidIdHash == payload.androidIdHash ||
                    it.payload.deviceFingerprintHash == payload.deviceFingerprintHash
        }

        if (existingStudentIndex != -1) {
            // Replace the existing student with the latest one (latest advertised roll)
            val existingStudent = scannedStudents[existingStudentIndex]
            Log.i("AttendanceActivity", "Replacing ${existingStudent.rollNumber} with $rollNumber (same device)")

            scannedStudents[existingStudentIndex] = ScannedStudent(payload, rollNumber, isProxy = false)
        } else {
            // New unique device - add to list
            val student = ScannedStudent(payload, rollNumber, isProxy = false)

            // Track this device's identifiers
            seenAndroidIds.add(payload.androidIdHash)
            seenDeviceFingerprints.add(payload.deviceFingerprintHash)
            scannedStudents.add(student)

            Log.i("AttendanceActivity", "Added new student: $rollNumber")
        }

        runOnUiThread {
            updateStudentList()
        }
    }



    private fun updateStudentList() {
        val displayList = scannedStudents.map { student ->
            val prefix = if (student.isProxy) "⭐ " else ""
            val suffix = if (student.isProxy) " (${student.proxyReason})" else ""
            "$prefix${student.rollNumber}$suffix"
        }

        adapter.clear()
        adapter.addAll(displayList)
        adapter.notifyDataSetChanged()
        updateRollCount()
    }

    // Update the existing updateRollCount method
    private fun updateRollCountWithProxy() {
        val totalCount = scannedStudents.size
        val proxyCount = scannedStudents.count { it.isProxy }
        tvRollCount.text = "Students: $totalCount (${proxyCount} flagged)"
    }





   /* private fun exportSessionCsv(rolls: Collection<String>) {
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
    } */



    // Call this at the END of exportSessionCsv()
    /*private fun sendAttendancePush(csvFile: File) {

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
    } */


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
        val totalCount = scannedStudents.size
        val validCount = scannedStudents.count { !it.isProxy }
        tvRollCount.text = "Students: $totalCount total, $validCount valid"
    }

    private fun syncRollNumbersFromFirestore() {
        btnRefreshRolls.isEnabled = false
        btnRefreshRolls.text = "🔄 Syncing..."

        lifecycleScope.launch {
            try {
                // Fetch all students with roll numbers from Firestore
                val snapshot = db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereNotEqualTo("rollNumber", "")
                    .get()
                    .await()

                val firebaseRolls = snapshot.documents.mapNotNull { doc ->
                    doc.getString("rollNumber")?.trim()?.takeIf { it.isNotEmpty() }
                }.distinct()

                Log.d("AttendanceActivity", "Retrieved ${firebaseRolls.size} roll numbers from Firebase")

                // Update local JSON file
                updateLocalJsonFile(firebaseRolls)

            } catch (e: Exception) {
                Log.e("AttendanceActivity", "Failed to sync roll numbers", e)
                runOnUiThread {
                    Toast.makeText(this@AttendanceActivity, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                runOnUiThread {
                    btnRefreshRolls.isEnabled = true
                    btnRefreshRolls.text = "🔄 Refresh Roll Numbers"
                }
            }
        }
    }

    private fun updateLocalJsonFile(firebaseRolls: List<String>) {
        try {
            // Read current local JSON
            val localFile = File(filesDir, "BLEcode_rollnumber.json")
            val currentJson = if (localFile.exists()) {
                JSONObject(localFile.readText())
            } else {
                // Copy from assets if local doesn't exist
                val assetText = assets.open("BLEcode_rollnumber.json").bufferedReader().use { it.readText() }
                localFile.writeText(assetText)
                JSONObject(assetText)
            }

            // Get existing roll numbers
            val existingRolls = mutableSetOf<String>()
            val keys = currentJson.keys()
            while (keys.hasNext()) {
                val rollNumber = currentJson.getString(keys.next())
                existingRolls.add(rollNumber)
            }

            // Find new roll numbers to add
            val newRolls = firebaseRolls.filterNot { existingRolls.contains(it) }

            if (newRolls.isNotEmpty()) {
                // Find the highest existing key number
                val maxKey = currentJson.keys().asSequence()
                    .mapNotNull { it.toIntOrNull() }
                    .maxOrNull() ?: 0

                // Add new roll numbers
                var nextKey = maxKey + 1
                newRolls.forEach { rollNumber ->
                    currentJson.put(nextKey.toString(), rollNumber)
                    nextKey++
                }

                // Write updated JSON to local file
                localFile.writeText(currentJson.toString(2))

                // Reload mappings in memory
                loadMappingFromFile(localFile)

                Log.d("AttendanceActivity", "Added ${newRolls.size} new roll numbers to local JSON")
                runOnUiThread {
                    Toast.makeText(this@AttendanceActivity, "Added ${newRolls.size} new roll numbers", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("AttendanceActivity", "No new roll numbers to add")
                runOnUiThread {
                    Toast.makeText(this@AttendanceActivity, "All roll numbers are up to date", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Failed to update local JSON", e)
            runOnUiThread {
                Toast.makeText(this@AttendanceActivity, "Failed to update local data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMappingFromFile(file: File) {
        val text = file.readText()
        val obj = JSONObject(text)
        val map = mutableMapOf<Int, String>()
        val hashMap = mutableMapOf<Int, String>()

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val code = key.toIntOrNull()
            val rollNumber = obj.getString(key)

            if (code != null) {
                map[code] = rollNumber
            }

            // Create hash mapping for roll numbers
            val rollHash = CustomDeviceFingerprint.getRollNumberHash(rollNumber)
            hashMap[rollHash] = rollNumber
        }

        codeToRoll = map
        rollHashToRollMap.clear()
        rollHashToRollMap.putAll(hashMap)

        Log.d("AttendanceActivity", "Reloaded ${rollHashToRollMap.size} roll number hash mappings")
    }

}  // ← This is the final closing brace

