package com.triloo

import com.triloo.data.heatmap.CategoryHeatmapCalculator
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Базовые проверки генерации тепловой карты по местам одной категории.
 */
class CategoryHeatmapCalculatorTest {

    @Test
    fun buildHeatmapReturnsCellsWhenRatingsPresent() {
        val places = listOf(
            Place(
                tripId = "trip",
                tripDayId = "day",
                name = "Cafe One",
                latitude = 55.751244,
                longitude = 37.618423,
                category = PlaceCategory.CAFE,
                rating = 4.6f
            ),
            Place(
                tripId = "trip",
                tripDayId = "day",
                name = "Cafe Two",
                latitude = 55.753215,
                longitude = 37.622504,
                category = PlaceCategory.CAFE,
                rating = 4.2f
            )
        )

        val calculator = CategoryHeatmapCalculator()
        val cells = calculator.buildHeatmap(places, PlaceCategory.CAFE)

        assertTrue(cells.isNotEmpty())
        assertTrue(cells.first().score > 0f)
    }
}
