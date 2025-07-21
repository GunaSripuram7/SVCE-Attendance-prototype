package com.svce.attendance.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.svce.attendance.R
import android.widget.TextView
import android.widget.ListView
import android.widget.ArrayAdapter
import com.google.gson.Gson
import com.svce.attendance.utils.AttendanceSession


class SessionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        val sessionJson = intent.getStringExtra("session")
        val session = Gson().fromJson(sessionJson, AttendanceSession::class.java)

        findViewById<TextView>(R.id.tvDetailDate).text = session.formattedTime
        findViewById<TextView>(R.id.tvDetailCount).text = "${session.rollNumbers.size} present"

        val listView = findViewById<ListView>(R.id.listRollDetails)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, session.rollNumbers)
    }
}
