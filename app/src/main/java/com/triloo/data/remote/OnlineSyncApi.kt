package com.triloo.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OnlineSyncApi {
    @POST("api/v1/sync/push")
    suspend fun push(
        @Header("Authorization") authorization: String,
        @Body request: SyncPushRequest
    ): SyncPushResponse

    @GET("api/v1/sync/pull")
    suspend fun pull(
        @Header("Authorization") authorization: String,
        @Query("since") since: Long
    ): SyncPullResponse
}

data class SyncPushRequest(
    val items: List<SyncPushItem>
)

data class SyncPushItem(
    val tripId: String,
    val payloadJson: String
)

data class SyncPushResponse(
    val applied: List<SyncAppliedTrip> = emptyList(),
    val rejected: List<SyncRejectedTrip> = emptyList(),
    val serverTime: Long = 0L
)

data class SyncAppliedTrip(
    val tripId: String,
    val serverUpdatedAt: Long,
    val sourceUpdatedAt: Long
)

data class SyncRejectedTrip(
    val tripId: String? = null,
    val message: String
)

data class SyncPullResponse(
    val items: List<SyncPullItem> = emptyList(),
    val serverTime: Long = 0L
)

data class SyncPullItem(
    val tripId: String,
    val payloadJson: String,
    val serverUpdatedAt: Long,
    val sourceUpdatedAt: Long
)
