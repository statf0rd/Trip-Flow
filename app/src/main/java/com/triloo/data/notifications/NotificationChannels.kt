package com.triloo.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val TRIP_REMINDERS = "trip_reminders"

    fun createDefaultChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val remindersChannel = NotificationChannel(
            TRIP_REMINDERS,
            "Напоминания о поездке",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Планы на день и напоминания о местах"
        }

        manager.createNotificationChannel(remindersChannel)
    }
}
