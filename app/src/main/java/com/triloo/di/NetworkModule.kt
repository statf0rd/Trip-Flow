package com.triloo.di

import com.triloo.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.triloo.data.remote.BackendAuthApi
import com.triloo.data.remote.BackendTripApi
import com.triloo.data.remote.CurrencyApi
import com.triloo.data.remote.GeoapifyApi
import com.triloo.data.remote.GeosuggestApi
import com.triloo.data.remote.OpenAiApi
import com.triloo.data.remote.OpenRouteServiceApi
import com.triloo.data.remote.OnlineSyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    @Named("CurrencyRetrofit")
    fun provideCurrencyRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("GeoapifyRetrofit")
    fun provideGeoapifyRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.geoapify.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("GeosuggestRetrofit")
    fun provideGeosuggestRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://suggest-maps.yandex.ru/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("OpenRouteServiceRetrofit")
    fun provideOpenRouteServiceRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("OpenAiRetrofit")
    fun provideOpenAiRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("BackendRetrofit")
    fun provideBackendRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.APP_TRILOO_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideGeoapifyApi(@Named("GeoapifyRetrofit") retrofit: Retrofit): GeoapifyApi {
        return retrofit.create(GeoapifyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeosuggestApi(@Named("GeosuggestRetrofit") retrofit: Retrofit): GeosuggestApi {
        return retrofit.create(GeosuggestApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenRouteServiceApi(
        @Named("OpenRouteServiceRetrofit") retrofit: Retrofit
    ): OpenRouteServiceApi {
        return retrofit.create(OpenRouteServiceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCurrencyApi(@Named("CurrencyRetrofit") retrofit: Retrofit): CurrencyApi {
        return retrofit.create(CurrencyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenAiApi(@Named("OpenAiRetrofit") retrofit: Retrofit): OpenAiApi {
        return retrofit.create(OpenAiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBackendAuthApi(@Named("BackendRetrofit") retrofit: Retrofit): BackendAuthApi {
        return retrofit.create(BackendAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBackendTripApi(@Named("BackendRetrofit") retrofit: Retrofit): BackendTripApi {
        return retrofit.create(BackendTripApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOnlineSyncApi(@Named("BackendRetrofit") retrofit: Retrofit): OnlineSyncApi {
        return retrofit.create(OnlineSyncApi::class.java)
    }
}
