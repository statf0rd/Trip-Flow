package com.triloo.ui.trips

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.ui.PreviewData
import com.triloo.data.model.Trip
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.theme.TrilooMotion
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

private val UpcomingCardWidth = 280.dp
private val UpcomingCardMinHeight = 176.dp
private val QuickStatHeight = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onNavigateToTrip: (String) -> Unit,
    onNavigateToCreateTrip: (isGroupTrip: Boolean) -> Unit,
    onNavigateToGroupTrips: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TripListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showCreateTripSheet by rememberSaveable { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    val createTripSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    Scaffold(
        // TopAppBar намеренно отсутствует: hero-блок с приветствием и иконки
        // действий теперь отображаются как первый item внутри LazyColumn,
        // что экономит вертикальное пространство и поднимает контент.
        floatingActionButton = {
            if (uiState.hasTrips) {
                TrilooFab(
                    onClick = { showCreateTripSheet = true },
                    icon = Icons.Rounded.Add,
                    contentDescription = "Создать путешествие"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        if (isLoading) {
            TripListLoadingState(modifier = Modifier.padding(paddingValues))
        } else if (!uiState.hasTrips) {
            EmptyTripsState(
                modifier = Modifier.padding(paddingValues),
                onCreateTrip = { showCreateTripSheet = true }
            )
        } else {
            TripListContent(
                uiState = uiState,
                onTripClick = onNavigateToTrip,
                onTripLongClick = { trip -> tripToDelete = trip },
                onCreateTrip = { showCreateTripSheet = true },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    if (showCreateTripSheet) {
        fun hideThen(action: () -> Unit) {
            scope.launch {
                createTripSheetState.hide()
                showCreateTripSheet = false
                action()
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showCreateTripSheet = false },
            sheetState = createTripSheetState
        ) {
            CreateTripTypeSheet(
                onCreatePersonalTrip = { hideThen { onNavigateToCreateTrip(false) } },
                onCreateGroupTrip = { hideThen { onNavigateToCreateTrip(true) } },
                onJoinGroupTrip = { hideThen(onNavigateToGroupTrips) },
                onOpenSettings = { hideThen(onNavigateToSettings) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = Error
                )
            },
            title = { Text("Удалить путешествие?") },
            text = { Text("Поездка \"${trip.name}\" будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripToDelete = null
                        viewModel.deleteTrip(trip.id)
                    }
                ) {
                    Text("Удалить", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripListContent(
    uiState: TripListUiState,
    onTripClick: (String) -> Unit,
    onTripLongClick: (Trip) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pastByYear = remember(uiState.pastTrips) {
        uiState.pastTrips
            .groupBy { it.endDate.year }
            .toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Hero — только приветствие по времени суток. Иконки действий
        // (Settings, Группы) скрыты с главного экрана и доступны через
        // bottom-sheet, открываемый по FAB.
        item(key = "hero") {
            HomeHero(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Текущая поездка в главной акцентной карточке.
        uiState.currentTrip?.let { trip ->
            item(key = "current-${trip.id}") {
                SectionHeader(title = "Сейчас в поездке")
                CurrentTripCard(
                    trip = trip,
                    placeCount = uiState.currentTripPlaceCount,
                    totalSpent = uiState.currentTripTotalSpent,
                    onClick = { onTripClick(trip.id) },
                    onLongClick = { onTripLongClick(trip) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Ближайшие поездки или CTA «Куда дальше?» — взаимоисключающие блоки.
        if (uiState.upcomingTrips.isNotEmpty()) {
            item(key = "upcoming-header") {
                SectionHeader(title = "Предстоящие")
            }
            item(key = "upcoming-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = uiState.upcomingTrips,
                        key = { trip -> trip.id }
                    ) { trip ->
                        UpcomingTripCard(
                            trip = trip,
                            onClick = { onTripClick(trip.id) },
                            onLongClick = { onTripLongClick(trip) },
                            modifier = Modifier
                                .width(UpcomingCardWidth)
                                .animateItem()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            item(key = "plan-next") {
                PlanNextTripCard(
                    onClick = onCreateTrip,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Прошедшие — журнал, сгруппированный по годам.
        if (pastByYear.isNotEmpty()) {
            item(key = "past-header") {
                SectionHeader(title = "Журнал поездок")
            }
            pastByYear.forEach { (year, trips) ->
                item(key = "year-$year") {
                    YearSeparator(
                        year = year,
                        tripCount = trips.size,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(
                    items = trips,
                    key = { trip -> "journal-${trip.id}" }
                ) { trip ->
                    JournalPastTripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) },
                        onLongClick = { onTripLongClick(trip) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    modifier: Modifier = Modifier
) {
    val greeting = remember { greetingForCurrentTime() }
    Text(
        text = greeting,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        lineHeight = 36.sp
    )
}

@Composable
private fun PlanNextTripCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = TrilooShapes.featureCard,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        CoralPrimary.copy(alpha = 0.18f),
                        GoldenAccent.copy(alpha = 0.18f),
                        TealSecondary.copy(alpha = 0.14f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Куда дальше?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Спланируйте следующую поездку — мы поможем с местами и бюджетом.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun YearSeparator(
    year: Int,
    tripCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = "$tripCount ${pluralizeTripsLabel(tripCount)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalPastTripCard(
    trip: Trip,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("LLLL", Locale.forLanguageTag("ru"))
    }
    val gradient = remember(trip.destination) { gradientForDestination(trip.destination) }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = TrilooShapes.Md
    ) {
        Box(
            modifier = Modifier.background(brush = Brush.linearGradient(gradient))
        ) {
            // Декоративный эмодзи как полупрозрачный «штамп» справа.
            Text(
                text = getDestinationEmoji(trip.destination),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .graphicsLayer { alpha = 0.7f }
            )
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = shortenDestination(trip.destination),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JournalChip(text = "${trip.durationDays} ${pluralizeDaysWord(trip.durationDays)}")
                    JournalChip(text = trip.endDate.format(dateFormatter).replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
private fun JournalChip(text: String) {
    Surface(
        shape = TrilooShapes.pill,
        color = Color.White.copy(alpha = 0.22f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CurrentTripCard(
    trip: Trip,
    placeCount: Int,
    totalSpent: Double,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val daysLeft = ChronoUnit.DAYS.between(java.time.LocalDate.now(), trip.endDate).toInt()
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = TrilooShapes.Lg,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(CoralPrimary, CoralLight)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatusBadge(
                            text = "В ПОЕЗДКЕ",
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shortenDestination(trip.destination),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Счётчик оставшихся дней.
                    Surface(
                        shape = TrilooShapes.Md,
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$daysLeft",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pluralizeDays(daysLeft),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Быстрая сводка по поездке.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuickStat(
                        icon = Icons.Rounded.CalendarToday,
                        value = "День ${trip.durationDays - daysLeft}",
                        label = "из ${trip.durationDays}",
                        modifier = Modifier.weight(1f)
                    )
                    QuickStat(
                        icon = Icons.Rounded.Place,
                        value = placeCount.toString(),
                        label = pluralizePlaces(placeCount),
                        modifier = Modifier.weight(1f)
                    )
                    QuickStat(
                        icon = Icons.Rounded.Payments,
                        value = formatCurrentTripAmount(totalSpent, trip.baseCurrency),
                        label = "потрачено",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(QuickStatHeight),
        shape = TrilooShapes.Sm,
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UpcomingTripCard(
    trip: Trip,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    }
    val daysUntil = ChronoUnit.DAYS.between(java.time.LocalDate.now(), trip.startDate).toInt()

    TrilooCard(
        modifier = modifier.heightIn(min = UpcomingCardMinHeight),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = shortenDestination(trip.destination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            DaysUntilBadge(days = daysUntil)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = Slate500,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "${trip.durationDays} дн.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )
        }

        if (trip.isGroupTrip) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = null,
                    tint = TealSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Групповая поездка",
                    style = MaterialTheme.typography.bodySmall,
                    color = TealSecondary
                )
            }
        }
    }
}

@Composable
private fun DaysUntilBadge(days: Int) {
    Surface(
        shape = TrilooShapes.Sm,
        color = TealSubtle
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "через",
                style = MaterialTheme.typography.labelSmall,
                color = TealSecondary
            )
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = TealSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralizeDaysWord(days),
                style = MaterialTheme.typography.labelSmall,
                color = TealSecondary
            )
        }
    }
}

@Composable
private fun EmptyTripsState(
    modifier: Modifier = Modifier,
    onCreateTrip: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val floatTransition = rememberInfiniteTransition(label = "emptyTripsFloat")
        val floatOffset by floatTransition.animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = TrilooMotion.durationExtraLong,
                    easing = TrilooMotion.easingStandard
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyTripsOffset"
        )
        val floatScale by floatTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = TrilooMotion.durationExtraLong,
                    easing = TrilooMotion.easingStandard
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyTripsScale"
        )

        Text(
            text = "✈️",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
            ),
            modifier = Modifier.graphicsLayer {
                translationY = floatOffset
                scaleX = floatScale
                scaleY = floatScale
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Начните своё путешествие",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Создайте первую поездку и соберите\nпланы, расходы и встречи в одном месте",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        TrilooButton(
            text = "Создать путешествие",
            onClick = onCreateTrip,
            icon = Icons.Rounded.Add
        )
    }
}

@Composable
private fun CreateTripTypeSheet(
    onCreatePersonalTrip: () -> Unit,
    onCreateGroupTrip: () -> Unit,
    onJoinGroupTrip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Что хотите сделать?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Создайте свою поездку или присоединитесь к чужой по коду",
            style = MaterialTheme.typography.bodySmall,
            color = Slate600
        )

        Spacer(modifier = Modifier.height(16.dp))

        CreateTripTypeItem(
            icon = Icons.Rounded.Person,
            iconTint = Slate700,
            iconBackground = Slate100,
            title = "Личная поездка",
            description = "План и расходы только для вас",
            onClick = onCreatePersonalTrip
        )

        Spacer(modifier = Modifier.height(12.dp))

        CreateTripTypeItem(
            icon = Icons.Rounded.Group,
            iconTint = TealSecondary,
            iconBackground = TealSubtle,
            title = "Групповая поездка",
            description = "Приглашения по коду и общие расходы",
            onClick = onCreateGroupTrip
        )

        Spacer(modifier = Modifier.height(12.dp))

        CreateTripTypeItem(
            icon = Icons.Rounded.QrCodeScanner,
            iconTint = GoldenDark,
            iconBackground = GoldenSubtle,
            title = "Присоединиться по коду",
            description = "Введите код или отсканируйте QR от организатора",
            onClick = onJoinGroupTrip
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Настройки")
        }
    }
}

@Composable
private fun CreateTripTypeItem(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    TrilooCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = TrilooShapes.Sm,
                color = iconBackground
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Slate400
            )
        }
    }
}

@Composable
private fun TripListLoadingState(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        userScrollEnabled = false
    ) {
        // Скелетон текущей поездки.
        item {
            SectionHeader(title = "Сейчас в поездке")
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(TrilooShapes.Lg)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Скелетон ближайших поездок.
        item {
            SectionHeader(title = "Предстоящие")
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false
            ) {
                items(3) {
                    Spacer(
                        modifier = Modifier
                            .width(UpcomingCardWidth)
                            .height(UpcomingCardMinHeight)
                            .clip(TrilooShapes.Md)
                            .shimmerEffect()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Скелетон прошедших поездок.
        item {
            SectionHeader(title = "Прошедшие")
        }
        items(4) {
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(TrilooShapes.Md)
                    .shimmerEffect()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TripListScreenPreview() {
    TrilooTheme {
        TripListContent(
            uiState = PreviewData.tripListState,
            onTripClick = {},
            onTripLongClick = {},
            onCreateTrip = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}


private fun pluralizeDays(count: Int): String {
    return when {
        count % 100 in 11..19 -> "дней осталось"
        count % 10 == 1 -> "день остался"
        count % 10 in 2..4 -> "дня осталось"
        else -> "дней осталось"
    }
}

private fun pluralizePlaces(count: Int): String {
    return when {
        count % 100 in 11..19 -> "мест"
        count % 10 == 1 -> "место"
        count % 10 in 2..4 -> "места"
        else -> "мест"
    }
}

/**
 * Компактный формат суммы для QuickStat: 1280 → «1.3K», 14500 → «14.5K».
 * Символ валюты подбирается из распространённых ISO-кодов с фолбэком на код.
 */
private fun formatCurrentTripAmount(amount: Double, currency: String): String {
    val symbol = when (currency.uppercase(Locale.US)) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "TRY" -> "₺"
        "THB" -> "฿"
        else -> currency
    }
    val formatted = when {
        amount >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000)
        amount >= 10_000 -> String.format(Locale.US, "%.1fK", amount / 1_000)
        amount >= 1_000 -> String.format(Locale.US, "%.1fK", amount / 1_000)
        amount > 0 -> String.format(Locale.US, "%.0f", amount)
        else -> "0"
    }
    return "$symbol$formatted"
}

private fun getDestinationEmoji(destination: String): String {
    val lower = destination.lowercase()
    return when {
        lower.contains("москва") || lower.contains("moscow") -> "🏛️"
        lower.contains("питер") || lower.contains("петербург") -> "⛪"
        lower.contains("сочи") || lower.contains("sochi") -> "🏖️"
        lower.contains("лондон") || lower.contains("london") -> "🎡"
        lower.contains("париж") || lower.contains("paris") -> "🗼"
        lower.contains("рим") || lower.contains("rome") -> "🏛️"
        lower.contains("токио") || lower.contains("tokyo") -> "🗾"
        lower.contains("нью-йорк") || lower.contains("new york") -> "🗽"
        lower.contains("дубай") || lower.contains("dubai") -> "🏙️"
        lower.contains("бали") || lower.contains("bali") -> "🌴"
        lower.contains("турция") || lower.contains("turkey") -> "🏝️"
        lower.contains("грузия") || lower.contains("тбилиси") -> "🍷"
        lower.contains("эльбрус") || lower.contains("азау") || lower.contains("кавказ") -> "🏔️"
        else -> "🌍"
    }
}

private fun greetingForCurrentTime(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

private fun pluralizeTripsLabel(count: Int): String {
    return when {
        count % 100 in 11..19 -> "поездок"
        count % 10 == 1 -> "поездка"
        count % 10 in 2..4 -> "поездки"
        else -> "поездок"
    }
}

private fun pluralizeDestinations(count: Int): String {
    return when {
        count % 100 in 11..19 -> "направлений"
        count % 10 == 1 -> "направление"
        count % 10 in 2..4 -> "направления"
        else -> "направлений"
    }
}

private fun pluralizeDaysWord(count: Int): String {
    return when {
        count % 100 in 11..19 -> "дней"
        count % 10 == 1 -> "день"
        count % 10 in 2..4 -> "дня"
        else -> "дней"
    }
}

/**
 * Берём только последний сегмент адреса вида «Регион, район, посёлок, точка»,
 * чтобы карточка не разваливалась на 4–5 строк, когда направление пришло из
 * Yandex/Google Places. Для коротких пользовательских строк ничего не меняем.
 */
private fun shortenDestination(destination: String): String {
    if (destination.isBlank()) return destination
    val segments = destination.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return segments.lastOrNull() ?: destination
}

/**
 * Стабильно подбирает градиентную пару цветов под направление поездки.
 * Один и тот же destination всегда получает одинаковый градиент, чтобы
 * пользователь визуально привязывал страну к цвету в журнале.
 */
private fun gradientForDestination(destination: String): List<Color> {
    val hash = kotlin.math.abs(destination.lowercase().hashCode())
    return when (hash % 5) {
        0 -> listOf(CoralPrimary, CoralLight)
        1 -> listOf(TealSecondary, TealLight)
        2 -> listOf(GoldenAccent, GoldenLight)
        3 -> listOf(CoralPrimary, GoldenAccent)
        else -> listOf(TealSecondary, CoralLight)
    }
}
