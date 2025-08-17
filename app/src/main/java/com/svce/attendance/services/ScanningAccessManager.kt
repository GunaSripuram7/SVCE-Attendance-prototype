package com.svce.attendance.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.svce.attendance.models.ScanningAccess
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

class ScanningAccessManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: ScanningAccessManager? = null

        fun getInstance(context: Context): ScanningAccessManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanningAccessManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val PREFS_NAME = "scanning_access"
        private const val KEY_ACTIVE_SESSIONS = "active_sessions"
        private const val ACCESS_DURATION_MINUTES = 5L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val TAG = "ScanningAccessManager"

    fun grantScanningAccess(rollNumber: String, sessionUuid: String): ScanningAccess {
        val now = System.currentTimeMillis()
        val expiresAt = now + TimeUnit.MINUTES.toMillis(ACCESS_DURATION_MINUTES)

        val access = ScanningAccess(
            roll_number = rollNumber,
            session_uuid = sessionUuid,
            granted_at = now,
            expires_at = expiresAt,
            is_active = true
        )

        saveAccessToPrefs(access)
        Log.d(TAG, "Granted scanning access to $rollNumber for session $sessionUuid")

        return access
    }

    fun hasActiveScanningAccess(rollNumber: String, sessionUuid: String): Boolean {
        val activeSessions = getActiveAccessSessions()
        val access = activeSessions.find {
            it.roll_number == rollNumber &&
                    it.session_uuid == sessionUuid &&
                    it.is_active
        }

        return if (access != null && access.expires_at > System.currentTimeMillis()) {
            true
        } else {
            // Clean up expired access
            if (access != null) {
                revokeScanningAccess(rollNumber, sessionUuid)
            }
            false
        }
    }

    fun getActiveScanningAccess(rollNumber: String): List<ScanningAccess> {
        val activeSessions = getActiveAccessSessions()
        val now = System.currentTimeMillis()

        return activeSessions.filter { access ->
            access.roll_number == rollNumber &&
                    access.is_active &&
                    access.expires_at > now
        }
    }

    fun revokeScanningAccess(rollNumber: String, sessionUuid: String) {
        val activeSessions = getActiveAccessSessions().toMutableList()
        val index = activeSessions.indexOfFirst {
            it.roll_number == rollNumber && it.session_uuid == sessionUuid
        }

        if (index != -1) {
            activeSessions[index] = activeSessions[index].copy(is_active = false)
            saveActiveAccessSessions(activeSessions)
            Log.d(TAG, "Revoked scanning access for $rollNumber in session $sessionUuid")
        }
    }

    fun getRemainingTimeMinutes(rollNumber: String, sessionUuid: String): Long {
        val access = getActiveAccessSessions().find {
            it.roll_number == rollNumber &&
                    it.session_uuid == sessionUuid &&
                    it.is_active
        }

        return if (access != null && access.expires_at > System.currentTimeMillis()) {
            TimeUnit.MILLISECONDS.toMinutes(access.expires_at - System.currentTimeMillis())
        } else {
            0L
        }
    }

    fun getRemainingTimeSeconds(rollNumber: String, sessionUuid: String): Long {
        val access = getActiveAccessSessions().find {
            it.roll_number == rollNumber &&
                    it.session_uuid == sessionUuid &&
                    it.is_active
        }

        return if (access != null && access.expires_at > System.currentTimeMillis()) {
            TimeUnit.MILLISECONDS.toSeconds(access.expires_at - System.currentTimeMillis())
        } else {
            0L
        }
    }

    fun cleanupExpiredSessions() {
        val activeSessions = getActiveAccessSessions()
        val now = System.currentTimeMillis()
        val validSessions = activeSessions.filter { it.expires_at > now }

        if (validSessions.size != activeSessions.size) {
            saveActiveAccessSessions(validSessions)
            Log.d(TAG, "Cleaned up ${activeSessions.size - validSessions.size} expired sessions")
        }
    }

    private fun saveAccessToPrefs(access: ScanningAccess) {
        val activeSessions = getActiveAccessSessions().toMutableList()

        // Remove existing access for same roll number and session
        activeSessions.removeAll {
            it.roll_number == access.roll_number && it.session_uuid == access.session_uuid
        }

        // Add new access
        activeSessions.add(access)
        saveActiveAccessSessions(activeSessions)
    }

    private fun getActiveAccessSessions(): List<ScanningAccess> {
        val json = prefs.getString(KEY_ACTIVE_SESSIONS, "[]")
        val type = object : TypeToken<List<ScanningAccess>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse active sessions", e)
            emptyList()
        }
    }

    private fun saveActiveAccessSessions(sessions: List<ScanningAccess>) {
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_ACTIVE_SESSIONS, json).apply()
    }

    fun grantHelperAccess(rollNumber: String, expiresAt: Long): ScanningAccess {
        val sessionUuid = "helper_${System.currentTimeMillis()}"
        val access = ScanningAccess(
            roll_number = rollNumber,
            session_uuid = sessionUuid,
            granted_at = System.currentTimeMillis(),
            expires_at = expiresAt,
            is_active = true
        )
        saveAccessToPrefs(access)
        Log.d(TAG, "Granted helper access to $rollNumber until $expiresAt")
        return access
    }

}