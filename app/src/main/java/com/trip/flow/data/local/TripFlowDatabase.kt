package com.trip.flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.trip.flow.data.local.dao.ExpenseDao
import com.trip.flow.data.local.dao.PlaceDao
import com.trip.flow.data.local.dao.TripDao
import com.trip.flow.data.model.CurrencyRate
import com.trip.flow.data.model.Expense
import com.trip.flow.data.model.ExpenseSplit
import com.trip.flow.data.model.Participant
import com.trip.flow.data.model.Place
import com.trip.flow.data.model.Trip
import com.trip.flow.data.model.TripDay

@Database(
    entities = [
        Trip::class,
        Participant::class,
        TripDay::class,
        Place::class,
        Expense::class,
        ExpenseSplit::class,
        CurrencyRate::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TripFlowDatabase : RoomDatabase() {
    
    abstract fun tripDao(): TripDao
    abstract fun placeDao(): PlaceDao
    abstract fun expenseDao(): ExpenseDao
    
    companion object {
        const val DATABASE_NAME = "tripflow.db"
    }
}

