package com.triloo.data.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.Place
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.model.TripStatus
import com.triloo.data.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class TripNotificationScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val settingsRepository: AppSettingsRepository
) {

    private val workManager = WorkManager.getInstance(context)
    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun syncAllTripsAsync() {
        schedulerScope.launch {
            syncAllTrips()
        }
    }

    fun syncTripAsync(tripId: String) {
        schedulerScope.launch {
            syncTrip(tripId)
        }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TripReminderWorker.GENERAL_TAG)
    }

    fun cancelTrip(tripId: String) {
        workManager.cancelAllWorkByTag(tripTag(tripId))
    }

    suspend fun syncAllTrips() {
        if (!settingsRepository.settings.first().notificationsEnabled) {
            cancelAll()
            return
        }

        cancelAll()
        tripDao.getAllTrips().forEach { trip ->
            scheduleTrip(trip)
        }
    }

    suspend fun syncTrip(tripId: String) {
        if (!settingsRepository.settings.first().notificationsEnabled) {
            cancelTrip(tripId)
            return
        }

        cancelTrip(tripId)
        val trip = tripDao.getTripById(tripId) ?: return
        scheduleTrip(trip)
    }

    private suspend fun scheduleTrip(trip: Trip) {
        if (trip.status == TripStatus.CANCELLED || trip.status == TripStatus.COMPLETED) return
        val today = LocalDate.now()
        val placesByDay = placeDao.getPlacesByTrip(trip.id)
            .groupBy { it.tripDayId }
        placeDao.getTripDays(trip.id)
            .filter { !it.date.isBefore(today) }
            .forEach { day ->
                val dayPlaces = placesByDay[day.id].orEmpty()
                scheduleDayPlan(trip, day, dayPlaces)
                schedulePlaceReminders(trip, day, dayPlaces)
                scheduleGapReminders(trip, day, dayPlaces)
            }
    }

    private fun scheduleDayPlan(
        trip: Trip,
        day: TripDay,
        dayPlaces: List<Place>
    ) {
        if (dayPlaces.isEmpty()) return

        val sortedPlaces = sortPlacesBySchedule(dayPlaces)
        val firstStart = sortedPlaces.firstNotNullOfOrNull { place ->
            scheduledDateTime(day.date, place.scheduledTime)
        }
        val preferredTime = firstStart
            ?.takeIf { it.toLocalTime().isBefore(LocalTime.of(8, 0)) }
            ?.minusHours(1)
            ?: LocalDateTime.of(day.date, LocalTime.of(8, 0))
        val triggerAt = resolveTriggerTime(
            preferredTime = preferredTime,
            fallbackEnd = firstStart ?: LocalDateTime.of(day.date, LocalTime.of(21, 0))
        ) ?: return

        enqueueReminder(
            workName = "trip_day_plan_${trip.id}_${day.date}",
            tripId = trip.id,
            triggerAt = triggerAt,
            data = Data.Builder()
                .putString(TripReminderWorker.KEY_TYPE, TripReminderWorker.TYPE_DAY_PLAN)
                .putString(TripReminderWorker.KEY_TRIP_ID, trip.id)
                .putString(TripReminderWorker.KEY_DAY_DATE, day.date.toString())
                .build()
        )
    }

    private fun schedulePlaceReminders(
        trip: Trip,
        day: TripDay,
        dayPlaces: List<Place>
    ) {
        dayPlaces.forEach { place ->
            val startAt = scheduledDateTime(day.date, place.scheduledTime) ?: return@forEach
            val triggerAt = resolveTriggerTime(
                preferredTime = startAt.minusMinutes(30),
                fallbackEnd = startAt
            ) ?: return@forEach

            enqueueReminder(
                workName = "trip_place_${place.id}",
                tripId = trip.id,
                triggerAt = triggerAt,
                data = Data.Builder()
                    .putString(TripReminderWorker.KEY_TYPE, TripReminderWorker.TYPE_PLACE_REMINDER)
                    .putString(TripReminderWorker.KEY_TRIP_ID, trip.id)
                    .putString(TripReminderWorker.KEY_PLACE_ID, place.id)
                    .build()
            )
        }
    }

    private fun scheduleGapReminders(
        trip: Trip,
        day: TripDay,
        dayPlaces: List<Place>
    ) {
        val scheduledPlaces = sortPlacesBySchedule(dayPlaces)
            .filter { !it.scheduledTime.isNullOrBlank() }
        scheduledPlaces.zipWithNext().forEach { (current, next) ->
            val currentStart = scheduledDateTime(day.date, current.scheduledTime) ?: return@forEach
            val nextStart = scheduledDateTime(day.date, next.scheduledTime) ?: return@forEach
            val currentDuration = (current.estimatedDuration ?: 60).coerceAtLeast(15)
            val gapStart = currentStart.plusMinutes(currentDuration.toLong())
            val gapMinutes = Duration.between(gapStart, nextStart).toMinutes()
            if (gapMinutes < 75) return@forEach

            val triggerAt = resolveTriggerTime(
                preferredTime = gapStart.plusMinutes(gapMinutes / 2),
                fallbackEnd = nextStart
            ) ?: return@forEach

            enqueueReminder(
                workName = "trip_gap_${current.id}_${next.id}",
                tripId = trip.id,
                triggerAt = triggerAt,
                data = Data.Builder()
                    .putString(TripReminderWorker.KEY_TYPE, TripReminderWorker.TYPE_GAP_WINDOW)
                    .putString(TripReminderWorker.KEY_TRIP_ID, trip.id)
                    .putString(TripReminderWorker.KEY_PLACE_ID, current.id)
                    .putString(TripReminderWorker.KEY_SECONDARY_PLACE_ID, next.id)
                    .build()
            )
        }
    }

    private fun enqueueReminder(
        workName: String,
        tripId: String,
        triggerAt: LocalDateTime,
        data: Data
    ) {
        val now = LocalDateTime.now()
        if (triggerAt.isBefore(now)) return

        val delayMillis = Duration.between(now, triggerAt).toMillis()
        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInputData(data)
            .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag(TripReminderWorker.GENERAL_TAG)
            .addTag(tripTag(tripId))
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun resolveTriggerTime(
        preferredTime: LocalDateTime,
        fallbackEnd: LocalDateTime
    ): LocalDateTime? {
        val now = LocalDateTime.now()
        if (preferredTime.isAfter(now.plusMinutes(1))) {
            return preferredTime
        }
        return if (fallbackEnd.isAfter(now.plusMinutes(10))) {
            now.plusMinutes(1)
        } else {
            null
        }
    }

    private fun sortPlacesBySchedule(places: List<Place>): List<Place> {
        return places.sortedWith(
            compareBy<Place> { scheduledDateTime(LocalDate.now(), it.scheduledTime) == null }
                .thenBy { scheduledDateTime(LocalDate.now(), it.scheduledTime)?.toLocalTime() }
                .thenBy { it.orderIndex }
        )
    }

    private fun scheduledDateTime(
        date: LocalDate,
        timeValue: String?
    ): LocalDateTime? {
        val time = parseTime(timeValue) ?: return null
        return LocalDateTime.of(date, time)
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().uppercase(Locale.US)
        val patterns = listOf("HH:mm", "H:mm", "h:mm a", "hh:mm a", "h:mma", "hh:mma")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalTime.parse(normalized, DateTimeFormatter.ofPattern(pattern, Locale.US))
            }.getOrNull()
        }
    }

    private fun tripTag(tripId: String): String = "${TripReminderWorker.GENERAL_TAG}_$tripId"
}
