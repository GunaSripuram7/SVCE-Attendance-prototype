package com.svce.attendance.services

import android.util.Log
import com.svce.attendance.models.AttendanceRecord
import com.svce.attendance.models.AttendanceSession
import com.svce.attendance.models.SessionHelper
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseService {
    private val TAG = "SupabaseService"

    suspend fun createSession(session: AttendanceSession): AttendanceSession? = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("attendance_sessions")
                .insert(session)
                .decodeSingle<AttendanceSession>()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create session", e)
        null
    }

    suspend fun addHelper(helper: SessionHelper): Boolean = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("session_helpers")
                .insert(helper)
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to add helper", e)
        false
    }

    suspend fun getHelpersForSession(sessionCode: String): List<SessionHelper> = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("session_helpers")
                .select {
                    filter { eq("session_code", sessionCode) }
                }
                .decodeList<SessionHelper>()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get helpers", e)
        emptyList()
    }

    suspend fun insertAttendanceRecord(record: AttendanceRecord): Boolean = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("attendance_records")
                .insert(record)
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to insert attendance record", e)
        false
    }

    suspend fun getAttendanceForSession(sessionCode: String): List<AttendanceRecord> = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("attendance_records")
                .select {
                    filter { eq("session_code", sessionCode) }
                }
                .decodeList<AttendanceRecord>()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get attendance records", e)
        emptyList()
    }

    suspend fun updateSessionStatus(sessionCode: String, status: String): Boolean = try {
        withContext(Dispatchers.IO) {
            SupabaseConfig.client
                .from("attendance_sessions")
                .update(
                    mapOf(
                        "status" to status,
                        // Optionally update scanning_ended_at if completed
                        "scanning_ended_at" to if (status == "completed") System.currentTimeMillis() else null
                    )
                ) {
                    filter { eq("session_code", sessionCode) }
                }
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update session status", e)
        false
    }

    suspend fun checkHelperAccess(rollNumber: String, sessionCode: String): Boolean = try {
        withContext(Dispatchers.IO) {
            val helpers = SupabaseConfig.client
                .from("session_helpers")
                .select {
                    filter {
                        eq("helper_roll_number", rollNumber)
                        eq("session_code", sessionCode)
                    }
                }
                .decodeList<SessionHelper>()
            helpers.isNotEmpty()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to check helper access", e)
        false
    }
}
