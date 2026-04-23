package com.triloo.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendTripApi {
    @POST("api/v1/trips/join-by-invite")
    suspend fun joinByInviteCode(
        @Header("Authorization") authorization: String,
        @Body request: JoinByInviteRequest
    ): JoinByInviteResponse
}

data class JoinByInviteRequest(
    val inviteCode: String,
    val displayName: String? = null
)

data class JoinByInviteResponse(
    val tripId: String,
    val serverUpdatedAt: Long
)
