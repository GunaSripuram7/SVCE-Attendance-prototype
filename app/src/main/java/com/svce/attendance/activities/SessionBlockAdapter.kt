package com.svce.attendance.activities

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.svce.attendance.R
import com.svce.attendance.activities.HomeActivity.SessionBlock

/**
 * Adapter for showing attendance sessions (columns) as blocks.
 * @param sessions List of SessionBlock objects (timestamp + present rolls)
 * @param mentorEmail The teacher's email for passing to detail activity
 */
class SessionBlockAdapter(
    private val context: Context,
    private val sessions: List<SessionBlock>,
    private val mentorEmail: String  // Added to fix unresolved reference
) : RecyclerView.Adapter<SessionBlockAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_session_block, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = sessions.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvTimestamp.text = session.timestamp
        // Fixed concatenation: Use string resource with placeholder
        holder.tvPresentCount.text = context.getString(R.string.present_count, session.presentRolls.size)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, PresentStudentsActivity::class.java).apply {
                putExtra("time", session.timestamp)
                putStringArrayListExtra("presentRolls", ArrayList(session.presentRolls))
                putExtra("email", mentorEmail)  // Pass teacher's email for CSV access
            }
            context.startActivity(intent)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvSessionTimestamp)
        val tvPresentCount: TextView = view.findViewById(R.id.tvPresentCount)
    }
}
