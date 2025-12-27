package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId")
        ?: throw IllegalArgumentException("tripId is required")
    private val expenseId: String? = savedStateHandle.get<String>("expenseId")

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            date = LocalDate.now()
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null

    init {
        viewModelScope.launch {
            val currentTrip = tripRepository.getTripById(tripId)
            _trip.value = currentTrip
            _uiState.update { state ->
                state.copy(currency = currentTrip?.baseCurrency ?: state.currency)
            }
            refreshExchangeRate()
        }

        viewModelScope.launch {
            val profile = userProfileRepository.getProfile()
            currentUserId = profile.userId
            currentUserName = profile.displayName.ifBlank { null }
            if (profile.displayName.isNotBlank()) {
                _uiState.update { state ->
                    if (state.paidBy.isBlank()) {
                        state.copy(paidBy = profile.displayName)
                    } else {
                        state
                    }
                }
            }
        }

        viewModelScope.launch {
            tripRepository.observeParticipants(tripId).collectLatest { list ->
                _participants.value = list
            }
        }

        if (expenseId != null) {
            viewModelScope.launch {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense != null) {
                    _uiState.update { state ->
                        state.copy(
                            description = expense.description,
                            amount = expense.amount.toString(),
                            currency = expense.currency,
                            category = expense.category,
                            paidBy = expense.paidByName,
                            date = expense.date,
                            time = expense.time.orEmpty(),
                            notes = expense.notes.orEmpty(),
                            splitType = expense.splitType,
                            splitAmounts = expense.splitAmounts?.mapValues { it.value.toString() }.orEmpty(),
                            exchangeRate = expense.exchangeRate,
                            isEditing = true
                        )
                    }
                    refreshExchangeRate()
                }
            }
        }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateCurrency(value: String) {
        _uiState.update { it.copy(currency = value.uppercase()) }
        refreshExchangeRate()
    }

    fun updatePayer(value: String) {
        _uiState.update { it.copy(paidBy = value) }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
        refreshExchangeRate()
    }

    fun updateTime(value: String) {
        _uiState.update { it.copy(time = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateSplitType(type: SplitType) {
        _uiState.update { state ->
            state.copy(
                splitType = type,
                splitAmounts = if (type == SplitType.EXACT) state.splitAmounts else emptyMap()
            )
        }
    }

    fun updateSplitAmount(userId: String, value: String) {
        _uiState.update { state ->
            state.copy(
                splitAmounts = state.splitAmounts.toMutableMap().apply {
                    if (value.isBlank()) {
                        remove(userId)
                    } else {
                        put(userId, value)
                    }
                }
            )
        }
    }

    fun saveExpense() {
        val state = _uiState.value
        val amount = state.amount.replace(",", ".").toDoubleOrNull() ?: return
        val currency = if (state.currency.isBlank()) {
            _trip.value?.baseCurrency ?: "RUB"
        } else state.currency
        val baseCurrency = _trip.value?.baseCurrency ?: currency
        val exchangeRate = if (currency == baseCurrency) 1.0 else state.exchangeRate
        if (currency != baseCurrency && exchangeRate <= 0.0) {
            _uiState.update { it.copy(error = "Не удалось получить курс валют") }
            return
        }
        val amountInBase = amount * exchangeRate
        val payerInput = state.paidBy.trim()
        val payerName = if (payerInput.isBlank()) {
            currentUserName ?: "Вы"
        } else {
            payerInput
        }
        val payerId = if (payerInput.isBlank() || (currentUserName != null && payerName == currentUserName)) {
            currentUserId ?: payerName.lowercase().replace(" ", "_")
        } else {
            payerName.lowercase().replace(" ", "_")
        }

        val splitAmounts = buildSplitAmounts(state.splitType, state.splitAmounts, amount)
        if (state.splitType == SplitType.EXACT && splitAmounts == null) {
            _uiState.update { it.copy(error = "Сумма по участникам не совпадает с общей") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val resolvedRate = if (currency == baseCurrency) {
                    1.0
                } else {
                    withContext(Dispatchers.IO) {
                        expenseRepository.getOrFetchCurrencyRate(
                            currency,
                            baseCurrency,
                            state.date ?: LocalDate.now()
                        )
                    }
                        ?: exchangeRate
                }
                val resolvedAmountInBase = amount * resolvedRate
                val expense = Expense(
                    id = expenseId ?: UUID.randomUUID().toString(),
                    tripId = tripId,
                    description = state.description.trim(),
                    amount = amount,
                    currency = currency,
                    amountInBaseCurrency = resolvedAmountInBase,
                    exchangeRate = resolvedRate,
                    exchangeRateDate = state.date ?: LocalDate.now(),
                    category = state.category,
                    paidByUserId = payerId,
                    paidByName = payerName,
                    splitType = state.splitType,
                    splitAmounts = splitAmounts,
                    date = state.date ?: LocalDate.now(),
                    time = state.time.trim().ifBlank { null },
                    notes = state.notes.trim().ifBlank { null }
                )

                if (expenseId == null) {
                    expenseRepository.addExpense(expense)
                } else {
                    expenseRepository.updateExpense(expense)
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Не удалось добавить расход"
                    )
                }
            }
        }
    }

    private fun buildSplitAmounts(
        splitType: SplitType,
        rawAmounts: Map<String, String>,
        totalAmount: Double
    ): Map<String, Double>? {
        if (splitType != SplitType.EXACT) return null
        if (rawAmounts.isEmpty()) return null

        val parsed = rawAmounts.mapValues { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
        val sum = parsed.values.sum()
        val diff = kotlin.math.abs(sum - totalAmount)
        return if (diff <= 0.01) parsed else null
    }

    private fun refreshExchangeRate() {
        val state = _uiState.value
        val currency = state.currency.ifBlank { _trip.value?.baseCurrency ?: "RUB" }
        val baseCurrency = _trip.value?.baseCurrency ?: currency
        val date = state.date ?: LocalDate.now()

        if (currency == baseCurrency) {
            _uiState.update { it.copy(exchangeRate = 1.0, rateError = null, isRateLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRateLoading = true, rateError = null) }
            val rate = withContext(Dispatchers.IO) {
                expenseRepository.getOrFetchCurrencyRate(currency, baseCurrency, date)
            }
            if (rate == null) {
                _uiState.update {
                    it.copy(isRateLoading = false, rateError = "Не удалось получить курс валют")
                }
            } else {
                _uiState.update { it.copy(exchangeRate = rate, isRateLoading = false, rateError = null) }
            }
        }
    }
}

data class AddExpenseUiState(
    val description: String = "",
    val amount: String = "",
    val currency: String = "RUB",
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val paidBy: String = "",
    val date: LocalDate? = null,
    val time: String = "",
    val notes: String = "",
    val splitType: SplitType = SplitType.PAYER_ONLY,
    val splitAmounts: Map<String, String> = emptyMap(),
    val exchangeRate: Double = 1.0,
    val isRateLoading: Boolean = false,
    val rateError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false
) {
    val isValid: Boolean
        get() = description.isNotBlank() &&
                amount.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true &&
                date != null &&
                !isSaving
}
