package com.svce.attendance.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SessionStore {
    private const val PREF_NAME = "attendance_sessions"
    private const val KEY_SESSIONS = "sessions_json"

    fun saveSession(context: Context, session: AttendanceSession) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val sessionList = getAllSessions(context).toMutableList()
        sessionList.add(0, session) // most recent first
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessionList)).apply()
    }

    fun getAllSessions(context: Context): List<AttendanceSession> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_SESSIONS, "[]")
        val type = object : TypeToken<List<AttendanceSession>>() {}.type
        return gson.fromJson(json, type)
    }
}
