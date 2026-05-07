package com.triloo.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.Animation
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class MapMarker(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val colorArgb: Int,
    val title: String? = null,
    val scale: Float = 1f,
    val zIndex: Float = 0f
)

data class MapHeatmapCell(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val score: Float,
    val placeCount: Int
)

/**
 * Императивный «пульт» для карты: zoom +/-, recenter на произвольную точку.
 * Привязывается к одной [TripYandexMapView] через параметр `controller`.
 */
class TripMapController {
    internal var mapView: MapView? = null

    private companion object {
        const val MIN_ZOOM = 3f
        const val MAX_ZOOM = 19f
        const val ZOOM_STEP = 1f
    }

    fun zoomIn() {
        val view = mapView ?: return
        val pos = view.map.cameraPosition
        view.map.move(
            CameraPosition(
                pos.target,
                (pos.zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM),
                pos.azimuth,
                pos.tilt
            ),
            Animation(Animation.Type.SMOOTH, 0.25f),
            null
        )
    }

    fun zoomOut() {
        val view = mapView ?: return
        val pos = view.map.cameraPosition
        view.map.move(
            CameraPosition(
                pos.target,
                (pos.zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM),
                pos.azimuth,
                pos.tilt
            ),
            Animation(Animation.Type.SMOOTH, 0.25f),
            null
        )
    }

    fun moveTo(latitude: Double, longitude: Double, zoom: Float? = null) {
        val view = mapView ?: return
        val current = view.map.cameraPosition
        view.map.move(
            CameraPosition(
                Point(latitude, longitude),
                zoom ?: current.zoom.coerceAtLeast(13f),
                current.azimuth,
                current.tilt
            ),
            Animation(Animation.Type.SMOOTH, 0.45f),
            null
        )
    }
}

@Composable
fun rememberTripMapController(): TripMapController = remember { TripMapController() }

@Composable
fun TripYandexMapView(
    modifier: Modifier = Modifier,
    markers: List<MapMarker>,
    routeEncodedPolyline: String?,
    fallbackRoutePoints: List<MapCoordinate> = emptyList(),
    heatmapCells: List<MapHeatmapCell> = emptyList(),
    defaultCenter: MapCoordinate = MapCoordinate(55.751244, 37.618423),
    controller: TripMapController? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            map.isRotateGesturesEnabled = false
            map.isTiltGesturesEnabled = false
            map.isFastTapEnabled = true
        }
    }
    val iconCache = remember { mutableMapOf<String, ImageProvider>() }
    val routePoints = remember(routeEncodedPolyline, fallbackRoutePoints) {
        routePolylinePoints(
            encodedPolyline = routeEncodedPolyline,
            fallbackPoints = fallbackRoutePoints
        )
    }
    val allPoints = remember(markers, routePoints) {
        buildList {
            markers.forEach { add(Point(it.latitude, it.longitude)) }
            addAll(routePoints)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
        }
    }

    DisposableEffect(controller, mapView) {
        controller?.mapView = mapView
        onDispose {
            if (controller?.mapView === mapView) controller.mapView = null
        }
    }

    LaunchedEffect(allPoints, mapView, defaultCenter) {
        val map = mapView.map
        if (allPoints.isEmpty()) {
            map.move(
                CameraPosition(
                    Point(defaultCenter.latitude, defaultCenter.longitude),
                    3.5f,
                    0f,
                    0f
                )
            )
            return@LaunchedEffect
        }
        if (allPoints.size == 1) {
            map.move(
                CameraPosition(allPoints.first(), 13f, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 0.4f),
                null
            )
            return@LaunchedEffect
        }

        val bounds = BoundingBox(
            Point(
                allPoints.minOf { it.latitude },
                allPoints.minOf { it.longitude }
            ),
            Point(
                allPoints.maxOf { it.latitude },
                allPoints.maxOf { it.longitude }
            )
        )
        val cameraPosition = map.cameraPosition(Geometry.fromBoundingBox(bounds))
        map.move(
            CameraPosition(
                cameraPosition.target,
                (cameraPosition.zoom - 0.35f).coerceAtLeast(3.5f),
                cameraPosition.azimuth,
                cameraPosition.tilt
            ),
            Animation(Animation.Type.SMOOTH, 0.45f),
            null
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            val mapObjects = view.map.mapObjects
            mapObjects.clear()

            heatmapCells.take(16).forEach { cell ->
                mapObjects.addCircle(
                    Circle(
                        Point(cell.centerLatitude, cell.centerLongitude),
                        220f + cell.placeCount * 45f
                    )
                ).apply {
                    fillColor = heatmapColor(cell.score)
                    strokeColor = heatmapStrokeColor(cell.score)
                    strokeWidth = 1.5f
                    zIndex = 0f
                }
            }

            if (routePoints.size >= 2) {
                mapObjects.addPolyline(Polyline(routePoints)).apply {
                    setStrokeColor(0xFFFF6B5B.toInt())
                    setOutlineColor(0x33FFFFFF)
                    strokeWidth = 5f
                    outlineWidth = 2f
                    zIndex = 1f
                }
            }

            markers.forEach { marker ->
                mapObjects.addPlacemark(
                    Point(marker.latitude, marker.longitude),
                    iconProvider(
                        cache = iconCache,
                        color = marker.colorArgb,
                        label = marker.label
                    ),
                    markerStyle(scale = marker.scale)
                ).apply {
                    marker.title?.takeIf { it.isNotBlank() }?.let(::setText)
                    zIndex = marker.zIndex
                }
            }
        }
    )
}

/**
 * Карта для выбора точки: пин фиксирован в центре, пользователь двигает карту.
 * При остановке камеры вызывается [onLocationPicked] с координатами центра.
 */
@Composable
fun MapPickerView(
    modifier: Modifier = Modifier,
    initialCenter: MapCoordinate = MapCoordinate(55.751244, 37.618423),
    initialZoom: Float = 12f,
    markers: List<MapMarker> = emptyList(),
    onLocationPicked: (latitude: Double, longitude: Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            map.isRotateGesturesEnabled = false
            map.isTiltGesturesEnabled = false
            // \u041a\u0430\u043c\u0435\u0440\u0443 \u0441\u0442\u0430\u0432\u0438\u043c \u043e\u0434\u0438\u043d \u0440\u0430\u0437 \u0432 factory'\u0438, \u0447\u0442\u043e\u0431\u044b \u043f\u043e\u0442\u043e\u043c \u043a\u0430\u0436\u0434\u043e\u0435
            // \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 initialCenter \u0438\u0437 ViewModel'\u0430 \u043d\u0435 \u0434\u0451\u0440\u0433\u0430\u043b\u043e move()
            // \u0438 \u043d\u0435 \u0441\u0431\u0438\u0432\u0430\u043b\u043e pan-\u0436\u0435\u0441\u0442 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f.
        }
    }
    val onLocationPickedCurrent = rememberUpdatedState(onLocationPicked)
    val iconCache = remember { mutableMapOf<String, ImageProvider>() }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
        }
    }

    // \u041e\u0434\u0438\u043d \u0440\u0430\u0437 \u043f\u043e\u0437\u0438\u0446\u0438\u043e\u043d\u0438\u0440\u0443\u0435\u043c \u043a\u0430\u043c\u0435\u0440\u0443 \u043f\u043e initialCenter \u2014 \u043f\u043e\u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0435 \u0430\u043f\u0434\u0435\u0439\u0442\u044b
    // \u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442 \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u044b \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u043d\u043e \u0434\u0432\u0438\u0433\u0430\u0442\u044c \u043a\u0430\u0440\u0442\u0443, \u0438\u043d\u0430\u0447\u0435 move() \u043f\u0440\u0438
    // \u043a\u0430\u0436\u0434\u043e\u0439 recomposition \u043f\u0440\u0435\u0440\u044b\u0432\u0430\u0435\u0442 pan \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f.
    LaunchedEffect(mapView) {
        mapView.map.move(
            CameraPosition(
                Point(initialCenter.latitude, initialCenter.longitude),
                initialZoom,
                0f,
                0f
            )
        )
    }

    DisposableEffect(mapView) {
        val listener = object : CameraListener {
            override fun onCameraPositionChanged(
                map: com.yandex.mapkit.map.Map,
                position: CameraPosition,
                reason: CameraUpdateReason,
                finished: Boolean
            ) {
                if (finished) {
                    onLocationPickedCurrent.value(
                        position.target.latitude,
                        position.target.longitude
                    )
                }
            }
        }
        mapView.map.addCameraListener(listener)
        onDispose { mapView.map.removeCameraListener(listener) }
    }

    // update = \u043f\u0443\u0441\u0442\u043e\u0439: \u0432\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0439 crosshair-\u043c\u0430\u0440\u043a\u0435\u0440 \u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f \u043f\u043e\u0432\u0435\u0440\u0445
    // \u0447\u0435\u0440\u0435\u0437 Compose Icon \u0432 \u0440\u043e\u0434\u0438\u0442\u0435\u043b\u0435 (DestinationMapPicker). \u0412\u043d\u0443\u0442\u0440\u044c \u043a\u0430\u0440\u0442\u044b
    // \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u0434\u043e\u0431\u0430\u0432\u043b\u044f\u0435\u043c, \u0447\u0442\u043e\u0431\u044b recomposition \u043d\u0435 \u043f\u0435\u0440\u0435\u0441\u043e\u0437\u0434\u0430\u0432\u0430\u043b mapObjects
    // \u0438 \u043d\u0435 \u0441\u0431\u0438\u0432\u0430\u043b \u0432\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u0435\u0435 \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 \u0436\u0435\u0441\u0442\u043e\u0432 MapKit.
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            // Перерисовываем «контекстные» маркеры (уже добавленные точки поездки),
            // чтобы пользователь видел, где он стоит относительно существующих
            // мест, и не ставил новую метку поверх существующей.
            val mapObjects = view.map.mapObjects
            mapObjects.clear()
            markers.forEach { marker ->
                mapObjects.addPlacemark(
                    Point(marker.latitude, marker.longitude),
                    iconProvider(
                        cache = iconCache,
                        color = marker.colorArgb,
                        label = marker.label
                    ),
                    markerStyle(scale = marker.scale * 0.85f)
                ).apply {
                    marker.title?.takeIf { it.isNotBlank() }?.let(::setText)
                    zIndex = marker.zIndex
                    opacity = 0.65f
                }
            }
        }
    )
}

private fun routePolylinePoints(
    encodedPolyline: String?,
    fallbackPoints: List<MapCoordinate>
): List<Point> {
    if (!encodedPolyline.isNullOrBlank()) {
        return decodePolyline(encodedPolyline).map { point ->
            Point(point.latitude, point.longitude)
        }
    }
    return fallbackPoints.map { point ->
        Point(point.latitude, point.longitude)
    }
}

private fun decodePolyline(encoded: String): List<MapCoordinate> {
    val points = mutableListOf<MapCoordinate>()
    var index = 0
    var latitude = 0
    var longitude = 0

    while (index < encoded.length) {
        val latitudeValue = decodePolylineValue(encoded, index)
        latitude += latitudeValue.first
        index = latitudeValue.second

        val longitudeValue = decodePolylineValue(encoded, index)
        longitude += longitudeValue.first
        index = longitudeValue.second

        points += MapCoordinate(
            latitude = latitude / 1e5,
            longitude = longitude / 1e5
        )
    }

    return points
}

private fun decodePolylineValue(
    encoded: String,
    startIndex: Int
): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var currentIndex = startIndex
    var chunk: Int
    do {
        chunk = encoded[currentIndex].code - 63
        result = result or ((chunk and 0x1f) shl shift)
        shift += 5
        currentIndex += 1
    } while (chunk >= 0x20)
    val delta = if (result and 1 != 0) {
        (result shr 1).inv()
    } else {
        result shr 1
    }
    return delta to currentIndex
}

private fun markerStyle(scale: Float): IconStyle {
    return IconStyle()
        .setAnchor(PointF(0.5f, 0.5f))
        .setScale(scale)
}

private fun iconProvider(
    cache: MutableMap<String, ImageProvider>,
    color: Int,
    label: String
): ImageProvider {
    val key = "$color-$label"
    return cache.getOrPut(key) {
        ImageProvider.fromBitmap(createMarkerBitmap(color, label))
    }
}

private fun createMarkerBitmap(
    color: Int,
    label: String
): Bitmap {
    val size = if (label.length > 2) 88 else 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = 0xFFFFFFFF.toInt()
        strokeWidth = 4f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = if (label.length > 2) 24f else 30f
        isFakeBoldText = true
    }
    val radius = size / 2f - 6f
    val center = size / 2f
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius, strokePaint)
    val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, center, textY, textPaint)
    return bitmap
}

private fun heatmapColor(score: Float): Int {
    val clamped = score.coerceIn(0f, 1f)
    val alpha = (55 + clamped * 110).toInt()
    val red = 255
    val green = (190 - clamped * 40).toInt()
    val blue = 75
    return android.graphics.Color.argb(alpha, red, green, blue)
}

private fun heatmapStrokeColor(score: Float): Int {
    val alpha = (90 + score.coerceIn(0f, 1f) * 120).toInt()
    return android.graphics.Color.argb(alpha, 255, 255, 255)
}
