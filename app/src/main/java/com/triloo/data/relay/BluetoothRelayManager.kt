package com.triloo.data.relay

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class BluetoothRelayDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean
)

data class BluetoothRelayState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isHosting: Boolean = false,
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isTransferring: Boolean = false,
    val localDeviceName: String? = null,
    val connectedDeviceName: String? = null,
    val statusMessage: String? = null,
    val error: String? = null,
    val devices: List<BluetoothRelayDevice> = emptyList()
)

data class IncomingBluetoothRelayPayload(
    val transferId: String,
    val payload: String,
    val deviceName: String
)

/**
 * Описывает входящий intent — первый фрейм, который хост шлёт гостю сразу
 * после успешного RFCOMM-коннекта. Содержит данные, нужные UI для показа
 * экрана согласия («$sender хочет поделиться $trip — Принять?»). После
 * подтверждения мы запишем хоста как Participant'а через
 * upsertParticipantFromRelay, поэтому senderUserId/senderDisplayName тут —
 * это не имя BT-устройства, а профиль пользователя.
 */
data class IncomingTransferIntent(
    val senderUserId: String,
    val senderDisplayName: String,
    val senderBtName: String,
    val tripId: String,
    val tripName: String
)

enum class BluetoothRelayAckStatus {
    APPLIED,
    DUPLICATE,
    FAILED
}

/**
 * Сигнал «передача завершилась успехом» — менеджер эмитит его, когда host
 * получает APPLIED ack от гостя (после `sendTrip`) или когда мы как
 * получающая сторона успешно применили пакет и отправили APPLIED ack
 * (`acknowledgeIncomingTransfer`). UI подписывается на этот SharedFlow, чтобы
 * зафиксировать «передача завершилась»-метку независимо от того, как
 * менеджер потом перетрёт `statusMessage`.
 */
data class BluetoothRelayOutgoingAck(
    val status: BluetoothRelayAckStatus,
    val deviceName: String?,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Передаёт relay-пакеты между устройствами по Bluetooth Classic RFCOMM
 * и ждёт подтверждение результата применения на принимающей стороне.
 *
 * Архитектура v7:
 *   • host (владелец поездки) — RFCOMM-CLIENT: сканирует, тапает пира,
 *     открывает RFCOMM-сокет, шлёт INTENT, ждёт CONSENT, шлёт PACKAGE,
 *     ждёт ACK.
 *   • guest (принимающий) — RFCOMM-SERVER: становится discoverable,
 *     слушает входящие коннекты, после accept() читает INTENT, дёргает
 *     `consentProvider` (UI показывает «Принять?»), при OK читает PACKAGE,
 *     эмитит наверх в `incomingPayloads`, после применения VM зовёт
 *     `acknowledgeIncomingTransfer` который шлёт ACK.
 */
@Singleton
class BluetoothRelayManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        BluetoothRelayState(
            isSupported = adapter != null,
            isEnabled = adapter?.isEnabled == true,
            localDeviceName = resolveLocalDeviceName(adapter)
        )
    )
    val state = _state.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<IncomingBluetoothRelayPayload>(extraBufferCapacity = 1)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    // SharedFlow «успешный ack» — фиксируется UI'ем (см. RelayViewModel), чтобы
    // показывать сцену Completed даже после того, как менеджер сбросит
    // statusMessage в "Ожидание поездки..." на следующей итерации accept().
    private val _outgoingAcks = MutableSharedFlow<BluetoothRelayOutgoingAck>(extraBufferCapacity = 4)
    val outgoingAcks = _outgoingAcks.asSharedFlow()

    private val activeIncomingSockets = ConcurrentHashMap<String, BluetoothSocket>()

    private var receiverRegistered = false
    private var receiveJob: Job? = null
    private var connectJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null

    // Метка момента последнего APPLIED-ack. Используется в sendTrip() для того,
    // чтобы подавить "Не удалось подключиться..."-баннер, когда socket падает
    // через несколько сотен мс после реального успеха (RFCOMM любит закрывать
    // сокет сразу после ACK на стороне POCO/Xiaomi).
    @Volatile
    private var lastSuccessfulAckAt: Long = 0L

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _state.update {
                        it.copy(
                            isDiscovering = true,
                            statusMessage = "Ищем устройства поблизости...",
                            error = null
                        )
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { current ->
                        current.copy(
                            isDiscovering = false,
                            statusMessage = current.statusMessage
                                ?.takeUnless { it == "Ищем устройства поблизости..." }
                        )
                    }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    intent.extractBluetoothDevice()?.let(::addOrUpdateDevice)
                }

                // ACTION_FOUND часто прилетает раньше, чем устройство сообщило
                // своё имя — в первом кадре у нас name == null, и UI рисует
                // только MAC-адрес. Когда OS позже разрешит имя (через 100-
                // 500 мс), приходит ACTION_NAME_CHANGED — апдейтим запись по
                // адресу, имя подтянется в список без перезапуска поиска.
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    intent.extractBluetoothDevice()?.let(::addOrUpdateDevice)
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshState()
            }
        }
    }

    init {
        registerDiscoveryReceiver()
        refreshState()
    }

    fun refreshState() {
        val bluetoothAdapter = adapter
        _state.update { current ->
            current.copy(
                isSupported = bluetoothAdapter != null,
                isEnabled = bluetoothAdapter?.isEnabled == true,
                localDeviceName = resolveLocalDeviceName(bluetoothAdapter)
            )
        }

        if (bluetoothAdapter?.isEnabled == true) {
            // Bluetooth включился: сбрасываем устаревший «Включите Bluetooth»,
            // если он остался от прошлого выключенного состояния. Другие
            // сообщения оставляем — их пишут активные флоу, и их перетирать
            // нельзя. Список устройств сюда не подмешиваем — он наполняется
            // только при активном discovery через ACTION_FOUND.
            _state.update { current ->
                if (current.statusMessage == "Включите Bluetooth") {
                    current.copy(statusMessage = null)
                } else current
            }
        } else {
            stopAll()
            _state.update {
                it.copy(
                    devices = emptyList(),
                    connectedDeviceName = null,
                    statusMessage = "Включите Bluetooth",
                    error = null
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        android.util.Log.d(
            TAG,
            "startDiscovery: adapter=${adapter != null} enabled=${adapter?.isEnabled}" +
                " receivingActive=${receiveJob?.isActive == true}" +
                " connectingActive=${connectJob?.isActive == true}"
        )
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        // Если уже идёт активная передача (хост→гость) — не дёргаем discovery,
        // иначе `connectJob?.cancel()` ниже убьёт коннект посреди handshake'а.
        // Auto-restart в `RelayScreen.LaunchedEffect` гейтится по isConnecting/
        // isTransferring, но между `sendTrip()` (synchronous) и моментом, когда
        // coroutine выставит isConnecting=true, есть окно ~20мс — именно сюда
        // приходит auto-start и убивал коннект до фикса.
        if (connectJob?.isActive == true) {
            android.util.Log.d(TAG, "startDiscovery: skipped — connect in flight")
            return
        }

        // Хост — RFCOMM-client. Гасим возможный зависший receive — мы либо
        // ищем, либо принимаем. connectJob НЕ трогаем (см. guard выше).
        stopReceiving()
        // Devices НЕ очищаем при auto-restart: список заполнялся ACTION_FOUND'ом
        // и paired-but-not-nearby устройства туда не попадают (broadcast только
        // для текущих в эфире). Постоянный сброс в []  каждые 12-15с при
        // авто-перезапуске discovery даёт UI-мерцание «список → 0 → список».
        // Сейчас оставляем как есть: новые ACTION_FOUND'ы upsert'ятся через
        // mergeDevices, стейл'ные просто пересоздадутся.

        runCatching {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
        }.onFailure { error ->
            android.util.Log.w(TAG, "startDiscovery: failed", error)
            setError("Не удалось запустить поиск устройств")
        }.onSuccess { started ->
            android.util.Log.d(TAG, "startDiscovery: result=$started")
            if (!started) {
                setError("Не удалось запустить поиск устройств")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val bluetoothAdapter = adapter ?: return
        runCatching {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
        _state.update { current ->
            current.copy(
                isDiscovering = false,
                statusMessage = current.statusMessage
                    ?.takeUnless { it == "Ищем устройства поблизости..." }
            )
        }
    }

    /**
     * Гость зовёт ОДИН раз при входе на экран «Принять по Bluetooth».
     * Открывает RFCOMM server socket, в бесконечном цикле accept'ит входящие
     * коннекты. Для каждого:
     *   1) читает INTENT-фрейм (sender + trip),
     *   2) дёргает `consentProvider(intent)` — UI показывает «Принять?»
     *      и suspend'ится до тапа/таймаута,
     *   3) шлёт CONSENT-фрейм с accepted+guestUserId+guestDisplayName,
     *   4) если accepted=true, читает PACKAGE и эмитит наверх — VM
     *      применяет и зовёт acknowledgeIncomingTransfer (ack уже здесь
     *      пишется на сокет внутри `writeAckResponse`).
     */
    @SuppressLint("MissingPermission")
    fun startReceiving(
        guestUserId: String,
        guestDisplayName: String,
        consentProvider: suspend (IncomingTransferIntent) -> Boolean
    ) {
        android.util.Log.d(
            TAG,
            "startReceiving: adapter=${adapter != null} enabled=${adapter?.isEnabled}" +
                " guestUserId=$guestUserId"
        )
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        stopDiscovery()
        stopReceiving()
        connectJob?.cancel()
        connectJob = null
        closeClientSocket()

        receiveJob = scope.launch {
            try {
                val listeningSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    SERVICE_NAME,
                    SERVICE_UUID
                )
                serverSocket = listeningSocket
                _state.update {
                    it.copy(
                        isHosting = true,
                        isTransferring = false,
                        connectedDeviceName = null,
                        statusMessage = "Ожидание поездки от другого устройства...",
                        error = null
                    )
                }

                while (isActive) {
                    val socket = listeningSocket.accept() ?: continue
                    val remoteBtName = socket.remoteDevice.displayName()
                    android.util.Log.d(TAG, "startReceiving: accepted from $remoteBtName")
                    handleIncomingConnection(
                        socket = socket,
                        remoteBtName = remoteBtName,
                        guestUserId = guestUserId,
                        guestDisplayName = guestDisplayName,
                        consentProvider = consentProvider
                    )
                }
            } catch (_: IOException) {
                // Ожидаемое завершение при stopReceiving/выключении Bluetooth.
            } catch (_: SecurityException) {
                setError("Нет доступа к Bluetooth. Проверьте разрешения.")
            } finally {
                closeServerSocket()
                _state.update { current ->
                    current.copy(
                        isHosting = false,
                        isTransferring = false,
                        connectedDeviceName = null,
                        statusMessage = current.statusMessage
                            ?.takeUnless { it == "Ожидание поездки от другого устройства..." }
                    )
                }
            }
        }
    }

    /**
     * Обрабатывает один входящий RFCOMM-коннект: INTENT → consent → PACKAGE
     * → эмит наверх. ACK пишется отдельно через `acknowledgeIncomingTransfer`,
     * когда VM применит пакет и решит итог. Выделено в отдельную функцию,
     * чтобы исключения внутри одного коннекта не валили весь accept()-цикл.
     */
    private suspend fun handleIncomingConnection(
        socket: BluetoothSocket,
        remoteBtName: String,
        guestUserId: String,
        guestDisplayName: String,
        consentProvider: suspend (IncomingTransferIntent) -> Boolean
    ) {
        try {
            val intent = readIntent(socket, remoteBtName)
            android.util.Log.d(
                TAG,
                "handleIncomingConnection: intent senderUserId=${intent.senderUserId}" +
                    " tripId=${intent.tripId} tripName=${intent.tripName}"
            )
            _state.update {
                it.copy(
                    isTransferring = true,
                    connectedDeviceName = intent.senderDisplayName.ifBlank { remoteBtName },
                    statusMessage = "Запрос от ${intent.senderDisplayName.ifBlank { remoteBtName }}...",
                    error = null
                )
            }

            val accepted = runCatching { consentProvider(intent) }.getOrElse { false }
            android.util.Log.d(TAG, "handleIncomingConnection: consent=$accepted")
            sendConsent(socket, accepted, guestUserId, guestDisplayName)
            if (!accepted) {
                closeSocket(socket)
                _state.update {
                    it.copy(
                        isTransferring = false,
                        connectedDeviceName = null,
                        statusMessage = "Ожидание поездки от другого устройства..."
                    )
                }
                return
            }

            _state.update {
                it.copy(
                    statusMessage = "Принимаем поездку от ${intent.senderDisplayName.ifBlank { remoteBtName }}..."
                )
            }
            val payload = readPackage(socket, intent.tripId)
            val transferId = UUID.randomUUID().toString()
            activeIncomingSockets[transferId] = socket
            _incomingPayloads.emit(
                IncomingBluetoothRelayPayload(
                    transferId = transferId,
                    payload = payload,
                    deviceName = intent.senderDisplayName.ifBlank { remoteBtName }
                )
            )
        } catch (error: IOException) {
            android.util.Log.w(TAG, "handleIncomingConnection: io error ${error.message}")
            closeSocket(socket)
            _state.update {
                it.copy(
                    isTransferring = false,
                    connectedDeviceName = null,
                    statusMessage = "Ожидание поездки от другого устройства...",
                    error = error.message ?: "Не удалось принять пакет"
                )
            }
        } catch (_: SecurityException) {
            closeSocket(socket)
            setError("Нет доступа к Bluetooth. Проверьте разрешения.")
        }
    }

    fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
        closeServerSocket()
        _state.update { current ->
            current.copy(
                isHosting = false,
                isTransferring = false,
                connectedDeviceName = null,
                statusMessage = current.statusMessage
                    ?.takeUnless { it == "Ожидание поездки от другого устройства..." }
            )
        }
    }

    /**
     * Хост зовёт когда тапает пира в кластере. Открывает RFCOMM client socket,
     * шлёт INTENT, ждёт CONSENT. Если consent=accepted, дёргает
     * `onGuestIdentified(guestUserId, guestDisplayName)` для записи гостя
     * в Participant'ы, потом строит payload через `packageProvider`, шифрует
     * и шлёт PACKAGE, ждёт ACK. Успех/провал отражается в state и outgoingAcks.
     */
    @SuppressLint("MissingPermission")
    fun sendTrip(
        address: String,
        tripId: String,
        tripName: String,
        senderUserId: String,
        senderDisplayName: String,
        onGuestIdentified: suspend (guestUserId: String, guestDisplayName: String) -> Unit,
        packageProvider: suspend () -> String
    ) {
        android.util.Log.d(
            TAG,
            "sendTrip: address=$address tripId=$tripId tripName=$tripName" +
                " senderUserId=$senderUserId senderName=$senderDisplayName"
        )
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        stopDiscovery()
        stopReceiving()
        connectJob?.cancel()
        closeClientSocket()

        // СИНХРОННО выставляем isConnecting/isTransferring=true ДО `scope.launch`.
        // Без этого LaunchedEffect в RelayScreen видит «всё false» в окне между
        // sendTrip() и моментом, когда coroutine стартанул и выставил состояние,
        // и в этот ~20мс зазор зовёт startDiscovery, который cancel'ит свежий
        // connectJob. Также сразу пишем connectedDeviceName/statusMessage —
        // юзер видит «Подключаемся к POCO M4 Pro...» сразу после тапа.
        val initialRemoteName = runCatching { bluetoothAdapter.getRemoteDevice(address).displayName() }
            .getOrNull() ?: address
        _state.update {
            it.copy(
                isConnecting = true,
                isTransferring = true,
                connectedDeviceName = initialRemoteName,
                statusMessage = "Подключаемся к $initialRemoteName...",
                error = null
            )
        }

        connectJob = scope.launch {
            val device = runCatching { bluetoothAdapter.getRemoteDevice(address) }
                .getOrElse {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false,
                            connectedDeviceName = null
                        )
                    }
                    setError("Не удалось найти выбранное устройство")
                    return@launch
                }
            val remoteName = device.displayName()

            val socket = connectWithRetry(device, remoteName) ?: run {
                // RFCOMM иногда закрывает сокет сразу за ACK'ом — если только
                // что мы видели APPLIED, "Не удалось подключиться..." это шум.
                val ackJustHappened =
                    System.currentTimeMillis() - lastSuccessfulAckAt < 3000
                if (ackJustHappened) {
                    android.util.Log.d(
                        TAG,
                        "suppressed post-ack connectWithRetry failure for $remoteName"
                    )
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false,
                            connectedDeviceName = null,
                            error = "Не удалось подключиться к $remoteName. Попробуйте ещё раз."
                        )
                    }
                }
                return@launch
            }
            clientSocket = socket

            // Внутри coroutine'ы пометим момент, когда основной обмен
            // завершён (ACK получен от гостя). После этого любой IOException —
            // это «socket закрылся после успеха», такой сценарий не должен
            // превращаться в красный баннер.
            var transferCompleted = false

            try {
                _state.update {
                    it.copy(statusMessage = "Отправляем запрос $remoteName...")
                }
                sendIntent(
                    socket = socket,
                    senderUserId = senderUserId,
                    senderDisplayName = senderDisplayName,
                    tripId = tripId,
                    tripName = tripName
                )

                _state.update {
                    it.copy(statusMessage = "Ждём подтверждения у $remoteName...")
                }
                val consent = readConsent(socket)
                android.util.Log.d(
                    TAG,
                    "sendTrip: consent accepted=${consent.accepted} guestUserId=${consent.guestUserId}"
                )
                if (!consent.accepted) {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false,
                            connectedDeviceName = null,
                            error = "$remoteName отклонил передачу"
                        )
                    }
                    closeSocket(socket)
                    if (clientSocket == socket) {
                        clientSocket = null
                    }
                    return@launch
                }

                // Записываем гостя в Participant'ы ДО сборки пакета — тогда
                // в пакете уже будет правильный список участников, и обе
                // стороны после ack'а увидят одинаковый набор.
                if (consent.guestUserId.isNotBlank()) {
                    runCatching {
                        onGuestIdentified(consent.guestUserId, consent.guestDisplayName)
                    }.onFailure {
                        android.util.Log.w(
                            TAG,
                            "sendTrip: onGuestIdentified failed: ${it.message}"
                        )
                    }
                }

                _state.update {
                    it.copy(statusMessage = "Отправляем поездку $remoteName...")
                }
                val payload = runCatching { packageProvider() }
                    .getOrElse { error ->
                        throw IOException(
                            "Не удалось подготовить пакет синхронизации",
                            error
                        )
                    }
                writePackage(socket, payload, tripId)

                _state.update {
                    it.copy(statusMessage = "Ждём подтверждения у $remoteName...")
                }
                val ack = readAck(socket)
                if (ack.status == BluetoothRelayAckStatus.APPLIED ||
                    ack.status == BluetoothRelayAckStatus.DUPLICATE
                ) {
                    lastSuccessfulAckAt = System.currentTimeMillis()
                    _outgoingAcks.tryEmit(
                        BluetoothRelayOutgoingAck(
                            status = ack.status,
                            deviceName = remoteName
                        )
                    )
                    android.util.Log.d(
                        TAG,
                        "sendTrip: received APPLIED/DUPLICATE ack from $remoteName" +
                            " ts=$lastSuccessfulAckAt"
                    )
                }
                transferCompleted = true
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isTransferring = false,
                        connectedDeviceName = remoteName,
                        statusMessage = ack.toStatusMessage(remoteName),
                        error = ack.toErrorMessage()
                    )
                }
            } catch (error: IOException) {
                closeSocket(socket)
                if (clientSocket == socket) {
                    clientSocket = null
                }
                val ackJustHappened =
                    System.currentTimeMillis() - lastSuccessfulAckAt < 3000
                if (transferCompleted || ackJustHappened) {
                    android.util.Log.d(
                        TAG,
                        "suppressed post-success sendTrip IOException: ${error.message}"
                    )
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isTransferring = false,
                            connectedDeviceName = null,
                            error = error.message ?: "Не удалось отправить пакет $remoteName"
                        )
                    }
                }
            } catch (_: SecurityException) {
                closeSocket(socket)
                if (clientSocket == socket) {
                    clientSocket = null
                }
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isTransferring = false,
                        connectedDeviceName = null,
                        error = "Нет доступа к Bluetooth. Проверьте разрешения."
                    )
                }
            } finally {
                closeSocket(socket)
                if (clientSocket == socket) {
                    clientSocket = null
                }
            }
        }
    }

    fun acknowledgeIncomingTransfer(
        transferId: String,
        status: BluetoothRelayAckStatus,
        message: String? = null
    ) {
        val socket = activeIncomingSockets.remove(transferId) ?: return
        scope.launch {
            // Запомним «совершён успешный обмен» ещё ДО записи ACK, чтобы даже
            // если writeAckResponse упадёт с IOException (POCO закрыл сокет
            // одновременно с финальным flush'ем) — не выпадал баннер ошибки.
            if (status == BluetoothRelayAckStatus.APPLIED ||
                status == BluetoothRelayAckStatus.DUPLICATE
            ) {
                lastSuccessfulAckAt = System.currentTimeMillis()
                _outgoingAcks.tryEmit(
                    BluetoothRelayOutgoingAck(
                        status = status,
                        deviceName = _state.value.connectedDeviceName
                    )
                )
                android.util.Log.d(
                    TAG,
                    "guest wrote APPLIED/DUPLICATE ack ts=$lastSuccessfulAckAt" +
                        " peer=${_state.value.connectedDeviceName}"
                )
            }
            runCatching {
                writeAckResponse(socket, status, message)
            }.onFailure {
                // Если только что мы зафиксировали успех — не валим UI ошибкой
                // отправки подтверждения; принимающая сторона уже всё применила.
                val ackJustHappened =
                    System.currentTimeMillis() - lastSuccessfulAckAt < 3000
                if (!ackJustHappened) {
                    setError("Не удалось отправить подтверждение синхронизации")
                } else {
                    android.util.Log.d(
                        TAG,
                        "suppressed post-success ack-write failure: ${it.message}"
                    )
                }
            }.also {
                closeSocket(socket)
                _state.update { current ->
                    current.copy(
                        isTransferring = false,
                        statusMessage = when (status) {
                            BluetoothRelayAckStatus.APPLIED -> "Пакет успешно применён"
                            BluetoothRelayAckStatus.DUPLICATE -> "Этот пакет уже был импортирован"
                            BluetoothRelayAckStatus.FAILED -> "Синхронизация завершилась ошибкой"
                        },
                        error = if (status == BluetoothRelayAckStatus.FAILED) {
                            message ?: current.error
                        } else {
                            current.error
                        }
                    )
                }
            }
        }
    }

    fun stopAll() {
        stopDiscovery()
        stopReceiving()
        connectJob?.cancel()
        connectJob = null
        closeClientSocket()
        closeIncomingSockets()
        _state.update {
            it.copy(
                isConnecting = false,
                isTransferring = false,
                connectedDeviceName = null
            )
        }
    }

    fun dispose() {
        stopAll()
        if (receiverRegistered) {
            appContext.unregisterReceiver(discoveryReceiver)
            receiverRegistered = false
        }
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun connectWithRetry(
        device: BluetoothDevice,
        remoteName: String
    ): BluetoothSocket? {
        val attempts = listOf(
            "Подключаемся к $remoteName...",
            "Повторная попытка подключения к $remoteName..."
        )

        attempts.forEachIndexed { index, status ->
            _state.update {
                it.copy(statusMessage = status)
            }
            val socket = if (index == 0) {
                device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
            } else {
                device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            }

            runCatching {
                socket.connect()
            }.onSuccess {
                return socket
            }.onFailure {
                closeSocket(socket)
            }
        }
        return null
    }

    private fun addOrUpdateDevice(device: BluetoothDevice) {
        val relayDevice = device.toRelayDevice()
        android.util.Log.d(
            TAG,
            "addOrUpdateDevice: name=${relayDevice.name} address=${relayDevice.address}" +
                " bonded=${relayDevice.isBonded}"
        )
        _state.update { current ->
            current.copy(devices = mergeDevices(current.devices, listOf(relayDevice)))
        }
    }

    private fun mergeDevices(
        current: List<BluetoothRelayDevice>,
        incoming: List<BluetoothRelayDevice>
    ): List<BluetoothRelayDevice> {
        val byAddress = LinkedHashMap<String, BluetoothRelayDevice>()
        current.forEach { byAddress[it.address] = it }
        incoming.forEach { newDevice ->
            val existing = byAddress[newDevice.address]
            // Если входящая запись пришла только с MAC'ом (displayName ==
            // address), а у нас уже есть та же запись с реальным именем —
            // имя не теряем.
            val newIsMacOnly = newDevice.name == newDevice.address
            val existingHasName = existing != null && existing.name != existing.address
            byAddress[newDevice.address] = if (newIsMacOnly && existingHasName) {
                existing!!.copy(isBonded = newDevice.isBonded)
            } else {
                newDevice
            }
        }
        return byAddress.values.toList()
    }

    private fun registerDiscoveryReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // ACTION_FOUND / ACTION_NAME_CHANGED / ACTION_STATE_CHANGED шлёт
            // системный процесс `com.android.bluetooth` (uid=1002). С флагом
            // RECEIVER_NOT_EXPORTED Android 13+ глушит эти broadcast'ы как
            // «Exported Denial» — UI запускает discovery, BtRelay видит
            // result=true, но ни одно найденное устройство до нас не доходит.
            appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(discoveryReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private fun resolveLocalDeviceName(bluetoothAdapter: BluetoothAdapter?): String? {
        if (bluetoothAdapter == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching { bluetoothAdapter.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun closeServerSocket() {
        serverSocket?.let(::closeQuietly)
        serverSocket = null
    }

    private fun closeClientSocket() {
        clientSocket?.let(::closeSocket)
        clientSocket = null
    }

    private fun closeIncomingSockets() {
        activeIncomingSockets.values.forEach(::closeSocket)
        activeIncomingSockets.clear()
    }

    // region wire protocol v7

    /** Хост → гость, после connect: id+имя отправителя, id+имя поездки. */
    private fun sendIntent(
        socket: BluetoothSocket,
        senderUserId: String,
        senderDisplayName: String,
        tripId: String,
        tripName: String
    ) {
        val output = DataOutputStream(socket.outputStream)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_INTENT)
        output.writeUTF(senderUserId)
        output.writeUTF(senderDisplayName)
        output.writeUTF(tripId)
        output.writeUTF(tripName)
        output.flush()
    }

    /** Гость читает INTENT-фрейм. Если магия/версия не совпали — IOException. */
    private fun readIntent(
        socket: BluetoothSocket,
        remoteBtName: String
    ): IncomingTransferIntent {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_INTENT) {
            throw IOException("Несовместимый формат запроса Bluetooth relay")
        }
        val senderUserId = input.readUTF()
        val senderDisplayName = input.readUTF()
        val tripId = input.readUTF()
        val tripName = input.readUTF()
        return IncomingTransferIntent(
            senderUserId = senderUserId,
            senderDisplayName = senderDisplayName,
            senderBtName = remoteBtName,
            tripId = tripId,
            tripName = tripName
        )
    }

    /**
     * Гость → хост: accepted + guestUserId + guestDisplayName. Хост по этим
     * полям заведёт Participant'а перед buildPackage.
     */
    private fun sendConsent(
        socket: BluetoothSocket,
        accepted: Boolean,
        guestUserId: String,
        guestDisplayName: String
    ) {
        val output = DataOutputStream(socket.outputStream)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_CONSENT)
        output.writeBoolean(accepted)
        output.writeUTF(guestUserId)
        output.writeUTF(guestDisplayName)
        output.flush()
    }

    private fun readConsent(socket: BluetoothSocket): ConsentResponse {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_CONSENT) {
            throw IOException("Несовместимый формат ответа Bluetooth relay")
        }
        val accepted = input.readBoolean()
        val guestUserId = input.readUTF()
        val guestDisplayName = input.readUTF()
        return ConsentResponse(accepted, guestUserId, guestDisplayName)
    }

    /** Хост → гость: зашифрованный пакет поездки (после consent=accepted). */
    private fun writePackage(socket: BluetoothSocket, payload: String, tripId: String) {
        val output = DataOutputStream(socket.outputStream)
        val key = RelayEncryption.deriveKey(tripId)
        val encrypted = RelayEncryption.encrypt(payload.toByteArray(Charsets.UTF_8), key)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_PACKAGE)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.flush()
    }

    private fun readPackage(socket: BluetoothSocket, tripId: String): String {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_PACKAGE) {
            throw IOException("Несовместимый формат пакета Bluetooth relay")
        }
        val length = input.readInt()
        if (length <= 0) {
            throw IOException("Пустой пакет синхронизации")
        }
        val encrypted = ByteArray(length)
        input.readFully(encrypted)
        val key = RelayEncryption.deriveKey(tripId)
        val decrypted = RelayEncryption.decrypt(encrypted, key)
        return String(decrypted, Charsets.UTF_8)
    }

    /** Гость → хост: ack APPLIED/DUPLICATE/FAILED + опциональное сообщение. */
    private fun writeAckResponse(
        socket: BluetoothSocket,
        status: BluetoothRelayAckStatus,
        message: String?
    ) {
        val output = DataOutputStream(socket.outputStream)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_RESPONSE)
        output.writeUTF(status.name)
        output.writeUTF(message.orEmpty())
        output.flush()
    }

    private fun readAck(socket: BluetoothSocket): BluetoothRelayAckStatusMessage {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_RESPONSE) {
            throw IOException("Получен некорректный ответ синхронизации")
        }
        val status = runCatching { BluetoothRelayAckStatus.valueOf(input.readUTF()) }
            .getOrElse { throw IOException("Неизвестный статус подтверждения") }
        val message = input.readUTF().ifBlank { null }
        return BluetoothRelayAckStatusMessage(status, message)
    }

    // endregion

    private fun closeQuietly(socket: BluetoothServerSocket) {
        runCatching { socket.close() }
    }

    private fun closeSocket(socket: BluetoothSocket) {
        runCatching { socket.close() }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toRelayDevice(): BluetoothRelayDevice {
        return BluetoothRelayDevice(
            name = displayName(),
            address = address.orEmpty(),
            isBonded = bondState == BluetoothDevice.BOND_BONDED
        )
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.displayName(): String {
        return name?.takeIf { it.isNotBlank() } ?: address.orEmpty()
    }

    private fun Intent.extractBluetoothDevice(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private data class ConsentResponse(
        val accepted: Boolean,
        val guestUserId: String,
        val guestDisplayName: String
    )

    private data class BluetoothRelayAckStatusMessage(
        val status: BluetoothRelayAckStatus,
        val message: String?
    ) {
        fun toStatusMessage(remoteName: String): String {
            return when (status) {
                BluetoothRelayAckStatus.APPLIED ->
                    message ?: "Пакет успешно применён на $remoteName"

                BluetoothRelayAckStatus.DUPLICATE ->
                    message ?: "На $remoteName этот пакет уже был импортирован"

                BluetoothRelayAckStatus.FAILED ->
                    "Передача завершилась ошибкой на стороне $remoteName"
            }
        }

        fun toErrorMessage(): String? {
            return if (status == BluetoothRelayAckStatus.FAILED) {
                message ?: "Удалённое устройство не смогло применить пакет"
            } else {
                null
            }
        }
    }

    companion object {
        private const val TAG = "BtRelay"
        private val SERVICE_UUID: UUID =
            UUID.fromString("4cf14d9b-cb79-4b27-90df-f69a8b1450c5")
        private const val SERVICE_NAME = "TripFlowRelay"
        private const val PROTOCOL_MAGIC = "TRILOO_BT"
        // v7: инвертированы роли — хост (владелец поездки) теперь client,
        // гость — server. Протокол:
        //   1) host → guest: FRAME_INTENT (senderUserId, senderName, tripId, tripName)
        //   2) guest → host: FRAME_CONSENT (accepted, guestUserId, guestDisplayName)
        //   3) host → guest: FRAME_PACKAGE (encrypted body)
        //   4) guest → host: FRAME_RESPONSE (ack status + message)
        private const val PROTOCOL_VERSION = 7
        private const val FRAME_INTENT = "INTENT"
        private const val FRAME_CONSENT = "CONSENT"
        private const val FRAME_PACKAGE = "PACKAGE"
        private const val FRAME_RESPONSE = "RESPONSE"
    }
}
