package com.svce.attendance.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

class CustomDeviceFingerprint {
    companion object {
        fun getAndroidId(context: Context): String {
            return Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        }

        fun getDeviceFingerprint(context: Context): String {
            // Device Model + Build Number (without Android ID)
            val deviceModel = "${Build.BRAND}-${Build.MODEL}"
            val buildNumber = Build.DISPLAY
            return "$deviceModel:$buildNumber"
        }

        fun getAndroidIdHash(context: Context): Int {
            return getAndroidId(context).hashCode()
        }

        fun getDeviceFingerprintHash(context: Context): Int {
            return getDeviceFingerprint(context).hashCode()
        }

        fun getFullFingerprint(context: Context): String {
            return "${getAndroidId(context)}:${getDeviceFingerprint(context)}"
        }

        fun getRollNumberHash(rollNumber: String): Int {
            return rollNumber.hashCode()
        }
    }
}
