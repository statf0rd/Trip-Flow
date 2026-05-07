package com.triloo.data.route

import android.os.Handler
import android.os.Looper
import com.triloo.data.model.Place
import com.triloo.data.model.TravelMode
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FilterVehicleTypes
import com.yandex.mapkit.transport.masstransit.MasstransitRouter
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.Route as MasstransitRoute
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session as MasstransitSession
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.mapkit.transport.masstransit.TransitOptions
import com.yandex.runtime.Error
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Сырой результат построения маршрута через Yandex MapKit Transport — точки
 * полилинии для рендера на карте плюс метрики. До этого RouteOptimizer для
 * TRANSIT возвращал straight-line fallback и карта рисовала прямые между
 * точками, что не имело отношения к реальной транспортной сети.
 *
 * Здесь сознательно не парсим секции (walk vs metro vs bus) — для этого
 * пришлось бы тащить SubpolylineHelper и проксировать индексы. Полилиния
 * целиком решает основную проблему: линия следует за дорогами/рельсами,
 * а не идёт по дуге большого круга.
 */
data class YandexRouteResult(
    val points: List<RoutePoint>,
    val distanceMeters: Int,
    val durationMinutes: Int
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Все вызовы Yandex-роутеров обязаны идти с main-thread'а (внутреннее
 * ограничение MapKit), поэтому каждый метод оборачивает работу в
 * `withContext(Dispatchers.Main)` + `suspendCancellableCoroutine`.
 */
@Singleton
class YandexRouter @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val drivingRouter: DrivingRouter by lazy {
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
    }
    private val pedestrianRouter: PedestrianRouter by lazy {
        TransportFactory.getInstance().createPedestrianRouter()
    }
    private val masstransitRouter: MasstransitRouter by lazy {
        TransportFactory.getInstance().createMasstransitRouter()
    }

    /**
     * Yandex Session.cancel() обязан выполняться на Main thread. Сам колбэк
     * `invokeOnCancellation` от kotlinx.coroutines зовётся синхронно из того
     * потока, который инициировал отмену (часто IO/Default), поэтому без
     * пост-в-Main MapKit падает с SIGTRAP «Invoked not in UI thread».
     */
    private fun cancelOnMain(action: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            runCatching { action() }
        } else {
            mainHandler.post { runCatching { action() } }
        }
    }

    suspend fun route(places: List<Place>, mode: TravelMode): YandexRouteResult? {
        if (places.size < 2) return null
        val requestPoints = places.map { place ->
            RequestPoint(
                Point(place.latitude, place.longitude),
                RequestPointType.WAYPOINT,
                null,
                null,
                null
            )
        }
        return runCatching {
            withContext(Dispatchers.Main) {
                when (mode) {
                    TravelMode.DRIVING -> requestDriving(requestPoints)
                    TravelMode.WALKING -> requestPedestrian(requestPoints)
                    TravelMode.TRANSIT -> requestMasstransit(requestPoints)
                    TravelMode.BICYCLING -> null
                }
            }
        }.getOrNull()
    }

    private suspend fun requestDriving(points: List<RequestPoint>): YandexRouteResult? =
        suspendCancellableCoroutine { cont ->
            val session = drivingRouter.requestRoutes(
                points,
                DrivingOptions(),
                VehicleOptions(),
                object : DrivingSession.DrivingRouteListener {
                    override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                        if (!cont.isActive) return
                        val route = routes.firstOrNull()
                        if (route == null) {
                            cont.resume(null); return
                        }
                        val pts = route.geometry.points.map { RoutePoint(it.latitude, it.longitude) }
                        val durationSec = route.metadata.weight.timeWithTraffic.value
                        val distanceMeters = route.metadata.weight.distance.value
                        cont.resume(
                            YandexRouteResult(
                                points = pts,
                                distanceMeters = distanceMeters.toInt(),
                                durationMinutes = ceil(durationSec / 60.0).toInt()
                            )
                        )
                    }

                    override fun onDrivingRoutesError(error: Error) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
            cont.invokeOnCancellation { cancelOnMain { session.cancel() } }
        }

    private suspend fun requestPedestrian(points: List<RequestPoint>): YandexRouteResult? =
        suspendCancellableCoroutine { cont ->
            val session = pedestrianRouter.requestRoutes(
                points,
                TimeOptions(),
                RouteOptions(com.yandex.mapkit.transport.masstransit.FitnessOptions(false, false)),
                object : MasstransitSession.RouteListener {
                    override fun onMasstransitRoutes(routes: MutableList<MasstransitRoute>) {
                        if (!cont.isActive) return
                        cont.resume(routes.firstOrNull()?.toYandexResult())
                    }

                    override fun onMasstransitRoutesError(error: Error) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
            cont.invokeOnCancellation { cancelOnMain { session.cancel() } }
        }

    private suspend fun requestMasstransit(points: List<RequestPoint>): YandexRouteResult? =
        suspendCancellableCoroutine { cont ->
            val session = masstransitRouter.requestRoutes(
                points,
                TransitOptions(FilterVehicleTypes.NONE.value, TimeOptions()),
                RouteOptions(com.yandex.mapkit.transport.masstransit.FitnessOptions(false, false)),
                object : MasstransitSession.RouteListener {
                    override fun onMasstransitRoutes(routes: MutableList<MasstransitRoute>) {
                        if (!cont.isActive) return
                        cont.resume(routes.firstOrNull()?.toYandexResult())
                    }

                    override fun onMasstransitRoutesError(error: Error) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
            cont.invokeOnCancellation { cancelOnMain { session.cancel() } }
        }

    private fun MasstransitRoute.toYandexResult(): YandexRouteResult {
        val pts = geometry.points.map { RoutePoint(it.latitude, it.longitude) }
        val durationSec = metadata.weight.time.value
        val distanceMeters = metadata.weight.walkingDistance.value
            .takeIf { it > 0 }
            ?: estimateDistanceMeters(pts)
        return YandexRouteResult(
            points = pts,
            distanceMeters = distanceMeters.toInt(),
            durationMinutes = ceil(durationSec / 60.0).toInt()
        )
    }

    private fun estimateDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        var prev = points.first()
        val r = 6371000.0
        for (i in 1 until points.size) {
            val cur = points[i]
            val dLat = Math.toRadians(cur.latitude - prev.latitude)
            val dLon = Math.toRadians(cur.longitude - prev.longitude)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(prev.latitude)) *
                kotlin.math.cos(Math.toRadians(cur.latitude)) *
                kotlin.math.sin(dLon / 2).let { it * it }
            val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
            sum += r * c
            prev = cur
        }
        return sum
    }
}
