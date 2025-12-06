package com.trip.flow.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trip.flow.data.model.ExpenseCategory
import com.trip.flow.data.model.ParticipantRole
import com.trip.flow.data.model.PlaceCategory
import com.trip.flow.data.model.SplitType
import com.trip.flow.data.model.TripStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Room TypeConverters for custom types
 */
class Converters {
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    // ══════════════════════════════════════════════════════════
    // LocalDate
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }
    
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, dateFormatter) }
    }
    
    // ══════════════════════════════════════════════════════════
    // TripStatus
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromTripStatus(status: TripStatus): String = status.name
    
    @TypeConverter
    fun toTripStatus(status: String): TripStatus = TripStatus.valueOf(status)
    
    // ══════════════════════════════════════════════════════════
    // ParticipantRole
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromParticipantRole(role: ParticipantRole): String = role.name
    
    @TypeConverter
    fun toParticipantRole(role: String): ParticipantRole = ParticipantRole.valueOf(role)
    
    // ══════════════════════════════════════════════════════════
    // PlaceCategory
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromPlaceCategory(category: PlaceCategory): String = category.name
    
    @TypeConverter
    fun toPlaceCategory(category: String): PlaceCategory = PlaceCategory.valueOf(category)
    
    // ══════════════════════════════════════════════════════════
    // ExpenseCategory
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory): String = category.name
    
    @TypeConverter
    fun toExpenseCategory(category: String): ExpenseCategory = ExpenseCategory.valueOf(category)
    
    // ══════════════════════════════════════════════════════════
    // SplitType
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromSplitType(type: SplitType): String = type.name
    
    @TypeConverter
    fun toSplitType(type: String): SplitType = SplitType.valueOf(type)
    
    // ══════════════════════════════════════════════════════════
    // Map<String, Double> for split amounts
    // ══════════════════════════════════════════════════════════
    @TypeConverter
    fun fromStringDoubleMap(map: Map<String, Double>?): String? {
        return map?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringDoubleMap(json: String?): Map<String, Double>? {
        return json?.let {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            gson.fromJson(it, type)
        }
    }
}

