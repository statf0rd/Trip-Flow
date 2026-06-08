package com.triloo

import com.google.gson.Gson
import com.triloo.data.ai.OpenAiService
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TravelMode
import com.triloo.data.model.TripDay
import com.triloo.data.remote.OpenAiApi
import com.triloo.data.remote.OpenAiChatRequest
import com.triloo.data.remote.OpenAiChatResponse
import com.triloo.data.route.RoutePlanSource
import com.triloo.data.route.RoutePlanningAssistant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RoutePlanningAssistantTest {

    @Test
    fun heuristicSuggestionReordersDayByDistanceAndSuggestsTravelMode() = runBlocking {
        val assistant = assistant()
        val day = sampleDay()
        val places = listOf(
            place("a", day.id, 55.7500, 37.6100, order = 0), // Start
            place("c", day.id, 55.7500, 37.6300, order = 1), // Far
            place("b", day.id, 55.7500, 37.6200, order = 2)  // Middle
        )

        val suggestion = assistant.suggestHeuristic(listOf(day), places)

        assertNotNull(suggestion)
        assertEquals(listOf("a", "b", "c"), suggestion?.dayOrders?.get(day.id))
        assertEquals(TravelMode.WALKING, suggestion?.suggestedTravelMode)
    }

    @Test
    fun suggestHeuristicReturnsNullForLessThanTwoPlaces() {
        val day = sampleDay()
        val suggestion = assistant().suggestHeuristic(
            listOf(day),
            listOf(place("only", day.id, 55.75, 37.61))
        )
        assertNull(suggestion)
    }

    @Test
    fun planRouteWithoutAiReturnsHeuristic() = runBlocking {
        val day = sampleDay()
        val suggestion = assistant().planRoute(
            trip = null,
            days = listOf(day),
            places = compactPlaces(day.id),
            preferAi = false
        )
        assertNotNull(suggestion)
        assertEquals(RoutePlanSource.HEURISTIC, suggestion?.source)
    }

    @Test
    fun planRouteFallsBackToHeuristicWhenAiUnavailable() = runBlocking {
        val day = sampleDay()
        // AI недоступен (Noop API -> generateJson == null): ожидаем fallback на эвристику.
        val suggestion = assistant().planRoute(
            trip = null,
            days = listOf(day),
            places = compactPlaces(day.id),
            preferAi = true
        )
        assertNotNull(suggestion)
        assertEquals(RoutePlanSource.HEURISTIC, suggestion?.source)
    }

    @Test
    fun planRouteReturnsNullForLessThanTwoPlaces() = runBlocking {
        val day = sampleDay()
        val suggestion = assistant().planRoute(
            trip = null,
            days = listOf(day),
            places = listOf(place("only", day.id, 55.75, 37.61)),
            preferAi = true
        )
        assertNull(suggestion)
    }

    @Test
    fun defaultDayOrdersSortByOrderIndex() {
        val day = sampleDay()
        val places = listOf(
            place("c", day.id, 55.75, 37.63, order = 2),
            place("a", day.id, 55.75, 37.61, order = 0),
            place("b", day.id, 55.75, 37.62, order = 1)
        )

        val orders = assistant().defaultDayOrders(listOf(day), places)

        assertEquals(listOf("a", "b", "c"), orders[day.id])
    }

    @Test
    fun flattenDayOrdersFollowsDayAndOrder() {
        val day = sampleDay()
        val places = compactPlaces(day.id)
        val orders = assistant().defaultDayOrders(listOf(day), places)

        val flat = assistant().flattenDayOrders(listOf(day), places, orders)

        assertEquals(orders[day.id], flat.map { it.id })
    }

    @Test
    fun heuristicSuggestsDrivingForLongDistances() {
        val day = sampleDay()
        val places = listOf(
            place("a", day.id, 55.60, 37.60, order = 0),
            place("b", day.id, 55.82, 37.60, order = 1) // ~24 км
        )

        val suggestion = assistant().suggestHeuristic(listOf(day), places)

        assertEquals(TravelMode.DRIVING, suggestion?.suggestedTravelMode)
    }

    private fun assistant() = RoutePlanningAssistant(
        openAiService = OpenAiService(NoopOpenAiApi()),
        gson = Gson()
    )

    private fun sampleDay() = TripDay(
        id = "day-1",
        tripId = "trip-1",
        date = LocalDate.of(2026, 5, 10),
        dayNumber = 1
    )

    private fun place(id: String, dayId: String, lat: Double, lon: Double, order: Int = 0) = Place(
        id = id,
        tripId = "trip-1",
        tripDayId = dayId,
        name = id,
        latitude = lat,
        longitude = lon,
        category = PlaceCategory.ATTRACTION,
        orderIndex = order
    )

    private fun compactPlaces(dayId: String) = listOf(
        place("a", dayId, 55.7500, 37.6100, 0),
        place("b", dayId, 55.7500, 37.6200, 1),
        place("c", dayId, 55.7500, 37.6300, 2)
    )

    private class NoopOpenAiApi : OpenAiApi {
        override suspend fun chatCompletions(
            authorization: String,
            request: OpenAiChatRequest
        ): OpenAiChatResponse = OpenAiChatResponse()
    }
}
