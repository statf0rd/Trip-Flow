package com.triloo.di

import com.triloo.data.route.NearestNeighborRouteOptimizer
import com.triloo.data.route.RouteOptimizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RouteModule {

    @Binds
    @Singleton
    abstract fun bindRouteOptimizer(
        optimizer: NearestNeighborRouteOptimizer
    ): RouteOptimizer
}
