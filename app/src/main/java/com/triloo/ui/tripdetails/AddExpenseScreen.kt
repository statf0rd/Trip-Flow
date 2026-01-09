package com.triloo.ui.tripdetails

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
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.ui.PreviewData
import com.triloo.ui.components.ParticipantAvatar
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

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

                ExpenseCategoryCarousel(
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
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            splitOptions.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = splitType == type,
                    onClick = { onSplitTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, splitOptions.size)
                ) {
                    Text(type.displayName)
                }
            }
        }

        when (splitType) {
            SplitType.PAYER_ONLY -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(18.dp),
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
                    shape = RoundedCornerShape(18.dp),
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
                                    shape = RoundedCornerShape(12.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseCategoryCarousel(
    categories: List<ExpenseCategory>,
    selectedCategory: ExpenseCategory,
    onCategoryChange: (ExpenseCategory) -> Unit
) {
    val initialPage = categories.indexOf(selectedCategory).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { categories.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        categories.getOrNull(pagerState.currentPage)?.let { category ->
            if (category != selectedCategory) {
                onCategoryChange(category)
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        val target = categories.indexOf(selectedCategory)
        if (target >= 0 && target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }

    VerticalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(56.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        beyondViewportPageCount = 2,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) { page ->
        val category = categories[page]
        val isSelected = category == selectedCategory
        val pageOffset = (
            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        ).absoluteValue
        val scale = 0.92f + (1f - pageOffset.coerceIn(0f, 1f)) * 0.08f
        val alpha = 0.6f + (1f - pageOffset.coerceIn(0f, 1f)) * 0.4f
        val accent = Color(category.colorHex)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clickable {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
            shape = RoundedCornerShape(18.dp),
            color = if (isSelected) accent.copy(alpha = 0.18f) else Slate100,
            border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = category.emoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) accent else Slate800
                    )
                    Text(
                        text = if (isSelected) "Выбрано" else "Прокрутите",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = accent
                    )
                }
            }
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
