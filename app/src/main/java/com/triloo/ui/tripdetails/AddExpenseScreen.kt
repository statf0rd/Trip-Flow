package com.triloo.ui.tripdetails

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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.ui.PreviewData
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    AddExpenseContent(
        uiState = uiState,
        trip = trip,
        participants = participants,
        onNavigateBack = onNavigateBack,
        onAmountChange = viewModel::updateAmount,
        onCurrencyChange = viewModel::updateCurrency,
        onDescriptionChange = viewModel::updateDescription,
        onCategoryChange = viewModel::updateCategory,
        onPayerChange = viewModel::updatePayer,
        onDateChange = viewModel::updateDate,
        onTimeChange = viewModel::updateTime,
        onNotesChange = viewModel::updateNotes,
        onSplitTypeChange = viewModel::updateSplitType,
        onSplitAmountChange = viewModel::updateSplitAmount,
        onSave = viewModel::saveExpense,
        focusManager = focusManager
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseContent(
    uiState: AddExpenseUiState,
    trip: Trip?,
    participants: List<Participant>,
    onNavigateBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onPayerChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSplitTypeChange: (SplitType) -> Unit,
    onSplitAmountChange: (String, String) -> Unit,
    onSave: () -> Unit,
    focusManager: FocusManager = LocalFocusManager.current
) {
    val scrollState = rememberScrollState()
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) "Редактировать расход" else "Добавить расход",
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            trip?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wallet,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Базовая валюта: ${it.baseCurrency}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate700
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Сумма",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("0", color = Slate500) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Payments,
                                contentDescription = null,
                                tint = CoralPrimary
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    buildCurrencyList(trip?.baseCurrency).forEach { currency ->
                        val isSelected = currency == uiState.currency
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrencyChange(currency) },
                            label = { Text(currency) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CoralPrimary
                            )
                        )
                    }
                }

                if (uiState.currency != (trip?.baseCurrency ?: uiState.currency)) {
                    val rateText = if (uiState.isRateLoading) {
                        "Обновление курса..."
                    } else if (uiState.rateError != null) {
                        uiState.rateError
                    } else {
                        "1 ${uiState.currency} ≈ ${String.format(Locale.US, "%.4f", uiState.exchangeRate)} ${trip?.baseCurrency}"
                    }
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.rateError != null) Error else Slate500
                    )
                }
            }

            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание") },
                placeholder = { Text("Ужин, билет, такси...", color = Slate500) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        val isSelected = category == uiState.category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategoryChange(category) },
                            label = {
                                Text(
                                    text = "${category.emoji} ${category.displayName}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(category.colorHex).copy(alpha = 0.15f),
                                selectedLabelColor = Color(category.colorHex),
                                selectedLeadingIconColor = Color(category.colorHex)
                            )
                        )
                    }
                }
            }

            ExpenseSplitSection(
                splitType = uiState.splitType,
                participants = participants,
                amount = uiState.amount,
                splitAmounts = uiState.splitAmounts,
                onSplitTypeChange = onSplitTypeChange,
                onSplitAmountChange = onSplitAmountChange
            )

            OutlinedTextField(
                value = uiState.paidBy,
                onValueChange = onPayerChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Плательщик") },
                placeholder = { Text("Ваше имя", color = Slate500) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.date?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true },
                    label = { Text("Дата") },
                    readOnly = true,
                    enabled = false,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = Slate500
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = Slate500,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = uiState.time,
                    onValueChange = onTimeChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Время") },
                    placeholder = { Text("14:30", color = Slate500) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
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
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Заметки (опционально)") },
                placeholder = { Text("Чаевые, детали...", color = Slate500) },
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
                        if (uiState.isValid) onSave()
                    }
                ),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp)
            )

            uiState.error?.let { errorText ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorLight
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TrilooButton(
                text = if (uiState.isEditing) "Сохранить расход" else "Добавить расход",
                onClick = onSave,
                enabled = uiState.isValid,
                isLoading = uiState.isSaving,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
                ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateChange(selectedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("ОК", color = CoralPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseSplitSection(
    splitType: SplitType,
    participants: List<Participant>,
    amount: String,
    splitAmounts: Map<String, String>,
    onSplitTypeChange: (SplitType) -> Unit,
    onSplitAmountChange: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Разделить",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate700
        )

        val splitOptions = listOf(
            SplitType.PAYER_ONLY,
            SplitType.EQUAL,
            SplitType.EXACT
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            splitOptions.forEach { type ->
                FilterChip(
                    selected = splitType == type,
                    onClick = { onSplitTypeChange(type) },
                    label = { Text(type.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoralPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = CoralPrimary
                    )
                )
            }
        }

        if (splitType == SplitType.PAYER_ONLY) {
            Text(
                text = "Расход учитывается только для плательщика",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
            return
        }

        if (participants.isEmpty()) {
            Text(
                text = "Добавьте участников, чтобы разделить расход",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
            return
        }

        val amountValue = amount.replace(",", ".").toDoubleOrNull()
        participants.forEach { participant ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = participant.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                if (splitType == SplitType.EQUAL) {
                    val share = if (amountValue != null && amountValue > 0) {
                        amountValue / participants.size
                    } else null
                    Text(
                        text = share?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                } else {
                    OutlinedTextField(
                        value = splitAmounts[participant.userId].orEmpty(),
                        onValueChange = { onSplitAmountChange(participant.userId, it) },
                        modifier = Modifier.width(120.dp),
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (splitType == SplitType.EXACT && amountValue != null) {
            val splitSum = splitAmounts.values.sumOf {
                it.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
            val diff = amountValue - splitSum
            val diffText = if (kotlin.math.abs(diff) < 0.01) {
                "Сумма распределена полностью"
            } else {
                "Осталось распределить: ${String.format(Locale.US, "%.2f", diff)}"
            }
            Text(
                text = diffText,
                style = MaterialTheme.typography.bodySmall,
                color = if (kotlin.math.abs(diff) < 0.01) TealDark else Slate600
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddExpenseScreenPreview() {
    TrilooTheme {
        AddExpenseContent(
            uiState = PreviewData.addExpenseState,
            trip = PreviewData.trip,
            participants = emptyList(),
            onNavigateBack = {},
            onAmountChange = {},
            onCurrencyChange = {},
            onDescriptionChange = {},
            onCategoryChange = {},
            onPayerChange = {},
            onDateChange = { _ -> },
            onTimeChange = {},
            onNotesChange = {},
            onSplitTypeChange = {},
            onSplitAmountChange = { _, _ -> },
            onSave = {}
        )
    }
}

private fun buildCurrencyList(base: String?): List<String> {
    val defaults = listOf("RUB", "USD", "EUR", "TRY", "THB", "AED")
    return listOfNotNull(base) + defaults
        .filter { it != base }
        .distinct()
        .take(5)
}
