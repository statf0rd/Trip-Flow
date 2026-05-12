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

    // tripId может быть null — это режим «гость без поездки»: экран открыт из
    // вкладки «Групповые поездки» для офлайн-присоединения к чужой поездке.
    // При null хостинг недоступен (нечего шарить), connect шлёт пустой
    // tripId, и хост на той стороне распознаёт это как «новый участник».
    private val tripId: String? = savedStateHandle.get<String>("tripId")
    val isReceiveOnly: Boolean = tripId == null

    private val _uiState = MutableStateFlow(RelayUiState(isReceiveOnly = isReceiveOnly))
    val uiState: StateFlow<RelayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(trip = tripId?.let { id -> tripRepository.getTripById(id) }) }
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
            _uiState.update { it.copy(trip = tripId?.let { id -> tripRepository.getTripById(id) }) }
        }
    }

    fun startHosting() {
        // В режиме «гость без поездки» хостить нечего — UI этой кнопки не
        // показывает, но защищаемся от случайного вызова.
        val hostTripId = tripId ?: run {
            _uiState.update { it.copy(syncError = "Нет поездки, которую можно расшарить") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncError = null) }
            try {
                val trip = tripRepository.getTripById(hostTripId)
                if (trip == null) {
                    _uiState.update { it.copy(syncError = "Поездка не найдена") }
                    return@launch
                }
                bluetoothRelayManager.startHosting { request, guestPackage ->
                    // Пустой tripId у запроса — это онбординг нового участника
                    // (вариант 1 нашего протокола): хост отдаёт ту поездку,
                    // которая у него сейчас в активной сессии hosting'а.
                    // Если запрос содержит конкретный tripId, и он не совпадает
                    // с нашим, отбиваем — нельзя «подсунуть» чужую поездку.
                    if (request.tripId.isNotEmpty() && request.tripId != hostTripId) {
                        throw IllegalStateException("Другая поездка запрашивает синхронизацию")
                    }
                    // Двунаправленный sync: если гость прислал свой пакет,
                    // сначала мерджим его в БД хоста. Если в guestPackage
                    // прилетела поездка с другим id (например, гость хостит
                    // несколько поездок и прислал «не ту»), мерджить её
                    // безопасно — она просто появится в БД, но в ответ всё
                    // равно уйдёт пакет hostTripId.
                    if (guestPackage != null) {
                        val decoded = relayRepository.decodePackage(guestPackage)
                        relayRepository.mergePackage(decoded)
                    }
                    val sinceCursor = request.knownChangeCursor
                        .takeIf { request.hasCompleteSnapshot && it > 0L && request.tripId == hostTripId }
                    val relayPackage = relayRepository.buildPackage(hostTripId, sinceCursor)
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
            // В режиме «гость без поездки» tripId == null — шлём пустую строку,
            // хост распознаёт это как онбординг и отдаёт свой текущий снимок.
            // В обычном режиме передаём наш tripId + cursor: если поездка уже
            // есть локально, получим только дельту.
            if (tripId == null) {
                bluetoothRelayManager.connect(
                    address = address,
                    tripId = "",
                    knownChangeCursor = 0L,
                    hasCompleteSnapshot = false,
                    // Онбординг: локальной поездки нет, шлём NO_PACKAGE.
                    guestPayloadProvider = { null }
                )
                return@launch
            }
            val syncState = relaySyncMetadataRepository.getTripSyncState(tripId)
            val hasLocalSnapshot = tripRepository.getTripById(tripId) != null && syncState.hasCompleteSnapshot
            bluetoothRelayManager.connect(
                address = address,
                tripId = tripId,
                knownChangeCursor = if (hasLocalSnapshot) syncState.lastMergedChangeCursor else 0L,
                hasCompleteSnapshot = hasLocalSnapshot,
                // Гость со своей копией поездки: собираем полный снапшот и
                // отдаём хосту, чтобы он мог смержить наши локальные правки
                // (например — добавленных участников) к себе перед тем, как
                // прислать назад объединённую версию.
                guestPayloadProvider = {
                    relayRepository.buildPackage(tripId)?.let {
                        relayRepository.encodePackage(it)
                    }
                }
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
    val isReceiveOnly: Boolean = false,
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
