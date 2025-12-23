package com.triloo.data.heatmap

import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import kotlin.math.cos
import kotlin.math.floor

data class HeatmapConfig(
    val cellSizeMeters: Int = 600,
    val minRating: Float = 3.5f,
    val ratingWeight: Float = 0.7f,
    val densityWeight: Float = 0.3f,
    val minPlacesPerCell: Int = 1
)

data class HeatmapCell(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val category: PlaceCategory,
    val score: Float,
    val averageRating: Float,
    val placeCount: Int
)

class CategoryHeatmapCalculator {

    fun buildHeatmap(
        places: List<Place>,
        category: PlaceCategory,
        config: HeatmapConfig = HeatmapConfig()
    ): List<HeatmapCell> {
        val filtered = places.asSequence()
            .filter { it.category == category }
            .mapNotNull { place -> place.rating?.let { rating -> place to rating } }
            .filter { it.second >= config.minRating }
            .toList()

        if (filtered.isEmpty()) return emptyList()

        val referenceLat = filtered.map { it.first.latitude }.average()
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = (111_320.0 * cos(Math.toRadians(referenceLat)))
            .coerceAtLeast(1e-6)
        val cellSize = config.cellSizeMeters.toDouble().coerceAtLeast(50.0)

        val cells = mutableMapOf<CellKey, MutableCell>()

        for ((place, rating) in filtered) {
            val xMeters = place.longitude * metersPerDegreeLon
            val yMeters = place.latitude * metersPerDegreeLat
            val col = floor(xMeters / cellSize).toInt()
            val row = floor(yMeters / cellSize).toInt()

            val cell = cells.getOrPut(CellKey(row, col)) { MutableCell() }
            cell.ratingSum += rating
            cell.count += 1
        }

        val maxCount = cells.values.maxOfOrNull { it.count } ?: 1
        val weightSum = (config.ratingWeight + config.densityWeight).coerceAtLeast(0.01f)
        val ratingWeight = config.ratingWeight / weightSum
        val densityWeight = config.densityWeight / weightSum

        return cells.mapNotNull { (key, cell) ->
            if (cell.count < config.minPlacesPerCell) return@mapNotNull null
            val avgRating = (cell.ratingSum / cell.count).toFloat()
            val normalizedRating = (avgRating / 5.0f).coerceIn(0f, 1f)
            val normalizedDensity = (cell.count / maxCount.toFloat()).coerceIn(0f, 1f)
            val score = (normalizedRating * ratingWeight + normalizedDensity * densityWeight)
                .coerceIn(0f, 1f)

            val centerX = (key.col + 0.5) * cellSize
            val centerY = (key.row + 0.5) * cellSize
            val centerLongitude = centerX / metersPerDegreeLon
            val centerLatitude = centerY / metersPerDegreeLat

            HeatmapCell(
                centerLatitude = centerLatitude,
                centerLongitude = centerLongitude,
                category = category,
                score = score,
                averageRating = avgRating,
                placeCount = cell.count
            )
        }.sortedByDescending { it.score }
    }

    private data class CellKey(val row: Int, val col: Int)

    private data class MutableCell(
        var ratingSum: Float = 0f,
        var count: Int = 0
    )
}
