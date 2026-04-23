package com.triloo.di

import android.content.Context
import com.triloo.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.triloo.data.auth.AuthRepository
import com.triloo.data.auth.BackendAuthRepository
import com.triloo.data.auth.FirebaseAuthRepository
import com.triloo.data.auth.LocalAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль авторизации, который выбирает Firebase при доступной конфигурации
 * и оставляет локальный репозиторий как безопасный резерв для разработки.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        backendAuthRepository: BackendAuthRepository,
        localAuthRepository: LocalAuthRepository
    ): AuthRepository {
        if (BuildConfig.APP_TRILOO_BACKEND_URL.isNotBlank()) {
            return backendAuthRepository
        }
        val firebaseApp = FirebaseApp.initializeApp(context)
        return if (firebaseApp != null) {
            FirebaseAuthRepository(FirebaseAuth.getInstance(firebaseApp))
        } else {
            localAuthRepository
        }
    }
}
