package com.triloo.ui.relay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.RelayMergeResult
import com.triloo.data.model.Trip
import com.triloo.data.relay.BluetoothRelayAckStatus
import com.triloo.data.relay.BluetoothRelayDevice
import com.triloo.data.relay.BluetoothRelayManager
import com.triloo.data.relay.RelayRepository
import com.triloo.data.relay.RelaySyncMetadataRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Собирает relay-пакет поездки и синхронизирует его с соседним устройством по Bluetooth.
 */
@HiltViewModel
class RelayViewModel @Inject constructor(
    private val relayRepository: RelayRepository,
    private val bluetoothRelayManager: BluetoothRelayManager,
    private val relaySyncMetadataRepository: RelaySyncMetadataRepository,
    private val tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId")
        ?: throw IllegalArgumentException("tripId is required")

    private val _uiState = MutableStateFlow(RelayUiState())
    val uiState: StateFlow<RelayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(trip = tripRepository.getTripById(tripId)) }
        }

        viewModelScope.launch {
            bluetoothRelayManager.state.collectLatest { transport ->
                _uiState.update {
                    it.copy(
                        isBluetoothSupported = transport.isSupported,
                        isBluetoothEnabled = transport.isEnabled,
                        isHosting = transport.isHosting,
                        isDiscovering = transport.isDiscovering,
                        isConnecting = transport.isConnecting,
                        isTransferring = transport.isTransferring,
                        localDeviceName = transport.localDeviceName,
                        connectedDeviceName = transport.connectedDeviceName,
                        statusMessage = transport.statusMessage,
                        transportError = transport.error,
                        devices = transport.devices
                    )
                }
            }
        }

        viewModelScope.launch {
            bluetoothRelayManager.incomingPayloads.collectLatest { transfer ->
                applyIncomingPayload(
                    transferId = transfer.transferId,
                    payload = transfer.payload,
                    deviceName = transfer.deviceName
                )
            }
        }
    }

    fun refreshBluetoothState() {
        bluetoothRelayManager.refreshState()
        viewModelScope.launch {
            _uiState.update { it.copy(trip = tripRepository.getTripById(tripId)) }
        }
    }

    fun startHosting() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncError = null) }
            try {
                val trip = tripRepository.getTripById(tripId)
                if (trip == null) {
                    _uiState.update { it.copy(syncError = "Поездка не найдена") }
                    return@launch
                }
                bluetoothRelayManager.startHosting { request ->
                    if (request.tripId != tripId) {
                        throw IllegalStateException("Другая поездка запрашивает синхронизацию")
                    }
                    val sinceCursor = request.knownChangeCursor.takeIf { request.hasCompleteSnapshot && it > 0L }
                    val relayPackage = relayRepository.buildPackage(tripId, sinceCursor)
                        ?: throw IllegalStateException("Поездка не найдена")
                    relayRepository.encodePackage(relayPackage)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(syncError = e.message ?: "Не удалось подготовить пакет синхронизации")
                }
            }
        }
    }

    fun stopHosting() {
        bluetoothRelayManager.stopHosting()
    }

    fun startDiscovery() {
        _uiState.update { it.copy(syncError = null) }
        bluetoothRelayManager.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRelayManager.stopDiscovery()
    }

    fun connect(address: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncError = null) }
            val syncState = relaySyncMetadataRepository.getTripSyncState(tripId)
            val hasLocalSnapshot = tripRepository.getTripById(tripId) != null && syncState.hasCompleteSnapshot
            bluetoothRelayManager.connect(
                address = address,
                tripId = tripId,
                knownChangeCursor = if (hasLocalSnapshot) syncState.lastMergedChangeCursor else 0L,
                hasCompleteSnapshot = hasLocalSnapshot
            )
        }
    }

    fun clearError() {
        bluetoothRelayManager.clearError()
        _uiState.update { it.copy(syncError = null) }
    }

    fun clearMergeResult() {
        _uiState.update { it.copy(mergeResult = null) }
    }

    fun onBluetoothPermissionDenied() {
        _uiState.update {
            it.copy(syncError = "Без разрешений Bluetooth синхронизация недоступна")
        }
    }

    fun onBluetoothEnableRejected() {
        _uiState.update {
            it.copy(syncError = "Bluetooth не был включён")
        }
    }

    fun onDiscoverableRejected() {
        _uiState.update {
            it.copy(syncError = "Устройство не стало видимым для других устройств")
        }
    }

    override fun onCleared() {
        bluetoothRelayManager.stopAll()
        super.onCleared()
    }

    private suspend fun applyIncomingPayload(
        transferId: String,
        payload: String,
        deviceName: String
    ) {
        _uiState.update { it.copy(isApplyingPackage = true, syncError = null) }
        try {
            val relayPackage = relayRepository.decodePackage(payload)
            if (relaySyncMetadataRepository.hasAppliedPackage(relayPackage.packageId)) {
                bluetoothRelayManager.acknowledgeIncomingTransfer(
                    transferId = transferId,
                    status = BluetoothRelayAckStatus.DUPLICATE,
                    message = "Пакет уже импортирован на этом устройстве"
                )
                _uiState.update {
                    it.copy(
                        isApplyingPackage = false,
                        mergeResult = RelayMergeResult(inserted = 0, updated = 0, deleted = 0),
                        statusMessage = "Повторный пакет от $deviceName пропущен"
                    )
                }
                return
            }

            val result = relayRepository.mergePackage(relayPackage)
            relaySyncMetadataRepository.markTripPackageApplied(
                tripId = relayPackage.trip.id,
                packageId = relayPackage.packageId,
                changeCursor = relayPackage.changeCursor,
                isFullSnapshot = !relayPackage.isDelta
            )
            bluetoothRelayManager.acknowledgeIncomingTransfer(
                transferId = transferId,
                status = BluetoothRelayAckStatus.APPLIED,
                message = "Изменения успешно применены"
            )
            _uiState.update {
                it.copy(
                    isApplyingPackage = false,
                    mergeResult = result,
                    statusMessage = if (relayPackage.isDelta) {
                        "Переданы только новые изменения"
                    } else {
                        "Синхронизация завершена"
                    }
                )
            }
        } catch (e: Exception) {
            bluetoothRelayManager.acknowledgeIncomingTransfer(
                transferId = transferId,
                status = BluetoothRelayAckStatus.FAILED,
                message = e.message ?: "Не удалось импортировать пакет"
            )
            _uiState.update {
                it.copy(
                    isApplyingPackage = false,
                    syncError = e.message ?: "Не удалось импортировать пакет"
                )
            }
        }
    }
}

/**
 * Состояние экрана Bluetooth-синхронизации.
 */
data class RelayUiState(
    val trip: Trip? = null,
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val isHosting: Boolean = false,
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isTransferring: Boolean = false,
    val isApplyingPackage: Boolean = false,
    val localDeviceName: String? = null,
    val connectedDeviceName: String? = null,
    val statusMessage: String? = null,
    val transportError: String? = null,
    val syncError: String? = null,
    val devices: List<BluetoothRelayDevice> = emptyList(),
    val mergeResult: RelayMergeResult? = null
)
