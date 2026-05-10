package com.triloo.ui.trips

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.accommodation.AccommodationRecommendation
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.PreviewData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Экран создания и редактирования поездки с базовыми полями и подбором проживания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    onNavigateBack: () -> Unit,
    onTripCreated: (String) -> Unit,
    viewModel: CreateTripViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hotelSuggestions by viewModel.hotelSuggestions.collectAsStateWithLifecycle()
    val destinationSuggestions by viewModel.destinationSuggestions.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(uiState.createdTripId) {
        uiState.createdTripId?.let { tripId ->
            onTripCreated(tripId)
        }
    }
    
    CreateTripContent(
        uiState = uiState,
        hotelSuggestions = hotelSuggestions,
        destinationSuggestions = destinationSuggestions,
        onNavigateBack = onNavigateBack,
        onNameChange = viewModel::updateName,
        onDestinationChange = viewModel::updateDestination,
        onDestinationSuggestionSelected = viewModel::selectDestinationSuggestion,
        onStartDateChange = viewModel::updateStartDate,
        onEndDateChange = viewModel::updateEndDate,
        onCurrencyChange = viewModel::updateCurrency,
        onHotelNameChange = viewModel::updateHotelName,
        onHotelSuggestionSelected = viewModel::selectHotelSuggestion,
        onBudgetChange = viewModel::updateBudget,
        onHotelAssist = viewModel::requestHotelRecommendations,
        onHotelRecommendationSelected = viewModel::applyHotelRecommendation,
        onDismissHotelRecommendations = viewModel::dismissHotelRecommendations,
        onDestinationCoordinatesPicked = viewModel::updateDestinationCoordinates,
        onSaveTrip = viewModel::saveTrip,
        onErrorConsumed = viewModel::clearError,
        focusManager = focusManager
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CreateTripContent(
    uiState: CreateTripUiState,
    hotelSuggestions: List<com.triloo.data.places.PlaceSuggestion> = emptyList(),
    destinationSuggestions: List<com.triloo.data.places.PlaceSuggestion> = emptyList(),
    onNavigateBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onDestinationSuggestionSelected: (com.triloo.data.places.PlaceSuggestion) -> Unit = {},
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onHotelNameChange: (String) -> Unit,
    onHotelSuggestionSelected: (com.triloo.data.places.PlaceSuggestion) -> Unit = {},
    onBudgetChange: (String) -> Unit,
    onHotelAssist: () -> Unit,
    onHotelRecommendationSelected: (AccommodationRecommendation) -> Unit,
    onDismissHotelRecommendations: () -> Unit,
    onDestinationCoordinatesPicked: (Double, Double) -> Unit = { _, _ -> },
    onSaveTrip: () -> Unit,
    onErrorConsumed: () -> Unit,
    focusManager: FocusManager = LocalFocusManager.current
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val openWebsite: (String) -> Unit = remember {
        { url: String ->
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    if (url.startsWith("http")) url else "https://$url"
                )
            )
            runCatching { context.startActivity(intent) }
        }
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val mapEnabled = remember { com.triloo.BuildConfig.APP_MAPKIT_VIEW_ENABLED }
    var isBudgetFieldFocused by remember { mutableStateOf(false) }
    var showDestinationMap by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Wizard-стейт: 1 → Куда, 2 → Даты, 3 → Бюджет и жильё. Дизайн V2 разбивает
    // длинную форму на три шага с прогресс-баром и нижней CTA «Далее / Сохранить».
    var currentStep by rememberSaveable { mutableStateOf(1) }
    val totalSteps = 3
    val stepCanAdvance = when (currentStep) {
        1 -> uiState.destination.isNotBlank()
        2 -> uiState.startDate != null && uiState.endDate != null &&
            !uiState.endDate.isBefore(uiState.startDate)
        else -> uiState.isValid
    }
    var accommodationType by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            onErrorConsumed()
        }
    }

    LaunchedEffect(isBudgetFieldFocused, imeBottom) {
        if (isBudgetFieldFocused && imeBottom > 0) {
            bringIntoViewRequester.bringIntoView()
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) {
                            "Редактирование"
                        } else {
                            "Шаг $currentStep / $totalSteps"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep -= 1 else onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Закрыть"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Progress + название текущего шага.
            CreateTripStepper(
                currentStep = currentStep,
                stepTitles = listOf("Куда", "Даты", "Бюджет")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Заголовок и подзаголовок текущего шага — основной фокус V2-дизайна.
            val stepTitle = when (currentStep) {
                1 -> "Куда вы едете?"
                2 -> "Когда поездка?"
                else -> "Бюджет и жильё"
            }
            val stepSubtitle = when (currentStep) {
                1 -> "Выберите город или страну — отель и детали можно настроить дальше."
                2 -> "Поставьте даты заезда и отъезда. Срок поездки посчитается сам."
                else -> "Сколько готовы потратить и где остановитесь — позже можно изменить."
            }
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stepSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (currentStep == 1) {
            Column {
                TrilooTextField(
                    value = uiState.destination,
                    onValueChange = onDestinationChange,
                    label = "Город или страна",
                    placeholder = "Стамбул, Турция",
                    leadingIcon = Icons.Rounded.Place,
                    trailingIcon = {
                        if (uiState.isSearchingDestination) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else if (mapEnabled) {
                            // Inline map-pin: открывает полноэкранный picker
                            IconButton(onClick = { showDestinationMap = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Map,
                                    contentDescription = "Указать на карте",
                                    tint = if (uiState.destinationLatitude != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                AnimatedVisibility(
                    visible = destinationSuggestions.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = TrilooShapes.Sm,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            destinationSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDestinationSuggestionSelected(suggestion)
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (suggestion.address.isNotBlank()) {
                                            Text(
                                                text = suggestion.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (suggestion != destinationSuggestions.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Опциональное название — мельче, ниже destination
            TrilooTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = "Название (опционально)",
                placeholder = "Например: Отпуск в Турции",
                leadingIcon = Icons.Rounded.FlightTakeoff,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            } // конец step 1

            if (currentStep == 2) {
                // Единая range-карточка вместо двух отдельных date-полей.
                DateRangeCard(
                    startDate = uiState.startDate,
                    endDate = uiState.endDate,
                    onRangeSelected = { start, end ->
                        onStartDateChange(start)
                        onEndDateChange(end)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // === Базовая валюта — компактно ===
                Text(
                    text = "Базовая валюта",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                CompactCurrencyRow(
                    selectedCurrency = uiState.baseCurrency,
                    onCurrencySelected = onCurrencyChange
                )
            }

            if (currentStep == 3) {
                BudgetCard(
                    value = uiState.budgetInput,
                    currency = uiState.baseCurrency,
                    onValueChange = onBudgetChange
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Жильё",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column {

            Column {
                TrilooTextField(
                    value = uiState.hotelName,
                    onValueChange = onHotelNameChange,
                    label = "Отель",
                    placeholder = "Название или адрес отеля",
                    leadingIcon = Icons.Rounded.Hotel,
                    trailingIcon = {
                        if (uiState.isSearchingHotel || uiState.isLoadingHotelRecommendations) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = onHotelAssist) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Подобрать жилье",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                AnimatedVisibility(
                    visible = hotelSuggestions.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = TrilooShapes.Sm,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            hotelSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onHotelSuggestionSelected(suggestion)
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (suggestion.address.isNotBlank()) {
                                            Text(
                                                text = suggestion.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    suggestion.rating?.let { rating ->
                                        Text(
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldenAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                if (suggestion != hotelSuggestions.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.hotelAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                SelectedHotelCard(
                    hotelName = uiState.hotelName,
                    hotelAddress = uiState.hotelAddress
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Тип жилья — сетка 2×3 «Отель / Airbnb / Хостел / У друзей / Квартира / Бесплатно».
            AccommodationTypeGrid(
                selected = accommodationType,
                onSelect = { type -> accommodationType = if (accommodationType == type) null else type }
            )
                } // конец Column step 3
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Нижняя CTA: «Далее» на шагах 1–2, «Сохранить» на шаге 3.
            TrilooButton(
                text = when {
                    uiState.isEditing -> "Сохранить изменения"
                    currentStep < totalSteps -> "Далее"
                    else -> "Сохранить"
                },
                onClick = {
                    if (currentStep < totalSteps && !uiState.isEditing) {
                        currentStep += 1
                    } else {
                        onSaveTrip()
                    }
                },
                enabled = stepCanAdvance,
                isLoading = currentStep == totalSteps && uiState.isCreating,
                icon = if (currentStep < totalSteps && !uiState.isEditing) {
                    Icons.AutoMirrored.Rounded.ArrowForward
                } else {
                    Icons.Rounded.Check
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.showHotelRecommendationsSheet) {
        HotelRecommendationsSheet(
            recommendations = uiState.hotelRecommendations,
            onDismiss = onDismissHotelRecommendations,
            onSelect = onHotelRecommendationSelected,
            onOpenWebsite = openWebsite
        )
    }

        if (showDestinationMap) {
            BackHandler(enabled = true) { showDestinationMap = false }
            DestinationMapPicker(
                initialLatitude = uiState.destinationLatitude,
                initialLongitude = uiState.destinationLongitude,
                onLocationPicked = onDestinationCoordinatesPicked,
                onDismiss = { showDestinationMap = false }
            )
        }
    }
}

/**
 * Единая карточка-диапазон дат: открывает Material 3 DateRangePicker одним
 * тапом и показывает выбранный интервал плюс пилюлю с длительностью.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeCard(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val rangeFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    }
    val rangeFormatterFull = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru"))
    }

    val days = if (startDate != null && endDate != null) {
        java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    } else null

    val rangeText = when {
        startDate != null && endDate != null -> {
            val sameYear = startDate.year == endDate.year
            val startStr = if (sameYear) startDate.format(rangeFormatter)
                else startDate.format(rangeFormatterFull)
            val endStr = endDate.format(rangeFormatterFull)
            "$startStr — $endStr"
        }
        else -> "Выберите даты поездки"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = if (startDate != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (startDate != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (days != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = TrilooShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$days ${pluralizeDays(days)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate?.toEpochDay()?.times(86_400_000L),
            initialSelectedEndDateMillis = endDate?.toEpochDay()?.times(86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis
                        if (s != null && e != null) {
                            val startLd = java.time.Instant.ofEpochMilli(s)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            val endLd = java.time.Instant.ofEpochMilli(e)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            onRangeSelected(startLd, endLd)
                        }
                        showPicker = false
                    },
                    enabled = state.selectedStartDateMillis != null &&
                        state.selectedEndDateMillis != null
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DateRangePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                modifier = Modifier.heightIn(max = 560.dp),
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Компактная строка чипов выбора валюты: символ + код, активный — заполненный
 * primary-pill, остальные — прозрачные. Заменяет старые квадратные карточки.
 */
@Composable
private fun CompactCurrencyRow(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    val currencies = listOf(
        "RUB" to "₽",
        "USD" to "$",
        "EUR" to "€",
        "TRY" to "₺",
        "THB" to "฿",
        "AED" to "AED"
    )

    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(currencies) { (code, symbol) ->
            val isSelected = selectedCurrency == code
            Surface(
                modifier = Modifier.clickable { onCurrencySelected(code) },
                shape = TrilooShapes.pill,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrilooTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = trailingIcon,
        suffix = suffix?.let {
            { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = TrilooShapes.Sm,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru")) }
    
    Surface(
        modifier = modifier
            .clip(TrilooShapes.Sm)
            .clickable { showPicker = true },
        shape = TrilooShapes.Sm,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarToday,
                contentDescription = null,
                tint = if (date != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date?.format(dateFormatter) ?: "Выбрать",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (date != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
    
    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toEpochDay()?.times(86400000)
                ?: System.currentTimeMillis()
        )
        
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(
                                java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                            )
                        }
                        showPicker = false
                    }
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun CurrencySelector(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    val currencies = listOf(
        "RUB" to "🇷🇺 Рубль",
        "USD" to "🇺🇸 Доллар",
        "EUR" to "🇪🇺 Евро",
        "TRY" to "🇹🇷 Лира",
        "THB" to "🇹🇭 Бат",
        "AED" to "🇦🇪 Дирхам"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        currencies.take(4).forEach { (code, label) ->
            val isSelected = selectedCurrency == code
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(TrilooShapes.Sm)
                    .clickable { onCurrencySelected(code) },
                shape = TrilooShapes.Sm,
                color = if (isSelected) CoralSubtle else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label.take(2),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotelRecommendationsSheet(
    recommendations: List<AccommodationRecommendation>,
    onDismiss: () -> Unit,
    onSelect: (AccommodationRecommendation) -> Unit,
    onOpenWebsite: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Подбор жилья",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Выберите один из вариантов. Подбор учитывает локацию, бюджет и длительность поездки.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Сегмент бюджета пока оценочный: он рассчитан по типу объекта, звёздности и данным Geoapify, а не по live цене номера.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(recommendations, key = { it.placeId }) { recommendation ->
                    HotelRecommendationCard(
                        recommendation = recommendation,
                        onSelect = { onSelect(recommendation) },
                        onOpenWebsite = onOpenWebsite
                    )
                }
            }
        }
    }
}

@Composable
private fun HotelRecommendationCard(
    recommendation: AccommodationRecommendation,
    onSelect: () -> Unit,
    onOpenWebsite: ((String) -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrilooShapes.Md)
            .clickable(onClick = onSelect),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column {
            HotelCardCover(
                photoUrl = recommendation.photoUrl,
                hotelName = recommendation.name,
                sourceLabel = recommendation.source.toLabel(),
                ratingText = recommendation.rating?.let {
                    String.format(Locale.US, "%.1f", it)
                } ?: recommendation.starLevel?.let { "$it★" }
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = recommendation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recommendation.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    recommendation.rating != null -> {
                        HotelMetaChip(
                            icon = Icons.Rounded.Star,
                            text = String.format(Locale.US, "%.1f", recommendation.rating),
                            tint = GoldenAccent
                        )
                    }
                    recommendation.starLevel != null -> {
                        HotelMetaChip(
                            icon = Icons.Rounded.Star,
                            text = "${recommendation.starLevel}★",
                            tint = GoldenAccent
                        )
                    }
                }
                recommendation.priceLevel?.let { level ->
                    HotelMetaChip(
                        icon = Icons.Rounded.Payments,
                        text = "Оценка: ${level.toBudgetLabel()}",
                        tint = TealSecondary
                    )
                }
            }

            Text(
                text = recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выбрать",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!recommendation.website.isNullOrBlank() && onOpenWebsite != null) {
                    Row(
                        modifier = Modifier.clickable { onOpenWebsite(recommendation.website) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = TealSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "На сайт",
                            style = MaterialTheme.typography.labelLarge,
                            color = TealSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Обложка карточки рекомендации отеля. Если есть photoUrl — показываем
 * AsyncImage; иначе генерируем стабильный градиент по хешу названия и
 * крупно рисуем первую букву отеля без декоративных emoji-стикеров.
 * Поверх — pill-чипы с рейтингом (слева) и источником (справа).
 */
@Composable
private fun HotelCardCover(
    photoUrl: String?,
    hotelName: String,
    sourceLabel: String,
    ratingText: String?
) {
    val gradient = remember(hotelName) { gradientForHotel(hotelName) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (!photoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Дарким верх для контраста pill-ов
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(colors = gradient)
                    )
            )
            Text(
                text = hotelName.firstOrNull()?.uppercase() ?: "H",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }

        // Pill-чипы поверх
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (ratingText != null) {
                Surface(
                    shape = TrilooShapes.pill,
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = GoldenAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = ratingText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(0.dp))
            }
            Surface(
                shape = TrilooShapes.pill,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = sourceLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Стабильный градиент по хешу названия отеля — каждое имя получает свою
 * визуальную «обложку», но всегда одну и ту же при перезапуске.
 */
private fun gradientForHotel(name: String): List<Color> {
    val hash = kotlin.math.abs(name.lowercase().hashCode())
    return when (hash % 5) {
        0 -> listOf(CoralPrimary, CoralLight)
        1 -> listOf(TealSecondary, TealLight)
        2 -> listOf(GoldenAccent, GoldenLight)
        3 -> listOf(CoralPrimary, GoldenAccent)
        else -> listOf(TealSecondary, CoralLight)
    }
}

@Composable
private fun HotelMetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Surface(
        shape = TrilooShapes.pill,
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SelectedHotelCard(
    hotelName: String,
    hotelAddress: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(TrilooShapes.Sm)
                    .background(CoralSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hotel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = hotelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hotelAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateTripScreenPreview() {
    TrilooTheme {
        CreateTripContent(
            uiState = PreviewData.createTripState,
            onNavigateBack = {},
            onNameChange = {},
            onDestinationChange = {},
            onStartDateChange = { _ -> },
            onEndDateChange = { _ -> },
            onCurrencyChange = {},
            onHotelNameChange = {},
            onBudgetChange = { _ -> },
            onHotelAssist = {},
            onHotelRecommendationSelected = {},
            onDismissHotelRecommendations = {},
            onSaveTrip = {},
            onErrorConsumed = {}
        )
    }
}

private fun pluralizeDays(count: Int): String {
    return when {
        count % 100 in 11..19 -> "дней"
        count % 10 == 1 -> "день"
        count % 10 in 2..4 -> "дня"
        else -> "дней"
    }
}

private fun Int.toBudgetLabel(): String {
    return when (this) {
        0, 1 -> "Эконом"
        2 -> "Средний"
        3 -> "Комфорт"
        else -> "Премиум"
    }
}

private fun com.triloo.data.accommodation.RecommendationSource.toLabel(): String {
    return when (this) {
        com.triloo.data.accommodation.RecommendationSource.AI -> "AI"
        com.triloo.data.accommodation.RecommendationSource.HEURISTIC -> "Geoapify"
    }
}

/**
 * Полноэкранный overlay-карта для выбора координат места назначения.
 *
 * Сознательно НЕ Dialog: Yandex MapKit рендерится через SurfaceView, и под
 * Dialog'овым отдельным Window тач-ивенты для SurfaceView могут не доходить
 * до карты. Здесь мы рендерим overlay в том же Window, поверх Scaffold'а
 * формы, что даёт картe полноценные pan/zoom/tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationMapPicker(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onLocationPicked: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Выберите место",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Двигайте карту, чтобы поставить метку в нужной точке",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Готово",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                com.triloo.feature.map.MapPickerView(
                    modifier = Modifier.fillMaxSize(),
                    initialCenter = com.triloo.feature.map.MapCoordinate(
                        latitude = initialLatitude ?: 55.751244,
                        longitude = initialLongitude ?: 37.618423
                    ),
                    initialZoom = if (initialLatitude != null) 12f else 5f,
                    onLocationPicked = onLocationPicked
                )
                Icon(
                    imageVector = Icons.Rounded.Place,
                    contentDescription = "Метка",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

// ──────────────────────── Wizard helpers ────────────────────────

/**
 * Прогресс-бар + три подписи «Куда / Даты / Бюджет». Активная секция
 * подсвечивается coral-цветом, пройденные — primary-сatин-вариантом, грядущие
 * — приглушённым outline. Соответствует V2-дизайну создания поездки.
 */
@Composable
private fun CreateTripStepper(
    currentStep: Int,
    stepTitles: List<String>
) {
    val total = stepTitles.size
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { idx ->
                val isActive = idx + 1 == currentStep
                val isPassed = idx + 1 < currentStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .background(
                            when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stepTitles.forEachIndexed { idx, title ->
                val isActive = idx + 1 == currentStep
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = if (idx == 0) {
                        androidx.compose.ui.text.style.TextAlign.Start
                    } else if (idx == stepTitles.lastIndex) {
                        androidx.compose.ui.text.style.TextAlign.End
                    } else {
                        androidx.compose.ui.text.style.TextAlign.Center
                    }
                )
            }
        }
    }
}

/**
 * Бюджет-карточка V2: крупный «€1 200» сверху, под ним слайдер с тремя
 * якорями-метками «экономно / средне / с размахом». Конвертация в условные
 * единицы (≈ 130k ₽) опущена — для неё нужна актуальная котировка курса,
 * добавим, когда подтянем сюда currency-API.
 */
@Composable
private fun BudgetCard(
    value: String,
    currency: String,
    onValueChange: (String) -> Unit
) {
    val numeric = remember(value) { value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
    // Якоря для слайдера: 0 → 0 (no budget), 0.33 → ~30k, 0.66 → ~150k,
    // 1.0 → ~500k. Пользователь может ввести вручную — тогда слайдер
    // подстраивается под введённое значение.
    val maxBudget = 500_000.0
    val sliderPos = (numeric / maxBudget).toFloat().coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "БЮДЖЕТ НА ПОЕЗДКУ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = currencySymbol(currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                BasicEditableAmount(
                    value = value,
                    onValueChange = onValueChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = sliderPos,
                onValueChange = { newPos ->
                    val newAmount = (newPos * maxBudget).toInt()
                    val rounded = (newAmount / 50) * 50  // округляем до 50
                    onValueChange(if (rounded == 0) "" else rounded.toString())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "экономно",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "средне",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "с размахом",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BasicEditableAmount(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value.ifBlank { "0" },
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            onValueChange(digits)
        },
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true
    )
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "EUR" -> "€"
    "USD" -> "$"
    "GBP" -> "£"
    "JPY", "CNY" -> "¥"
    "RUB" -> "₽"
    "TRY" -> "₺"
    else -> currency
}

/**
 * Сетка 2×3 типов жилья: Отель / Airbnb / Хостел / У друзей / Квартира /
 * Бесплатно. Локальное состояние выбора — выбор пока не сохраняется в Trip
 * (нужно расширение модели), сейчас даёт пользователю ментальный «фильтр»
 * перед поиском конкретного места.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccommodationTypeGrid(
    selected: String?,
    onSelect: (String) -> Unit
) {
    val items = listOf(
        "Отель" to "Booking",
        "Airbnb" to "Квартира",
        "Хостел" to "Бюджет",
        "У друзей" to "Бесплатно",
        "Квартира" to "Долгий срок",
        "Кемпинг" to "На природе"
    )
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2
    ) {
        items.forEach { (title, sub) ->
            val isSelected = selected == title
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(TrilooShapes.Md)
                    .clickable { onSelect(title) },
                shape = TrilooShapes.Md,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                } else null
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
