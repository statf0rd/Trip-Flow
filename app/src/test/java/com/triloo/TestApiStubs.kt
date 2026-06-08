package com.triloo

import com.triloo.data.remote.BackendTripApi
import com.triloo.data.remote.JoinByInviteRequest
import com.triloo.data.remote.JoinByInviteResponse
import com.triloo.data.remote.OnlineSyncApi
import com.triloo.data.remote.SyncPullResponse
import com.triloo.data.remote.SyncPushRequest
import com.triloo.data.remote.SyncPushResponse

/**
 * Общие заглушки сетевых API для юнит-тестов репозиториев (Room + Robolectric).
 * Возвращают пустые/нейтральные ответы — сеть в тестах не задействуется.
 */
class StubOnlineSyncApi : OnlineSyncApi {
    override suspend fun push(authorization: String, request: SyncPushRequest): SyncPushResponse =
        SyncPushResponse()

    override suspend fun pull(authorization: String, since: Long): SyncPullResponse =
        SyncPullResponse()
}

class StubBackendTripApi(private val tripId: String = "trip") : BackendTripApi {
    override suspend fun joinByInviteCode(
        authorization: String,
        request: JoinByInviteRequest
    ): JoinByInviteResponse = JoinByInviteResponse(tripId = tripId, serverUpdatedAt = 0L)
}
