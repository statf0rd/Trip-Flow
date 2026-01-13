package com.triloo.ui.tripdetails

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.PlaceCategory
import com.triloo.data.places.PlaceSuggestion
import com.triloo.ui.PreviewData
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.theme.TrilooMotion
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

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

            // Name field with autocomplete
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
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CoralPrimary
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
                
                // Suggestions dropdown
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
            
            // Selected place indicator
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
                                text = "Место выбрано",
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

            // Address field (optional, pre-filled from suggestion)
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

            // Category selector
            CategoryCarousel(
                selected = uiState.category,
                onSelected = viewModel::updateCategory
            )

            // Time and duration row
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
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleTimeFormat() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                            text = "Формат зафиксирован: ${locked.label}",
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

            // Notes field
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

            // Error message
            uiState.error?.let { errorText ->
                Text(
                    text = errorText,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Save button
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
                .background(getCategoryColor(suggestion.category).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = suggestion.category.emoji,
                style = MaterialTheme.typography.titleMedium
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCarousel(
    selected: PlaceCategory,
    onSelected: (PlaceCategory) -> Unit
) {
    val categories = remember { PlaceCategory.entries }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentSelected by rememberUpdatedState(selected)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - center)
                }?.index
            }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index ->
                val category = categories.getOrNull(index) ?: return@collect
                if (category != currentSelected) {
                    onSelected(category)
                }
            }
    }

    Column {
        Text(
            text = "Категория",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate700
        )
        Spacer(modifier = Modifier.size(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(categories) { index, category ->
                    val isSelected = category == selected
                    val layoutInfo = listState.layoutInfo
                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val distance = itemInfo?.let {
                        abs((it.offset + it.size / 2) - center)
                    } ?: 0
                    val maxDistance = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                    val normalized = if (maxDistance > 0) {
                        (distance / maxDistance).coerceIn(0f, 1f)
                    } else 0f
                    val alpha = 1f - (normalized * 0.55f)
                    val scale = 1f - (normalized * 0.08f)
                    val backgroundColor = if (isSelected) {
                        getCategoryColor(category).copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                    val textColor = if (isSelected) getCategoryColor(category) else Slate600

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable {
                                onSelected(category)
                                scope.launch { listState.animateScrollToItem(index) }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = backgroundColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = category.emoji,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
    }
}

private fun getCategoryColor(category: PlaceCategory): Color {
    return when (category) {
        PlaceCategory.RESTAURANT, PlaceCategory.CAFE -> ExpenseFood
        PlaceCategory.BAR, PlaceCategory.NIGHTLIFE -> Color(0xFFEC4899)
        PlaceCategory.MUSEUM, PlaceCategory.ATTRACTION -> CoralPrimary
        PlaceCategory.PARK, PlaceCategory.NATURE, PlaceCategory.BEACH -> TealSecondary
        PlaceCategory.SHOPPING -> Color(0xFF14B8A6)
        PlaceCategory.ENTERTAINMENT -> Color(0xFF8B5CF6)
        PlaceCategory.HOLIDAY -> Color(0xFFF97316)
        PlaceCategory.TRANSPORT -> Color(0xFF6366F1)
        PlaceCategory.VIEWPOINT -> GoldenAccent
        else -> Slate600
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
            CategoryCarousel(
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
