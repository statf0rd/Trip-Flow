package com.trip.flow.ui.trips

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trip.flow.data.model.Trip
import com.trip.flow.ui.components.*
import com.trip.flow.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

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
    val createTripSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trip Flow",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ваши путешествия",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToGroupTrips) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = "Групповые поездки"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            TripFlowFab(
                onClick = { showCreateTripSheet = true },
                icon = Icons.Rounded.Add,
                contentDescription = "Создать путешествие"
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        if (isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (!uiState.hasTrips) {
            EmptyTripsState(
                modifier = Modifier.padding(paddingValues),
                onCreateTrip = { showCreateTripSheet = true }
            )
        } else {
            TripListContent(
                uiState = uiState,
                onTripClick = onNavigateToTrip,
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
                onJoinGroupTrip = { hideThen(onNavigateToGroupTrips) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TripListContent(
    uiState: TripListUiState,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Current Trip (Hero Card)
        uiState.currentTrip?.let { trip ->
            item {
                SectionHeader(title = "Сейчас в поездке")
                CurrentTripCard(
                    trip = trip,
                    onClick = { onTripClick(trip.id) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Upcoming Trips
        if (uiState.upcomingTrips.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Предстоящие",
                    action = if (uiState.upcomingTrips.size > 3) "Все" else null,
                    onAction = { /* Show all */ }
                )
            }
            
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(uiState.upcomingTrips.take(5)) { index, trip ->
                        UpcomingTripCard(
                            trip = trip,
                            onClick = { onTripClick(trip.id) },
                            animationDelay = index * 50,
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Past Trips
        if (uiState.pastTrips.isNotEmpty()) {
            item {
                SectionHeader(title = "Прошедшие")
            }
            
            items(
                items = uiState.pastTrips.take(10),
                key = { it.id }
            ) { trip ->
                PastTripCard(
                    trip = trip,
                    onClick = { onTripClick(trip.id) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentTripCard(
    trip: Trip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val daysLeft = ChronoUnit.DAYS.between(java.time.LocalDate.now(), trip.endDate).toInt()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick
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
                            text = trip.destination,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Days counter
                    Surface(
                        shape = RoundedCornerShape(16.dp),
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
                
                // Quick stats
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
                        value = "0",
                        label = "мест",
                        modifier = Modifier.weight(1f)
                    )
                    QuickStat(
                        icon = Icons.Rounded.Payments,
                        value = "₽0",
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
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun UpcomingTripCard(
    trip: Trip,
    onClick: () -> Unit,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM", Locale("ru")) 
    }
    val daysUntil = ChronoUnit.DAYS.between(java.time.LocalDate.now(), trip.startDate).toInt()
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 }
    ) {
        TripFlowCard(
            modifier = modifier,
            onClick = onClick
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
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trip.destination,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate600
                    )
                }
                
                TripFlowChip(
                    text = "через $daysUntil д",
                    emoji = "📅"
                )
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
}

@Composable
private fun PastTripCard(
    trip: Trip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("MMM yyyy", Locale("ru")) 
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji placeholder for cover
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate200),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getDestinationEmoji(trip.destination),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trip.destination} • ${trip.endDate.format(dateFormatter)}",
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
private fun EmptyTripsState(
    modifier: Modifier = Modifier,
    onCreateTrip: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated illustration
        Text(
            text = "✈️",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Начните своё путешествие",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Создайте первую поездку и планируйте\nмаршруты, расходы и встречи с друзьями",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        TripFlowButton(
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
    onJoinGroupTrip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Создать путешествие",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Выберите формат: личная или групповая поездка",
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

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onJoinGroupTrip,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = "У меня есть код — присоединиться")
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
    TripFlowCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
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

private fun pluralizeDays(count: Int): String {
    return when {
        count % 100 in 11..19 -> "дней осталось"
        count % 10 == 1 -> "день остался"
        count % 10 in 2..4 -> "дня осталось"
        else -> "дней осталось"
    }
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
        else -> "🌍"
    }
}
