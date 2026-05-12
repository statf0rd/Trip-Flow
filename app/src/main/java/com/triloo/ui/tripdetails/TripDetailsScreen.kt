package com.triloo.ui.tripdetails

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.Trip
import com.triloo.data.route.RoutePlanningMode
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
    onNavigateToEditTrip: (String) -> Unit,
    onNavigateToRelay: (String) -> Unit = {},
    onNavigateToEditPlace: (String) -> Unit = {},
    onNavigateToPlaceDetails: (String) -> Unit = {}, // placeId
    onNavigateToEditExpense: (String, String) -> Unit = { _, _ -> }, // tripId, expenseId
    viewModel: TripDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Поездка в архиве (`endDate < сегодня`) — все write-actions скрываем,
    // оставляем только чтение и удаление всей поездки.
    val isReadOnly = uiState.trip?.isPast == true
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingLocationSharingStart by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted && pendingLocationSharingStart) {
            viewModel.startLocationSharing()
        }
        pendingLocationSharingStart = false
    }
    
    val tabs = listOf(
        TabItem("План", Icons.AutoMirrored.Rounded.EventNote),
        TabItem("Карта", Icons.Rounded.Map),
        TabItem("Расходы", Icons.Rounded.Payments)
    )
    
    // После удаления поездки закрываем экран.
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(pagerState.currentPage, uiState.trip?.isGroupTrip) {
        val isMapTab = pagerState.currentPage == 1 && uiState.trip?.isGroupTrip == true
        viewModel.setMapVisible(isMapTab)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setMapVisible(false)
        }
    }
    
    Scaffold(
        topBar = {
            uiState.trip?.let { trip ->
                TripDetailsTopBar(
                    trip = trip,
                    onNavigateBack = onNavigateBack,
                    onEdit = { onNavigateToEditTrip(trip.id) },
                    onRelay = { onNavigateToRelay(trip.id) },
                    onDelete = { showDeleteDialog = true },
                    isReadOnly = isReadOnly
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
                if (isReadOnly) {
                    ArchivedTripBanner()
                }
                // Кастомная строка табов.
                TrilooTabRow(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
                
                // Содержимое pager-а.
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerState.currentPage != 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> PlanTab(
                            days = uiState.days,
                            places = uiState.places,
                            onPlaceClick = { placeId -> onNavigateToPlaceDetails(placeId) },
                            onEditPlace = { placeId -> onNavigateToEditPlace(placeId) },
                            onAddPlace = { dayId ->
                                onNavigateToAddPlace(tripId, dayId)
                            },
                            onDeletePlace = { placeId -> viewModel.deletePlace(placeId) },
                            onOptimizeRoute = { viewModel.optimizeRoute() },
                            isReadOnly = isReadOnly
                        )
                        1 -> MapTab(
                            trip = uiState.trip!!,
                            places = uiState.places,
                            participants = uiState.participants,
                            routeDetails = uiState.routeDetails,
                            recommendations = uiState.recommendations,
                            destinationMarker = uiState.destinationMarker,
                            selectedTravelMode = uiState.selectedTravelMode,
                            selectedPlanningMode = uiState.selectedPlanningMode,
                            suggestedTravelMode = uiState.suggestedTravelMode,
                            routePlanningSummary = uiState.routePlanningSummary,
                            routePlanningSource = uiState.routePlanningSource,
                            locationPermissionGranted = hasLocationPermission,
                            showLocationSharingPrompt = uiState.trip!!.isGroupTrip && !hasLocationPermission,
                            locationSharingActive = uiState.isLocationSharingActive,
                            locationSharingStatus = uiState.locationSharingStatus,
                            locationSharingError = uiState.locationSharingError,
                            onPlanningModeSelected = viewModel::setPlanningMode,
                            onTravelModeSelected = viewModel::setTravelMode,
                            onApplySuggestedTravelMode = viewModel::applySuggestedTravelMode,
                            onStartLocationSharing = {
                                if (hasLocationPermission) {
                                    viewModel.startLocationSharing()
                                } else {
                                    pendingLocationSharingStart = true
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            onStopLocationSharing = viewModel::stopLocationSharing,
                            onEnableLocationSharing = {
                                pendingLocationSharingStart = true
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                            // Тот же permission-launcher, что и для геошаринга, —
                            // FINE_LOCATION хватает обоим сценариям, и нет смысла
                            // плодить второй ActivityResult-launcher.
                            onRequestLocationPermission = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                            onFetchUserLocation = { onResolved ->
                                viewModel.requestCurrentUserLocation(onResolved)
                            }
                        )
                        2 -> ExpensesTab(
                            trip = uiState.trip!!,
                            expenses = uiState.expenses,
                            totalAmount = uiState.totalExpenses,
                            currency = uiState.trip!!.baseCurrency,
                            balances = uiState.balances,
                            onExpenseClick = { expenseId ->
                                // В архиве клик по записи не должен открывать
                                // экран редактирования — игнорируем.
                                if (!isReadOnly) onNavigateToEditExpense(tripId, expenseId)
                            },
                            onAddExpense = { onNavigateToAddExpense(tripId) },
                            onToggleExpenseSettled = viewModel::toggleExpenseSettled,
                            onDeleteExpense = { expenseId -> viewModel.deleteExpense(expenseId) },
                            isReadOnly = isReadOnly
                        )
                    }
                }
            }
        }
    }
    
    // Диалог подтверждения удаления поездки.
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
    onEdit: () -> Unit,
    onRelay: () -> Unit,
    onDelete: () -> Unit,
    isReadOnly: Boolean = false
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
                val subtitleBase = "${trip.destination} • ${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}"
                val subtitle = if (isReadOnly) "$subtitleBase • Завершена" else subtitleBase
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // В меню: «Редактировать» и «Bluetooth-синхронизация» — обе
            // write-action'а, прячем в read-only. «Удалить» доступна всегда:
            // даже архивную поездку юзер должен мочь убрать с устройства.
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
                    if (!isReadOnly) {
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
                            text = { Text("Bluetooth-синхронизация") },
                            onClick = {
                                showMenu = false
                                onRelay()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Bluetooth,
                                    contentDescription = null
                                )
                            }
                        )

                        HorizontalDivider()
                    }

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

/**
 * Шапка-баннер «Поездка завершена». Показывается на всех табах при
 * `isReadOnly = true`. Использует tertiary-палитру (golden) — намеренно
 * отличается от primary (coral) и secondary (teal), чтобы статус не путали с
 * активной поездкой или чипом дней до старта.
 */
@Composable
private fun ArchivedTripBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = TrilooShapes.Sm,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Поездка завершена",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Журнальная запись — только просмотр.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
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
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "tabIndicatorOffset"
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(rowHeight)
                    .clip(TrilooShapes.Sm)
                    .background(MaterialTheme.colorScheme.surface)
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
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationShort,
                            easing = TrilooMotion.easingStandard
                        ),
                        label = "tabIconColor$index"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            // По дизайну V1 — у выбранной вкладки текст того же coral-цвета,
                            // что и иконка. Раньше был onSurface (белый), не совпадало с макетом.
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                        onEdit = {},
                        onRelay = {},
                        onDelete = {}
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
