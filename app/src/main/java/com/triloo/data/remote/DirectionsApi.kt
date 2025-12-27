package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("mode") mode: String,
        @Query("key") apiKey: String
    ): DirectionsResponse
}

data class DirectionsResponse(
    val status: String,
    val routes: List<DirectionsRoute> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null
)

data class DirectionsRoute(
    @SerializedName("overview_polyline") val overviewPolyline: Polyline? = null,
    val legs: List<DirectionsLeg> = emptyList()
)

data class Polyline(
    @SerializedName("points") val points: String
)

data class DirectionsLeg(
    val distance: DirectionsValue? = null,
    val duration: DirectionsValue? = null,
    @SerializedName("duration_in_traffic") val durationInTraffic: DirectionsValue? = null
)

data class DirectionsValue(
    val value: Int,
    val text: String
)
