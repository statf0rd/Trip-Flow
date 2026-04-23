package com.triloo.ui.tripdetails

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    
    var showSuggestions by remember { mutableStateOf(false) }
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
                            showSuggestions = focusState.isFocused && suggestions.isNotEmpty()
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
                    shape = RoundedCornerShape(14.dp)
                )
                
                // Выпадающий список подсказок.
                AnimatedVisibility(
                    visible = showSuggestions && suggestions.isNotEmpty(),
                    enter = TrilooMotion.enterExpand(),
                    exit = TrilooMotion.exitShrink()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            suggestions.forEach { suggestion ->
                                SuggestionItem(
                                    suggestion = suggestion,
                                    onClick = {
                                        viewModel.selectSuggestion(suggestion)
                                        showSuggestions = false
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
                    shape = RoundedCornerShape(12.dp),
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

            // Кнопка «Указать на карте» и встроенный пикер.
            if (mapEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMapPicker = !showMapPicker },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (showMapPicker) Icons.Rounded.ExpandLess else Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (showMapPicker) "Скрыть карту" else "Указать на карте",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showMapPicker,
                    enter = TrilooMotion.enterExpand(),
                    exit = TrilooMotion.exitShrink()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .nestedScroll(object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource) = available
                            }),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box {
                            com.triloo.feature.map.MapPickerView(
                                modifier = Modifier.fillMaxSize(),
                                initialCenter = com.triloo.feature.map.MapCoordinate(
                                    latitude = if (uiState.hasCoordinates) uiState.latitude else 55.751244,
                                    longitude = if (uiState.hasCoordinates) uiState.longitude else 37.618423
                                ),
                                initialZoom = if (uiState.hasCoordinates) 15f else 10f,
                                onLocationPicked = { lat, lng ->
                                    viewModel.updateCoordinates(lat, lng)
                                }
                            )
                            // Перекрестие в центре карты.
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = "Метка",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                            )
                        }
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
                shape = RoundedCornerShape(14.dp)
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
                        placeholder = {
                            Text(
                                text = if (uiState.timeFormat == TimeFormat.HOURS_24) "09:30" else "1:30 PM",
                                color = Slate500
                            )
                        },
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
                        shape = RoundedCornerShape(14.dp)
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
                    shape = RoundedCornerShape(14.dp)
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
                shape = RoundedCornerShape(14.dp)
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
                .clip(RoundedCornerShape(10.dp))
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
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = category == selected
                val catColor = category.color

                Surface(
                    modifier = Modifier.clickable { onSelected(category) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) catColor.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) catColor else Slate600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) catColor else Slate600,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddPlaceScreenPreview() {
    TrilooTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Добавить место",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            CategoryGrid(
                selected = PreviewData.addPlaceState.category,
                onSelected = {}
            )
            SuggestionItem(
                suggestion = PreviewData.suggestions.first(),
                onClick = {}
            )
        }
    }
}
