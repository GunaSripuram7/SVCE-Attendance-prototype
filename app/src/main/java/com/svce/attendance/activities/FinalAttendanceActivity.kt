package com.svce.attendance.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.svce.attendance.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi



class FinalAttendanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_final_attendance)

        val rolls = intent.getStringArrayListExtra("finalRolls") ?: arrayListOf()
        val listView = findViewById<ListView>(R.id.lvFinalRolls)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rolls)

        findViewById<Button>(R.id.btnSaveFinal).setOnClickListener {
            saveToFirestore(rolls)
        }
        findViewById<Button>(R.id.btnPingFinal).setOnClickListener {
            sendPushNotifications(rolls)
        }
    }

    private fun saveToFirestore(rolls: List<String>) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sessionId = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        db.collection("users")
            .document(userId)
            .collection("sessions")
            .document(sessionId)
            .set(mapOf("rolls" to rolls))
            .addOnSuccessListener {
                Toast.makeText(this, "Saved session $sessionId", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPushNotifications(rolls: List<String>) {
        if (rolls.isEmpty()) {
            Toast.makeText(this, "No students to notify", Toast.LENGTH_SHORT).show()
            return
        }

        // Build OneSignal filters array
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

        // Build JSON payload for OneSignal
        val payload = mapOf(
            "app_id" to "5707627c-23d3-41da-8d32-309113db8718",
            "filters" to filters,
            "headings" to mapOf("en" to "Attendance Confirmed"),
            "contents" to mapOf("en" to "✅ Your attendance is confirmed")
        )

        // Send the notification
        Thread {
            try {
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(Map::class.java)
                val jsonBody = jsonAdapter.toJson(payload)

                val client = okhttp3.OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)

                val request = okhttp3.Request.Builder()
                    .url("https://onesignal.com/api/v1/notifications")
                    .addHeader("Authorization", "os_v2_app_k4dwe7bd2na5vdjsgcirhw4hdcl4kaojncseofffamdip4qnke6ptolxwisrlfkiyueihhtokj6e5ar5ztnvzxebxjuvja3pbxdk7cy")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@FinalAttendanceActivity, "Notifications sent to ${rolls.size} students", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@FinalAttendanceActivity, "Failed to send notifications", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@FinalAttendanceActivity, "Error sending notifications: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

}
