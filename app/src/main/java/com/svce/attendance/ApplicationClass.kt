package com.svce.attendance

import android.app.Application
import android.content.Intent
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationClickEvent

class ApplicationClass : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize OneSignal
        OneSignal.initWithContext(this, "5707627c-23d3-41da-8d32-309113db8718")

        // Set up notification click handler using the CORRECT 5.1.6 API
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                try {
                    val additionalData = event.notification.additionalData
                    Log.d("OneSignal", "Notification clicked with data: $additionalData")

                    if (additionalData != null && additionalData.has("type")) {
                        val type = additionalData.getString("type")
                        val expiresAt = additionalData.optString("expires_at", "0").toLongOrNull() ?: 0L

                        if (type == "helper_access") {
                            val timeLeft = expiresAt - System.currentTimeMillis()
                            if (timeLeft > 0) {
                                // Save helper access to SharedPreferences
                                val prefs = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                                prefs.edit()
                                    .putBoolean("is_helper_access_granted", true)
                                    .putLong("helper_access_expires_at", expiresAt)
                                    .apply()

                                // Broadcast to notify any listening activities
                                val broadcastIntent = Intent("com.svce.attendance.HELPER_ACCESS_GRANTED")
                                sendBroadcast(broadcastIntent)

                                Log.d("OneSignal", "Helper access granted via notification click")
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("OneSignal", "Failed to parse notification click data", e)
                }
            }
        })
    }
}
