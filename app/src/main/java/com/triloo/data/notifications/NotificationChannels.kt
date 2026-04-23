package com.triloo.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Создаёт и хранит идентификаторы системных каналов уведомлений TripFlow.
 */
object NotificationChannels {
    const val TRIP_REMINDERS = "trip_reminders"
    const val LOCATION_SHARING = "location_sharing"

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

        val locationSharingChannel = NotificationChannel(
            LOCATION_SHARING,
            "Фоновый геошаринг",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Показывает, когда Triloo делится вашим местоположением"
        }

        manager.createNotificationChannels(listOf(remindersChannel, locationSharingChannel))
    }
}
