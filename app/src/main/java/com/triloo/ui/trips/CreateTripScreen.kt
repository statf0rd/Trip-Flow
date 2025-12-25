package com.triloo.ui.trips

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.PreviewData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    onNavigateBack: () -> Unit,
    onTripCreated: (String) -> Unit,
    viewModel: CreateTripViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(uiState.createdTripId) {
        uiState.createdTripId?.let { tripId ->
            onTripCreated(tripId)
        }
    }
    
    CreateTripContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNameChange = viewModel::updateName,
        onDestinationChange = viewModel::updateDestination,
        onStartDateChange = viewModel::updateStartDate,
        onEndDateChange = viewModel::updateEndDate,
        onCurrencyChange = viewModel::updateCurrency,
        onHotelNameChange = viewModel::updateHotelName,
        onBudgetChange = { viewModel.updateBudget(it) },
        onSaveTrip = viewModel::saveTrip,
        focusManager = focusManager
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTripContent(
    uiState: CreateTripUiState,
    onNavigateBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onHotelNameChange: (String) -> Unit,
    onBudgetChange: (Double?) -> Unit,
    onSaveTrip: () -> Unit,
    focusManager: FocusManager = LocalFocusManager.current
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) "Редактировать путешествие" else "Новое путешествие",
                        style = MaterialTheme.typography.titleLarge,
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
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            TrilooTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = "Название поездки",
                placeholder = "Отпуск в Турции",
                leadingIcon = Icons.Rounded.FlightTakeoff,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TrilooTextField(
                value = uiState.destination,
                onValueChange = onDestinationChange,
                label = "Город или страна",
                placeholder = "Стамбул, Турция",
                leadingIcon = Icons.Rounded.Place,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Даты поездки",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Slate700
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatePickerField(
                    label = "Начало",
                    date = uiState.startDate,
                    onDateSelected = onStartDateChange,
                    modifier = Modifier.weight(1f)
                )
                
                DatePickerField(
                    label = "Конец",
                    date = uiState.endDate,
                    onDateSelected = onEndDateChange,
                    minDate = uiState.startDate,
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (uiState.startDate != null && uiState.endDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    uiState.startDate,
                    uiState.endDate
                ).toInt() + 1
                Text(
                    text = "📅 $days ${pluralizeDays(days)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TealSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Базовая валюта",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Slate700
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            CurrencySelector(
                selectedCurrency = uiState.baseCurrency,
                onCurrencySelected = onCurrencyChange
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TrilooTextField(
                value = uiState.hotelName,
                onValueChange = onHotelNameChange,
                label = "Отель (опционально)",
                placeholder = "Название или адрес отеля",
                leadingIcon = Icons.Rounded.Hotel,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            TrilooTextField(
                value = uiState.budget?.toString() ?: "",
                onValueChange = { onBudgetChange(it.toDoubleOrNull()) },
                label = "Бюджет (опционально)",
                placeholder = "100000",
                leadingIcon = Icons.Rounded.AccountBalanceWallet,
                suffix = uiState.baseCurrency,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            TrilooButton(
                text = if (uiState.isEditing) "Сохранить изменения" else "Создать путешествие",
                onClick = onSaveTrip,
                enabled = uiState.isValid,
                isLoading = uiState.isCreating,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Slate500) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = Slate500
            )
        },
        suffix = suffix?.let {
            { Text(it, color = Slate600) }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CoralPrimary,
            unfocusedBorderColor = Slate300,
            focusedLabelColor = CoralPrimary,
            cursorColor = CoralPrimary
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { showPicker = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarToday,
                contentDescription = null,
                tint = if (date != null) CoralPrimary else Slate500,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate600
                )
                Text(
                    text = date?.format(dateFormatter) ?: "Выбрать",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (date != null) MaterialTheme.colorScheme.onSurface else Slate500
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
                    Text("OK", color = CoralPrimary)
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
                    selectedDayContainerColor = CoralPrimary,
                    todayDateBorderColor = CoralPrimary
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
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCurrencySelected(code) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) CoralSubtle else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, CoralPrimary)
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
                        color = if (isSelected) CoralPrimary else Slate600,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
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
            onBudgetChange = {},
            onSaveTrip = {}
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
