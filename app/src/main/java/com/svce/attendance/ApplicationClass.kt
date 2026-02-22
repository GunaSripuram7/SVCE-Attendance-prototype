package com.svce.attendance

import android.app.Application
import com.onesignal.OneSignal

class ApplicationClass : Application() {
    override fun onCreate() {
        super.onCreate()
        OneSignal.initWithContext(this, "5707627c-23d3-41da-8d32-309113db8718")

    }
}
