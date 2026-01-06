package com.triloo.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.triloo.data.auth.AuthRepository
import com.triloo.data.auth.FirebaseAuthRepository
import com.triloo.data.auth.LocalAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        localAuthRepository: LocalAuthRepository
    ): AuthRepository {
        val firebaseApp = FirebaseApp.initializeApp(context)
        return if (firebaseApp != null) {
            FirebaseAuthRepository(FirebaseAuth.getInstance(firebaseApp))
        } else {
            localAuthRepository
        }
    }
}
