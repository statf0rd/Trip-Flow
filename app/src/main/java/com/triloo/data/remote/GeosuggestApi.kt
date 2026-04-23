package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * HTTP-контракт для Yandex Geosuggest API.
 */
interface GeosuggestApi {
    @GET("v1/suggest")
    suspend fun suggest(
        @Query("apikey") apiKey: String,
        @Query("text") text: String,
        @Query("lang") language: String = "ru_RU",
        @Query("results") results: Int = 6,
        @Query("types") types: String = "geo,biz",
        @Query("attrs") attrs: String = "uri"
    ): GeosuggestResponse
}

data class GeosuggestResponse(
    @SerializedName("suggest_reqid") val requestId: String? = null,
    val results: List<GeosuggestItem> = emptyList()
)

data class GeosuggestItem(
    val title: GeosuggestText? = null,
    val subtitle: GeosuggestText? = null,
    val tags: List<String>? = null,
    val uri: String? = null,
    @SerializedName("search_text") val searchText: String? = null,
    @SerializedName("display_text") val displayText: String? = null
)

data class GeosuggestText(
    val text: String? = null
)
