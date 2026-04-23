package com.triloo.data.sync

import com.triloo.BuildConfig
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.local.dao.TripDao
import com.triloo.data.remote.OnlineSyncApi
import com.triloo.data.remote.SyncPushItem
import com.triloo.data.remote.SyncPushRequest
import com.triloo.data.relay.RelayRepository
import com.triloo.data.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineSyncRepository @Inject constructor(
    private val onlineSyncApi: OnlineSyncApi,
    private val relayRepository: RelayRepository,
    private val serverSessionRepository: ServerSessionRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val tripDao: TripDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    fun syncTripAsync(tripId: String) {
        scope.launch {
            syncTripNow(tripId)
        }
    }

    fun syncAllTripsAsync() {
        scope.launch {
            syncAllTrips()
        }
    }

    fun bootstrapSyncAsync() {
        scope.launch {
            bootstrapSync()
        }
    }

    fun pullRemoteChangesAsync() {
        scope.launch {
            pullRemoteChanges()
        }
    }

    suspend fun syncTripNow(tripId: String): Boolean {
        val trip = tripDao.getTripById(tripId) ?: return false
        if (!trip.isGroupTrip) return true

        val token = getAuthorizedToken() ?: return false
        val relayPackage = relayRepository.buildPackage(tripId) ?: return false
        val payloadJson = relayRepository.encodePackage(relayPackage)

        val hasServerConflict = syncMutex.withLock {
            runCatching {
                val response = onlineSyncApi.push(
                    authorization = token.asBearer(),
                    request = SyncPushRequest(
                        items = listOf(
                            SyncPushItem(
                                tripId = tripId,
                                payloadJson = payloadJson
                            )
                        )
                    )
                )
                val isRejected = response.rejected.any {
                    it.tripId == null || it.tripId == tripId
                }
                if (!isRejected) {
                    val maxTimestamp = buildList {
                        add(response.serverTime)
                        response.applied.forEach { add(it.serverUpdatedAt) }
                    }.maxOrNull() ?: response.serverTime
                    if (maxTimestamp > 0L) {
                        serverSessionRepository.updateLastSyncAt(maxTimestamp)
                    }
                }
                isRejected
            }.getOrDefault(true)
        }

        if (hasServerConflict) {
            pullRemoteChanges()
            return false
        }
        return true
    }

    suspend fun syncAllTrips(): Boolean {
        val groupTripIds = tripDao.getAllTrips()
            .filter { it.isGroupTrip }
            .map { it.id }
        if (groupTripIds.isEmpty()) return false

        var syncedAnyTrip = false
        groupTripIds.forEach { tripId ->
            syncedAnyTrip = syncTripNow(tripId) || syncedAnyTrip
        }
        return syncedAnyTrip
    }

    suspend fun bootstrapSync(): Boolean {
        val pulled = pullRemoteChanges()
        val pushed = syncAllTrips()
        return pulled || pushed
    }

    suspend fun pullRemoteChanges(): Boolean {
        val token = getAuthorizedToken() ?: return false
        val session = serverSessionRepository.getSession()

        return syncMutex.withLock {
            runCatching {
                val response = onlineSyncApi.pull(
                    authorization = token.asBearer(),
                    since = session.lastSyncAt
                )
                response.items.forEach { item ->
                    val relayPackage = relayRepository.decodePackage(item.payloadJson)
                    relayRepository.mergePackage(relayPackage)
                }
                val maxTimestamp = buildList {
                    add(response.serverTime)
                    response.items.forEach { add(it.serverUpdatedAt) }
                }.maxOrNull() ?: response.serverTime
                if (maxTimestamp > 0L) {
                    serverSessionRepository.updateLastSyncAt(maxTimestamp)
                }
                true
            }.getOrDefault(false)
        }
    }

    private suspend fun getAuthorizedToken(): String? {
        if (BuildConfig.APP_TRILOO_BACKEND_URL.isBlank()) return null
        val syncEnabled = appSettingsRepository.settings.first().syncEnabled
        if (!syncEnabled) return null
        return serverSessionRepository.getSession().authToken
    }

    private fun String.asBearer(): String = "Bearer $this"
}
