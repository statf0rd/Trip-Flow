package com.triloo

import android.app.Application
import com.triloo.data.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrilooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createDefaultChannels(this)
    }
}


