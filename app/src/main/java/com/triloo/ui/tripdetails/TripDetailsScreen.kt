package com.triloo.ui.tripdetails

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.Trip
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.PreviewData
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.theme.TrilooMotion
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                            participants = uiState.participants,
                            routeDetails = uiState.routeDetails
                        )
                        2 -> ExpensesTab(
                            expenses = uiState.expenses,
                            totalAmount = uiState.totalExpenses,
                            currency = uiState.trip!!.baseCurrency,
                            balances = uiState.balances,
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${trip.destination} • ${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    var rowHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Slate100
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = tween(
                    durationMillis = TrilooMotion.durationMedium,
                    easing = TrilooMotion.easingEmphasized
                ),
                label = "tabIndicatorOffset"
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(rowHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Slate200, RoundedCornerShape(12.dp))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        rowHeight = with(density) { size.height.toDp() }
                    }
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = TrilooMotion.selectSpring,
                        label = "tabIconScale$index"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) CoralPrimary else Slate500,
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationShort,
                            easing = TrilooMotion.easingStandard
                        ),
                        label = "tabIconColor$index"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Slate900 else Slate600,
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationShort,
                            easing = TrilooMotion.easingStandard
                        ),
                        label = "tabTextColor$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .scale(iconScale)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun TripDetailsScreenPreview() {
    val uiState = PreviewData.tripDetailsState
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
                    selectedIndex = 0,
                    onTabSelected = { }
                )

                PlanTab(
                    days = uiState.days,
                    places = uiState.places,
                    onDayClick = {},
                    onPlaceClick = {},
                    onEditPlace = {},
                    onAddPlace = {},
                    onDeletePlace = {}
                )
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector
)
