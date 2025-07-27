package com.svce.attendance.utils


import android.content.Context

object JsonUtils {
    fun loadJsonFromAssets(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
