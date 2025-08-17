package com.svce.attendance.services

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

class SocketIOService private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: SocketIOService? = null

        fun getInstance(): SocketIOService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketIOService().also { INSTANCE = it }
            }
        }
    }

    private var socket: Socket? = null
    private val TAG = "SocketIOService"

    // Replace with your Socket.IO server URL
    private val SERVER_URL = "https://your-socketio-server.herokuapp.com"

    fun connect(onConnected: () -> Unit = {}) {
        try {
            val uri = URI.create(SERVER_URL)
            socket = IO.socket(uri).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket connected")
                    onConnected()
                }
                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket disconnected")
                }
                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "Connection error: ${args.joinToString()}")
                }
            }
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect socket", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    // Teacher creates a new session
    fun createSession(sessionCode: String, teacherId: String, maxScanners: Int) {
        val data = JSONObject().apply {
            put("sessionCode", sessionCode)
            put("teacherId", teacherId)
            put("maxScanners", maxScanners)
        }
        socket?.emit("createSession", data)
    }

    // Teacher assigns helpers
    fun assignHelper(sessionCode: String, helperRollNumber: String) {
        val data = JSONObject().apply {
            put("sessionCode", sessionCode)
            put("helperRollNumber", helperRollNumber)
        }
        socket?.emit("assignHelper", data)
    }

    // Helper joins scanning session
    fun joinAsHelper(sessionCode: String, helperRollNumber: String, deviceId: String) {
        val data = JSONObject().apply {
            put("sessionCode", sessionCode)
            put("helperRollNumber", helperRollNumber)
            put("deviceId", deviceId)
        }
        socket?.emit("joinAsHelper", data)
    }

    // Request scan permission (conflict prevention)
    fun requestScanPermission(
        sessionCode: String,
        helperId: String,
        detectedRollNumber: String
    ) {
        val data = JSONObject().apply {
            put("sessionCode", sessionCode)
            put("helperId", helperId)
            put("detectedRollNumber", detectedRollNumber)
            put("timestamp", System.currentTimeMillis())
        }
        socket?.emit("requestScanPermission", data)
    }

    // Submit scanned student
    fun submitScannedStudent(
        sessionCode: String,
        rollNumber: String,
        scannedBy: String
    ) {
        val data = JSONObject().apply {
            put("sessionCode", sessionCode)
            put("rollNumber", rollNumber)
            put("scannedBy", scannedBy)
        }
        socket?.emit("studentScanned", data)
    }

    // Event listeners
    fun onHelperJoined(callback: (String) -> Unit) {
        socket?.on("helperJoined") { args ->
            try {
                val data = args[0] as JSONObject
                val helperRollNumber = data.getString("helperRollNumber")
                callback(helperRollNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing helperJoined", e)
            }
        }
    }

    fun onScanPermissionResponse(callback: (String, Boolean, String?) -> Unit) {
        socket?.on("scanPermissionResponse") { args ->
            try {
                val data = args[0] as JSONObject
                val rollNumber = data.getString("rollNumber")
                val granted = data.getBoolean("granted")
                val reason = data.optString("reason", null)
                callback(rollNumber, granted, reason)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing scanPermissionResponse", e)
            }
        }
    }

    fun onAttendanceUpdate(callback: (Int, String, String) -> Unit) {
        socket?.on("attendanceUpdate") { args ->
            try {
                val data = args[0] as JSONObject
                val totalScanned = data.getInt("totalScanned")
                val latestStudent = data.getString("latestStudent")
                val scannedBy = data.getString("scannedBy")
                callback(totalScanned, latestStudent, scannedBy)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing attendanceUpdate", e)
            }
        }
    }

    fun onScanningStarted(callback: () -> Unit) {
        socket?.on("scanningStarted") {
            callback()
        }
    }

    fun onScanningEnded(callback: (Int, Int) -> Unit) {
        socket?.on("scanningEnded") { args ->
            try {
                val data = args[0] as JSONObject
                val finalCount = data.getInt("finalCount")
                val gracePeriodSeconds = data.getInt("gracePeriodSeconds")
                callback(finalCount, gracePeriodSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing scanningEnded", e)
            }
        }
    }
}