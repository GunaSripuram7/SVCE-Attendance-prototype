package com.svce.attendance.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.svce.attendance.R
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PresentStudentsActivity : AppCompatActivity() {

    private lateinit var rvRollList: RecyclerView
    private lateinit var tvSessionTime: TextView
    private lateinit var btnAddManual: Button

    // Passed from HomeActivity via intent
    private lateinit var sessionTime: String
    private lateinit var presentRolls: ArrayList<String>
    private lateinit var mentorEmail: String  // Passed for CSV access

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_present_students)

        tvSessionTime = findViewById(R.id.tvSessionTime)
        rvRollList = findViewById(R.id.rvRollList)
        btnAddManual = findViewById(R.id.btnAddManual)  // Assumes this Button is in your XML

        sessionTime = intent.getStringExtra("time") ?: "Unknown Session"
        presentRolls = intent.getStringArrayListExtra("presentRolls") ?: arrayListOf()
        mentorEmail = intent.getStringExtra("email") ?: ""  // Get teacher's email

        tvSessionTime.text = "Session: $sessionTime"
        rvRollList.layoutManager = LinearLayoutManager(this)
        rvRollList.adapter = RollAdapter(presentRolls)

        // Add Manual Attendance button click
        btnAddManual.setOnClickListener {
            promptForManualRoll()
        }
    }

    private fun promptForManualRoll() {
        val input = EditText(this)
        input.hint = "Enter Roll Number"
        AlertDialog.Builder(this)
            .setTitle("Add Manual Attendance")
            .setMessage("Enter the roll number to mark as present for this session")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val roll = input.text.toString().trim()
                if (roll.isNotEmpty() && isAssignedRoll(roll)) {
                    markAsPresent(roll)
                    presentRolls.add(roll)  // Update local list
                    rvRollList.adapter?.notifyDataSetChanged()  // Refresh RecyclerView
    
                    // POST *after* updating the list
                    postAttendanceToSheet(sessionTime, presentRolls) { success ->
                        val message = if (success) "Attendance updated in Google Sheet!" else "Failed to update Google Sheet."
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                    Toast.makeText(this, "$roll marked as present", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid or unassigned roll number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Check if roll is assigned to this teacher
    private fun isAssignedRoll(roll: String): Boolean {
        val file = File(filesDir, "rolls/$mentorEmail.csv")
        try {
            CSVReader(FileReader(file)).use { reader ->
                val all = reader.readAll()
                return all.drop(1).any { it.getOrNull(0) == roll }
            }
        } catch (e: Exception) {
            return false
        }
    }

   private fun postAttendanceToSheet(session: String, rollNumbers: List<String>, onResult: (Boolean) -> Unit) {
    val url = "https://script.google.com/macros/s/AKfycbyq0ZxzXLEYYGwaKW_u4rqC9fn154H6tm1omXfWsap9f5mLZCDKpPrp9J5DfMstiMZh/exec"
    val client = OkHttpClient()

    val json = JSONObject()
    json.put("session", session)
    json.put("rollNumbers", JSONArray(rollNumbers))

    val body = RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        json.toString()
    )

    val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            runOnUiThread { onResult(false) }
        }

        override fun onResponse(call: Call, response: Response) {
            val isSuccess = response.isSuccessful && response.body?.string()?.contains("\"status\":\"success\"") == true
            runOnUiThread { onResult(isSuccess) }
        }
     })
    }

    // Update CSV: Find session column by timestamp and set "P" for the roll
    private fun markAsPresent(roll: String) {
        val file = File(filesDir, "rolls/$mentorEmail.csv")
        val allRows = CSVReader(FileReader(file)).readAll().toMutableList()

        // Find the column index for this session's timestamp
        val header = allRows[0]
        val colIdx = header.indexOfFirst { it == sessionTime }
        if (colIdx == -1) return  // Session not found (edge case)

        // Find the row for this roll number
        for (i in 1 until allRows.size) {
            if (allRows[i][0] == roll) {
                allRows[i][colIdx] = "P"
                break
            }
        }

        // Write back to file
        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeAll(allRows)
        }

        postAttendanceToSheet(sessionTime, presentRolls) { success ->
            val message = if (success) "Attendance updated in Google Sheet!" else "Failed to update Google Sheet."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    class RollAdapter(private val rolls: List<String>) : RecyclerView.Adapter<RollAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(v)
        }
        override fun getItemCount(): Int = rolls.size
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.txt.text = rolls[position]
        }
        class ViewHolder(v: android.view.View) : RecyclerView.ViewHolder(v) {
            val txt: TextView = v.findViewById(android.R.id.text1)
        }
    }
}
