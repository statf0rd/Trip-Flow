package com.triloo.data.route

import com.google.gson.Gson
import com.triloo.data.ai.OpenAiService
import com.triloo.data.model.Place
import com.triloo.data.model.TravelMode
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class RoutePlanningMode(val displayName: String) {
    CLASSIC("Классика"),
    AI_ASSISTED("AI")
}

enum class RoutePlanSource {
    AI,
    HEURISTIC
}

data class RoutePlanSuggestion(
    val dayOrders: Map<String, List<String>>,
    val suggestedTravelMode: TravelMode,
    val summary: String,
    val source: RoutePlanSource
)

@Singleton
class RoutePlanningAssistant @Inject constructor(
    private val openAiService: OpenAiService,
    private val gson: Gson
) {

    suspend fun planRoute(
        trip: Trip?,
        days: List<TripDay>,
        places: List<Place>,
        preferAi: Boolean = true
    ): RoutePlanSuggestion? {
        if (places.size < 2) return null

        val heuristic = buildHeuristicPlan(days, places)
        if (!preferAi) return heuristic
        val aiSuggestion = buildAiPlan(
            trip = trip,
            days = days,
            places = places,
            fallback = heuristic
        )
        return aiSuggestion ?: heuristic
    }

    fun suggestHeuristic(
        days: List<TripDay>,
        places: List<Place>
    ): RoutePlanSuggestion? {
        if (places.size < 2) return null
        return buildHeuristicPlan(days, places)
    }

    private suspend fun buildAiPlan(
        trip: Trip?,
        days: List<TripDay>,
        places: List<Place>,
        fallback: RoutePlanSuggestion
    ): RoutePlanSuggestion? {
        val prompt = buildPrompt(trip, days, places)
        val json = openAiService.generateJson(
            systemPrompt = AI_SYSTEM_PROMPT,
            userPrompt = prompt,
            temperature = 0.15,
            maxTokens = 900
        ) ?: return null

        val parsed = runCatching {
            gson.fromJson(json, AiRoutePlanResponse::class.java)
        }.getOrNull() ?: return null

        val validatedOrders = validateDayOrders(
            days = days,
            places = places,
            aiDays = parsed.days
        )
        if (validatedOrders.isEmpty()) return null

        val suggestedMode = parsed.suggestedTravelMode
            ?.let { raw -> TravelMode.entries.firstOrNull { it.name == raw.trim().uppercase() } }
            ?: fallback.suggestedTravelMode

        val summary = parsed.summary
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallback.summary

        return RoutePlanSuggestion(
            dayOrders = validatedOrders,
            suggestedTravelMode = suggestedMode,
            summary = summary,
            source = RoutePlanSource.AI
        )
    }

    private fun buildHeuristicPlan(
        days: List<TripDay>,
        places: List<Place>
    ): RoutePlanSuggestion {
        val dayOrders = buildHeuristicDayOrders(days, places)
        val orderedPlaces = flattenDayOrders(days, places, dayOrders)
        val totalDistance = orderedPlaces.zipWithNext().sumOf { (from, to) ->
            haversineDistance(
                from.latitude,
                from.longitude,
                to.latitude,
                to.longitude
            ).toInt()
        }
        val maxLegDistance = orderedPlaces.zipWithNext().maxOfOrNull { (from, to) ->
            haversineDistance(
                from.latitude,
                from.longitude,
                to.latitude,
                to.longitude
            ).toInt()
        } ?: 0

        val suggestedMode = when {
            totalDistance <= 2_500 -> TravelMode.WALKING
            totalDistance <= 7_000 && maxLegDistance <= 2_500 -> TravelMode.BICYCLING
            totalDistance >= 15_000 || maxLegDistance >= 7_000 -> TravelMode.DRIVING
            else -> TravelMode.TRANSIT
        }
        val summary = when (suggestedMode) {
            TravelMode.WALKING ->
                "Точки маршрута расположены компактно, поэтому пеший режим выглядит самым простым."
            TravelMode.BICYCLING ->
                "Маршрут умеренный по длине, велосипед даёт хороший баланс между скоростью и гибкостью."
            TravelMode.DRIVING ->
                "Между точками большие переезды, поэтому автомобильный режим выглядит самым практичным."
            TravelMode.TRANSIT ->
                "Маршрут уже длиннее пешего, но ещё хорошо укладывается в городской общественный транспорт."
        }

        return RoutePlanSuggestion(
            dayOrders = dayOrders,
            suggestedTravelMode = suggestedMode,
            summary = summary,
            source = RoutePlanSource.HEURISTIC
        )
    }

    private fun buildHeuristicDayOrders(
        days: List<TripDay>,
        places: List<Place>
    ): Map<String, List<String>> {
        return days.sortedBy { it.dayNumber }.associate { day ->
            val dayPlaces = places.filter { it.tripDayId == day.id }
            day.id to optimizeDayOrder(dayPlaces)
        }.filterValues { it.isNotEmpty() }
    }

    private fun optimizeDayOrder(dayPlaces: List<Place>): List<String> {
        if (dayPlaces.size <= 2) {
            return dayPlaces.sortedBy { it.orderIndex }.map { it.id }
        }

        val orderedByTime = dayPlaces.sortedWith(
            compareBy<Place> { it.scheduledTime ?: "99:99" }
                .thenBy { it.orderIndex }
        )
        val start = orderedByTime.first()
        val remaining = orderedByTime.drop(1).toMutableList()
        val result = mutableListOf(start)
        var current = start

        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { candidate ->
                haversineDistance(
                    current.latitude,
                    current.longitude,
                    candidate.latitude,
                    candidate.longitude
                )
            }
            result += next
            remaining.remove(next)
            current = next
        }

        return result.map { it.id }
    }

    private fun buildPrompt(
        trip: Trip?,
        days: List<TripDay>,
        places: List<Place>
    ): String {
        val daysById = days.associateBy { it.id }
        val payload = gson.toJson(
            places.map { place ->
                mapOf(
                    "id" to place.id,
                    "name" to place.name,
                    "tripDayId" to place.tripDayId,
                    "dayNumber" to (daysById[place.tripDayId]?.dayNumber ?: Int.MAX_VALUE),
                    "scheduledTime" to place.scheduledTime,
                    "estimatedDuration" to place.estimatedDuration,
                    "category" to place.category.name,
                    "latitude" to place.latitude,
                    "longitude" to place.longitude,
                    "rating" to place.rating
                )
            }
        )

        return """
            destination: ${trip?.destination ?: ""}
            hotel: ${trip?.hotelName ?: ""}
            places: $payload
        """.trimIndent()
    }

    private fun validateDayOrders(
        days: List<TripDay>,
        places: List<Place>,
        aiDays: List<AiRoutePlanDay>
    ): Map<String, List<String>> {
        if (aiDays.isEmpty()) return emptyMap()

        val defaultOrders = defaultDayOrders(days, places)
        val placeIdsByDay = places.groupBy { it.tripDayId }
            .mapValues { entry -> entry.value.map { it.id }.toSet() }

        return days.sortedBy { it.dayNumber }.associate { day ->
            val originalIds = defaultOrders[day.id].orEmpty()
            val aiIds = aiDays.firstOrNull { it.dayId == day.id }?.orderedPlaceIds.orEmpty()
            val allowedIds = placeIdsByDay[day.id].orEmpty()
            val validated = aiIds.filter { it in allowedIds }.distinct()
            val missing = originalIds.filterNot { it in validated }
            day.id to (validated + missing)
        }.filterValues { it.isNotEmpty() }
    }

    fun flattenDayOrders(
        days: List<TripDay>,
        places: List<Place>,
        dayOrders: Map<String, List<String>>
    ): List<Place> {
        val placesById = places.associateBy { it.id }
        return days.sortedBy { it.dayNumber }.flatMap { day ->
            dayOrders[day.id].orEmpty().mapNotNull(placesById::get)
        }
    }

    fun defaultDayOrders(
        days: List<TripDay>,
        places: List<Place>
    ): Map<String, List<String>> {
        return days.sortedBy { it.dayNumber }.associate { day ->
            day.id to places.filter { it.tripDayId == day.id }
                .sortedBy { it.orderIndex }
                .map { it.id }
        }
    }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private companion object {
        private const val AI_SYSTEM_PROMPT = """
            Ты помощник по планированию поездки.
            Нужно предложить более удобный порядок мест внутри каждого дня и выбрать один подходящий режим передвижения.
            Нельзя переносить место в другой день и нельзя придумывать новые id.
            Верни JSON вида:
            {
              "suggestedTravelMode": "WALKING|DRIVING|TRANSIT|BICYCLING",
              "summary": "Краткое объяснение на русском, до 160 символов",
              "days": [
                {
                  "dayId": "id дня",
                  "orderedPlaceIds": ["id1", "id2"]
                }
              ]
            }
            Учитывай компактность маршрута, предполагаемую городскую среду и удобство перемещений.
        """
    }
}

private data class AiRoutePlanResponse(
    val suggestedTravelMode: String? = null,
    val summary: String? = null,
    val days: List<AiRoutePlanDay> = emptyList()
)

private data class AiRoutePlanDay(
    val dayId: String = "",
    val orderedPlaceIds: List<String> = emptyList()
)
