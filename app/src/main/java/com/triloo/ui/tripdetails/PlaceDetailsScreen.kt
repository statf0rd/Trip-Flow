package com.triloo.ui.tripdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.ui.components.*
import com.triloo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {}, // TODO: Edit place
    viewModel: PlaceDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Handle successful deletion
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали места", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Edit button
                    IconButton(onClick = { uiState.place?.let { onNavigateToEdit(it.id) } }) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Редактировать"
                        )
                    }
                    // Delete button
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Удалить",
                            tint = Error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(paddingValues))
            }
            uiState.place == null -> {
                EmptyState(
                    emoji = "🤷",
                    title = "Место не найдено",
                    subtitle = "Возможно, оно было удалено",
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                PlaceDetailsContent(
                    place = uiState.place!!,
                    onMarkVisited = viewModel::toggleVisited,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = Error
                )
            },
            title = {
                Text("Удалить место?")
            },
            text = {
                Text("Это действие нельзя отменить. Место будет удалено из плана поездки.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePlace()
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

@Composable
private fun PlaceDetailsContent(
    place: Place,
    onMarkVisited: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with category icon
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(getCategoryColor(place.category).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = place.iconEmoji ?: place.category.emoji,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                TrilooChip(
                    text = place.category.displayName,
                    color = getCategoryColor(place.category).copy(alpha = 0.15f),
                    textColor = getCategoryColor(place.category)
                )
            }
        }
        
        // Visited toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (place.isVisited) TealSubtle else Slate100,
            onClick = onMarkVisited
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (place.isVisited) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (place.isVisited) TealSecondary else Slate500
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (place.isVisited) "Посещено ✓" else "Отметить как посещённое",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (place.isVisited) TealDark else Slate700
                )
            }
        }
        
        // Info cards
        TrilooCard {
            // Address
            place.address?.let { address ->
                InfoRow(
                    icon = Icons.Rounded.LocationOn,
                    label = "Адрес",
                    value = address
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Time
            place.scheduledTime?.let { time ->
                InfoRow(
                    icon = Icons.Rounded.Schedule,
                    label = "Запланировано",
                    value = formatTimeDisplay(time)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Duration
            place.estimatedDuration?.let { duration ->
                InfoRow(
                    icon = Icons.Rounded.Timer,
                    label = "Длительность",
                    value = formatDurationLabel(duration)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Rating
            place.rating?.let { rating ->
                InfoRow(
                    icon = Icons.Rounded.Star,
                    label = "Рейтинг",
                    value = String.format("%.1f", rating),
                    valueColor = GoldenAccent
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Opening hours
            place.openingHours?.let { hours ->
                InfoRow(
                    icon = Icons.Rounded.AccessTime,
                    label = "Часы работы",
                    value = hours
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Coordinates (collapsed by default)
            InfoRow(
                icon = Icons.Rounded.MyLocation,
                label = "Координаты",
                value = "${String.format("%.5f", place.latitude)}, ${String.format("%.5f", place.longitude)}"
            )
        }
        
        // Notes
        place.notes?.let { notes ->
            TrilooCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Заметки",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // Contact info
        if (place.website != null || place.phoneNumber != null) {
            TrilooCard {
                place.website?.let { website ->
                    InfoRow(
                        icon = Icons.Rounded.Language,
                        label = "Сайт",
                        value = website
                    )
                }
                
                if (place.website != null && place.phoneNumber != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
                
                place.phoneNumber?.let { phone ->
                    InfoRow(
                        icon = Icons.Rounded.Phone,
                        label = "Телефон",
                        value = phone
                    )
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* TODO: Open in Maps */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("На карте")
            }
            
            OutlinedButton(
                onClick = { /* TODO: Build route */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Маршрут")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Slate500,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Slate600
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
        }
    }
}

private fun getCategoryColor(category: PlaceCategory): Color {
    return when (category) {
        PlaceCategory.RESTAURANT, PlaceCategory.CAFE -> ExpenseFood
        PlaceCategory.MUSEUM, PlaceCategory.ATTRACTION -> CoralPrimary
        PlaceCategory.PARK, PlaceCategory.NATURE, PlaceCategory.BEACH -> TealSecondary
        PlaceCategory.SHOPPING -> TealSecondary
        PlaceCategory.NIGHTLIFE, PlaceCategory.BAR -> Color(0xFFEC4899)
        PlaceCategory.ENTERTAINMENT -> Color(0xFF8B5CF6)
        PlaceCategory.HOLIDAY -> Color(0xFFF97316)
        PlaceCategory.TRANSPORT -> Color(0xFF6366F1)
        PlaceCategory.VIEWPOINT -> GoldenAccent
        else -> Slate600
    }
}

