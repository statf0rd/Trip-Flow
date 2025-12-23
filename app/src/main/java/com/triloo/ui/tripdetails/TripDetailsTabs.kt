package com.triloo.ui.tripdetails

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.triloo.data.heatmap.CategoryHeatmapCalculator
import com.triloo.data.heatmap.HeatmapConfig
import com.triloo.data.model.*
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
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

// PLAN TAB

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
        val schedules by remember(days, places) {
            mutableStateOf(buildDaySchedules(sortedDays, places))
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
        // Day Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Day number badge
                Surface(
                    shape = CircleShape,
                    color = CoralSubtle
                ) {
                    Text(
                        text = "${day.dayNumber}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralPrimary,
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
                        color = Slate600
                    )
                }
            }
            
            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = Slate500
                )
            }
        }
        
        // Places list (expandable)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (schedule.isEmpty) {
                    // Empty state for day
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddPlace),
                        shape = RoundedCornerShape(12.dp),
                        color = Slate100
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLocation,
                                contentDescription = null,
                                tint = Slate500,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Добавить место",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate600
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

                    // Add more button
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
    val timelineFormat = sortedScheduled.firstOrNull()?.timeFormat ?: TimeFormat.HOURS_24

    if (sortedScheduled.isNotEmpty()) {
        val items = buildTimelineItems(sortedScheduled)
        val hourHeight = 48.dp
        val minEventHeight = 44.dp
        val minGapHeight = 8.dp

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                val calculated = hourHeight * (item.minutes / 60f)
                val blockHeight = if (item is PlaceSegment) {
                    calculated.coerceAtLeast(minEventHeight)
                } else {
                    calculated.coerceIn(minGapHeight, hourHeight)
                }
                when (item) {
                    is PlaceSegment -> TimelineEventRow(
                        item = item,
                        timeFormat = timelineFormat,
                        blockHeight = blockHeight,
                        onClick = { onPlaceClick(item.place.id) },
                        onEdit = { onEditPlace(item.place.id) },
                        onDelete = { onDeletePlace(item.place.id) }
                    )
                    is TimelineGap -> TimelineGapRow(
                        item = item,
                        timeFormat = timelineFormat,
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
            color = Slate500
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
    timeFormat: TimeFormat,
    blockHeight: Dp,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val startLabel = formatMinutesToTime(item.startMinutes, item.timeFormat)
    val railLabel = formatMinutesToTime(item.startMinutes, TimeFormat.HOURS_24)
    val endLabel = formatMinutesToTime(item.startMinutes + item.durationMinutes, timeFormat)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(
            startMinutes = item.startMinutes,
            timeFormat = TimeFormat.HOURS_24,
            timeLabel = railLabel,
            endLabel = formatMinutesToTime(item.startMinutes + item.durationMinutes, TimeFormat.HOURS_24),
            blockMinutes = item.durationMinutes,
            blockHeight = blockHeight,
            lineColor = CoralPrimary,
            showIntermediateTicks = true
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
    timeFormat: TimeFormat,
    blockHeight: Dp
) {
    val gapLabel = "Окно ${formatDurationLabel(item.minutes)}"
    val railLabel = formatMinutesToTime(item.startMinutes, TimeFormat.HOURS_24)
    val endLabel = formatMinutesToTime(item.startMinutes + item.minutes, TimeFormat.HOURS_24)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(
            startMinutes = item.startMinutes,
            timeFormat = TimeFormat.HOURS_24,
            timeLabel = railLabel,
            endLabel = endLabel,
            blockMinutes = item.minutes,
            blockHeight = blockHeight,
            lineColor = Slate200,
            isMuted = true,
            showIntermediateTicks = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = blockHeight),
            shape = RoundedCornerShape(14.dp),
            color = Slate100
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
                    tint = Slate500,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = gapLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    Text(
                        text = "$railLabel — $endLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
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
    Column(
        modifier = Modifier.width(104.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (showIntermediateTicks && timeLabel.isNotBlank()) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isMuted) Slate500 else Slate700,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Spacer(modifier = Modifier.height(6.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight),
            verticalAlignment = Alignment.Top
        ) {
            // Labels column to the left of the line
            if (blockMinutes >= 60 && showIntermediateTicks) {
                val tickCount = blockMinutes / 60
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    repeat(tickCount + 1) { idx ->
                        Text(
                            text = formatMinutesToTime(
                                startMinutes + idx * 60,
                                timeFormat
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMuted) Slate500 else Slate700,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else if (!showIntermediateTicks && endLabel != null) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMuted) Slate500 else Slate700
                    )
                    Text(
                        text = endLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMuted) Slate500 else Slate700
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
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
                    .background(if (isMuted) Slate300 else lineColor)
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
        border = BorderStroke(1.dp, Slate200)
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
                            .background(
                                color = when (place.category) {
                                    PlaceCategory.RESTAURANT, PlaceCategory.CAFE -> ExpenseFood.copy(alpha = 0.15f)
                                    PlaceCategory.MUSEUM, PlaceCategory.ATTRACTION -> CoralSubtle
                                    PlaceCategory.PARK, PlaceCategory.NATURE, PlaceCategory.BEACH -> TealSubtle
                                    PlaceCategory.SHOPPING -> TealSecondary.copy(alpha = 0.15f)
                                    PlaceCategory.HOLIDAY -> Color(0xFFF97316).copy(alpha = 0.18f)
                                    else -> Slate100
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = place.iconEmoji ?: place.category.emoji,
                            style = MaterialTheme.typography.titleMedium
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
                            color = Slate600,
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
                            tint = Slate500,
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
                            tint = Slate400,
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
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val duration = durationMinutes ?: place.estimatedDuration
                    duration?.let {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDurationLabel(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate600
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

// MAP TAB

@Composable
fun MapTab(
    trip: Trip,
    places: List<Place>,
    participants: List<Participant>
) {
    // Placeholder for Google Maps integration
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate100),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🗺️",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Карта",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Здесь будет карта с маршрутом\nи позициями участников",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate600
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick stats
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip(
                    icon = Icons.Rounded.Place,
                    text = "${places.size} мест"
                )
                if (participants.isNotEmpty()) {
                    InfoChip(
                        icon = Icons.Rounded.Group,
                        text = "${participants.size} участников"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HeatmapPreview(places = places)
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
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CoralPrimary,
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
            color = Slate600
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (categories.isEmpty()) {
            Text(
                text = "Нет мест с рейтингами для расчёта",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )
            return@TrilooCard
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                TrilooChip(
                    text = category.displayName,
                    emoji = category.emoji,
                    color = if (isSelected) CoralSubtle else Slate100,
                    textColor = if (isSelected) CoralPrimary else Slate600,
                    onClick = { selectedCategory = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (heatmapCells.isEmpty()) {
            Text(
                text = "Недостаточно отзывов для коэффициентов по этой категории",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
        } else {
            val topCell = heatmapCells.first()
            Text(
                text = "Ячеек: ${heatmapCells.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Сильная зона: ${formatHeatmapScore(topCell.score)} • " +
                    "${topCell.placeCount} мест • " +
                    String.format(Locale.US, "%.1f", topCell.averageRating),
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
        }
    }
}

private fun formatHeatmapScore(score: Float): String {
    return "${(score * 100).roundToInt()}%"
}

// EXPENSES TAB

@Composable
fun ExpensesTab(
    expenses: List<Expense>,
    totalAmount: Double,
    currency: String,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
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
            // Total summary card
            item {
                ExpenseSummaryCard(
                    totalAmount = totalAmount,
                    currency = currency,
                    expenseCount = expenses.size
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Expense list
            items(expenses, key = { it.id }) { expense ->
                ExpenseItem(
                    expense = expense,
                    baseCurrency = currency,
                    onClick = { onExpenseClick(expense.id) },
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
        color = TealSubtle
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
                    color = TealDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatCurrency(totalAmount, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealDark
                )
            }
            
            Surface(
                shape = CircleShape,
                color = TealSecondary.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$expenseCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                    Text(
                        text = "записей",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealDark
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
            // Category icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(expense.category.colorHex).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.emoji,
                    style = MaterialTheme.typography.titleMedium
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
                        color = Slate600
                    )
                    Text(
                        text = " • ",
                        color = Slate400
                    )
                    Text(
                        text = expense.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
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
                        color = Slate500
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Delete button
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Удалить",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    // Delete confirmation dialog
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
    val symbol = when (currency) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "TRY" -> "₺"
        "THB" -> "฿"
        "AED" -> "د.إ"
        else -> currency
    }
    return "${String.format("%,.0f", amount)} $symbol"
}
