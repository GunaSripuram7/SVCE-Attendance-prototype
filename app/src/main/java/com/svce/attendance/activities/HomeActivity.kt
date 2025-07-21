package com.svce.attendance.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.svce.attendance.R
import android.widget.Button
import android.widget.TextView
import android.content.Intent
import android.widget.ImageView
import android.widget.Toast

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svce.attendance.utils.SessionStore
import com.svce.attendance.utils.SessionAdapter
import com.svce.attendance.utils.AttendanceSession
import com.google.gson.Gson
import android.view.View

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val role = intent.getStringExtra("role") ?: "Unknown"
        val tvHomeRole = findViewById<TextView>(R.id.tvHomeRole)
        tvHomeRole.text = getString(R.string.home_as, role)

        // Set up Take/Give Attendance button as appropriate (ORIGINAL)
        val btnAttendance = findViewById<Button>(R.id.btnAttendance)
        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"

        // Set up attendance button click to open AttendanceActivity
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
        }

        // Optional: Handle profile image click
        val ivProfile = findViewById<ImageView?>(R.id.ivProfile)
        ivProfile?.setOnClickListener {
            Toast.makeText(this, "Profile: $role (email to be shown)", Toast.LENGTH_SHORT).show()
        }

        // Optionally set visibility of the sessionList view here as well
        val recycler = findViewById<RecyclerView>(R.id.sessionList)
        if (role == "teacher") {
            recycler.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.GONE
        }
    }

    // Always reload and display sessions when returning home
    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        val recycler = findViewById<RecyclerView>(R.id.sessionList)
        if (role == "teacher") {
            recycler.visibility = View.VISIBLE
            val sessions = SessionStore.getAllSessions(this)
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = SessionAdapter(sessions) { session ->
                val intent = Intent(this, SessionDetailActivity::class.java)
                intent.putExtra("session", Gson().toJson(session))
                startActivity(intent)
            }
        } else {
            recycler.visibility = View.GONE
        }
    }
}
