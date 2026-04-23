package com.triloo.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit-контракт для openrouteservice Directions API.
 */
interface OpenRouteServiceApi {
    @POST("v2/directions/{profile}")
    suspend fun getDirections(
        @Path("profile") profile: String,
        @Header("Authorization") apiKey: String,
        @Body request: OpenRouteServiceDirectionsRequest
    ): OpenRouteServiceDirectionsResponse
}

data class OpenRouteServiceDirectionsRequest(
    val coordinates: List<List<Double>>
)

data class OpenRouteServiceDirectionsResponse(
    val routes: List<OpenRouteServiceRoute> = emptyList()
)

data class OpenRouteServiceRoute(
    val summary: OpenRouteServiceSummary? = null,
    val segments: List<OpenRouteServiceSegment> = emptyList(),
    val geometry: String? = null
)

data class OpenRouteServiceSummary(
    val distance: Double? = null,
    val duration: Double? = null
)

data class OpenRouteServiceSegment(
    val distance: Double? = null,
    val duration: Double? = null
)
