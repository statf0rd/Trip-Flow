package com.triloo.ui.tripdetails

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.triloo.data.model.*
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale

// PLAN TAB

@Composable
fun PlanTab(
    days: List<TripDay>,
    places: List<Place>,
    onDayClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(days, key = { it.id }) { day ->
                DayCard(
                    day = day,
                    places = places.filter { it.tripDayId == day.id },
                    onDayClick = { onDayClick(day.id) },
                    onPlaceClick = onPlaceClick,
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
    places: List<Place>,
    onDayClick: () -> Unit,
    onPlaceClick: (String) -> Unit,
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
                
                if (places.isEmpty()) {
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
                    places.forEachIndexed { index, place ->
                        PlaceItem(
                            place = place,
                            isLast = index == places.lastIndex,
                            onClick = { onPlaceClick(place.id) },
                            onDelete = { onDeletePlace(place.id) }
                        )
                    }
                    
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
private fun PlaceItem(
    place: Place,
    isLast: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(Slate200)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                place.scheduledTime?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelMedium,
                        color = Slate500
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = place.address ?: place.category.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Slate600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Rating & duration
            if (place.rating != null || place.estimatedDuration != null) {
                Spacer(modifier = Modifier.height(4.dp))
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
                    
                    place.estimatedDuration?.let { duration ->
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${duration} мин",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate600
                        )
                    }
                }
            }
        }
        
        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Visited indicator
            if (place.isVisited) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Посещено",
                    tint = TealSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
