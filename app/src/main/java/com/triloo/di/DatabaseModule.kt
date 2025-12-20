package com.triloo.di

import android.content.Context
import androidx.room.Room
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.local.dao.DeletionLogDao
import com.triloo.data.local.dao.ExpenseDao
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
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
    ): TrilooDatabase {
        return Room.databaseBuilder(
            context,
            TrilooDatabase::class.java,
            TrilooDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTripDao(database: TrilooDatabase): TripDao {
        return database.tripDao()
    }
    
    @Provides
    @Singleton
    fun providePlaceDao(database: TrilooDatabase): PlaceDao {
        return database.placeDao()
    }
    
    @Provides
    @Singleton
    fun provideExpenseDao(database: TrilooDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    @Singleton
    fun provideDeletionLogDao(database: TrilooDatabase): DeletionLogDao {
        return database.deletionLogDao()
    }
}


