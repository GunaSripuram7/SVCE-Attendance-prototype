package com.svce.attendance.utils

data class AttendanceSession(
    val timestamp: Long,
    val formattedTime: String,
    val rollNumbers: List<String>,
    val confirmations: Map<String, Boolean> = emptyMap() // rollNo -> true if confirmed via broadcast
)
