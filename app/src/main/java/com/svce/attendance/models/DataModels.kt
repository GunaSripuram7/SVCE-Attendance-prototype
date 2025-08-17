package com.svce.attendance.models

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AttendanceSession(
    val id: Long? = null,
    val session_uuid: String = UUID.randomUUID().toString(),
    val session_code: String,
    val teacher_id: String,
    val class_id: String? = null,
    val status: String = "helpers_selection", // helpers_selection, active, grace_period, completed
    val max_helpers: Int = 25,
    val created_at: String? = null,
    val scanning_ended_at: String? = null,
    val final_count: Int? = null
)

@Serializable
data class SessionHelper(
    val id: Long? = null,
    val session_code: String,
    val helper_roll_number: String,
    val status: String = "assigned", // assigned, active, completed
    val device_id: String? = null,
    val joined_at: String? = null
)

@Serializable
data class AttendanceRecord(
    val id: Long? = null,
    val session_code: String,
    val student_roll_number: String,
    val scanned_by_helper: String,
    val scanned_at: String? = null,
    val scanner_device_id: String? = null,
    val is_manual_entry: Boolean = false
)

@Serializable
data class ScanningAccess(
    val roll_number: String,
    val session_uuid: String,
    val granted_at: Long,
    val expires_at: Long,
    val is_active: Boolean = true
)