package com.triloo.ui.relay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.RelayMergeResult
import com.triloo.data.relay.RelayPayloadType
import com.triloo.data.relay.RelayQrCodec
import com.triloo.data.relay.RelayQrCollector
import com.triloo.data.relay.RelayRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RelayViewModel @Inject constructor(
    private val relayRepository: RelayRepository,
    private val tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId")
        ?: throw IllegalArgumentException("tripId is required")

    private val _uiState = MutableStateFlow(RelayUiState())
    val uiState: StateFlow<RelayUiState> = _uiState.asStateFlow()

    private val collector = RelayQrCollector()

    init {
        refreshExport()
        viewModelScope.launch {
            _uiState.update { it.copy(trip = tripRepository.getTripById(tripId)) }
        }
    }

    fun refreshExport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, exportError = null) }
            try {
                val relayPackage = relayRepository.buildPackage(tripId)
                if (relayPackage == null) {
                    _uiState.update { it.copy(isLoading = false, exportError = "Поездка не найдена") }
                    return@launch
                }
                val json = relayRepository.encodePackage(relayPackage)
                val chunks = RelayQrCodec.encode(RelayPayloadType.RELAY, json)
                _uiState.update { it.copy(isLoading = false, exportChunks = chunks) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, exportError = e.message ?: "Ошибка экспорта")
                }
            }
        }
    }

    fun handleRelayQrPayload(payload: String) {
        val chunk = RelayQrCodec.parse(payload)
        if (chunk == null || chunk.type != RelayPayloadType.RELAY) {
            collector.reset()
            _uiState.update { it.copy(scanError = "Это не пакет Triloo Relay", scanProgress = 0, scanTotal = 0) }
            return
        }

        if (!collector.addChunk(chunk)) {
            collector.reset()
            _uiState.update { it.copy(scanError = "QR-код относится к другой сессии", scanProgress = 0, scanTotal = 0) }
            return
        }

        val (received, total) = collector.progress()
        _uiState.update { it.copy(scanProgress = received, scanTotal = total, scanError = null) }

        if (!collector.isComplete()) return
        val jsonPayload = collector.assemblePayload() ?: run {
            _uiState.update { it.copy(scanError = "Не удалось собрать пакет") }
            collector.reset()
            return
        }

        collector.reset()
        _uiState.update { it.copy(isMerging = true, scanError = null) }

        viewModelScope.launch {
            try {
                val relayPackage = relayRepository.decodePackage(jsonPayload)
                val result = relayRepository.mergePackage(relayPackage)
                _uiState.update {
                    it.copy(
                        isMerging = false,
                        mergeResult = result,
                        scanProgress = 0,
                        scanTotal = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isMerging = false,
                        scanError = e.message ?: "Не удалось импортировать пакет"
                    )
                }
            }
        }
    }

    fun clearMergeResult() {
        _uiState.update { it.copy(mergeResult = null) }
    }
}

data class RelayUiState(
    val trip: com.triloo.data.model.Trip? = null,
    val isLoading: Boolean = false,
    val exportChunks: List<String> = emptyList(),
    val exportError: String? = null,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanError: String? = null,
    val isMerging: Boolean = false,
    val mergeResult: RelayMergeResult? = null
)
