package com.triloo.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.triloo.MainActivity
import com.triloo.R
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.settings.AppSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.firstOrNull

class TripReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!canPostNotifications()) {
            return Result.success()
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val settings = entryPoint.settingsRepository().settings.firstOrNull()
        if (settings?.notificationsEnabled == false) {
            return Result.success()
        }

        return runCatching {
            when (inputData.getString(KEY_TYPE)) {
                TYPE_DAY_PLAN -> sendDayPlan(
                    tripDao = entryPoint.tripDao(),
                    placeDao = entryPoint.placeDao()
                )
                TYPE_PLACE_REMINDER -> sendPlaceReminder(
                    tripDao = entryPoint.tripDao(),
                    placeDao = entryPoint.placeDao()
                )
                TYPE_GAP_WINDOW -> sendGapReminder(placeDao = entryPoint.placeDao())
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private suspend fun sendDayPlan(
        tripDao: TripDao,
        placeDao: PlaceDao
    ) {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return
        val date = inputData.getString(KEY_DAY_DATE)?.let(LocalDate::parse) ?: return
        val trip = tripDao.getTripById(tripId) ?: return
        val tripDay = placeDao.getTripDayByDate(tripId, date) ?: return
        val places = placeDao.getPlacesByDay(tripDay.id)
            .filterNot { it.isVisited }
            .sortedBy { it.scheduledTime?.let(::parseTime) ?: LocalTime.MAX }
        if (places.isEmpty()) return

        val firstPlace = places.first()
        val summary = buildString {
            append("${places.size} мест")
            firstPlace.scheduledTime?.let { append(" • старт в $it") }
            append(" • ${trip.destination}")
        }
        val bigText = places.joinToString(separator = "\n") { place ->
            val prefix = place.scheduledTime?.let { "$it • " }.orEmpty()
            "$prefix${place.name}"
        }

        postNotification(
            notificationId = stableId(TYPE_DAY_PLAN, trip.id, date.toString()),
            title = if (date == LocalDate.now()) {
                "План на сегодня: ${trip.name}"
            } else {
                "План на ${date.dayOfMonth}.${date.monthValue}: ${trip.name}"
            },
            text = summary,
            bigText = bigText
        )
    }

    private suspend fun sendPlaceReminder(
        tripDao: TripDao,
        placeDao: PlaceDao
    ) {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return
        val placeId = inputData.getString(KEY_PLACE_ID) ?: return
        val trip = tripDao.getTripById(tripId) ?: return
        val place = placeDao.getPlaceById(placeId) ?: return
        if (place.isVisited) return

        val title = "Скоро: ${place.name}"
        val text = buildString {
            place.scheduledTime?.let { append("Запланировано на $it") }
            place.address?.takeIf { it.isNotBlank() }?.let { address ->
                if (isNotEmpty()) append(" • ")
                append(address)
            }
        }.ifBlank {
            "Следующая точка в поездке ${trip.name}"
        }

        postNotification(
            notificationId = stableId(TYPE_PLACE_REMINDER, trip.id, place.id),
            title = title,
            text = text,
            bigText = buildString {
                append(text)
                place.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    append("\n")
                    append(notes)
                }
            }
        )
    }

    private suspend fun sendGapReminder(placeDao: PlaceDao) {
        val currentPlace = placeDao.getPlaceById(inputData.getString(KEY_PLACE_ID) ?: return) ?: return
        val nextPlace = placeDao.getPlaceById(inputData.getString(KEY_SECONDARY_PLACE_ID) ?: return) ?: return
        if (currentPlace.isVisited && nextPlace.isVisited) return

        val currentEnd = currentPlace.scheduledTime?.let { parseTime(it) }
            ?.plusMinutes((currentPlace.estimatedDuration ?: 60).toLong())
            ?: return
        val nextStart = nextPlace.scheduledTime?.let { parseTime(it) } ?: return
        val gapMinutes = Duration.between(currentEnd, nextStart).toMinutes()
        if (gapMinutes < 45) return

        val gapLabel = when {
            gapMinutes >= 120 -> "${gapMinutes / 60} ч ${(gapMinutes % 60).toString().padStart(2, '0')} мин"
            else -> "$gapMinutes мин"
        }

        postNotification(
            notificationId = stableId(TYPE_GAP_WINDOW, currentPlace.id, nextPlace.id),
            title = "Окно между точками",
            text = "Между ${currentPlace.name} и ${nextPlace.name} есть $gapLabel",
            bigText = buildString {
                append("После ${currentPlace.name}")
                currentPlace.address?.takeIf { it.isNotBlank() }?.let { append(" (${it})") }
                append(" до ${nextPlace.name} остаётся $gapLabel.")
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        notificationId: Int,
        title: String,
        text: String,
        bigText: String
    ) {
        if (!canPostNotifications()) return

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.TRIP_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stableId(vararg parts: String): Int = parts.joinToString(":").hashCode()

    private fun parseTime(value: String): LocalTime {
        val normalized = value.trim().uppercase(Locale.US)
        val patterns = listOf("HH:mm", "H:mm", "h:mm a", "hh:mm a", "h:mma", "hh:mma")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalTime.parse(
                    normalized,
                    DateTimeFormatter.ofPattern(pattern, Locale.US)
                )
            }.getOrNull()
        } ?: error("Unsupported time format: $value")
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun tripDao(): TripDao
        fun placeDao(): PlaceDao
        fun settingsRepository(): AppSettingsRepository
    }

    companion object {
        const val GENERAL_TAG = "trip_notifications"

        const val KEY_TYPE = "type"
        const val KEY_TRIP_ID = "trip_id"
        const val KEY_DAY_DATE = "day_date"
        const val KEY_PLACE_ID = "place_id"
        const val KEY_SECONDARY_PLACE_ID = "secondary_place_id"

        const val TYPE_DAY_PLAN = "day_plan"
        const val TYPE_PLACE_REMINDER = "place_reminder"
        const val TYPE_GAP_WINDOW = "gap_window"
    }
}
