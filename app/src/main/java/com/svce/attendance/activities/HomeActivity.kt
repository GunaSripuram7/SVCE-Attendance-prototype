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
import com.google.gson.Gson
import android.view.View

class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var btnAttendance: Button
    private lateinit var tvHomeRole: TextView
    private lateinit var ivProfile: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val role = intent.getStringExtra("role") ?: "Unknown"
        tvHomeRole = findViewById(R.id.tvHomeRole)
        tvHomeRole.text = getString(R.string.home_as, role)

        btnAttendance = findViewById(R.id.btnAttendance)
        btnAttendance.text = if (role == "teacher") "Take Attendance" else "Give Attendance"
        btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
        }

        ivProfile = findViewById(R.id.ivProfile)
        ivProfile.setOnClickListener {
            Toast.makeText(this, "Profile: $role (login/email planned)", Toast.LENGTH_SHORT).show()
        }

        recycler = findViewById(R.id.sessionList)
        if (role == "teacher") {
            recycler.visibility = View.VISIBLE
            recycler.layoutManager = LinearLayoutManager(this)
        } else {
            recycler.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val role = intent.getStringExtra("role") ?: "Unknown"
        // Refresh session list for teacher every time home is shown
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
