package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface PlacesApi {
    @GET("maps/api/place/textsearch/json")
    suspend fun textSearch(
        @Query("query") query: String,
        @Query("location") location: String? = null,
        @Query("radius") radius: Int? = null,
        @Query("key") apiKey: String
    ): PlacesTextSearchResponse

    @GET("maps/api/place/details/json")
    suspend fun details(
        @Query("place_id") placeId: String,
        @Query("fields") fields: String,
        @Query("key") apiKey: String
    ): PlacesDetailsResponse

    @GET("maps/api/place/nearbysearch/json")
    suspend fun nearbySearch(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") type: String? = null,
        @Query("key") apiKey: String
    ): PlacesNearbyResponse
}

data class PlacesTextSearchResponse(
    val status: String,
    val results: List<PlacesResult> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null
)

data class PlacesNearbyResponse(
    val status: String,
    val results: List<PlacesResult> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null
)

data class PlacesDetailsResponse(
    val status: String,
    val result: PlaceDetailsResult? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class PlacesResult(
    @SerializedName("place_id") val placeId: String,
    val name: String,
    @SerializedName("formatted_address") val formattedAddress: String? = null,
    val vicinity: String? = null,
    val geometry: PlaceGeometry? = null,
    val types: List<String>? = null,
    val rating: Float? = null,
    @SerializedName("price_level") val priceLevel: Int? = null,
    val photos: List<PlacePhoto>? = null
)

data class PlaceDetailsResult(
    @SerializedName("place_id") val placeId: String,
    val name: String,
    @SerializedName("formatted_address") val formattedAddress: String? = null,
    val geometry: PlaceGeometry? = null,
    val types: List<String>? = null,
    val rating: Float? = null,
    @SerializedName("price_level") val priceLevel: Int? = null,
    @SerializedName("formatted_phone_number") val phoneNumber: String? = null,
    val website: String? = null,
    @SerializedName("opening_hours") val openingHours: PlaceOpeningHours? = null,
    val photos: List<PlacePhoto>? = null
)

data class PlaceGeometry(
    val location: PlaceLocation
)

data class PlaceLocation(
    val lat: Double,
    val lng: Double
)

data class PlacePhoto(
    @SerializedName("photo_reference") val photoReference: String
)

data class PlaceOpeningHours(
    @SerializedName("weekday_text") val weekdayText: List<String>? = null
)
