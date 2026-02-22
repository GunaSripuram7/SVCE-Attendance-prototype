package com.svce.attendance.models

data class User(
    val uid: String = "",
    val email: String = "",
    val role: String = "", // "teacher" or "student"
    val rollNumber: String = "", // only for students
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
