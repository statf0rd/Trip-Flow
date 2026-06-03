package com.triloo

import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TravelMode
import com.triloo.data.places.NearbyPlacesProvider
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.remote.OpenRouteServiceApi
import com.triloo.data.remote.OpenRouteServiceDirectionsRequest
import com.triloo.data.remote.OpenRouteServiceDirectionsResponse
import com.triloo.data.route.MapRouteProvider
import com.triloo.data.route.NearestNeighborRouteOptimizer
import com.triloo.data.route.YandexRouteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Замер качества эвристики ближайшего соседа (§4.3 ВКР).
 * Сравнивает длину маршрута в исходном порядке, после NN и оптимального
 * открытого маршрута (Хелда–Карпа) на наборах из 5–15 точек в границах Сеула.
 * Все длины — по геодезическому расстоянию (формула гаверсинусов), как в самом
 * оптимизаторе; сеть не вызывается (optimizeRoute использует только гаверсинус).
 */
class RouteQualityMeasurementTest {

    private fun haversine(a: Place, b: Place): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun pathLength(order: List<Place>): Double =
        order.zipWithNext().sumOf { (x, y) -> haversine(x, y) }

    /** Оптимальный открытый маршрут (свободные концы), Хелда–Карпа. */
    private fun optimalOpenPath(points: List<Place>): Double {
        val n = points.size
        if (n <= 1) return 0.0
        val d = Array(n) { i -> DoubleArray(n) { j -> haversine(points[i], points[j]) } }
        val full = 1 shl n
        val dp = Array(full) { DoubleArray(n) { Double.POSITIVE_INFINITY } }
        for (i in 0 until n) dp[1 shl i][i] = 0.0
        for (mask in 0 until full) for (i in 0 until n) {
            val cur = dp[mask][i]
            if (cur == Double.POSITIVE_INFINITY || mask and (1 shl i) == 0) continue
            for (j in 0 until n) {
                if (mask and (1 shl j) != 0) continue
                val nm = mask or (1 shl j)
                val c = cur + d[i][j]
                if (c < dp[nm][j]) dp[nm][j] = c
            }
        }
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until n) best = min(best, dp[full - 1][i])
        return best
    }

    private fun optimizer() = NearestNeighborRouteOptimizer(
        openRouteServiceApi = object : OpenRouteServiceApi {
            override suspend fun getDirections(
                profile: String, apiKey: String, request: OpenRouteServiceDirectionsRequest
            ): OpenRouteServiceDirectionsResponse = OpenRouteServiceDirectionsResponse(routes = emptyList())
        },
        nearbyPlacesProvider = object : NearbyPlacesProvider {
            override suspend fun getNearbyPlaces(
                latitude: Double, longitude: Double, radius: Int, type: String?
            ): List<PlaceSuggestion> = emptyList()
        },
        mapRouteProvider = object : MapRouteProvider {
            override suspend fun route(places: List<Place>, mode: TravelMode): YandexRouteResult? = null
        }
    )

    private fun place(lat: Double, lng: Double) = Place(
        tripId = "t", tripDayId = "d", name = "p", latitude = lat, longitude = lng,
        category = PlaceCategory.ATTRACTION
    )

    @Test
    fun nearestNeighborQualityAcrossSizes() = runBlocking {
        val opt = optimizer()
        val sizes = listOf(5, 7, 10, 12, 15)
        val setsPerSize = 20
        val latMin = 37.45; val latMax = 37.62; val lngMin = 126.85; val lngMax = 127.12
        println("== Качество NN (среднее по $setsPerSize наборам, Сеул) ==")
        println("N | исходный,м | NN,м | оптимум,м | выигрыш,% | откл.от опт.,%")
        for (n in sizes) {
            val rnd = Random(42L + n)
            var sOrig = 0.0; var sNn = 0.0; var sOpt = 0.0; var sImpr = 0.0; var sDev = 0.0
            repeat(setsPerSize) {
                val pts = (0 until n).map {
                    place(
                        latMin + rnd.nextDouble() * (latMax - latMin),
                        lngMin + rnd.nextDouble() * (lngMax - lngMin)
                    )
                }
                val orig = pathLength(pts)
                val nn = pathLength(opt.optimizeRoute(pts, startLocation = null).optimizedPlaces)
                val best = optimalOpenPath(pts)
                sOrig += orig; sNn += nn; sOpt += best
                sImpr += if (orig > 0) (orig - nn) / orig * 100 else 0.0
                sDev += if (best > 0) (nn - best) / best * 100 else 0.0
            }
            val k = setsPerSize.toDouble()
            println(
                String.format(
                    Locale.US, "%2d | %10.0f | %8.0f | %9.0f | %8.1f | %8.1f",
                    n, sOrig / k, sNn / k, sOpt / k, sImpr / k, sDev / k
                )
            )
            assertTrue("NN не может быть короче оптимума", sNn / k >= sOpt / k - 1.0)
        }
    }
}
