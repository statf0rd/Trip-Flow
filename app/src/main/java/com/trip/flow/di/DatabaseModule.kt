package com.trip.flow.di

import android.content.Context
import androidx.room.Room
import com.trip.flow.data.local.TripFlowDatabase
import com.trip.flow.data.local.dao.ExpenseDao
import com.trip.flow.data.local.dao.PlaceDao
import com.trip.flow.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TripFlowDatabase {
        return Room.databaseBuilder(
            context,
            TripFlowDatabase::class.java,
            TripFlowDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTripDao(database: TripFlowDatabase): TripDao {
        return database.tripDao()
    }
    
    @Provides
    @Singleton
    fun providePlaceDao(database: TripFlowDatabase): PlaceDao {
        return database.placeDao()
    }
    
    @Provides
    @Singleton
    fun provideExpenseDao(database: TripFlowDatabase): ExpenseDao {
        return database.expenseDao()
    }
}

