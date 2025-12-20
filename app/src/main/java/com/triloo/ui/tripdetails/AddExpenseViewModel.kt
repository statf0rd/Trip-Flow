package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            date = LocalDate.now()
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null

    init {
        viewModelScope.launch {
            val currentTrip = tripRepository.getTripById(tripId)
            _trip.value = currentTrip
            _uiState.update { state ->
                state.copy(currency = currentTrip?.baseCurrency ?: state.currency)
            }
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
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateCurrency(value: String) {
        _uiState.update { it.copy(currency = value) }
    }

    fun updatePayer(value: String) {
        _uiState.update { it.copy(paidBy = value) }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateTime(value: String) {
        _uiState.update { it.copy(time = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun saveExpense() {
        val state = _uiState.value
        val amount = state.amount.replace(",", ".").toDoubleOrNull() ?: return
        val currency = if (state.currency.isBlank()) {
            _trip.value?.baseCurrency ?: "RUB"
        } else state.currency
        val baseCurrency = _trip.value?.baseCurrency ?: currency
        val exchangeRate = if (currency == baseCurrency) 1.0 else 1.0 // Conversion not implemented yet
        val amountInBase = if (currency == baseCurrency) {
            amount
        } else {
            amount * exchangeRate
        }
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

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val expense = Expense(
                    tripId = tripId,
                    description = state.description.trim(),
                    amount = amount,
                    currency = currency,
                    amountInBaseCurrency = amountInBase,
                    exchangeRate = exchangeRate,
                    exchangeRateDate = state.date ?: LocalDate.now(),
                    category = state.category,
                    paidByUserId = payerId,
                    paidByName = payerName,
                    splitType = SplitType.PAYER_ONLY,
                    date = state.date ?: LocalDate.now(),
                    time = state.time.trim().ifBlank { null },
                    notes = state.notes.trim().ifBlank { null }
                )

                expenseRepository.addExpense(expense)
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
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = description.isNotBlank() &&
                amount.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true &&
                date != null &&
                !isSaving
}
