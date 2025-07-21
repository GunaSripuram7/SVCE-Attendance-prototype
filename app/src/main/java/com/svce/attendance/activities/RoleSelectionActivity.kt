package com.svce.attendance.activities
import com.svce.attendance.R
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class RoleSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        val teacherBtn = findViewById<Button>(R.id.btnTeacher)
        val studentBtn = findViewById<Button>(R.id.btnStudent)

        teacherBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("role", "teacher")
            startActivity(intent)
        }

        studentBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("role", "student")
            startActivity(intent)
        }
    }
}
