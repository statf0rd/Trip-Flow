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
import com.triloo.data.route.RoutePlanningAssistant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class RoutePlanningAssistantTest {

    @Test
    fun heuristicSuggestionReordersDayByDistanceAndSuggestsTravelMode() = runBlocking {
        val assistant = RoutePlanningAssistant(
            openAiService = OpenAiService(NoopOpenAiApi()),
            gson = Gson()
        )
        val day = TripDay(
            id = "day-1",
            tripId = "trip-1",
            date = LocalDate.of(2026, 5, 10),
            dayNumber = 1
        )
        val places = listOf(
            Place(
                id = "a",
                tripId = "trip-1",
                tripDayId = day.id,
                name = "Start",
                latitude = 55.7500,
                longitude = 37.6100,
                category = PlaceCategory.ATTRACTION,
                orderIndex = 0
            ),
            Place(
                id = "c",
                tripId = "trip-1",
                tripDayId = day.id,
                name = "Far",
                latitude = 55.7500,
                longitude = 37.6300,
                category = PlaceCategory.ATTRACTION,
                orderIndex = 1
            ),
            Place(
                id = "b",
                tripId = "trip-1",
                tripDayId = day.id,
                name = "Middle",
                latitude = 55.7500,
                longitude = 37.6200,
                category = PlaceCategory.ATTRACTION,
                orderIndex = 2
            )
        )

        val suggestion = assistant.suggestHeuristic(listOf(day), places)

        assertNotNull(suggestion)
        assertEquals(listOf("a", "b", "c"), suggestion?.dayOrders?.get(day.id))
        assertEquals(TravelMode.WALKING, suggestion?.suggestedTravelMode)
    }

    private class NoopOpenAiApi : OpenAiApi {
        override suspend fun chatCompletions(
            authorization: String,
            request: OpenAiChatRequest
        ): OpenAiChatResponse = OpenAiChatResponse()
    }
}
