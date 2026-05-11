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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
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
    onPlaceClick: (String) -> Unit,
    onEditPlace: (String) -> Unit = {},
    onAddPlace: (String) -> Unit,
    onDeletePlace: (String) -> Unit = {},
    onOptimizeRoute: () -> Unit = {},
    isReadOnly: Boolean = false
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
        // В read-only «Оптимизировать маршрут» прячется — это write-action.
        val showOptimizeBanner = remember(places, isReadOnly) {
            !isReadOnly && places.size >= 3
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showOptimizeBanner) {
                item(key = "optimize-banner") {
                    OptimizeRouteBanner(onOptimizeRoute = onOptimizeRoute)
                }
            }
            items(sortedDays, key = { it.id }) { day ->
                val schedule = schedules[day.id] ?: DaySchedule()
                DayCard(
                    day = day,
                    schedule = schedule,
                    onPlaceClick = onPlaceClick,
                    onEditPlace = onEditPlace,
                    onAddPlace = { onAddPlace(day.id) },
                    onDeletePlace = onDeletePlace,
                    isReadOnly = isReadOnly
                )
            }
        }
    }
}

@Composable
private fun OptimizeRouteBanner(
    onOptimizeRoute: () -> Unit
) {
    Surface(
        shape = TrilooShapes.Md,
        color = TealSubtle,
        border = BorderStroke(1.dp, TealSecondary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TealSecondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = TealDark,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Оптимизировать маршрут",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Triloo соберёт места по дням так, чтобы вы меньше ездили и больше успевали.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onOptimizeRoute,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = TealSecondary,
                    contentColor = Color.White
                ),
                shape = TrilooShapes.Sm,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Запустить",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: TripDay,
    schedule: DaySchedule,
    onPlaceClick: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onAddPlace: () -> Unit,
    onDeletePlace: (String) -> Unit,
    isReadOnly: Boolean = false
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))
    }
    var isExpanded by remember { mutableStateOf(true) }
    val totalPlaces = remember(schedule) { schedule.scheduled.size + schedule.unscheduled.size }
    val daySubtitle = remember(day, totalPlaces, isExpanded) {
        val date = day.date.format(dateFormatter)
        // Когда день свёрнут — добавляем счётчик мест в сабтайтл («четверг, 7 мая · 1 место»).
        if (!isExpanded && totalPlaces > 0) {
            "$date · $totalPlaces ${pluralizePlacesShort(totalPlaces)}"
        } else date
    }

    TrilooCard(onClick = { isExpanded = !isExpanded }) {
        // Заголовок дня.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Круглый бейдж с номером дня. Раньше тут был CoralSubtle
                // (coral @ 12%) поверх тёмной карточки + coral-текст — в dark-
                // теме получалась мутная коричневая «пилюля» с почти не-
                // читаемой цифрой того же оттенка (три тонко-разных слоя
                // одного цвета). Сделал solid CoralPrimary + белая цифра —
                // высокий контраст, бейдж читается как акцент, без слоёв.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CoralPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${day.dayNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
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
                        text = daySubtitle,
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
                    if (isReadOnly) {
                        // Поездка завершена — место для дня осталось пустым,
                        // показываем нейтральный текст без CTA.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TrilooShapes.Sm),
                            shape = TrilooShapes.Sm,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "Без событий",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Пустое состояние дня — пилюля выходит за оба
                        // padding'а TrilooCard'а:
                        //   • по горизонтали — через `Modifier.layout`:
                        //     меряем surface с шириной `parentMax + 32dp`,
                        //     ставим на `place(-16dp)` → края совпадают с
                        //     краями карточки. Column'у репортим обычную
                        //     ширину, чтобы layout не сломался.
                        //   • по вертикали — через `offset(y = 16.dp)`:
                        //     визуально опускаем в зону bottom-padding'а
                        //     Column'а, нижний край совпадает с нижним краем
                        //     карточки. Высота карточки при этом не растёт.
                        Surface(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val pad = 16.dp.roundToPx()
                                    val expanded = constraints.maxWidth + pad * 2
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = expanded,
                                            maxWidth = expanded
                                        )
                                    )
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.place(-pad, 0)
                                    }
                                }
                                .offset(y = 16.dp)
                                .clickable(onClick = onAddPlace),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "Добавить место",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    DayTimeline(
                        segments = schedule.scheduled,
                        unscheduled = schedule.unscheduled,
                        onPlaceClick = onPlaceClick,
                        onEditPlace = onEditPlace,
                        onDeletePlace = onDeletePlace,
                        isReadOnly = isReadOnly
                    )

                    if (!isReadOnly) {
                        // Кнопка добавления ещё одного места — совпадает по цвету с
                        // primary-цветом приложения (coral), как в дизайне.
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onAddPlace,
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Добавить",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
    onDeletePlace: (String) -> Unit,
    isReadOnly: Boolean = false
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
                        onDelete = { onDeletePlace(item.place.id) },
                        isReadOnly = isReadOnly
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            unscheduled.forEach { place ->
                TimelineEventCard(
                    place = place,
                    timeRange = null,
                    onClick = { onPlaceClick(place.id) },
                    onEdit = { onEditPlace(place.id) },
                    onDelete = { onDeletePlace(place.id) },
                    isReadOnly = isReadOnly
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
    // Раньше между событиями вставлялись TimelineGap'ы («Окно X · свободно»)
    // — пользователь попросил убрать эту плашку из UI, поэтому возвращаем
    // только события. TimelineGap / TimelineGapRow / превью остаются в коде
    // как dead-code на случай возврата.
    return events
}

@Composable
private fun TimelineEventRow(
    item: PlaceSegment,
    displayFormat: TimeFormat,
    blockHeight: Dp,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit,
    isReadOnly: Boolean = false
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
            modifier = Modifier.heightIn(min = blockHeight),
            isReadOnly = isReadOnly
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
            shape = TrilooShapes.Sm,
            color = Color.Transparent
        ) {
            // Пунктирная рамка вокруг «окна» — отличает свободный слот от
            // плотной карточки места и совпадает с дизайном (V1-мокап).
            val outline = MaterialTheme.colorScheme.outlineVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f),
                                0f
                            )
                        )
                        drawRoundRect(
                            color = outline,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                12.dp.toPx(),
                                12.dp.toPx()
                            ),
                            style = stroke
                        )
                    }
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$railLabel — $endLabel · свободно",
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
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(TrilooShapes.Md)
            .clickable(onClick = onClick),
        shape = TrilooShapes.Md,
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
                            .clip(TrilooShapes.Sm)
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
                            text = buildPlaceSubtitle(place),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isReadOnly) {
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
                    }
                    if (place.isVisited) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Посещено",
                            tint = TealSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (!isReadOnly) {
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
    onEnableLocationSharing: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onFetchUserLocation: ((Double, Double) -> Unit) -> Unit = {}
) {
    val mapController = com.triloo.feature.map.rememberTripMapController()
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
    val fallbackRoutePoints = remember(routeDetails, validPlaces) {
        // Если Yandex Transport вернул реальную полилинию — рисуем её,
        // иначе соединяем валидные точки прямыми (как было раньше).
        val decoded = routeDetails?.decodedPath
        if (!decoded.isNullOrEmpty()) {
            decoded.map { MapCoordinate(it.latitude, it.longitude) }
        } else {
            validPlaces.map { place -> MapCoordinate(place.latitude, place.longitude) }
        }
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
                        colorArgb = MarkerHotel.toArgb(),
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
                        colorArgb = MarkerDestination.toArgb(),
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
                        colorArgb = MarkerParticipant.toArgb(),
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
                        colorArgb = MarkerRecommendation.toArgb(),
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
                },
                controller = mapController
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = TrilooShapes.featureCard,
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
                        text = "Карта временно недоступна",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Места и маршрут уже сохранены — мы покажем их на карте, как только починим интеграцию. Расходы и план остаются доступны.",
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
                shape = TrilooShapes.Md,
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

        // Сверху оставляем только info-чипы — без планировщика, который теперь
        // внизу рядом с переключателем режимов передвижения.
        val hasInfoChips = validPlaces.isNotEmpty() || participants.isNotEmpty()
        if (hasInfoChips) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validPlaces.isNotEmpty()) {
                    InfoChip(
                        icon = Icons.Rounded.Place,
                        text = "${validPlaces.size} ${pluralizePlacesShort(validPlaces.size)}"
                    )
                }
                if (participants.isNotEmpty()) {
                    InfoChip(
                        icon = Icons.Rounded.Group,
                        text = "${participants.size} ${pluralizeParticipantsShort(participants.size)}"
                    )
                }
            }
        }

        // Геошаринг — компактная иконка-пилюля справа сверху. По тапу
        // разворачивается в полную плашку с пояснением и CTA. На карте больше
        // нет постоянной шторки, перекрывающей половину видимой области.
        if (trip.isGroupTrip) {
            var sharingExpanded by rememberSaveable { mutableStateOf(false) }
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
            val sharingTint = when {
                !locationSharingError.isNullOrBlank() -> Error
                locationSharingActive -> TealSecondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .widthIn(max = 320.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = TrilooShapes.pill,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { sharingExpanded = !sharingExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (locationSharingActive) {
                                Icons.Rounded.LocationOn
                            } else {
                                Icons.Rounded.LocationOff
                            },
                            contentDescription = if (sharingExpanded) "Скрыть" else "Геошаринг",
                            tint = sharingTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (locationSharingActive) "Геошаринг" else "Геошаринг выкл",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (sharingExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = sharingExpanded) {
                    bannerText?.let { text ->
                        Surface(
                            shape = TrilooShapes.Md,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
            }
        }

        // Управление картой — zoom +/- и «найти меня». Размещаем по правому
        // краю над BottomCenter-блоком с heatmap'ом, чтобы не закрывать markers.
        var pendingMyLocation by remember { mutableStateOf(false) }
        LaunchedEffect(locationPermissionGranted, pendingMyLocation) {
            if (pendingMyLocation && locationPermissionGranted) {
                onFetchUserLocation { lat, lng -> mapController.moveTo(lat, lng, 15f) }
                pendingMyLocation = false
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Приблизить",
                onClick = mapController::zoomIn
            )
            MapControlButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Отдалить",
                onClick = mapController::zoomOut
            )
            MapControlButton(
                icon = Icons.Rounded.MyLocation,
                contentDescription = "Найти меня",
                onClick = {
                    if (locationPermissionGranted) {
                        onFetchUserLocation { lat, lng -> mapController.moveTo(lat, lng, 15f) }
                    } else {
                        pendingMyLocation = true
                        onRequestLocationPermission()
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Главная панель маршрута — как в Яндекс.Картах: ряд режимов с
            // временем под каждым и подсказка под выбранным режимом. Показываем
            // только если есть смысл считать маршрут (≥2 валидных точек).
            if (validPlaces.size >= 2) {
                RouteModesPanel(
                    selectedMode = selectedTravelMode,
                    suggestedMode = suggestedTravelMode,
                    routeDetails = routeDetails,
                    summary = routePlanningSummary,
                    source = routePlanningSource,
                    onModeSelected = onTravelModeSelected,
                    onApplySuggested = onApplySuggestedTravelMode
                )
            }
            if (recommendations.isNotEmpty()) {
                RecommendationsCard(
                    recommendations = recommendations
                )
            }
            if (categories.isNotEmpty()) {
                Surface(
                    shape = TrilooShapes.Md,
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
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = TrilooShapes.pill,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Сабтайтл места в плане: «Категория · Адрес». Если адреса нет — только категория,
 * если адрес совпадает с именем — только категория. Адрес режем по первой запятой,
 * чтобы длинный «Южная дорога, дом 15, корп 2…» влезал в одну строку.
 */
private fun buildPlaceSubtitle(place: com.triloo.data.model.Place): String {
    val category = place.category.displayName
    val rawAddress = place.address?.trim().orEmpty()
    val shortAddress = rawAddress
        .split(',')
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals(place.name, ignoreCase = true) }
    return if (shortAddress != null) "$category · $shortAddress" else category
}

private fun pluralizePlacesShort(count: Int): String {
    return when {
        count % 100 in 11..19 -> "мест"
        count % 10 == 1 -> "место"
        count % 10 in 2..4 -> "места"
        else -> "мест"
    }
}

private fun pluralizeParticipantsShort(count: Int): String {
    return when {
        count % 100 in 11..19 -> "участников"
        count % 10 == 1 -> "участник"
        count % 10 in 2..4 -> "участника"
        else -> "участников"
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

/**
 * Нижняя панель маршрута в стиле Яндекс.Карт: горизонтальный ряд иконок
 * режимов с подписью времени, подсказка планировщика под выбранным режимом и
 * км/мин в одну строку. Заменяет старую плашку «Подсказка маршрута» сверху.
 */
@Composable
private fun RouteModesPanel(
    selectedMode: TravelMode,
    suggestedMode: TravelMode?,
    routeDetails: RouteDetails?,
    summary: String?,
    source: RoutePlanSource?,
    onModeSelected: (TravelMode) -> Unit,
    onApplySuggested: () -> Unit
) {
    Surface(
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Чипы режимов: иконка + время для текущего маршрута. Подсветка
            // только у выбранного, остальные приглушены.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TravelMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val durationText = if (isSelected && routeDetails != null) {
                        formatModeDuration(routeDetails.totalDurationMinutes)
                    } else {
                        null
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onModeSelected(mode) },
                        shape = TrilooShapes.Sm,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = mode.icon,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = durationText ?: mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Метрики выбранного маршрута: км · мин · источник.
            routeDetails?.let { details ->
                val km = details.totalDistanceMeters / 1000.0
                Text(
                    text = buildString {
                        append(String.format(Locale.US, "%.1f", km))
                        append(" км · ")
                        append(formatModeDuration(details.totalDurationMinutes))
                        if (details.isEstimated) {
                            append(" · ")
                            append(details.sourceLabel)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Подсказка планировщика — компактно, с указанием источника AI / эвристики.
            if (!summary.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (source == RoutePlanSource.AI) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.Rounded.AltRoute
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // CTA «Включить рекомендованный режим» — только если он отличается от текущего.
            if (suggestedMode != null && suggestedMode != selectedMode) {
                FilledTonalButton(
                    onClick = onApplySuggested,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = TrilooShapes.pill,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Переключить на «${suggestedMode.displayName}»",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Длительность маршрута в человекочитаемом виде:
 *  - до часа: «42 мин»
 *  - от часа: «1 час 30 мин», «2 часа 5 мин», «5 часов» (склонение по русским правилам).
 * Используется и для чипа режима, и для строки «км · мин» под маршрутом.
 */
private fun formatModeDuration(minutes: Int): String {
    if (minutes <= 0) return "—"
    if (minutes < 60) return "$minutes мин"
    val hours = minutes / 60
    val rem = minutes % 60
    val hoursPart = "$hours ${pluralizeHours(hours)}"
    return if (rem == 0) hoursPart else "$hoursPart $rem мин"
}

private fun pluralizeHours(count: Int): String {
    return when {
        count % 100 in 11..19 -> "часов"
        count % 10 == 1 -> "час"
        count % 10 in 2..4 -> "часа"
        else -> "часов"
    }
}

@Composable
private fun RoutePlanningCard(
    selectedTravelMode: TravelMode,
    suggestedTravelMode: TravelMode?,
    summary: String,
    source: RoutePlanSource?,
    routeDetails: RouteDetails?,
    onApplySuggestedTravelMode: () -> Unit
) {
    Surface(
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Шапка: иконка + короткий лейбл источника + чип «текущий режим».
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (source == RoutePlanSource.AI) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Rounded.AltRoute
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when (source) {
                        RoutePlanSource.AI -> "AI-планировщик"
                        else -> "Подсказка маршрута"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = TrilooShapes.pill,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${selectedTravelMode.icon} ${selectedTravelMode.displayName}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Краткая сводка — две строки максимум.
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Метрики маршрута (если посчитаны) — компактной строкой.
            if (routeDetails != null) {
                val km = routeDetails.totalDistanceMeters / 1000.0
                Text(
                    text = buildString {
                        append(String.format(Locale.US, "%.1f", km))
                        append(" км · ")
                        append(formatModeDuration(routeDetails.totalDurationMinutes))
                        if (routeDetails.isEstimated) {
                            append(" · ")
                            append(routeDetails.sourceLabel)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // CTA — только когда планировщик предлагает режим, отличный от текущего.
            if (suggestedTravelMode != null && suggestedTravelMode != selectedTravelMode) {
                FilledTonalButton(
                    onClick = onApplySuggestedTravelMode,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = TrilooShapes.pill,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Включить «${suggestedTravelMode.displayName}»",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
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
        shape = TrilooShapes.Md,
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
        shape = TrilooShapes.Md,
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
                    shape = TrilooShapes.pill,
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
    trip: Trip,
    expenses: List<Expense>,
    totalAmount: Double,
    currency: String,
    balances: List<Balance>,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
    onToggleExpenseSettled: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteExpense: (String) -> Unit = {},
    isReadOnly: Boolean = false
) {
    if (expenses.isEmpty()) {
        if (isReadOnly) {
            EmptyState(
                emoji = "💰",
                title = "Нет расходов",
                subtitle = "В этой поездке расходы не записывались"
            )
        } else {
            EmptyState(
                emoji = "💰",
                title = "Нет расходов",
                subtitle = "Добавьте первый расход, чтобы отслеживать бюджет поездки",
                actionText = "Добавить расход",
                onAction = onAddExpense
            )
        }
    } else {
        // Группируем траты по дате — для секций «2 АВГ · СЕГОДНЯ» в дизайне.
        val grouped = remember(expenses) {
            expenses
                .sortedByDescending { it.date }
                .groupBy { it.date }
                .toList()
        }
        // Раскладка категорий в проценты — для пончика и легенды.
        val breakdown = remember(expenses) { expenseCategoryBreakdown(expenses) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "donut") {
                ExpensesDonutCard(
                    totalAmount = totalAmount,
                    currency = currency,
                    breakdown = breakdown
                )
            }

            item(key = "kpis") {
                ExpensesKpiRow(
                    trip = trip,
                    totalAmount = totalAmount,
                    currency = currency,
                    expenses = expenses,
                    balances = balances
                )
            }

            grouped.forEach { (date, dayExpenses) ->
                item(key = "header-$date") {
                    ExpensesDateHeader(date = date)
                }
                items(dayExpenses, key = { it.id }) { expense ->
                    ExpenseItem(
                        expense = expense,
                        baseCurrency = currency,
                        onClick = { onExpenseClick(expense.id) },
                        onToggleSettled = { settled ->
                            onToggleExpenseSettled(expense.id, settled)
                        },
                        onDelete = { onDeleteExpense(expense.id) },
                        isReadOnly = isReadOnly
                    )
                }
            }
        }
    }
}

// ───────────────────── donut + breakdown ─────────────────────

private data class ExpenseSlice(
    val category: ExpenseCategory,
    val amount: Double,
    val percent: Float
)

/** Локальная плюрализация дней — pluralizeDaysWord живёт в TripListScreen.kt и приватная. */
private fun pluralizeDaysShortLocal(count: Int): String = when {
    count % 100 in 11..19 -> "дней"
    count % 10 == 1 -> "день"
    count % 10 in 2..4 -> "дня"
    else -> "дней"
}

private fun expenseCategoryBreakdown(expenses: List<Expense>): List<ExpenseSlice> {
    if (expenses.isEmpty()) return emptyList()
    val sums = expenses.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountInBaseCurrency } }
    val total = sums.values.sum().takeIf { it > 0 } ?: return emptyList()
    return sums.entries
        .map { (cat, amount) ->
            ExpenseSlice(
                category = cat,
                amount = amount,
                percent = ((amount / total) * 100.0).toFloat()
            )
        }
        .sortedByDescending { it.amount }
}

@Composable
private fun ExpensesDonutCard(
    totalAmount: Double,
    currency: String,
    breakdown: List<ExpenseSlice>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(slices = breakdown, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Всего",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(totalAmount, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                breakdown.take(4).forEach { slice ->
                    DonutLegendRow(slice = slice)
                }
                if (breakdown.size > 4) {
                    val rest = breakdown.drop(4).sumOf { it.percent.toDouble() }.toFloat()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = "Другое",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${rest.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutLegendRow(slice: ExpenseSlice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(slice.category.colorHex))
        )
        Text(
            text = slice.category.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${slice.percent.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DonutChart(
    slices: List<ExpenseSlice>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (slices.isEmpty()) return@Canvas
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx())
        val gap = 2f
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.percent / 100f) * 360f - gap
            if (sweep > 0) {
                drawArc(
                    color = Color(slice.category.colorHex),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke.width / 2, stroke.width / 2),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke.width,
                        size.height - stroke.width
                    ),
                    style = stroke
                )
            }
            startAngle += sweep + gap
        }
    }
}

// ───────────────────── KPI row ─────────────────────

@Composable
private fun ExpensesKpiRow(
    trip: Trip,
    totalAmount: Double,
    currency: String,
    expenses: List<Expense>,
    balances: List<Balance>
) {
    // Бюджет
    val budgetTile = trip.budget?.takeIf { it > 0 }?.let { budget ->
        val percent = ((totalAmount / budget) * 100).toInt().coerceAtLeast(0)
        KpiTileData(
            label = "Бюджет",
            value = formatCurrency(budget, currency),
            sub = "$percent% потрачено",
            accent = TealSecondary
        )
    }
    // В день — общий потраченный делим на дни от старта поездки до сегодня (но не больше длины поездки).
    val perDayTile = run {
        val startDate = trip.startDate
        val endDate = trip.endDate
        val today = java.time.LocalDate.now()
        val effectiveEnd = if (today.isBefore(endDate)) today else endDate
        val daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, effectiveEnd).toInt() + 1
        val days = daysElapsed.coerceAtLeast(1)
        val avg = totalAmount / days
        KpiTileData(
            label = "В день",
            value = formatCurrency(avg, currency),
            sub = "$days ${pluralizeDaysShortLocal(days)} в пути",
            accent = CoralPrimary
        )
    }
    // Долг — первый ненулевой баланс, как в дизайне «Аня → Я».
    val debtTile = balances.firstOrNull { it.amount > 0 }?.let { balance ->
        KpiTileData(
            label = "Долг",
            value = formatCurrency(balance.amount, balance.currency),
            sub = "${balance.fromUserName} → ${balance.toUserName}",
            accent = GoldenAccent
        )
    }

    val tiles = listOfNotNull(budgetTile, perDayTile, debtTile)
    if (tiles.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { tile ->
            KpiTile(tile, modifier = Modifier.weight(1f))
        }
    }
}

private data class KpiTileData(
    val label: String,
    val value: String,
    val sub: String,
    val accent: Color
)

@Composable
private fun KpiTile(data: KpiTileData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = data.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = data.value,
                style = MaterialTheme.typography.titleMedium,
                color = data.accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = data.sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpensesDateHeader(date: java.time.LocalDate) {
    val today = java.time.LocalDate.now()
    val rel = when {
        date == today -> "Сегодня"
        date == today.minusDays(1) -> "Вчера"
        else -> null
    }
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    }
    Text(
        text = buildString {
            append(date.format(formatter))
            if (rel != null) {
                append(" · ")
                append(rel)
            }
        }.uppercase(),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ExpenseSummaryCard(
    totalAmount: Double,
    currency: String,
    expenseCount: Int
) {
    Surface(
        shape = TrilooShapes.Md,
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
        shape = TrilooShapes.Md,
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
    onDelete: () -> Unit,
    isReadOnly: Boolean = false
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")) 
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
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
                    .clip(TrilooShapes.Sm)
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
                        shape = TrilooShapes.pill,
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
                if (expense.splitType != SplitType.PAYER_ONLY && !isReadOnly) {
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

            if (!isReadOnly) {
                Spacer(modifier = Modifier.width(8.dp))

                // Кнопка удаления — touch target 48dp (a11y), иконка визуально 18dp.
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(48.dp)
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
            trip = TripDetailsPreviewData.trip,
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
