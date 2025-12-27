package com.triloo.di

import com.triloo.data.auth.AuthRepository
import com.triloo.data.auth.LocalAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: LocalAuthRepository
    ): AuthRepository
}
