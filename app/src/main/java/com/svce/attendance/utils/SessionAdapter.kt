package com.svce.attendance.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.svce.attendance.R

class SessionAdapter(
    private val sessions: List<AttendanceSession>,
    private val onItemClick: (AttendanceSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvCount: TextView = view.findViewById(R.id.tvCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_block, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvDate.text = session.formattedTime
        holder.tvCount.text = "${session.rollNumbers.size} present"
        holder.view.setOnClickListener { onItemClick(session) }
    }

    override fun getItemCount() = sessions.size
}
