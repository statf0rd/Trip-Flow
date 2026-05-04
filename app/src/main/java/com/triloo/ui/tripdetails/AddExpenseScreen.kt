package com.triloo.ui.tripdetails

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.ui.PreviewData
import com.triloo.ui.components.ParticipantAvatar
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import com.triloo.ui.theme.TrilooMotion
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

/**
 * Экран добавления и редактирования расхода с выбором категории, валюты и плательщика.
 */
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
    val context = LocalContext.current
    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.importReceipt(uri.toString())
    }

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
        onSettledChange = viewModel::updateSettled,
        onSplitTypeChange = viewModel::updateSplitType,
        onSplitAmountChange = viewModel::updateSplitAmount,
        onPickReceipt = { receiptPickerLauncher.launch(arrayOf("image/*")) },
        onRemoveReceipt = viewModel::removeReceipt,
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
    onSettledChange: (Boolean) -> Unit,
    onSplitTypeChange: (SplitType) -> Unit,
    onSplitAmountChange: (String, String) -> Unit,
    onPickReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onSave: () -> Unit,
    focusManager: FocusManager = LocalFocusManager.current
) {
    val scrollState = rememberScrollState()
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            trip?.let {
                Surface(
                    shape = TrilooShapes.Sm,
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = TrilooShapes.Sm,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    buildCurrencyList(trip?.baseCurrency).forEach { currency ->
                        val isSelected = currency == uiState.currency
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrencyChange(currency) },
                            label = { Text(currency) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
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
                shape = TrilooShapes.Sm
            )

            ReceiptSection(
                receiptImageUri = uiState.receiptImageUri,
                isProcessing = uiState.isReceiptProcessing,
                summary = uiState.receiptSummary,
                error = uiState.receiptError,
                onPickReceipt = onPickReceipt,
                onRemoveReceipt = onRemoveReceipt
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )

                ExpenseCategoryGrid(
                    categories = ExpenseCategory.entries.toList(),
                    selectedCategory = uiState.category,
                    onCategoryChange = onCategoryChange
                )
            }

            ExpenseSplitSection(
                splitType = uiState.splitType,
                participants = participants,
                amount = uiState.amount,
                currency = uiState.currency,
                splitAmounts = uiState.splitAmounts,
                onSplitTypeChange = onSplitTypeChange,
                onSplitAmountChange = onSplitAmountChange
            )

            if (uiState.splitType != SplitType.PAYER_ONLY) {
                Surface(
                    shape = TrilooShapes.Md,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Долг уже закрыт",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Text(
                                text = "Закрытые расходы не участвуют в балансе между участниками",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.isSettled,
                            onCheckedChange = onSettledChange
                        )
                    }
                }
            }

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
                shape = TrilooShapes.Sm
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
                    shape = TrilooShapes.Sm,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = Slate500,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = uiState.time,
                    onValueChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTimePicker = true },
                    label = { Text("Время") },
                    placeholder = { Text("14:30", color = Slate500) },
                    readOnly = true,
                    enabled = false,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Slate500
                        )
                    },
                    singleLine = true,
                    shape = TrilooShapes.textField,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = Slate500,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = Slate500
                    )
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
                shape = TrilooShapes.Sm
            )

            uiState.error?.let { errorText ->
                Surface(
                    shape = TrilooShapes.Sm,
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
                    Text("ОК", color = MaterialTheme.colorScheme.primary)
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
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    if (showTimePicker) {
        ExpenseTimePickerDialog(
            initialValue = uiState.time,
            onDismissRequest = { showTimePicker = false },
            onConfirm = { hh, mm ->
                onTimeChange(String.format(Locale.US, "%02d:%02d", hh, mm))
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseTimePickerDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val now = remember { java.time.LocalTime.now() }
    val parsed = remember(initialValue) { parseInitialTime(initialValue) }
    val state = rememberTimePickerState(
        initialHour = parsed?.first ?: now.hour,
        initialMinute = parsed?.second ?: now.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Выберите время") },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}

private fun parseInitialTime(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hh = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
    val mm = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
    return hh to mm
}

@Composable
private fun ReceiptSection(
    receiptImageUri: String?,
    isProcessing: Boolean,
    summary: String?,
    error: String?,
    onPickReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit
) {
    Surface(
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Чек",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                    Text(
                        text = "Загрузите фото чека, и Triloo попробует заполнить сумму, дату и описание автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AssistChip(
                    onClick = onPickReceipt,
                    label = { Text(if (receiptImageUri == null) "Загрузить" else "Заменить") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null
                        )
                    }
                )
            }

            receiptImageUri?.let { uriString ->
                AsyncImage(
                    model = Uri.parse(uriString),
                    contentDescription = "Чек",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(TrilooShapes.Md)
                )
            }

            if (isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Распознаём чек и ищем сумму...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            }

            summary?.let { summaryText ->
                Surface(
                    shape = TrilooShapes.Sm,
                    color = TealSubtle
                ) {
                    Text(
                        text = "Распознано: $summaryText",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TealDark
                    )
                }
            }

            error?.let { errorText ->
                Surface(
                    shape = TrilooShapes.Sm,
                    color = ErrorLight
                ) {
                    Text(
                        text = errorText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }

            if (receiptImageUri != null) {
                TextButton(
                    onClick = onRemoveReceipt,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Удалить чек")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseSplitSection(
    splitType: SplitType,
    participants: List<Participant>,
    amount: String,
    currency: String,
    splitAmounts: Map<String, String>,
    onSplitTypeChange: (SplitType) -> Unit,
    onSplitAmountChange: (String, String) -> Unit
) {
    val amountValue = amount.replace(",", ".").toDoubleOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Разделить",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate700
        )

        val splitOptions = listOf(SplitType.PAYER_ONLY, SplitType.EQUAL, SplitType.EXACT)
        val accent = MaterialTheme.colorScheme.primary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            splitOptions.forEach { type ->
                val isSelected = splitType == type
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1f,
                    animationSpec = TrilooMotion.selectSpring,
                    label = "splitTypeScale"
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable { onSplitTypeChange(type) },
                    shape = TrilooShapes.Md,
                    color = if (isSelected) accent.copy(alpha = 0.14f) else Slate100,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) accent.copy(alpha = 0.6f) else Slate200
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) accent else Slate700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        when (splitType) {
            SplitType.PAYER_ONLY -> {
                Surface(
                    shape = TrilooShapes.Md,
                    color = Slate100
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Slate600
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Личный расход, не участвует в расчёте долгов",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
            }
            SplitType.EQUAL -> {
                if (participants.isEmpty()) {
                    Text(
                        text = "Добавьте участников, чтобы разделить расход",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    return
                }

                val perPerson = if (amountValue != null && amountValue > 0) {
                    amountValue / participants.size
                } else null

                Surface(
                    shape = TrilooShapes.Md,
                    color = Slate100
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Участники: ${participants.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                        Text(
                            text = perPerson?.let {
                                "Каждый платит ≈ ${String.format(Locale.US, "%.2f", it)} $currency"
                            } ?: "Введите сумму, чтобы рассчитать долю",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate700
                        )

                        participants.forEach { participant ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ParticipantAvatar(
                                        name = participant.displayName,
                                        size = 32.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = participant.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate800
                                    )
                                }
                                Text(
                                    text = perPerson?.let {
                                        "${String.format(Locale.US, "%.2f", it)} $currency"
                                    } ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }
            }
            SplitType.EXACT -> {
                if (participants.isEmpty()) {
                    Text(
                        text = "Добавьте участников, чтобы разделить расход",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    return
                }

                val splitSum = splitAmounts.values.sumOf {
                    it.replace(",", ".").toDoubleOrNull() ?: 0.0
                }
                val diff = if (amountValue != null) amountValue - splitSum else null
                val diffText = when {
                    amountValue == null -> "Введите сумму, чтобы заполнить распределение"
                    diff != null && kotlin.math.abs(diff) < 0.01 -> "Сумма распределена полностью"
                    diff != null && diff > 0 -> {
                        "Осталось распределить: ${String.format(Locale.US, "%.2f", diff)} $currency"
                    }
                    diff != null && diff < 0 -> {
                        "Превышение: ${String.format(Locale.US, "%.2f", kotlin.math.abs(diff))} $currency"
                    }
                    else -> "—"
                }

                Surface(
                    shape = TrilooShapes.Md,
                    color = Slate100
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Суммы по участникам",
                                style = MaterialTheme.typography.titleSmall,
                                color = Slate700,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        if (amountValue == null || participants.isEmpty()) return@TextButton
                                        val share = amountValue / participants.size
                                        participants.forEach { participant ->
                                            onSplitAmountChange(
                                                participant.userId,
                                                String.format(Locale.US, "%.2f", share)
                                            )
                                        }
                                    }
                                ) {
                                    Text("Поровну")
                                }
                                TextButton(
                                    onClick = {
                                        participants.forEach { participant ->
                                            onSplitAmountChange(participant.userId, "")
                                        }
                                    }
                                ) {
                                    Text("Очистить")
                                }
                            }
                        }

                        participants.forEach { participant ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ParticipantAvatar(
                                    name = participant.displayName,
                                    size = 32.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = participant.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate800
                                    )
                                    Text(
                                        text = "Введите сумму в $currency",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate500
                                    )
                                }
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
                                    shape = TrilooShapes.Sm,
                                    suffix = { Text(currency) }
                                )
                            }
                        }

                        Text(
                            text = diffText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diff != null && kotlin.math.abs(diff) < 0.01) TealDark else Slate600
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ExpenseCategoryGrid(
    categories: List<ExpenseCategory>,
    selectedCategory: ExpenseCategory,
    onCategoryChange: (ExpenseCategory) -> Unit
) {
    val rows = categories.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { category ->
                    ExpenseCategoryTile(
                        category = category,
                        isSelected = category == selectedCategory,
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryChange(category) }
                    )
                }
                // Если в последней строке меньше трёх элементов — добиваем пустыми
                // weight'ами, чтобы плитки сохранили одинаковый размер.
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExpenseCategoryTile(
    category: ExpenseCategory,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = Color(category.colorHex)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = TrilooMotion.selectSpring,
        label = "categoryTileScale"
    )
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick),
        shape = TrilooShapes.Md,
        color = if (isSelected) accent.copy(alpha = 0.16f) else Slate100,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accent.copy(alpha = 0.55f) else Slate200
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(TrilooShapes.Sm)
                    .background(accent.copy(alpha = if (isSelected) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.displayName,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) accent else Slate800,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
            onSettledChange = {},
            onSplitTypeChange = {},
            onSplitAmountChange = { _, _ -> },
            onPickReceipt = {},
            onRemoveReceipt = {},
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
