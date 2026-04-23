package com.triloo.data.location

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.triloo.MainActivity
import com.triloo.R
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.notifications.NotificationChannels
import com.triloo.data.repository.TripRepository
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationSharingForegroundService : Service() {

    @Inject
    lateinit var locationSharingManager: LocationSharingManager

    @Inject
    lateinit var tripRepository: TripRepository

    @Inject
    lateinit var userProfileRepository: UserProfileRepository

    @Inject
    lateinit var onlineSyncRepository: OnlineSyncRepository

    @Inject
    lateinit var controller: LocationSharingServiceController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sharingJob: Job? = null
    private var activeTripId: String? = null
    private var activeUserId: String? = null
    private var lastServerPullAt: Long = 0L
    private var lastServerPushAt: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                if (tripId.isNullOrBlank()) {
                    controller.updateState(
                        LocationSharingSessionState(
                            isActive = false,
                            error = "Не удалось определить поездку для геошаринга"
                        )
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                startSharing(tripId)
            }

            ACTION_STOP -> stopSharing(stopService = true)
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sharingJob?.cancel()
        controller.updateState(LocationSharingSessionState())
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSharing(tripId: String) {
        if (activeTripId == tripId && sharingJob != null) return

        sharingJob?.cancel()
        startForeground()
        activeTripId = tripId
        controller.updateState(
            LocationSharingSessionState(
                tripId = tripId,
                isActive = true,
                statusMessage = "Подключаем фоновый геошаринг..."
            )
        )

        sharingJob = serviceScope.launch {
            val trip = tripRepository.getTripById(tripId)
            if (trip == null || !trip.isGroupTrip) {
                controller.updateState(
                    LocationSharingSessionState(
                        tripId = tripId,
                        isActive = false,
                        error = "Фоновый геошаринг доступен только в групповой поездке"
                    )
                )
                stopSharing(stopService = true)
                return@launch
            }

            val profile = userProfileRepository.getProfile()
            activeUserId = profile.userId
            ensureCurrentParticipant(tripId, profile.userId, profile.displayName)
            tripRepository.updateParticipantOnlineStatus(tripId, profile.userId, true)
            syncOnline(tripId, force = true)

            runCatching {
                locationSharingManager.locationUpdates().collectLatest { point ->
                    tripRepository.updateParticipantLocation(
                        tripId = tripId,
                        userId = profile.userId,
                        latitude = point.latitude,
                        longitude = point.longitude
                    )
                    tripRepository.updateParticipantOnlineStatus(tripId, profile.userId, true)
                    controller.updateState(
                        LocationSharingSessionState(
                            tripId = tripId,
                            isActive = true,
                            statusMessage = "Геошаринг активен даже если приложение свернуто"
                        )
                    )
                    syncOnline(tripId)
                }
            }.onFailure { error ->
                controller.updateState(
                    LocationSharingSessionState(
                        tripId = tripId,
                        isActive = false,
                        error = error.message ?: "Не удалось обновлять геопозицию"
                    )
                )
                stopSharing(stopService = true)
            }
        }
    }

    private suspend fun ensureCurrentParticipant(
        tripId: String,
        userId: String,
        displayName: String
    ) {
        val exists = tripRepository.getParticipants(tripId).any { it.userId == userId }
        if (exists) return

        tripRepository.addParticipant(
            Participant(
                tripId = tripId,
                userId = userId,
                displayName = displayName.ifBlank { "Участник" },
                role = ParticipantRole.MEMBER,
                isOnline = true
            )
        )
    }

    private suspend fun syncOnline(tripId: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastServerPushAt >= ONLINE_PUSH_INTERVAL_MS) {
            onlineSyncRepository.syncTripNow(tripId)
            lastServerPushAt = now
        }
        if (force || now - lastServerPullAt >= ONLINE_PULL_INTERVAL_MS) {
            onlineSyncRepository.pullRemoteChanges()
            lastServerPullAt = now
        }
    }

    private fun stopSharing(stopService: Boolean) {
        sharingJob?.cancel()
        sharingJob = null
        val tripId = activeTripId
        val userId = activeUserId
        activeTripId = null
        activeUserId = null
        controller.updateState(LocationSharingSessionState())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        if (tripId != null && userId != null) {
            serviceScope.launch {
                runCatching {
                    tripRepository.updateParticipantOnlineStatus(tripId, userId, false)
                    syncOnline(tripId, force = true)
                }
                if (stopService) {
                    stopSelf()
                }
            }
        } else if (stopService) {
            stopSelf()
        }
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            FOREGROUND_SERVICE_TYPE_LOCATION_COMPAT
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, NotificationChannels.LOCATION_SHARING)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Triloo делится геопозицией")
        .setContentText("Участники поездки видят ваше текущее местоположение")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "Остановить",
            PendingIntent.getService(
                this,
                2,
                Intent(this, LocationSharingForegroundService::class.java)
                    .setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        const val ACTION_START = "com.triloo.location.action.START"
        const val ACTION_STOP = "com.triloo.location.action.STOP"
        const val EXTRA_TRIP_ID = "trip_id"

        private const val NOTIFICATION_ID = 2_204
        private const val ONLINE_PUSH_INTERVAL_MS = 15_000L
        private const val ONLINE_PULL_INTERVAL_MS = 20_000L
        private const val FOREGROUND_SERVICE_TYPE_LOCATION_COMPAT = 0x00000008
    }
}
