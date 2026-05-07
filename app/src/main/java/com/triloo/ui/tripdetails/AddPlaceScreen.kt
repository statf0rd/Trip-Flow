package com.triloo.ui.tripdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.BuildConfig
import com.triloo.data.model.PlaceCategory
import com.triloo.data.places.PlaceSuggestion
import com.triloo.ui.PreviewData
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.theme.TrilooMotion
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddPlaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
    }
    
    var isNameFocused by remember { mutableStateOf(false) }
    val showSuggestions = isNameFocused && suggestions.isNotEmpty()
    var showMapPicker by remember { mutableStateOf(false) }
    val mapEnabled = remember { BuildConfig.APP_MAPKIT_VIEW_ENABLED }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (uiState.isEditing) "Редактировать место" else "Добавить место",
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            day?.let {
                Text(
                    text = "День ${it.dayNumber} • ${it.date.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            }

            // Поле названия с автодополнением.
            Column {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isNameFocused = focusState.isFocused
                    },
                label = { Text("Название места") },
                placeholder = { Text("Начните вводить...", color = Slate500) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                trailingIcon = {
                    if (uiState.isSearching || uiState.isLoadingDetails) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (uiState.name.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateName("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )

            // Выпадающий список подсказок — поведение как в выборе города на
            // экране создания поездки: реактивно показываем, пока есть совпадения
            // и поле в фокусе; гасим автоматически после выбора.
            AnimatedVisibility(
                visible = showSuggestions,
                enter = TrilooMotion.enterExpand(),
                exit = TrilooMotion.exitShrink()
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
                        suggestions.forEach { suggestion ->
                            SuggestionItem(
                                suggestion = suggestion,
                                onClick = {
                                    viewModel.selectSuggestion(suggestion)
                                    focusManager.clearFocus()
                                }
                            )
                            if (suggestion != suggestions.last()) {
                                HorizontalDivider(color = Slate200)
                            }
                        }
                    }
                }
            }
            }
            
            // Индикатор выбранного места.
            AnimatedVisibility(
                visible = uiState.hasCoordinates,
                enter = TrilooMotion.enterExpand(),
                exit = TrilooMotion.exitShrink()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TrilooShapes.Sm,
                    color = TealSubtle
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TealSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isLoadingDetails) {
                                    "Загружаем детали места..."
                                } else {
                                    "Место выбрано"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TealDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.address.isNotBlank()) {
                                Text(
                                    text = uiState.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealDark.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        uiState.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = GoldenAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format("%.1f", rating),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TealDark
                                )
                            }
                        }
                    }
                }
            }

            // Триггер полноэкранного map-пикера. Сама карта рендерится как
            // overlay поверх формы — см. MapPickerOverlay ниже по файлу: внутри
            // 260dp-плашки Yandex SurfaceView было физически неудобно крутить.
            if (mapEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMapPicker = true },
                    shape = TrilooShapes.Sm,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Указать на карте",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Поле адреса: необязательное, может заполниться из подсказки.
            OutlinedTextField(
                value = uiState.address,
                onValueChange = viewModel::updateAddress,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес (опционально)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )

            // Выбор категории.
            CategoryGrid(
                selected = uiState.category,
                onSelected = viewModel::updateCategory
            )

            // Строка времени и длительности.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = uiState.time,
                        onValueChange = viewModel::updateTime,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Время") },
                        suffix = {
                            Text(
                                text = uiState.timeFormat.label,
                                color = Slate600,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = Slate500
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = TrilooShapes.Sm
                    )

                    uiState.lockedTimeFormat?.let { locked ->
                        Text(
                            text = "Формат времени: ${locked.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.durationValue,
                    onValueChange = viewModel::updateDuration,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = "Длительность",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = {
                        Text(
                            text = if (uiState.durationUnit == DurationUnit.HOURS) "1" else "60",
                            color = Slate500
                        )
                    },
                    suffix = {
                        Text(
                            text = uiState.durationUnit.label,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.toggleDurationUnit() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = Slate500
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (uiState.durationUnit == DurationUnit.HOURS) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    shape = TrilooShapes.Sm
                )
            }

            // Поле заметок.
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::updateNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                label = { Text("Заметки (опционально)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { 
                        focusManager.clearFocus()
                        if (uiState.isValid) viewModel.savePlace() 
                    }
                ),
                minLines = 2,
                maxLines = 4,
                shape = TrilooShapes.Sm
            )

            // Сообщение об ошибке.
            uiState.error?.let { errorText ->
                Text(
                    text = errorText,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Кнопка сохранения.
            TrilooButton(
                text = "Сохранить место",
                onClick = viewModel::savePlace,
                enabled = uiState.isValid,
                isLoading = uiState.isSaving,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showMapPicker) {
        BackHandler(enabled = true) { showMapPicker = false }
        // Контекстные маркеры — уже добавленные точки поездки, чтобы было видно,
        // где относительно них пользователь сейчас ставит новую метку.
        val contextMarkers = remember(uiState.existingPlaces) {
            uiState.existingPlaces.mapIndexed { index, place ->
                com.triloo.feature.map.MapMarker(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    label = (index + 1).toString(),
                    colorArgb = CoralPrimary.toArgb(),
                    title = place.name,
                    scale = 0.95f,
                    zIndex = 1f
                )
            }
        }
        MapPickerOverlay(
            initialLatitude = when {
                uiState.hasCoordinates -> uiState.latitude
                uiState.tripDestinationLatitude != null -> uiState.tripDestinationLatitude!!
                else -> 55.751244
            },
            initialLongitude = when {
                uiState.hasCoordinates -> uiState.longitude
                uiState.tripDestinationLongitude != null -> uiState.tripDestinationLongitude!!
                else -> 37.618423
            },
            initialZoom = when {
                uiState.hasCoordinates -> 15f
                uiState.tripDestinationLatitude != null -> 12f
                else -> 10f
            },
            contextMarkers = contextMarkers,
            onLocationPicked = { lat, lng -> viewModel.updateCoordinates(lat, lng) },
            onDismiss = { showMapPicker = false }
        )
    }
}

/**
 * Полноэкранный overlay с Yandex MapKit — использует тот же приём, что и
 * выбор места при создании поездки: рендерится как Surface поверх Scaffold'а
 * (а не Dialog), чтобы тач-ивенты доходили до SurfaceView карты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapPickerOverlay(
    initialLatitude: Double,
    initialLongitude: Double,
    initialZoom: Float,
    contextMarkers: List<com.triloo.feature.map.MapMarker> = emptyList(),
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
                        latitude = initialLatitude,
                        longitude = initialLongitude
                    ),
                    initialZoom = initialZoom,
                    markers = contextMarkers,
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

@Composable
private fun SuggestionItem(
    suggestion: PlaceSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(TrilooShapes.Sm)
                .background(suggestion.category.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = suggestion.category.icon,
                contentDescription = suggestion.category.displayName,
                tint = suggestion.category.color,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = suggestion.address,
                style = MaterialTheme.typography.bodySmall,
                color = Slate600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        suggestion.rating?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = GoldenAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate700
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    selected: PlaceCategory,
    onSelected: (PlaceCategory) -> Unit
) {
    val categories = remember { PlaceCategory.entries }

    Column {
        Text(
            text = "Категория",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate700
        )
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { category ->
                CategoryChip(
                    category = category,
                    isSelected = category == selected,
                    onClick = { onSelected(category) }
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: PlaceCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val catColor = category.color
    val unselectedSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Surface(
        modifier = Modifier
            .width(72.dp)
            .clip(TrilooShapes.Sm)
            .clickable(onClick = onClick),
        shape = TrilooShapes.Sm,
        color = if (isSelected) catColor.copy(alpha = 0.16f) else unselectedSurface,
        border = if (isSelected) {
            BorderStroke(1.5.dp, catColor.copy(alpha = 0.55f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) catColor.copy(alpha = 0.22f)
                        else catColor.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Slate900 else Slate700,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AddPlaceScreenPreview() {
    TrilooTheme {
        var selectedCategory by remember { mutableStateOf(PreviewData.addPlaceState.category) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "День 1 • 11 августа",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate600
            )

            // Поле названия (статичный mock — без ViewModel).
            OutlinedTextField(
                value = PreviewData.addPlaceState.name,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название места") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                singleLine = true,
                shape = TrilooShapes.Sm
            )

            // Превью выпадающих подсказок.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = TrilooShapes.Sm,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    PreviewData.suggestions.forEachIndexed { index, suggestion ->
                        SuggestionItem(suggestion = suggestion, onClick = {})
                        if (index < PreviewData.suggestions.lastIndex) {
                            HorizontalDivider(color = Slate200)
                        }
                    }
                }
            }

            // Карточка-кнопка «Указать на карте».
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = TrilooShapes.Sm,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Указать на карте",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Категории — основной фокус превью.
            CategoryGrid(
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )

            // Время + длительность (статика).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = PreviewData.addPlaceState.time,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text("Время") },
                    suffix = { Text(text = "24", color = Slate600) },
                    leadingIcon = {
                        Icon(Icons.Rounded.AccessTime, null, tint = Slate500)
                    },
                    singleLine = true,
                    shape = TrilooShapes.Sm
                )
                OutlinedTextField(
                    value = PreviewData.addPlaceState.durationValue,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text("Длительность") },
                    suffix = {
                        Text(
                            text = "час",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = Slate500) },
                    singleLine = true,
                    shape = TrilooShapes.Sm
                )
            }

            TrilooButton(
                text = "Сохранить место",
                onClick = {},
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
