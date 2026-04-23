package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit-контракт для Geoapify Geocoding/Places API.
 */
interface GeoapifyApi {
    @GET("v1/geocode/search")
    suspend fun geocodeSearch(
        @Query("text") text: String,
        @Query("lang") lang: String = "ru",
        @Query("limit") limit: Int = 1,
        @Query("type") type: String? = "city",
        @Query("apiKey") apiKey: String
    ): GeoapifyGeocodeResponse

    @GET("v2/places")
    suspend fun searchPlaces(
        @Query("categories") categories: String,
        @Query("filter") filter: String,
        @Query("bias") bias: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("apiKey") apiKey: String
    ): GeoapifyPlacesResponse
}

data class GeoapifyGeocodeResponse(
    val features: List<GeoapifyGeocodeFeature> = emptyList()
)

data class GeoapifyGeocodeFeature(
    val properties: GeoapifyGeocodeProperties? = null,
    val geometry: GeoapifyGeometry? = null
)

data class GeoapifyPlacesResponse(
    val features: List<GeoapifyPlaceFeature> = emptyList()
)

data class GeoapifyPlaceFeature(
    val properties: GeoapifyPlaceProperties? = null,
    val geometry: GeoapifyGeometry? = null
)

data class GeoapifyGeometry(
    val coordinates: List<Double> = emptyList()
)

data class GeoapifyGeocodeProperties(
    @SerializedName("place_id") val placeId: String? = null,
    @SerializedName("result_type") val resultType: String? = null,
    val formatted: String? = null
)

data class GeoapifyPlaceProperties(
    @SerializedName("place_id") val placeId: String? = null,
    val name: String? = null,
    val formatted: String? = null,
    @SerializedName("address_line1") val addressLine1: String? = null,
    @SerializedName("address_line2") val addressLine2: String? = null,
    val categories: List<String>? = null,
    val website: String? = null,
    @SerializedName("opening_hours") val openingHours: String? = null,
    val distance: Int? = null,
    val contact: GeoapifyContact? = null,
    val accommodation: GeoapifyAccommodation? = null,
    val datasource: GeoapifyDataSource? = null
)

data class GeoapifyContact(
    val phone: String? = null
)

data class GeoapifyAccommodation(
    val stars: Int? = null
)

data class GeoapifyDataSource(
    val raw: GeoapifyRaw? = null
)

data class GeoapifyRaw(
    val stars: Int? = null,
    val tourism: String? = null,
    val brand: String? = null,
    val website: String? = null,
    @SerializedName("contact:website") val contactWebsite: String? = null,
    @SerializedName("contact:phone") val contactPhone: String? = null,
    @SerializedName("opening_hours") val openingHours: String? = null
)
