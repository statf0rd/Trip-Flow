package com.triloo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.triloo.data.local.dao.ExpenseDao
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.CurrencyRate
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseSplit
import com.triloo.data.model.Participant
import com.triloo.data.model.Place
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay

@Database(
    entities = [
        Trip::class,
        Participant::class,
        TripDay::class,
        Place::class,
        Expense::class,
        ExpenseSplit::class,
        CurrencyRate::class,
        DeletionLog::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
/**
 * Основная Room-база приложения с поездками, маршрутами, расходами и журналом удалений.
 */
abstract class TrilooDatabase : RoomDatabase() {
    
    abstract fun tripDao(): TripDao
    abstract fun placeDao(): PlaceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deletionLogDao(): com.triloo.data.local.dao.DeletionLogDao
    
    companion object {
        const val DATABASE_NAME = "triloo.db"
    }
}

