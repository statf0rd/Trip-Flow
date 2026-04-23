package com.triloo

import android.app.Application
import com.triloo.BuildConfig
import com.triloo.data.notifications.NotificationChannels
import com.triloo.data.notifications.TripNotificationScheduler
import com.triloo.data.sync.OnlineSyncRepository
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Корневой класс приложения, который поднимает Hilt и регистрирует системные каналы уведомлений.
 */
@HiltAndroidApp
class TrilooApp : Application() {
    @Inject
    lateinit var tripNotificationScheduler: TripNotificationScheduler
    @Inject
    lateinit var onlineSyncRepository: OnlineSyncRepository

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.APP_MAPKIT_VIEW_ENABLED && BuildConfig.APP_MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.setApiKey(BuildConfig.APP_MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }
        NotificationChannels.createDefaultChannels(this)
        tripNotificationScheduler.syncAllTripsAsync()
        onlineSyncRepository.bootstrapSyncAsync()
    }
}
