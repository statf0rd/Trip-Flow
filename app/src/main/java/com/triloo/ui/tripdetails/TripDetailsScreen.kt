package com.triloo.ui.tripdetails

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.Trip
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.PreviewData
import com.triloo.ui.theme.TrilooTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TripDetailsScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddPlace: (String, String) -> Unit, // tripId, dayId
    onNavigateToAddExpense: (String) -> Unit,
    onNavigateToInvite: (String) -> Unit,
    onNavigateToRelay: (String) -> Unit,
    onNavigateToEditTrip: (String) -> Unit,
    onNavigateToEditPlace: (String) -> Unit = {},
    onNavigateToPlaceDetails: (String) -> Unit = {}, // placeId
    onNavigateToEditExpense: (String, String) -> Unit = { _, _ -> }, // tripId, expenseId
    viewModel: TripDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val tabs = listOf(
        TabItem("План", Icons.AutoMirrored.Rounded.EventNote),
        TabItem("Карта", Icons.Rounded.Map),
        TabItem("Расходы", Icons.Rounded.Payments)
    )
    
    // Handle trip deletion
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            uiState.trip?.let { trip ->
                TripDetailsTopBar(
                    trip = trip,
                    onNavigateBack = onNavigateBack,
                    onShare = { onNavigateToInvite(trip.id) },
                    onRelay = { onNavigateToRelay(trip.id) },
                    onEdit = { onNavigateToEditTrip(trip.id) },
                    onDelete = { showDeleteDialog = true },
                    onOptimizeRoute = { viewModel.optimizeRoute() }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (uiState.trip == null) {
            EmptyState(
                emoji = "🤷",
                title = "Поездка не найдена",
                subtitle = "Возможно, она была удалена",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Custom Tab Row
                TrilooTabRow(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
                
                // Pager Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> PlanTab(
                            days = uiState.days,
                            places = uiState.places,
                            onDayClick = { /* Expand day */ },
                            onPlaceClick = { placeId -> onNavigateToPlaceDetails(placeId) },
                            onEditPlace = { placeId -> onNavigateToEditPlace(placeId) },
                            onAddPlace = { dayId -> 
                                onNavigateToAddPlace(tripId, dayId) 
                            },
                            onDeletePlace = { placeId -> viewModel.deletePlace(placeId) }
                        )
                        1 -> MapTab(
                            trip = uiState.trip!!,
                            places = uiState.places,
                            participants = uiState.participants
                        )
                        2 -> ExpensesTab(
                            expenses = uiState.expenses,
                            totalAmount = uiState.totalExpenses,
                            currency = uiState.trip!!.baseCurrency,
                            onExpenseClick = { expenseId -> 
                                onNavigateToEditExpense(tripId, expenseId) 
                            },
                            onAddExpense = { onNavigateToAddExpense(tripId) },
                            onDeleteExpense = { expenseId -> viewModel.deleteExpense(expenseId) }
                        )
                    }
                }
            }
        }
    }
    
    // Delete trip confirmation dialog
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
                Text("Удалить поездку?")
            },
            text = {
                Text("Все данные поездки, включая места и расходы, будут удалены безвозвратно.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTrip()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailsTopBar(
    trip: Trip,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onRelay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOptimizeRoute: () -> Unit
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")) 
    }
    
    var showMenu by remember { mutableStateOf(false) }
    
    TopAppBar(
        title = {
            Column {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${trip.destination} • ${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад"
                )
            }
        },
        actions = {
            if (trip.isGroupTrip) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Пригласить"
                    )
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Меню"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null
                            )
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Оптимизировать маршрут") },
                        onClick = {
                            showMenu = false
                            onOptimizeRoute()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = TealSecondary
                            )
                        }
                    )
                    
                    if (trip.isGroupTrip) {
                        DropdownMenuItem(
                            text = { Text("Triloo Relay") },
                            onClick = {
                                showMenu = false
                                onRelay()
                            },
                            leadingIcon = {
                                Icon(
                                imageVector = Icons.Rounded.Sync,
                                    contentDescription = null
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            onClick = {
                                showMenu = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    
                    HorizontalDivider()
                    
                    DropdownMenuItem(
                        text = { 
                            Text(
                                "Удалить поездку",
                                color = Error
                            ) 
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = Error
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun TrilooTabRow(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Slate100
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color.White else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    onClick = { onTabSelected(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) CoralPrimary else Slate500,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Slate900 else Slate600,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun TripDetailsScreenPreview() {
    val uiState = PreviewData.tripDetailsState
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf(
        TabItem("План", Icons.AutoMirrored.Rounded.EventNote),
        TabItem("Карта", Icons.Rounded.Map),
        TabItem("Расходы", Icons.Rounded.Payments)
    )
    TrilooTheme {
        Scaffold(
            topBar = {
                uiState.trip?.let { trip ->
                    TripDetailsTopBar(
                        trip = trip,
                        onNavigateBack = {},
                        onShare = {},
                        onRelay = {},
                        onEdit = {},
                        onDelete = {},
                        onOptimizeRoute = {}
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TrilooTabRow(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { }
                )

                when (pagerState.currentPage) {
                    0 -> PlanTab(
                        days = uiState.days,
                        places = uiState.places,
                        onDayClick = {},
                        onPlaceClick = {},
                        onEditPlace = {},
                        onAddPlace = {},
                        onDeletePlace = {}
                    )
                    1 -> MapTab(
                        trip = uiState.trip!!,
                        places = uiState.places,
                        participants = uiState.participants
                    )
                    else -> ExpensesTab(
                        expenses = uiState.expenses,
                        totalAmount = uiState.totalExpenses,
                        currency = uiState.trip!!.baseCurrency,
                        onExpenseClick = {},
                        onAddExpense = {},
                        onDeleteExpense = {}
                    )
                }
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector
)
