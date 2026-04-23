package com.triloo.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.SplitType
import com.triloo.data.model.TripStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * TypeConverter-ы Room для пользовательских типов, которые нельзя сохранить напрямую.
 */
class Converters {
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    // Преобразование `LocalDate`.
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }
    
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, dateFormatter) }
    }
    
    // Преобразование статуса поездки.
    @TypeConverter
    fun fromTripStatus(status: TripStatus): String = status.name

    @TypeConverter
    fun toTripStatus(status: String): TripStatus =
        runCatching { TripStatus.valueOf(status) }.getOrDefault(TripStatus.PLANNING)

    // Преобразование роли участника.
    @TypeConverter
    fun fromParticipantRole(role: ParticipantRole): String = role.name

    @TypeConverter
    fun toParticipantRole(role: String): ParticipantRole =
        runCatching { ParticipantRole.valueOf(role) }.getOrDefault(ParticipantRole.MEMBER)

    // Преобразование категории места.
    @TypeConverter
    fun fromPlaceCategory(category: PlaceCategory): String = category.name

    @TypeConverter
    fun toPlaceCategory(category: String): PlaceCategory =
        runCatching { PlaceCategory.valueOf(category) }.getOrDefault(PlaceCategory.OTHER)

    // Преобразование категории расхода.
    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory): String = category.name

    @TypeConverter
    fun toExpenseCategory(category: String): ExpenseCategory =
        runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER)

    // Преобразование типа сплита.
    @TypeConverter
    fun fromSplitType(type: SplitType): String = type.name

    @TypeConverter
    fun toSplitType(type: String): SplitType =
        runCatching { SplitType.valueOf(type) }.getOrDefault(SplitType.PAYER_ONLY)

    // Преобразование типа relay-сущности.
    @TypeConverter
    fun fromRelayEntityType(type: RelayEntityType): String = type.name

    @TypeConverter
    fun toRelayEntityType(type: String): RelayEntityType =
        runCatching { RelayEntityType.valueOf(type) }.getOrDefault(RelayEntityType.TRIP)
    
    // Сериализация карты сумм для пользовательских сплитов.
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

