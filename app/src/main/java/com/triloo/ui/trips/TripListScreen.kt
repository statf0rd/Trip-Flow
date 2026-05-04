package com.triloo.ui.trips

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ваши путешествия",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
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

@Composable
private fun TripListContent(
    uiState: TripListUiState,
    onTripClick: (String) -> Unit,
    onTripLongClick: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Текущая поездка в главной акцентной карточке.
        uiState.currentTrip?.let { trip ->
            item {
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
        
        // Ближайшие поездки.
        if (uiState.upcomingTrips.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Предстоящие",
                    action = if (uiState.upcomingTrips.size > 3) "Все" else null,
                    onAction = { /* Показать все. */ }
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
                            onLongClick = { onTripLongClick(trip) },
                            animationDelay = index * 50,
                            modifier = Modifier.width(UpcomingCardWidth)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Прошедшие поездки.
        if (uiState.pastTrips.isNotEmpty()) {
            item {
                SectionHeader(title = "Прошедшие")
            }
            
            itemsIndexed(
                items = uiState.pastTrips.take(10),
                key = { _, trip -> trip.id }
            ) { index, trip ->
                PastTripCard(
                    trip = trip,
                    onClick = { onTripClick(trip.id) },
                    onLongClick = { onTripLongClick(trip) },
                    animationDelay = index * 50,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
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
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
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
                    
                    // Счётчик оставшихся дней.
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
        shape = RoundedCornerShape(12.dp),
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
    animationDelay: Int = 0,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")) 
    }
    val daysUntil = ChronoUnit.DAYS.between(java.time.LocalDate.now(), trip.startDate).toInt()
    
    val visibilityState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = TrilooMotion.enterHorizontalStagger(delayMillis = animationDelay),
        exit = TrilooMotion.exitStagger()
    ) {
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
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trip.destination,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate600
                    )
                }
                
                TrilooChip(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PastTripCard(
    trip: Trip,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("ru")) 
    }

    val visibilityState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = TrilooMotion.enterVerticalStagger(delayMillis = animationDelay),
        exit = TrilooMotion.exitStagger()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji-заглушка для обложки.
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
    onJoinGroupTrip: () -> Unit
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
                    .clip(RoundedCornerShape(24.dp))
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
                            .clip(RoundedCornerShape(16.dp))
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
                    .clip(RoundedCornerShape(16.dp))
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
        else -> "🌍"
    }
}
