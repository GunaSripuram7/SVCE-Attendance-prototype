package com.svce.attendance.utils // or .models

data class AttendanceSession(
    val timestamp: Long,
    val formattedTime: String,
    val rollNumbers: List<String>
)
