package com.triloo.ui.relay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.relay.BluetoothRelayDevice
import com.triloo.ui.theme.Error
import com.triloo.ui.theme.Slate100
import com.triloo.ui.theme.Slate500
import com.triloo.ui.theme.Slate700
import com.triloo.ui.theme.Slate800
import com.triloo.ui.theme.TealSecondary

private sealed interface PendingRelayAction {
    data object Host : PendingRelayAction
    data object Scan : PendingRelayAction
    data class Connect(val address: String) : PendingRelayAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(
    onNavigateBack: () -> Unit,
    viewModel: RelayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<PendingRelayAction?>(null) }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode > 0) {
            viewModel.startHosting()
        } else {
            viewModel.onDiscoverableRejected()
        }
    }

    fun runGrantedAction(action: PendingRelayAction) {
        executeRelayAction(
            action = action,
            context = context,
            uiState = uiState,
            viewModel = viewModel,
            onRequireDiscoverable = {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                    }
                )
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            pendingAction?.let { action ->
                pendingAction = null
                runGrantedAction(action)
            }
        } else {
            pendingAction = null
            viewModel.onBluetoothPermissionDenied()
        }
        viewModel.refreshBluetoothState()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBluetoothState()
        if (context.isBluetoothEnabled()) {
            pendingAction?.let { action ->
                pendingAction = null
                runGrantedAction(action)
            }
        } else {
            pendingAction = null
            viewModel.onBluetoothEnableRejected()
        }
    }

    fun requestOrRun(action: PendingRelayAction) {
        val permissions = bluetoothPermissions()
        if (!context.hasBluetoothPermissions()) {
            pendingAction = action
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions)
            }
            return
        }

        if (!context.isBluetoothEnabled()) {
            pendingAction = action
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        pendingAction = null
        runGrantedAction(action)
    }

    RelayContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refreshBluetoothState,
        onStartHosting = { requestOrRun(PendingRelayAction.Host) },
        onStopHosting = viewModel::stopHosting,
        onStartDiscovery = { requestOrRun(PendingRelayAction.Scan) },
        onStopDiscovery = viewModel::stopDiscovery,
        onConnect = { address -> requestOrRun(PendingRelayAction.Connect(address)) },
        onClearError = viewModel::clearError,
        onClearMergeResult = viewModel::clearMergeResult
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelayContent(
    uiState: RelayUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnect: (String) -> Unit,
    onClearError: () -> Unit,
    onClearMergeResult: () -> Unit
) {
    // В receive-only режиме (гость без поездки, экран открыт из «Группы»)
    // показываем сразу таб «Получить» и не даём переключиться на «Отправить»:
    // у гостя нет поездки, расшаривать нечего.
    var selectedTab by remember(uiState.isReceiveOnly) {
        mutableIntStateOf(if (uiState.isReceiveOnly) 1 else 0)
    }
    val errorMessage = uiState.syncError ?: uiState.transportError
    val isBusy = uiState.isConnecting || uiState.isTransferring || uiState.isApplyingPackage

    uiState.mergeResult?.let { mergeResult ->
        AlertDialog(
            onDismissRequest = onClearMergeResult,
            title = { Text("Синхронизация завершена") },
            text = {
                Text(
                    "Добавлено: ${mergeResult.inserted}\n" +
                        "Обновлено: ${mergeResult.updated}\n" +
                        "Удалено: ${mergeResult.deleted}"
                )
            },
            confirmButton = {
                TextButton(onClick = onClearMergeResult) {
                    Text("ОК")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bluetooth-синхронизация",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        uiState.trip?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Обновить"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // В receive-only TabRow прячем целиком — оставлять одну вкладку
            // «Получить» бессмысленно и шумно.
            if (!uiState.isReceiveOnly) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Отправить") },
                        icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Получить") },
                        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BluetoothEnvironmentCard(uiState = uiState)

                uiState.statusMessage?.let {
                    StatusCard(
                        title = "Статус",
                        body = it,
                        accent = if (uiState.isTransferring || uiState.isApplyingPackage) TealSecondary else MaterialTheme.colorScheme.primary
                    )
                }

                errorMessage?.let {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = Error.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Error
                            )
                            TextButton(
                                onClick = onClearError,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Скрыть")
                            }
                        }
                    }
                }

                when (selectedTab) {
                    0 -> SendTab(
                        uiState = uiState,
                        isBusy = isBusy,
                        onStartHosting = onStartHosting,
                        onStopHosting = onStopHosting
                    )

                    1 -> ReceiveTab(
                        uiState = uiState,
                        isBusy = isBusy,
                        onStartDiscovery = onStartDiscovery,
                        onStopDiscovery = onStopDiscovery,
                        onConnect = onConnect
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothEnvironmentCard(uiState: RelayUiState) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = uiState.localDeviceName ?: "Это устройство",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            !uiState.isBluetoothSupported -> "Bluetooth не поддерживается"
                            uiState.isBluetoothEnabled -> "Bluetooth включён"
                            else -> "Bluetooth выключен"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isBluetoothEnabled) TealSecondary else Slate500
                    )
                }
            }

            Text(
                text = "Первое соединение передаёт полный снимок поездки, дальше отправляются только изменения. Всё идёт напрямую по Bluetooth, без QR и без интернета.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate700
            )
        }
    }
}

@Composable
private fun SendTab(
    uiState: RelayUiState,
    isBusy: Boolean,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "1. Нажмите кнопку ниже.\n2. На втором устройстве откройте «Получить».\n3. Держите экран открытым до завершения передачи.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate800
            )

            if (uiState.connectedDeviceName != null && uiState.isTransferring) {
                StatusCard(
                    title = "Передача",
                    body = "Подключено устройство ${uiState.connectedDeviceName}",
                    accent = TealSecondary
                )
            }

            if (uiState.isHosting) {
                OutlinedButton(
                    onClick = onStopHosting,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isApplyingPackage
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Остановить ожидание")
                }
            } else {
                Button(
                    onClick = onStartHosting,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isBluetoothSupported && !isBusy
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Стать видимым и ждать подключение")
                }
            }
        }
    }
}

@Composable
private fun ReceiveTab(
    uiState: RelayUiState,
    isBusy: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnect: (String) -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Поиск покажет соседние или уже спаренные устройства. Выберите телефон, который сейчас ждёт подключение.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate800
            )

            if (uiState.isDiscovering) {
                OutlinedButton(
                    onClick = onStopDiscovery,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isApplyingPackage
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Остановить поиск")
                }
            } else {
                Button(
                    onClick = onStartDiscovery,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isBluetoothSupported && !isBusy
                ) {
                    Icon(Icons.Rounded.Devices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Найти устройства")
                }
            }
        }
    }

    if (uiState.devices.isEmpty()) {
        Surface(
            color = Slate100,
            shape = MaterialTheme.shapes.large
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (uiState.isDiscovering) {
                        "Ищем устройства поблизости..."
                    } else {
                        "Пока нет найденных устройств. Запустите поиск."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate700
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            uiState.devices.forEach { device ->
                DeviceCard(
                    device = device,
                    isBusy = isBusy,
                    onConnect = { onConnect(device.address) }
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothRelayDevice,
    isBusy: Boolean,
    onConnect: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
                if (device.isBonded) {
                    Surface(
                        color = TealSecondary.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Спарено",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TealSecondary
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onConnect,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Подключиться")
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    accent: androidx.compose.ui.graphics.Color
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate800
            )
        }
    }
}

private fun executeRelayAction(
    action: PendingRelayAction,
    context: Context,
    uiState: RelayUiState,
    viewModel: RelayViewModel,
    onRequireDiscoverable: () -> Unit
) {
    if (!uiState.isBluetoothSupported) return
    if (!context.isBluetoothEnabled()) return

    when (action) {
        PendingRelayAction.Host -> onRequireDiscoverable()
        PendingRelayAction.Scan -> viewModel.startDiscovery()
        is PendingRelayAction.Connect -> viewModel.connect(action.address)
    }
}

private fun bluetoothPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        else -> emptyArray()
    }
}

private fun Context.hasBluetoothPermissions(): Boolean {
    return bluetoothPermissions().all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED
    }
}

private fun Context.isBluetoothEnabled(): Boolean {
    return getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}
