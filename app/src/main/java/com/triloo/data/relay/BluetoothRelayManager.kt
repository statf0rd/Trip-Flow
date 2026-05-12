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

data class BluetoothRelaySyncRequest(
    val tripId: String,
    val knownChangeCursor: Long,
    val hasCompleteSnapshot: Boolean,
    val deviceName: String
)

enum class BluetoothRelayAckStatus {
    APPLIED,
    DUPLICATE,
    FAILED
}

/**
 * Передаёт relay-пакеты между устройствами по Bluetooth Classic RFCOMM
 * и ждёт подтверждение результата применения на принимающей стороне.
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

    private val activeIncomingSockets = ConcurrentHashMap<String, BluetoothSocket>()

    private var receiverRegistered = false
    private var hostJob: Job? = null
    private var connectJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null

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
            // сообщения (Hosting/Discovery/...) оставляем — их пишут активные
            // флоу, и их перетирать нельзя. Список устройств сюда не
            // подмешиваем — он наполняется только при активном discovery
            // через ACTION_FOUND, чтобы UI не путал юзера старыми спаренными
            // наушниками/часами.
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
                " hostingActive=${hostJob?.isActive == true}" +
                " connectingActive=${connectJob?.isActive == true}"
        )
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        stopHosting()
        connectJob?.cancel()
        connectJob = null
        closeClientSocket()
        // Перед каждым новым скан-проходом обнуляем список устройств — раньше
        // тут досыпались спаренные наушники/часы, и юзер тапал по ним вместо
        // нужного телефона. Реальные кандидаты прилетят через ACTION_FOUND.
        _state.update { it.copy(devices = emptyList()) }

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

    @SuppressLint("MissingPermission")
    fun startHosting(
        // Колбэк-провайдер пакета. Получает sync-request гостя и его пакет
        // (или null — гость без локальной копии поездки). Должен:
        // 1) применить guestPackage в локальную БД хоста (если он не null);
        // 2) собрать и вернуть свой пакет — уже с учётом смерженных
        //    изменений гостя.
        payloadProvider: suspend (BluetoothRelaySyncRequest, guestPackage: String?) -> String
    ) {
        android.util.Log.d(TAG, "startHosting: adapter=${adapter != null} enabled=${adapter?.isEnabled}")
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        stopDiscovery()
        stopHosting()
        connectJob?.cancel()
        connectJob = null
        closeClientSocket()

        hostJob = scope.launch {
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
                        statusMessage = "Ожидание подключения по Bluetooth...",
                        error = null
                    )
                }

                while (isActive) {
                    val socket = listeningSocket.accept() ?: continue
                    val remoteName = socket.remoteDevice.displayName()
                    _state.update {
                        it.copy(
                            isTransferring = true,
                            connectedDeviceName = remoteName,
                            statusMessage = "Согласовываем пакет с $remoteName...",
                            error = null
                        )
                    }

                    runCatching {
                        sendPayloadAndAwaitAck(socket, remoteName, payloadProvider)
                    }.onSuccess { ack ->
                        _state.update {
                            it.copy(
                                isTransferring = false,
                                connectedDeviceName = remoteName,
                                statusMessage = ack.toStatusMessage(remoteName),
                                error = ack.toErrorMessage()
                            )
                        }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isTransferring = false,
                                connectedDeviceName = remoteName,
                                statusMessage = "Ожидание подключения по Bluetooth...",
                                error = error.message ?: "Не удалось отправить пакет"
                            )
                        }
                    }.also {
                        closeSocket(socket)
                    }
                }
            } catch (_: IOException) {
                // Ожидаемое завершение при stopHosting/выключении Bluetooth.
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
                            ?.takeUnless { it == "Ожидание подключения по Bluetooth..." }
                    )
                }
            }
        }
    }

    fun stopHosting() {
        hostJob?.cancel()
        hostJob = null
        closeServerSocket()
        _state.update { current ->
            current.copy(
                isHosting = false,
                isTransferring = false,
                connectedDeviceName = null,
                statusMessage = current.statusMessage
                    ?.takeUnless { it == "Ожидание подключения по Bluetooth..." }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        address: String,
        tripId: String,
        knownChangeCursor: Long,
        hasCompleteSnapshot: Boolean,
        // Опциональный провайдер пакета гостя. В режиме онбординга или когда у
        // гостя нет локальной копии поездки — возвращает null, и тогда после
        // sync-request шлётся маркер FRAME_NO_PACKAGE. В остальных случаях
        // гость сам строит свой снапшот через RelayRepository.buildPackage и
        // отдаёт его хосту до получения ответа.
        guestPayloadProvider: suspend () -> String? = { null }
    ) {
        android.util.Log.d(TAG, "connect: address=$address tripId=$tripId hasSnapshot=$hasCompleteSnapshot")
        val bluetoothAdapter = adapter ?: return setError("Bluetooth недоступен на этом устройстве")
        if (!bluetoothAdapter.isEnabled) {
            setError("Сначала включите Bluetooth")
            return
        }

        stopDiscovery()
        stopHosting()
        connectJob?.cancel()
        closeClientSocket()

        connectJob = scope.launch {
            val device = runCatching { bluetoothAdapter.getRemoteDevice(address) }
                .getOrElse {
                    setError("Не удалось найти выбранное устройство")
                    return@launch
                }
            val remoteName = device.displayName()
            _state.update {
                it.copy(
                    isConnecting = true,
                    isTransferring = true,
                    connectedDeviceName = remoteName,
                    statusMessage = "Подключаемся к $remoteName...",
                    error = null
                )
            }

            val socket = connectWithRetry(device, remoteName) ?: run {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isTransferring = false,
                        connectedDeviceName = null,
                        error = "Не удалось подключиться к $remoteName. Попробуйте ещё раз."
                    )
                }
                return@launch
            }
            clientSocket = socket

            try {
                _state.update {
                    it.copy(statusMessage = "Запрашиваем изменения у $remoteName...")
                }
                sendSyncRequest(
                    socket = socket,
                    tripId = tripId,
                    knownChangeCursor = knownChangeCursor,
                    hasCompleteSnapshot = hasCompleteSnapshot
                )
                // Двунаправленный обмен: после sync-request'а гость шлёт свой
                // снапшот (или NO_PACKAGE для онбординга), чтобы хост сначала
                // смерджил его, а потом вернул объединённую версию. Если
                // guestPayloadProvider бросит — отбиваем коннект, лучше упасть,
                // чем отдать неполный/некорректный пакет.
                val guestOutgoing = runCatching { guestPayloadProvider() }
                    .getOrElse { error ->
                        throw IOException(
                            "Не удалось подготовить локальный пакет для отправки",
                            error
                        )
                    }
                writeOutgoingPackage(socket, guestOutgoing, tripId)
                val payload = readIncomingPackage(socket, tripId)
                val transferId = UUID.randomUUID().toString()
                activeIncomingSockets[transferId] = socket
                _incomingPayloads.emit(
                    IncomingBluetoothRelayPayload(
                        transferId = transferId,
                        payload = payload,
                        deviceName = remoteName
                    )
                )
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isTransferring = true,
                        connectedDeviceName = remoteName,
                        statusMessage = "Пакет получен от $remoteName. Применяем изменения..."
                    )
                }
            } catch (error: IOException) {
                closeSocket(socket)
                if (clientSocket == socket) {
                    clientSocket = null
                }
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isTransferring = false,
                        connectedDeviceName = null,
                        error = error.message ?: "Не удалось получить пакет от $remoteName"
                    )
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
            runCatching {
                writeAckResponse(socket, status, message)
            }.onFailure {
                setError("Не удалось отправить подтверждение синхронизации")
            }.also {
                closeSocket(socket)
                if (clientSocket == socket) {
                    clientSocket = null
                }
                _state.update { current ->
                    current.copy(
                        isConnecting = false,
                        isTransferring = false,
                        connectedDeviceName = null,
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
        stopHosting()
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
            // имя не теряем. Это важно для ACTION_FOUND/ACTION_NAME_CHANGED:
            // часто первое событие приходит без имени, второе уже с именем,
            // но в редких сборках одна сторона может прислать пустоту назад.
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
            // result=true, но ни одно найденное устройство до нас не доходит,
            // и пользователь видит «кнопка не работает».
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

    private suspend fun sendPayloadAndAwaitAck(
        socket: BluetoothSocket,
        remoteName: String,
        payloadProvider: suspend (BluetoothRelaySyncRequest, guestPackage: String?) -> String
    ): BluetoothRelayAckStatusMessage {
        val syncRequest = readSyncRequest(socket, remoteName)
        // Сразу за sync-request гость присылает свой пакет (или маркер «нет»).
        // Ключ шифрования общий — derive по tripId из sync-request'а; на
        // онбординге tripId пустой, ключ тоже общий, маркер NO_PACKAGE
        // обходит расшифровку.
        val guestPackage = readOptionalIncomingPackage(socket, syncRequest.tripId)
        val payload = payloadProvider(syncRequest, guestPackage)
        val output = DataOutputStream(socket.outputStream)
        val input = DataInputStream(socket.inputStream)

        val key = RelayEncryption.deriveKey(syncRequest.tripId)
        val encrypted = RelayEncryption.encrypt(payload.toByteArray(Charsets.UTF_8), key)

        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_PACKAGE)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.flush()

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

    private fun sendSyncRequest(
        socket: BluetoothSocket,
        tripId: String,
        knownChangeCursor: Long,
        hasCompleteSnapshot: Boolean
    ) {
        val output = DataOutputStream(socket.outputStream)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeUTF(FRAME_SYNC_REQUEST)
        output.writeUTF(tripId)
        output.writeBoolean(hasCompleteSnapshot)
        output.writeLong(knownChangeCursor)
        output.flush()
    }

    private fun readSyncRequest(
        socket: BluetoothSocket,
        remoteName: String
    ): BluetoothRelaySyncRequest {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_SYNC_REQUEST) {
            throw IOException("Несовместимый формат запроса Bluetooth relay")
        }
        return BluetoothRelaySyncRequest(
            tripId = input.readUTF(),
            hasCompleteSnapshot = input.readBoolean(),
            knownChangeCursor = input.readLong(),
            deviceName = remoteName
        )
    }

    private fun readIncomingPackage(socket: BluetoothSocket, tripId: String): String {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION || frame != FRAME_PACKAGE) {
            throw IOException("Несовместимый формат Bluetooth relay")
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

    /**
     * Читает optional-frame пакета: либо FRAME_PACKAGE с зашифрованным телом
     * (после расшифровки возвращается plain JSON), либо FRAME_NO_PACKAGE без
     * тела — тогда возвращаем null. Используется host'ом для входящего пакета
     * гостя в двусторонней синхронизации.
     */
    private fun readOptionalIncomingPackage(
        socket: BluetoothSocket,
        tripId: String
    ): String? {
        val input = DataInputStream(socket.inputStream)
        val magic = input.readUTF()
        val version = input.readInt()
        val frame = input.readUTF()
        if (magic != PROTOCOL_MAGIC || version != PROTOCOL_VERSION) {
            throw IOException("Несовместимый формат Bluetooth relay")
        }
        return when (frame) {
            FRAME_NO_PACKAGE -> null
            FRAME_PACKAGE -> {
                val length = input.readInt()
                if (length <= 0) throw IOException("Пустой пакет синхронизации")
                val encrypted = ByteArray(length)
                input.readFully(encrypted)
                val key = RelayEncryption.deriveKey(tripId)
                val decrypted = RelayEncryption.decrypt(encrypted, key)
                String(decrypted, Charsets.UTF_8)
            }
            else -> throw IOException("Несовместимый формат Bluetooth relay")
        }
    }

    /**
     * Пишет в socket optional-frame пакета: либо FRAME_PACKAGE с шифровкой,
     * либо FRAME_NO_PACKAGE без тела (для онбординга/гостей без локальной
     * копии). Парный к readOptionalIncomingPackage.
     */
    private fun writeOutgoingPackage(
        socket: BluetoothSocket,
        payload: String?,
        tripId: String
    ) {
        val output = DataOutputStream(socket.outputStream)
        output.writeUTF(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        if (payload == null) {
            output.writeUTF(FRAME_NO_PACKAGE)
        } else {
            output.writeUTF(FRAME_PACKAGE)
            val key = RelayEncryption.deriveKey(tripId)
            val encrypted = RelayEncryption.encrypt(payload.toByteArray(Charsets.UTF_8), key)
            output.writeInt(encrypted.size)
            output.write(encrypted)
        }
        output.flush()
    }

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
        // v5: добавили двусторонний обмен пакетами. Гость после
        // FRAME_SYNC_REQUEST шлёт либо FRAME_PACKAGE со своим снапшотом,
        // либо FRAME_NO_PACKAGE (онбординг/нет локальной поездки). Хост
        // мерджит и шлёт свой пакет назад. Версия инкрементнута, чтобы
        // старые сборки сразу видели «несовместимый формат» вместо
        // зависания на чтении.
        private const val PROTOCOL_VERSION = 5
        private const val FRAME_SYNC_REQUEST = "SYNC_REQUEST"
        private const val FRAME_PACKAGE = "PACKAGE"
        private const val FRAME_NO_PACKAGE = "NO_PACKAGE"
        private const val FRAME_RESPONSE = "RESPONSE"
    }
}
