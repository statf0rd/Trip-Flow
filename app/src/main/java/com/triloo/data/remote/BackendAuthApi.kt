package com.triloo.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface BackendAuthApi {
    @POST("api/v1/auth/sign-up")
    suspend fun signUp(@Body request: BackendSignUpRequest): BackendAuthResponse

    @POST("api/v1/auth/sign-in")
    suspend fun signIn(@Body request: BackendSignInRequest): BackendAuthResponse

    @POST("api/v1/auth/sign-out")
    suspend fun signOut(@Header("Authorization") authorization: String)

    @POST("api/v1/auth/password-reset")
    suspend fun sendPasswordReset(@Body request: BackendPasswordResetRequest)

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): BackendCurrentUserResponse

    @PATCH("api/v1/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: BackendUpdateProfileRequest
    ): BackendCurrentUserResponse

    @DELETE("api/v1/auth/account")
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String
    )
}

data class BackendSignUpRequest(
    val email: String,
    val password: String,
    val displayName: String
)

data class BackendSignInRequest(
    val email: String,
    val password: String
)

data class BackendPasswordResetRequest(
    val email: String
)

data class BackendUpdateProfileRequest(
    val displayName: String?,
    val avatarUrl: String?
)

data class BackendAuthResponse(
    val token: String,
    val user: BackendUser
)

data class BackendCurrentUserResponse(
    val user: BackendUser
)

data class BackendUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val phoneNumber: String? = null,
    val preferredCurrency: String = "RUB",
    val createdAt: Long,
    val lastLoginAt: Long
)
