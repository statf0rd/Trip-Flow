package com.triloo.ui.tripdetails

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.triloo.BuildConfig
import com.triloo.data.heatmap.CategoryHeatmapCalculator
import com.triloo.data.heatmap.HeatmapConfig
import com.triloo.data.model.*
import com.triloo.data.route.LatLng
import com.triloo.data.route.PlaceRecommendation
import com.triloo.data.route.RoutePlanSource
import com.triloo.data.route.RouteDetails
import com.triloo.data.route.RoutePlanningMode
import com.triloo.feature.map.MapCoordinate
import com.triloo.feature.map.MapHeatmapCell
import com.triloo.feature.map.MapMarker
import com.triloo.feature.map.TripYandexMapView
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

private data class DaySchedule(
    val scheduled: List<PlaceSegment> = emptyList(),
    val unscheduled: List<Place> = emptyList()
) {
    val isEmpty: Boolean get() = scheduled.isEmpty() && unscheduled.isEmpty()
}

private data class PlaceSegment(
    val place: Place,
    val startMinutes: Int,
    val durationMinutes: Int,
    val isContinuation: Boolean,
    val timeFormat: TimeFormat
) : TimelineItem {
    override val minutes: Int = durationMinutes
}

private data class MutableDaySchedule(
    val scheduled: MutableList<PlaceSegment> = mutableListOf(),
    val unscheduled: MutableList<Place> = mutableListOf()
)

private fun buildDaySchedules(
    sortedDays: List<TripDay>,
    places: List<Place>
): Map<String, DaySchedule> {
    val dayIndex = sortedDays.mapIndexed { index, day -> day.id to index }.toMap()
    val builder = sortedDays.associate { it.id to MutableDaySchedule() }.toMutableMap()

    places.forEach { place ->
        val baseIndex = dayIndex[place.tripDayId] ?: return@forEach
        val startMinutes = place.scheduledTime?.let { parseTimeToMinutes(it) }
        val timeFormat = place.scheduledTime?.let { detectTimeFormat(it) } ?: TimeFormat.HOURS_24
        if (startMinutes == null) {
            builder[place.tripDayId]?.unscheduled?.add(place)
            return@forEach
        }
        var remaining = max(place.estimatedDuration ?: 45, 1)
        var currentDayIndex = baseIndex
        var currentStart: Int = startMinutes
        var isContinuation = false

        while (remaining > 0 && currentDayIndex < sortedDays.size) {
            val dayId = sortedDays[currentDayIndex].id
            val availableToday = (1440 - currentStart).coerceAtLeast(0)
            val segmentDuration = min(remaining, max(availableToday, 1))
            builder[dayId]?.scheduled?.add(
                PlaceSegment(
                    place = place,
                    startMinutes = currentStart,
                    durationMinutes = segmentDuration,
                    isContinuation = isContinuation,
                    timeFormat = timeFormat
                )
            )
            remaining -= segmentDuration
            currentDayIndex += 1
            currentStart = 0
            isContinuation = true
        }
    }

    return builder.mapValues { entry ->
        DaySchedule(
            scheduled = entry.value.scheduled.sortedBy { it.startMinutes },
            unscheduled = entry.value.unscheduled
        )
    }
}

// Вкладка плана.

@Composable
fun PlanTab(
    days: List<TripDay>,
    places: List<Place>,
    onDayClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
    onEditPlace: (String) -> Unit = {},
    onAddPlace: (String) -> Unit,
    onDeletePlace: (String) -> Unit = {}
) {
    if (days.isEmpty()) {
        EmptyState(
            emoji = "📅",
            title = "Нет дней в плане",
            subtitle = "Дни будут созданы автоматически по датам поездки"
        )
    } else {
        val sortedDays = remember(days) { days.sortedBy { it.dayNumber } }
        val schedules = remember(sortedDays, places) {
            buildDaySchedules(sortedDays, places)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sortedDays, key = { it.id }) { day ->
                val schedule = schedules[day.id] ?: DaySchedule()
                DayCard(
                    day = day,
                    schedule = schedule,
                    onDayClick = { onDayClick(day.id) },
                    onPlaceClick = onPlaceClick,
                    onEditPlace = onEditPlace,
                    onAddPlace = { onAddPlace(day.id) },
                    onDeletePlace = onDeletePlace
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: TripDay,
    schedule: DaySchedule,
    onDayClick: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onAddPlace: () -> Unit,
    onDeletePlace: (String) -> Unit
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru")) 
    }
    var isExpanded by remember { mutableStateOf(true) }
    
    TrilooCard(onClick = onDayClick) {
        // Заголовок дня.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Бейдж с номером дня.
                Surface(
                    shape = CircleShape,
                    color = CoralSubtle
                ) {
                    Text(
                        text = "${day.dayNumber}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = day.title ?: "День ${day.dayNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = day.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = TrilooMotion.selectSpring,
                label = "dayExpandRotation"
            )
            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
        
        // Разворачиваемый список мест.
        AnimatedVisibility(
            visible = isExpanded,
            enter = TrilooMotion.enterExpand(),
            exit = TrilooMotion.exitShrink()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (schedule.isEmpty) {
                    // Пустое состояние дня.
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddPlace),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Добавить место",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    DayTimeline(
                        segments = schedule.scheduled,
                        unscheduled = schedule.unscheduled,
                        onPlaceClick = onPlaceClick,
                        onEditPlace = onEditPlace,
                        onDeletePlace = onDeletePlace
                    )

                    // Кнопка добавления ещё одного места.
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onAddPlace,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Добавить")
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(
    segments: List<PlaceSegment>,
    unscheduled: List<Place>,
    onPlaceClick: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onDeletePlace: (String) -> Unit
) {
    val sortedScheduled = segments.sortedBy { it.startMinutes }
    val context = LocalContext.current
    val displayFormat = remember(context) { resolveDeviceTimeFormat(context) }

    if (sortedScheduled.isNotEmpty()) {
        val items = buildTimelineItems(sortedScheduled)
        val hourHeight = 36.dp
        val minEventHeight = 44.dp
        val minGapHeight = 8.dp
        val maxEventHeight = 140.dp

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                val calculated = hourHeight * (item.minutes / 60f)
                val blockHeight = if (item is PlaceSegment) {
                    calculated.coerceIn(minEventHeight, maxEventHeight)
                } else {
                    calculated.coerceIn(minGapHeight, hourHeight)
                }
                when (item) {
                    is PlaceSegment -> TimelineEventRow(
                        item = item,
                        displayFormat = displayFormat,
                        blockHeight = blockHeight,
                        onClick = { onPlaceClick(item.place.id) },
                        onEdit = { onEditPlace(item.place.id) },
                        onDelete = { onDeletePlace(item.place.id) }
                    )
                    is TimelineGap -> TimelineGapRow(
                        item = item,
                        displayFormat = displayFormat,
                        blockHeight = blockHeight
                    )
                }
            }
        }
    }

    if (unscheduled.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Без времени",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            unscheduled.forEach { place ->
                TimelineEventCard(
                    place = place,
                    timeRange = null,
                    onClick = { onPlaceClick(place.id) },
                    onEdit = { onEditPlace(place.id) },
                    onDelete = { onDeletePlace(place.id) }
                )
            }
        }
    }
}

private sealed interface TimelineItem {
    val minutes: Int
}

private data class TimelineGap(
    val startMinutes: Int,
    override val minutes: Int
) : TimelineItem

private fun buildTimelineItems(events: List<PlaceSegment>): List<TimelineItem> {
    if (events.isEmpty()) return emptyList()
    val items = mutableListOf<TimelineItem>()
    var lastEnd = events.first().startMinutes
    events.forEach { event ->
        if (event.startMinutes > lastEnd) {
            items.add(
                TimelineGap(
                    startMinutes = lastEnd,
                    minutes = event.startMinutes - lastEnd
                )
            )
        }
        items.add(event)
        val end = event.startMinutes + event.durationMinutes
        if (end > lastEnd) lastEnd = end
    }
    return items
}

@Composable
private fun TimelineEventRow(
    item: PlaceSegment,
    displayFormat: TimeFormat,
    blockHeight: Dp,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val railLabel = formatMinutesToTime(item.startMinutes, displayFormat)
    val endLabel = formatMinutesToTime(item.startMinutes + item.durationMinutes, displayFormat)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(
            startMinutes = item.startMinutes,
            timeFormat = displayFormat,
            timeLabel = railLabel,
            endLabel = endLabel,
            blockMinutes = item.durationMinutes,
            blockHeight = blockHeight,
            lineColor = MaterialTheme.colorScheme.primary,
            showIntermediateTicks = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        TimelineEventCard(
            place = item.place,
            timeRange = null,
            durationMinutes = item.durationMinutes,
            onClick = onClick,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier.heightIn(min = blockHeight)
        )
    }
}

@Composable
private fun TimelineGapRow(
    item: TimelineGap,
    displayFormat: TimeFormat,
    blockHeight: Dp
) {
    val gapLabel = "Окно ${formatDurationLabel(item.minutes)}"
    val railLabel = formatMinutesToTime(item.startMinutes, displayFormat)
    val endLabel = formatMinutesToTime(item.startMinutes + item.minutes, displayFormat)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(
            startMinutes = item.startMinutes,
            timeFormat = displayFormat,
            timeLabel = railLabel,
            endLabel = endLabel,
            blockMinutes = item.minutes,
            blockHeight = blockHeight,
            lineColor = MaterialTheme.colorScheme.outlineVariant,
            isMuted = true,
            showIntermediateTicks = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = blockHeight),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = gapLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$railLabel — $endLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(
    startMinutes: Int,
    timeFormat: TimeFormat,
    timeLabel: String,
    endLabel: String? = null,
    blockMinutes: Int,
    blockHeight: Dp,
    lineColor: Color,
    isMuted: Boolean = false,
    showIntermediateTicks: Boolean = true
) {
    val labelWidth = 64.dp
    Column(
        modifier = Modifier.width(104.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight),
            verticalAlignment = Alignment.Top
        ) {
            // Колонка временных меток слева от линии таймлайна.
            if (!showIntermediateTicks && endLabel != null) {
                Column(
                    modifier = Modifier
                        .width(labelWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                    Text(
                        text = endLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(labelWidth))
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
            ) {
                val lineModifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        if (isMuted) {
                            MaterialTheme.colorScheme.outlineVariant
                        } else {
                            lineColor
                        }
                    )
                Box(modifier = lineModifier)

                if (!isMuted) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopCenter)
                            .clip(CircleShape)
                            .background(lineColor)
                    )
                }

                if (!isMuted) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(lineColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    place: Place,
    timeRange: String?,
    durationMinutes: Int? = null,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color = place.category.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = place.category.icon,
                            contentDescription = place.category.displayName,
                            tint = place.category.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = place.address ?: place.category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (place.isVisited) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Посещено",
                            tint = TealSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (place.rating != null || durationMinutes != null || place.estimatedDuration != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    place.rating?.let { rating ->
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = GoldenAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val duration = durationMinutes ?: place.estimatedDuration
                    duration?.let {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDurationLabel(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить место?") },
            text = { Text("${place.name} будет удалено из плана поездки.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// Вкладка карты.

@Composable
fun MapTab(
    trip: Trip,
    places: List<Place>,
    participants: List<Participant>,
    routeDetails: RouteDetails? = null,
    recommendations: List<PlaceRecommendation> = emptyList(),
    destinationMarker: DestinationMapMarker? = null,
    selectedTravelMode: TravelMode = TravelMode.WALKING,
    selectedPlanningMode: RoutePlanningMode = RoutePlanningMode.CLASSIC,
    suggestedTravelMode: TravelMode? = null,
    routePlanningSummary: String? = null,
    routePlanningSource: RoutePlanSource? = null,
    locationPermissionGranted: Boolean = false,
    showLocationSharingPrompt: Boolean = false,
    locationSharingActive: Boolean = false,
    locationSharingStatus: String? = null,
    locationSharingError: String? = null,
    onPlanningModeSelected: (RoutePlanningMode) -> Unit = {},
    onTravelModeSelected: (TravelMode) -> Unit = {},
    onApplySuggestedTravelMode: () -> Unit = {},
    onStartLocationSharing: () -> Unit = {},
    onStopLocationSharing: () -> Unit = {},
    onEnableLocationSharing: () -> Unit = {}
) {
    val yandexMapEnabled = BuildConfig.APP_MAPKIT_VIEW_ENABLED
    val validPlaces = remember(places) { places.filter { it.latitude != 0.0 && it.longitude != 0.0 } }
    val participantPoints = remember(participants) {
        participants.filter { it.shareLocation }.mapNotNull { participant ->
            val lat = participant.lastLatitude
            val lon = participant.lastLongitude
            if (lat != null && lon != null) {
                participant to LatLng(lat, lon)
            } else null
        }
    }
    val hotelPoint = remember(trip.hotelLatitude, trip.hotelLongitude) {
        val hotelLat = trip.hotelLatitude
        val hotelLon = trip.hotelLongitude
        if (hotelLat != null && hotelLon != null) {
            LatLng(hotelLat, hotelLon)
        } else {
            null
        }
    }

    val ratedPlaces = remember(validPlaces) { validPlaces.filter { it.rating != null } }
    val categories = remember(ratedPlaces) {
        ratedPlaces
            .groupBy { it.category }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }
    }
    var selectedCategory by remember(ratedPlaces) {
        mutableStateOf(categories.firstOrNull() ?: PlaceCategory.RESTAURANT)
    }
    val calculator = remember { CategoryHeatmapCalculator() }
    val heatmapCells = remember(ratedPlaces, selectedCategory) {
        calculator.buildHeatmap(
            places = ratedPlaces,
            category = selectedCategory,
            config = HeatmapConfig()
        )
    }
    val fallbackRoutePoints = remember(validPlaces) {
        validPlaces.map { place -> MapCoordinate(place.latitude, place.longitude) }
    }
    val renderedHeatmapCells = remember(heatmapCells) {
        heatmapCells.map { cell ->
            MapHeatmapCell(
                centerLatitude = cell.centerLatitude,
                centerLongitude = cell.centerLongitude,
                score = cell.score,
                placeCount = cell.placeCount
            )
        }
    }
    val mapMarkers = remember(
        validPlaces,
        participantPoints,
        hotelPoint,
        destinationMarker,
        recommendations,
        trip.hotelName
    ) {
        buildList {
            validPlaces.forEachIndexed { index, place ->
                add(
                    MapMarker(
                        latitude = place.latitude,
                        longitude = place.longitude,
                        label = (index + 1).toString(),
                        colorArgb = CoralPrimary.toArgb(),
                        scale = 0.96f,
                        zIndex = 2f
                    )
                )
            }
            hotelPoint?.let { point ->
                add(
                    MapMarker(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        label = "H",
                        colorArgb = Color(0xFF7C3AED).toArgb(),
                        title = trip.hotelName ?: "Отель",
                        scale = 1.02f,
                        zIndex = 3f
                    )
                )
            }
            destinationMarker?.let { marker ->
                add(
                    MapMarker(
                        latitude = marker.latitude,
                        longitude = marker.longitude,
                        label = "D",
                        colorArgb = TealSecondary.toArgb(),
                        title = marker.name,
                        scale = 1.02f,
                        zIndex = 3f
                    )
                )
            }
            participantPoints.forEachIndexed { index, (participant, point) ->
                val label = participant.displayName.firstOrNull()?.uppercaseChar()?.toString()
                    ?: (index + 1).toString()
                add(
                    MapMarker(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        label = label,
                        colorArgb = Color(0xFF2563EB).toArgb(),
                        title = participant.displayName,
                        scale = 0.92f,
                        zIndex = 4f
                    )
                )
            }
            recommendations.forEachIndexed { index, recommendation ->
                add(
                    MapMarker(
                        latitude = recommendation.latitude,
                        longitude = recommendation.longitude,
                        label = "R${index + 1}",
                        colorArgb = GoldenAccent.toArgb(),
                        title = recommendation.name,
                        scale = 0.88f,
                        zIndex = 2.5f
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (yandexMapEnabled) {
            TripYandexMapView(
                modifier = Modifier.fillMaxSize(),
                markers = mapMarkers,
                routeEncodedPolyline = routeDetails?.polylineEncoded,
                fallbackRoutePoints = fallbackRoutePoints,
                heatmapCells = renderedHeatmapCells,
                defaultCenter = run {
                    val destLat = trip.destinationLatitude
                    val destLon = trip.destinationLongitude
                    val hotelLat = trip.hotelLatitude
                    val hotelLon = trip.hotelLongitude
                    when {
                        destLat != null && destLon != null -> MapCoordinate(destLat, destLon)
                        hotelLat != null && hotelLon != null -> MapCoordinate(hotelLat, hotelLon)
                        else -> MapCoordinate(55.751244, 37.618423)
                    }
                }
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Настоящая карта временно отключена",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Yandex MapKit отклоняет текущий API ключ. Вкладка остаётся в безопасном режиме, чтобы приложение не падало.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (
            validPlaces.isEmpty() &&
            participantPoints.isEmpty() &&
            hotelPoint == null &&
            destinationMarker == null &&
            recommendations.isEmpty()
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "На карте пока нет точек",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Добавьте место в план или выберите отель при создании поездки, чтобы сразу увидеть маршрут и маркеры.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip(
                    icon = Icons.Rounded.Place,
                    text = "${validPlaces.size} мест"
                )
                if (participants.isNotEmpty()) {
                    InfoChip(
                        icon = Icons.Rounded.Group,
                        text = "${participants.size} участников"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!routePlanningSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                RoutePlanningCard(
                    selectedTravelMode = selectedTravelMode,
                    suggestedTravelMode = suggestedTravelMode,
                    summary = routePlanningSummary,
                    source = routePlanningSource,
                    onApplySuggestedTravelMode = onApplySuggestedTravelMode
                )
            }
            if (trip.isGroupTrip) {
                val bannerText = when {
                    showLocationSharingPrompt ->
                        "Разрешите доступ к геопозиции, чтобы участники видели вас на карте"
                    !locationSharingError.isNullOrBlank() ->
                        locationSharingError
                    !locationSharingStatus.isNullOrBlank() ->
                        locationSharingStatus
                    locationSharingActive ->
                        "Геошаринг активен"
                    locationPermissionGranted ->
                        "Включите фоновый геошаринг, чтобы участники видели вас даже после сворачивания приложения"
                    else ->
                        "Разрешите доступ к геопозиции, чтобы включить геошаринг"
                }

                bannerText?.let { text ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (showLocationSharingPrompt) {
                                    Icons.Rounded.MyLocation
                                } else {
                                    Icons.Rounded.LocationOn
                                },
                                contentDescription = null,
                                tint = if (locationSharingError.isNullOrBlank()) {
                                    TealSecondary
                                } else {
                                    Error
                                }
                            )
                            Text(
                                text = text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            when {
                                showLocationSharingPrompt -> {
                                    TextButton(onClick = onEnableLocationSharing) {
                                        Text("Разрешить")
                                    }
                                }

                                locationSharingActive -> {
                                    TextButton(onClick = onStopLocationSharing) {
                                        Text("Остановить")
                                    }
                                }

                                else -> {
                                    TextButton(onClick = onStartLocationSharing) {
                                        Text("Включить")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            routeDetails?.let { details ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    Text(
                        text = buildString {
                            append("Маршрут: ")
                            append((details.totalDistanceMeters / 1000.0).let { String.format(Locale.US, "%.1f", it) })
                            append(" км • ")
                            append(details.totalDurationMinutes)
                            append(" мин • ")
                            append(selectedTravelMode.displayName)
                            if (details.isEstimated) {
                                append(" • ")
                                append(details.sourceLabel)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (recommendations.isNotEmpty()) {
                RecommendationsCard(
                    recommendations = recommendations
                )
            }
            if (categories.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .widthIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Heatmap по отзывам",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { category ->
                                val isSelected = category == selectedCategory
                                TrilooChip(
                                    text = category.displayName,
                                    icon = category.icon,
                                    iconTint = if (isSelected) category.color else null,
                                    color = if (isSelected) {
                                        CoralSubtle
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    textColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = { selectedCategory = category }
                                )
                            }
                        }
                        if (heatmapCells.isNotEmpty()) {
                            val topCell = heatmapCells.first()
                            Text(
                                text = "Сильная зона: ${formatHeatmapScore(topCell.score)} • ${topCell.placeCount} мест",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Недостаточно рейтингов для heatmap",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PlanningModeSelector(
    selectedMode: RoutePlanningMode,
    onPlanningModeSelected: (RoutePlanningMode) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        items(RoutePlanningMode.entries.toList()) { mode ->
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onPlanningModeSelected(mode) },
                label = {
                    Text(
                        text = if (mode == RoutePlanningMode.AI_ASSISTED) {
                            "AI план"
                        } else {
                            mode.displayName
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun TravelModeSelector(
    selectedMode: TravelMode,
    onTravelModeSelected: (TravelMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            items(TravelMode.entries.toList()) { mode ->
                FilterChip(
                    selected = mode == selectedMode,
                    onClick = { onTravelModeSelected(mode) },
                    label = { Text("${mode.icon} ${mode.displayName}") },
                    leadingIcon = if (mode == selectedMode) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }

        if (selectedMode == TravelMode.TRANSIT) {
            Text(
                text = "Для общественного транспорта пока показывается оценочное время без live-маршрута.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoutePlanningCard(
    selectedTravelMode: TravelMode,
    suggestedTravelMode: TravelMode?,
    summary: String,
    source: RoutePlanSource?,
    onApplySuggestedTravelMode: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (source) {
                    RoutePlanSource.AI -> "AI-планировщик"
                    RoutePlanSource.HEURISTIC -> "Подсказка маршрута"
                    null -> "Подсказка маршрута"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (suggestedTravelMode != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Рекомендуемый режим: ${suggestedTravelMode.icon} ${suggestedTravelMode.displayName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (suggestedTravelMode != selectedTravelMode) {
                        TextButton(onClick = onApplySuggestedTravelMode) {
                            Text("Применить")
                        }
                    }
                }
                if (suggestedTravelMode == TravelMode.TRANSIT) {
                    Text(
                        text = "Режим общественного транспорта пока остаётся оценочным: без live-route и пересадок.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationsCard(
    recommendations: List<PlaceRecommendation>
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Рядом с маршрутом",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recommendations, key = { it.placeId }) { recommendation ->
                    RecommendationItem(recommendation = recommendation)
                }
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    recommendation: PlaceRecommendation
) {
    Surface(
        modifier = Modifier.width(228.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recommendation.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = recommendation.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = CoralSubtle
                ) {
                    Text(
                        text = recommendation.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                recommendation.rating?.let { rating ->
                    Text(
                        text = "★ ${String.format(Locale.US, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text = recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = TealSecondary
            )
        }
    }
}

@Composable
private fun HeatmapPreview(places: List<Place>) {
    val ratedPlaces = remember(places) { places.filter { it.rating != null } }
    val categories = remember(ratedPlaces) {
        ratedPlaces
            .groupBy { it.category }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }
    }
    var selectedCategory by remember(ratedPlaces) {
        mutableStateOf(categories.firstOrNull() ?: PlaceCategory.RESTAURANT)
    }
    val calculator = remember { CategoryHeatmapCalculator() }
    val heatmapCells = remember(ratedPlaces, selectedCategory) {
        calculator.buildHeatmap(
            places = ratedPlaces,
            category = selectedCategory,
            config = HeatmapConfig()
        )
    }

    TrilooCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Коэффициенты по отзывам",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Выберите категорию, чтобы увидеть зоны, где она сильнее развита",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (categories.isEmpty()) {
            Text(
                text = "Нет мест с рейтингами для расчёта",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@TrilooCard
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                TrilooChip(
                    text = category.displayName,
                    emoji = category.emoji,
                    color = if (isSelected) {
                        CoralSubtle
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    textColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { selectedCategory = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (heatmapCells.isEmpty()) {
            Text(
                text = "Недостаточно отзывов для коэффициентов по этой категории",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val topCell = heatmapCells.first()
            Text(
                text = "Ячеек: ${heatmapCells.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Сильная зона: ${formatHeatmapScore(topCell.score)} • " +
                    "${topCell.placeCount} мест • " +
                    String.format(Locale.US, "%.1f", topCell.averageRating),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatHeatmapScore(score: Float): String {
    return "${(score * 100).roundToInt()}%"
}

// Вкладка расходов.

@Composable
fun ExpensesTab(
    expenses: List<Expense>,
    totalAmount: Double,
    currency: String,
    balances: List<Balance>,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
    onToggleExpenseSettled: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteExpense: (String) -> Unit = {}
) {
    if (expenses.isEmpty()) {
        EmptyState(
            emoji = "💰",
            title = "Нет расходов",
            subtitle = "Добавьте первый расход, чтобы отслеживать бюджет поездки",
            actionText = "Добавить расход",
            onAction = onAddExpense
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Итоговая карточка по расходам.
            item {
                ExpenseSummaryCard(
                    totalAmount = totalAmount,
                    currency = currency,
                    expenseCount = expenses.size
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (balances.isNotEmpty()) {
                item {
                    BalancesCard(balances = balances)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Список расходов.
            items(expenses, key = { it.id }) { expense ->
                ExpenseItem(
                    expense = expense,
                    baseCurrency = currency,
                    onClick = { onExpenseClick(expense.id) },
                    onToggleSettled = { settled ->
                        onToggleExpenseSettled(expense.id, settled)
                    },
                    onDelete = { onDeleteExpense(expense.id) }
                )
            }
        }
    }
}

@Composable
private fun ExpenseSummaryCard(
    totalAmount: Double,
    currency: String,
    expenseCount: Int
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Всего потрачено",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatCurrency(totalAmount, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$expenseCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "записей",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun BalancesCard(
    balances: List<Balance>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Кому сколько должен",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            balances.forEach { balance ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${balance.fromUserName} → ${balance.toUserName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Закрыть переводом",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatCurrency(balance.amount, balance.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseItem(
    expense: Expense,
    baseCurrency: String,
    onClick: () -> Unit,
    onToggleSettled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")) 
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка категории.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(expense.category.colorHex).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = expense.category.icon,
                    contentDescription = expense.category.displayName,
                    tint = Color(expense.category.colorHex),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.paidByName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = expense.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expense.splitType != SplitType.PAYER_ONLY) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (expense.isSettled) {
                            TealSecondary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (expense.isSettled) "Долг закрыт" else "Ожидает закрытия",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (expense.isSettled) {
                                TealSecondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(expense.amountInBaseCurrency, baseCurrency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(expense.category.colorHex)
                )
                if (expense.currency != baseCurrency) {
                    Text(
                        text = "${expense.amount} ${expense.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expense.splitType != SplitType.PAYER_ONLY) {
                    TextButton(
                        onClick = { onToggleSettled(!expense.isSettled) },
                        contentPadding = PaddingValues(top = 4.dp, start = 0.dp, end = 0.dp, bottom = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (expense.isSettled) {
                                Icons.Rounded.RestartAlt
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (expense.isSettled) "Вернуть" else "Закрыть")
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка удаления.
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    // Диалог подтверждения удаления.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить расход?") },
            text = { Text("\"${expense.description}\" будет удалён из списка расходов.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun formatCurrency(amount: Double, currency: String): String {
    val currencyCode = currency.uppercase(Locale.US)
    val symbol = when (currencyCode) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "TRY" -> "₺"
        "THB" -> "฿"
        "AED" -> "AED"
        else -> currencyCode
    }
    return "${String.format(Locale.getDefault(), "%,.0f", amount)} $symbol"
}

private object TripDetailsPreviewData {
    val trip = Trip(
        id = "trip-preview",
        name = "Новый год в Москве",
        destination = "Москва, Россия",
        startDate = LocalDate.of(2024, 12, 31),
        endDate = LocalDate.of(2025, 1, 2),
        baseCurrency = "USD",
        isGroupTrip = true
    )

    val days = listOf(
        TripDay(
            id = "day-1",
            tripId = trip.id,
            date = trip.startDate,
            dayNumber = 1,
            title = "День 1"
        ),
        TripDay(
            id = "day-2",
            tripId = trip.id,
            date = trip.startDate.plusDays(1),
            dayNumber = 2,
            title = "День 2"
        )
    )

    val places = listOf(
        Place(
            id = "place-1",
            tripId = trip.id,
            tripDayId = days[0].id,
            name = "2026",
            address = "Праздник",
            latitude = 55.751244,
            longitude = 37.618423,
            category = PlaceCategory.HOLIDAY,
            iconEmoji = "🎉",
            orderIndex = 0,
            scheduledTime = "12:00",
            estimatedDuration = 5,
            rating = 4.9f
        ),
        Place(
            id = "place-2",
            tripId = trip.id,
            tripDayId = days[0].id,
            name = "New Year",
            address = "Home",
            latitude = 55.7522,
            longitude = 37.6156,
            category = PlaceCategory.ATTRACTION,
            iconEmoji = "🌙",
            orderIndex = 1,
            scheduledTime = "23:00",
            estimatedDuration = 60,
            rating = 4.7f,
            isVisited = true
        ),
        Place(
            id = "place-3",
            tripId = trip.id,
            tripDayId = days[0].id,
            name = "Кофе с видом",
            address = "Патриаршие",
            latitude = 55.7601,
            longitude = 37.6136,
            category = PlaceCategory.CAFE,
            iconEmoji = "☕",
            orderIndex = 2,
            estimatedDuration = 45,
            rating = 4.5f
        ),
        Place(
            id = "place-4",
            tripId = trip.id,
            tripDayId = days[1].id,
            name = "Третьяковская галерея",
            address = "Лаврушинский 10",
            latitude = 55.7414,
            longitude = 37.6200,
            category = PlaceCategory.MUSEUM,
            iconEmoji = "🖼️",
            orderIndex = 0,
            scheduledTime = "10:00",
            estimatedDuration = 120,
            rating = 4.8f
        )
    )

    val expenses = listOf(
        Expense(
            tripId = trip.id,
            description = "Ужин на Арбате",
            amount = 4200.0,
            currency = "RUB",
            amountInBaseCurrency = 45.0,
            exchangeRate = 93.3,
            exchangeRateDate = LocalDate.of(2024, 12, 30),
            category = ExpenseCategory.FOOD,
            paidByUserId = "user-1",
            paidByName = "Аня",
            splitType = SplitType.EQUAL,
            date = LocalDate.of(2024, 12, 31)
        ),
        Expense(
            tripId = trip.id,
            description = "Такси до отеля",
            amount = 18.0,
            currency = "USD",
            amountInBaseCurrency = 18.0,
            exchangeRate = 1.0,
            exchangeRateDate = LocalDate.of(2024, 12, 31),
            category = ExpenseCategory.TRANSPORT,
            paidByUserId = "user-2",
            paidByName = "Кирилл",
            splitType = SplitType.EXACT,
            date = LocalDate.of(2024, 12, 31)
        )
    )

    val participants = listOf(
        Participant(
            tripId = trip.id,
            userId = "user-1",
            displayName = "Аня",
            role = ParticipantRole.ADMIN
        ),
        Participant(
            tripId = trip.id,
            userId = "user-2",
            displayName = "Кирилл",
            role = ParticipantRole.MEMBER,
            isOnline = true
        )
    )

    private val daySchedules: Map<String, DaySchedule> by lazy {
        buildDaySchedules(days, places)
    }

    val day1Schedule: DaySchedule
        get() = daySchedules[days.first().id] ?: DaySchedule()

    val day1Segments: List<PlaceSegment>
        get() = day1Schedule.scheduled

    val day1Gap: TimelineGap
        get() = buildTimelineItems(day1Segments).filterIsInstance<TimelineGap>().firstOrNull()
            ?: TimelineGap(startMinutes = 12 * 60, minutes = 90)
}

private const val PreviewBackgroundColor = 0xFF0F131AL

@Composable
private fun PreviewContainer(content: @Composable () -> Unit) {
    TrilooTheme(darkTheme = true) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(PreviewBackgroundColor)),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Preview(name = "Plan Tab", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun PlanTabPreview() {
    PreviewContainer {
        PlanTab(
            days = TripDetailsPreviewData.days,
            places = TripDetailsPreviewData.places,
            onDayClick = { _ -> },
            onPlaceClick = { _ -> },
            onEditPlace = { _ -> },
            onAddPlace = { _ -> },
            onDeletePlace = { _ -> }
        )
    }
}

@Preview(name = "Day Card", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun DayCardPreview() {
    PreviewContainer {
        DayCard(
            day = TripDetailsPreviewData.days.first(),
            schedule = TripDetailsPreviewData.day1Schedule,
            onDayClick = { },
            onPlaceClick = { _ -> },
            onEditPlace = { _ -> },
            onAddPlace = { },
            onDeletePlace = { _ -> }
        )
    }
}

@Preview(name = "Day Timeline", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun DayTimelinePreview() {
    PreviewContainer {
        DayTimeline(
            segments = TripDetailsPreviewData.day1Segments,
            unscheduled = TripDetailsPreviewData.day1Schedule.unscheduled,
            onPlaceClick = { _ -> },
            onEditPlace = { _ -> },
            onDeletePlace = { _ -> }
        )
    }
}

@Preview(name = "Timeline Event Row", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun TimelineEventRowPreview() {
    val segment = TripDetailsPreviewData.day1Segments.firstOrNull() ?: PlaceSegment(
        place = TripDetailsPreviewData.places.first(),
        startMinutes = 12 * 60,
        durationMinutes = 60,
        isContinuation = false,
        timeFormat = TimeFormat.HOURS_24
    )
    PreviewContainer {
        TimelineEventRow(
            item = segment,
            displayFormat = TimeFormat.HOURS_24,
            blockHeight = 72.dp,
            onClick = { },
            onEdit = { },
            onDelete = { }
        )
    }
}

@Preview(name = "Timeline Gap Row", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun TimelineGapRowPreview() {
    PreviewContainer {
        TimelineGapRow(
            item = TripDetailsPreviewData.day1Gap,
            displayFormat = TimeFormat.HOURS_24,
            blockHeight = 96.dp
        )
    }
}

@Preview(name = "Timeline Rail", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun TimelineRailPreview() {
    PreviewContainer {
        TimelineRail(
            startMinutes = 12 * 60,
            timeFormat = TimeFormat.HOURS_24,
            timeLabel = "12:00",
            endLabel = "23:00",
            blockMinutes = 11 * 60,
            blockHeight = 140.dp,
            lineColor = MaterialTheme.colorScheme.primary,
            showIntermediateTicks = true
        )
    }
}

@Preview(name = "Timeline Event Card", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun TimelineEventCardPreview() {
    PreviewContainer {
        TimelineEventCard(
            place = TripDetailsPreviewData.places.first(),
            timeRange = "12:00 — 13:00",
            durationMinutes = 60,
            onClick = { },
            onEdit = { },
            onDelete = { }
        )
    }
}

@Preview(name = "Map Tab", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun MapTabPreview() {
    PreviewContainer {
        MapTab(
            trip = TripDetailsPreviewData.trip,
            places = TripDetailsPreviewData.places,
            participants = TripDetailsPreviewData.participants,
            routeDetails = null
        )
    }
}

@Preview(name = "Info Chip", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun InfoChipPreview() {
    PreviewContainer {
        InfoChip(
            icon = Icons.Rounded.Place,
            text = "3 места"
        )
    }
}

@Preview(name = "Heatmap Preview", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun HeatmapPreviewPreview() {
    PreviewContainer {
        HeatmapPreview(places = TripDetailsPreviewData.places)
    }
}

@Preview(name = "Expenses Tab", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun ExpensesTabPreview() {
    val total = TripDetailsPreviewData.expenses.sumOf { it.amountInBaseCurrency }
    PreviewContainer {
        ExpensesTab(
            expenses = TripDetailsPreviewData.expenses,
            totalAmount = total,
            currency = TripDetailsPreviewData.trip.baseCurrency,
            balances = emptyList(),
            onExpenseClick = { _ -> },
            onAddExpense = { },
            onToggleExpenseSettled = { _, _ -> },
            onDeleteExpense = { _ -> }
        )
    }
}

@Preview(name = "Expense Summary", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun ExpenseSummaryCardPreview() {
    PreviewContainer {
        ExpenseSummaryCard(
            totalAmount = 63.0,
            currency = TripDetailsPreviewData.trip.baseCurrency,
            expenseCount = TripDetailsPreviewData.expenses.size
        )
    }
}

@Preview(name = "Expense Item", showBackground = true, backgroundColor = PreviewBackgroundColor)
@Composable
private fun ExpenseItemPreview() {
    PreviewContainer {
        ExpenseItem(
            expense = TripDetailsPreviewData.expenses.first(),
            baseCurrency = TripDetailsPreviewData.trip.baseCurrency,
            onClick = { },
            onToggleSettled = { },
            onDelete = { }
        )
    }
}
