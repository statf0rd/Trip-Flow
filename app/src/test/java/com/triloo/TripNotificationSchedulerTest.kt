package com.triloo

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.notifications.TripNotificationScheduler
import com.triloo.data.notifications.TripReminderWorker
import com.triloo.data.settings.AppSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripNotificationSchedulerTest {

    private lateinit var context: android.content.Context
    private lateinit var database: TrilooDatabase
    private lateinit var scheduler: TripNotificationScheduler
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        )
        database = Room.inMemoryDatabaseBuilder(context, TrilooDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.setNotificationsEnabled(true)
        scheduler = TripNotificationScheduler(
            context = context,
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            settingsRepository = settingsRepository
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork()
        database.close()
    }

    @Test
    fun syncTripSchedulesDayPlaceAndGapNotifications() = runBlocking {
        val tripDate = LocalDate.now().plusDays(1)
        val trip = Trip(
            id = "trip-notify",
            name = "Madrid",
            destination = "Spain",
            startDate = tripDate,
            endDate = tripDate,
            baseCurrency = "EUR",
            isGroupTrip = true
        )
        val day = TripDay(
            id = "day-notify",
            tripId = trip.id,
            date = tripDate,
            dayNumber = 1
        )
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(day)
        database.placeDao().insertPlaces(
            listOf(
                Place(
                    id = "place-breakfast",
                    tripId = trip.id,
                    tripDayId = day.id,
                    name = "Breakfast",
                    latitude = 40.4168,
                    longitude = -3.7038,
                    category = PlaceCategory.CAFE,
                    scheduledTime = "10:00",
                    estimatedDuration = 60,
                    orderIndex = 0
                ),
                Place(
                    id = "place-museum",
                    tripId = trip.id,
                    tripDayId = day.id,
                    name = "Museum",
                    latitude = 40.4178,
                    longitude = -3.7048,
                    category = PlaceCategory.MUSEUM,
                    scheduledTime = "13:30",
                    estimatedDuration = 90,
                    orderIndex = 1
                )
            )
        )

        scheduler.syncTrip(trip.id)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag("${TripReminderWorker.GENERAL_TAG}_${trip.id}")
            .get()

        assertEquals(4, workInfos.size)
        assertTrue(workInfos.all { it.state == WorkInfo.State.ENQUEUED })
    }
}
