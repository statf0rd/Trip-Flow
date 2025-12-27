package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

interface CurrencyApi {
    @GET("v6/latest/{base}")
    suspend fun latestRates(
        @Path("base") base: String
    ): CurrencyRatesResponse
}

data class CurrencyRatesResponse(
    val result: String? = null,
    @SerializedName("base_code") val baseCode: String? = null,
    val rates: Map<String, Double> = emptyMap(),
    @SerializedName("time_last_update_unix") val lastUpdateUnix: Long? = null,
    @SerializedName("error_type") val errorType: String? = null
)
