package com.triloo.data.sync

import com.triloo.BuildConfig
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.model.Trip
import com.triloo.data.remote.BackendTripApi
import com.triloo.data.remote.JoinByInviteRequest
import com.triloo.data.local.dao.TripDao
import com.triloo.data.remote.OnlineSyncApi
import com.triloo.data.remote.SyncRejectedTrip
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
    private val backendTripApi: BackendTripApi,
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
        return syncTripNowInternal(tripId, canRepairMembership = true)
    }

    private suspend fun syncTripNowInternal(
        tripId: String,
        canRepairMembership: Boolean
    ): Boolean {
        val trip = tripDao.getTripById(tripId) ?: return false
        if (!trip.isGroupTrip) return true

        val token = getAuthorizedToken() ?: return false
        val relayPackage = relayRepository.buildPackage(tripId) ?: return false
        val payloadJson = relayRepository.encodePackage(relayPackage)

        val pushOutcome = syncMutex.withLock {
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
                val hasMembershipRejection = response.rejected.hasMembershipRejection(tripId)
                if (!isRejected) {
                    val maxTimestamp = buildList {
                        add(response.serverTime)
                        response.applied.forEach { add(it.serverUpdatedAt) }
                    }.maxOrNull() ?: response.serverTime
                    if (maxTimestamp > 0L) {
                        serverSessionRepository.updateLastSyncAt(maxTimestamp)
                    }
                }
                PushOutcome(
                    hasServerConflict = isRejected,
                    hasMembershipRejection = hasMembershipRejection
                )
            }.getOrDefault(
                PushOutcome(
                    hasServerConflict = true,
                    hasMembershipRejection = false
                )
            )
        }

        if (pushOutcome.hasMembershipRejection && canRepairMembership) {
            val repaired = repairRemoteMembership(token, trip)
            if (repaired) {
                return syncTripNowInternal(tripId, canRepairMembership = false)
            }
        }

        if (pushOutcome.hasServerConflict) {
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

    suspend fun pullRemoteChanges(sinceOverride: Long? = null): Boolean {
        val token = getAuthorizedToken() ?: return false
        val session = serverSessionRepository.getSession()
        val since = sinceOverride ?: session.lastSyncAt

        return syncMutex.withLock {
            runCatching {
                val response = onlineSyncApi.pull(
                    authorization = token.asBearer(),
                    since = since
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

    private suspend fun repairRemoteMembership(token: String, trip: Trip): Boolean {
        if (trip.inviteCode.isBlank()) return false
        val user = serverSessionRepository.getSession().user
        return runCatching {
            backendTripApi.joinByInviteCode(
                authorization = token.asBearer(),
                request = JoinByInviteRequest(
                    inviteCode = trip.inviteCode,
                    displayName = user?.displayName?.takeIf { it.isNotBlank() }
                )
            )
            pullRemoteChanges(sinceOverride = 0L)
        }.isSuccess
    }

    private fun List<SyncRejectedTrip>.hasMembershipRejection(tripId: String): Boolean {
        return any { rejection ->
            (rejection.tripId == null || rejection.tripId == tripId) &&
                rejection.message.contains("not a participant", ignoreCase = true)
        }
    }

    private data class PushOutcome(
        val hasServerConflict: Boolean,
        val hasMembershipRejection: Boolean
    )
}
