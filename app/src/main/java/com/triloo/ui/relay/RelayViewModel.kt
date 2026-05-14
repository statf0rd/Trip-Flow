package com.triloo.ui.relay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.RelayMergeResult
import com.triloo.data.model.Trip
import com.triloo.data.relay.BluetoothRelayAckStatus
import com.triloo.data.relay.BluetoothRelayDevice
import com.triloo.data.relay.BluetoothRelayManager
import com.triloo.data.relay.BluetoothRelayOutgoingAck
import com.triloo.data.relay.IncomingTransferIntent
import com.triloo.data.relay.RelayRepository
import com.triloo.data.relay.RelaySyncMetadataRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Управляет relay-экраном: на хосте сканирует и шлёт поездку соседу,
 * на госте принимает поездку через RFCOMM-server.
 *
 * Архитектура v7 (см. BluetoothRelayManager):
 *   • tripId != null → хост (отправитель). Тапает пира → sendTrip.
 *   • tripId == null → гость (приёмник). startReceiving + consent UI.
 */
@HiltViewModel
class RelayViewModel @Inject constructor(
    private val relayRepository: RelayRepository,
    private val bluetoothRelayManager: BluetoothRelayManager,
    private val relaySyncMetadataRepository: RelaySyncMetadataRepository,
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // tripId == null → «гость без поездки»: экран открыт из вкладки
    // «Групповые поездки» для офлайн-приёма. UI прячет host-секцию,
    // менеджер крутит receive-server.
    private val tripId: String? = savedStateHandle.get<String>("tripId")
    val isReceiveOnly: Boolean = tripId == null

    private val _uiState = MutableStateFlow(RelayUiState(isReceiveOnly = isReceiveOnly))
    val uiState: StateFlow<RelayUiState> = _uiState.asStateFlow()

    // Слот для consent-future: гостевая сторона держит одну активную
    // передачу за раз. Когда менеджер дёргает consentProvider, мы кладём
    // сюда CompletableDeferred и suspend'имся внутри withTimeout. UI зовёт
    // acceptIncoming() → deferred.complete(true), таймаут → false.
    private val pendingConsent = AtomicReference<CompletableDeferred<Boolean>?>(null)

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

        // outgoingAcks приходит из менеджера, когда хост получил APPLIED ack
        // от гостя ИЛИ когда мы как гость успешно применили пакет и отправили
        // APPLIED ack обратно. В обоих случаях UI должен показать сцену
        // Completed, поэтому ставим долговечный маркер lastHostSendCompletedAt.
        viewModelScope.launch {
            bluetoothRelayManager.outgoingAcks.collect { ack: BluetoothRelayOutgoingAck ->
                if (ack.status == BluetoothRelayAckStatus.APPLIED ||
                    ack.status == BluetoothRelayAckStatus.DUPLICATE
                ) {
                    val ts = ack.timestampMs
                    Log.d(
                        "RelayVM",
                        "transfer completed @ $ts peer=${ack.deviceName}"
                    )
                    _uiState.update {
                        it.copy(
                            lastHostSendCompletedAt = ts,
                            // Не теряем имя пира — менеджер может позже его
                            // занулить, а UI хочет показать «Поездка теперь у …».
                            connectedDeviceName = ack.deviceName ?: it.connectedDeviceName,
                            // Сбрасываем «висячий» transportError, оставшийся
                            // от RFCOMM-шумов сразу после успеха.
                            transportError = null
                        )
                    }
                }
            }
        }
    }

    fun refreshBluetoothState() {
        bluetoothRelayManager.refreshState()
        viewModelScope.launch {
            _uiState.update { it.copy(trip = tripId?.let { id -> tripRepository.getTripById(id) }) }
        }
    }

    /**
     * Гость зовёт когда экран готов слушать (BT включён, permissions
     * выданы, discoverable окно поднято). Запускает receive-loop в
     * менеджере — будет ждать INTENT, потом дёргать наш consentProvider,
     * потом читать PACKAGE и эмитить через incomingPayloads.
     */
    fun startReceiving() {
        if (!isReceiveOnly) return
        viewModelScope.launch {
            _uiState.update { it.copy(syncError = null) }
            val profile = userProfileRepository.getProfile()
            val guestUserId = profile.userId
            val guestDisplayName = profile.displayName.ifBlank { "Участник" }
            bluetoothRelayManager.startReceiving(
                guestUserId = guestUserId,
                guestDisplayName = guestDisplayName
            ) { intent ->
                awaitConsent(intent)
            }
        }
    }

    fun stopReceiving() {
        // Сбрасываем «застрявший» consent: если пользователь нажал Назад,
        // не успев тапнуть «Принять», deferred всё ещё ждёт. Закрываем его
        // как «отклонено», чтобы receive-job не зависал в withTimeout.
        pendingConsent.getAndSet(null)?.takeIf { !it.isCompleted }?.complete(false)
        bluetoothRelayManager.stopReceiving()
    }

    fun startDiscovery() {
        _uiState.update { it.copy(syncError = null) }
        bluetoothRelayManager.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRelayManager.stopDiscovery()
    }

    /**
     * Хост тапает пира в кластере — открываем коннект, шлём intent,
     * после consent OK шлём поездку.
     */
    fun sendTrip(address: String) {
        val hostTripId = tripId ?: run {
            _uiState.update { it.copy(syncError = "Нет поездки, которую можно расшарить") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncError = null) }
            val trip = tripRepository.getTripById(hostTripId) ?: run {
                _uiState.update { it.copy(syncError = "Поездка не найдена") }
                return@launch
            }
            val profile = userProfileRepository.getProfile()
            val senderUserId = profile.userId
            val senderDisplayName = profile.displayName.ifBlank { "Участник" }
            bluetoothRelayManager.sendTrip(
                address = address,
                tripId = hostTripId,
                tripName = trip.name,
                senderUserId = senderUserId,
                senderDisplayName = senderDisplayName,
                onGuestIdentified = { guestUserId, guestDisplayName ->
                    // Записываем гостя как Participant ДО buildPackage —
                    // тогда отдаваемый пакет уже содержит обоих.
                    relayRepository.upsertParticipantFromRelay(
                        tripId = hostTripId,
                        userId = guestUserId,
                        displayName = guestDisplayName.ifBlank { "Участник" }
                    )
                },
                packageProvider = {
                    val relayPackage = relayRepository.buildPackage(hostTripId)
                        ?: throw IllegalStateException("Поездка не найдена")
                    relayRepository.encodePackage(relayPackage)
                }
            )
        }
    }

    /**
     * Гость тапнул «Принять» в consent UI — резолвим suspend, менеджер
     * читает CONSENT(true) и переходит к чтению пакета.
     */
    fun acceptIncoming() {
        val deferred = pendingConsent.get() ?: return
        if (!deferred.isCompleted) {
            Log.d("RelayVM", "acceptIncoming: resolving consent with accepted=true")
            deferred.complete(true)
        }
    }

    fun clearError() {
        bluetoothRelayManager.clearError()
        _uiState.update { it.copy(syncError = null) }
    }

    fun clearMergeResult() {
        _uiState.update { it.copy(mergeResult = null, lastReceivedTripId = null) }
    }

    /**
     * Сбрасывает «следы» успешного приёма: id полученной поездки и сводный
     * mergeResult. Экран вызывает это сразу после `onNavigateToTrip(tripId)`,
     * чтобы рекомпозиция не повторила навигацию.
     */
    fun consumeReceivedTrip() {
        Log.d("RelayVM", "consumeReceivedTrip: clearing lastReceivedTripId+mergeResult")
        _uiState.update { it.copy(mergeResult = null, lastReceivedTripId = null) }
    }

    /**
     * Сбрасывает маркер «передача завершена» — после этого `pickScene`
     * перестаёт держать Completed-сцену. Вызывается после auto-navigate
     * либо ручным «Готово».
     */
    fun consumeHostSendCompleted() {
        Log.d("RelayVM", "consumeHostSendCompleted: clearing lastHostSendCompletedAt")
        _uiState.update { it.copy(lastHostSendCompletedAt = null) }
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
        // На всякий случай разморозим возможный pendingConsent — без этого
        // receive-job останется suspend'нутым внутри withTimeout до самого
        // таймаута, удерживая socket.
        pendingConsent.getAndSet(null)?.takeIf { !it.isCompleted }?.complete(false)
        bluetoothRelayManager.stopAll()
        super.onCleared()
    }

    /**
     * Suspend-функция, которую BluetoothRelayManager.startReceiving зовёт
     * на каждый принятый INTENT. Показываем consent UI через
     * `pendingIncomingIntent` в state, ждём accept до таймаута, чистим
     * слот.
     */
    private suspend fun awaitConsent(intent: IncomingTransferIntent): Boolean {
        Log.d(
            "RelayVM",
            "awaitConsent: sender=${intent.senderDisplayName} tripId=${intent.tripId}" +
                " tripName=${intent.tripName}"
        )
        val deferred = CompletableDeferred<Boolean>()
        pendingConsent.set(deferred)
        _uiState.update { it.copy(pendingIncomingIntent = intent) }
        return try {
            withTimeout(CONSENT_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            Log.d("RelayVM", "awaitConsent: timed out")
            false
        } finally {
            pendingConsent.compareAndSet(deferred, null)
            _uiState.update { it.copy(pendingIncomingIntent = null) }
        }
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
                Log.d("RelayVM", "applyIncomingPayload: duplicate package, tripId=${relayPackage.trip.id}")
                _uiState.update {
                    it.copy(
                        isApplyingPackage = false,
                        mergeResult = RelayMergeResult(inserted = 0, updated = 0, deleted = 0),
                        lastReceivedTripId = relayPackage.trip.id,
                        trip = relayPackage.trip,
                        statusMessage = "Повторный пакет от $deviceName пропущен"
                    )
                }
                return
            }

            val result = relayRepository.mergePackage(relayPackage)
            // Safety net: записываем самого себя как Participant'a, если хост
            // по какой-то причине не прислал нас в списке (например, race до
            // того, как наш upsert на стороне хоста закоммитится в DB). На
            // нормальном пути мы уже есть в relayPackage.participants —
            // upsert просто обновит updatedAt.
            val profile = userProfileRepository.getProfile()
            if (profile.userId.isNotBlank()) {
                relayRepository.upsertParticipantFromRelay(
                    tripId = relayPackage.trip.id,
                    userId = profile.userId,
                    displayName = profile.displayName.ifBlank { "Участник" }
                )
            }
            relaySyncMetadataRepository.markTripPackageApplied(
                tripId = relayPackage.trip.id,
                packageId = relayPackage.packageId,
                changeCursor = relayPackage.changeCursor,
                isFullSnapshot = !relayPackage.isDelta
            )
            // ACK уходит немедленно — пока UI задерживается на «Completed»,
            // хост уже знает об успехе и может закрыть свою сессию.
            bluetoothRelayManager.acknowledgeIncomingTransfer(
                transferId = transferId,
                status = BluetoothRelayAckStatus.APPLIED,
                message = "Изменения успешно применены"
            )
            // Минимальная длительность сцены IncomingTransfer: оставляем
            // `isApplyingPackage = true` ещё ~1.5с, чтобы пользователь
            // успевал увидеть «Принимаю поездку…».
            delay(1500)
            Log.d(
                "RelayVM",
                "applyIncomingPayload: merged tripId=${relayPackage.trip.id} " +
                    "inserted=${result.inserted} updated=${result.updated} deleted=${result.deleted}"
            )
            _uiState.update {
                it.copy(
                    isApplyingPackage = false,
                    mergeResult = result,
                    lastReceivedTripId = relayPackage.trip.id,
                    trip = relayPackage.trip,
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

    private companion object {
        private const val CONSENT_TIMEOUT_MS = 60_000L
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
    val mergeResult: RelayMergeResult? = null,
    // Идентификатор поездки, только что полученной по Bluetooth. Заполняется
    // в applyIncomingPayload после успешного merge. Гость-экран использует
    // его для auto-navigate в TripDetails.
    val lastReceivedTripId: String? = null,
    // Метка времени (System.currentTimeMillis), когда сторона зафиксировала
    // успешный обмен (хост получил ack гостя ИЛИ гость отправил APPLIED ack
    // хосту). Используется как «долговечный» признак для сцены Completed.
    val lastHostSendCompletedAt: Long? = null,
    // Входящий intent на госте — заполняется, пока ждём тап «Принять».
    // null = consent UI не нужен.
    val pendingIncomingIntent: IncomingTransferIntent? = null
)
