package com.triloo.ui.relay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.Trip
import com.triloo.data.relay.BluetoothRelayDevice
import com.triloo.data.relay.IncomingTransferIntent
import com.triloo.ui.theme.CoralLight
import com.triloo.ui.theme.DarkBackground
import com.triloo.ui.theme.DarkBorder
import com.triloo.ui.theme.DarkSurface
import com.triloo.ui.theme.DarkTextPrimary
import com.triloo.ui.theme.DarkTextSecondary
import com.triloo.ui.theme.GoldenAccent
import com.triloo.ui.theme.GoldenLight
import com.triloo.ui.theme.Slate100
import com.triloo.ui.theme.Slate200
import com.triloo.ui.theme.Slate300
import com.triloo.ui.theme.Slate50
import com.triloo.ui.theme.Slate600
import com.triloo.ui.theme.Slate950
import com.triloo.ui.theme.TealLight
import com.triloo.ui.theme.TrilooTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

// Палитра аватарок: коралл, бирюза, золото и мягкий фиолет (см. мокапы 01/05).
// Эти оттенки — насыщенные mid-tone'ы, которые одинаково хорошо читаются и на
// светлом, и на тёмном фоне (фон аватарки + центральный круг, белая буква
// поверх). Не зависят от темы, поэтому остаются константами файла.
private val PeerAccentCoral = Color(0xFFF26A55)
private val PeerAccentTeal = Color(0xFF3FD7B7)
private val PeerAccentGold = Color(0xFFE7A856)
private val PeerAccentPurple = Color(0xFFB47AE0)
private val PeerPalette = listOf(PeerAccentCoral, PeerAccentTeal, PeerAccentGold, PeerAccentPurple)

/**
 * Палитра «эфирного» экрана, адаптированная под обе темы. Каждое поле
 * подобрано так, чтобы:
 *  • светлая тема — мягкие пастельные свечения и читабельный тёмный текст
 *    на белёсой подложке (фон Slate50 совпадает с общим shell-фоном);
 *  • тёмная тема — почти-чёрный фон 0D1117 и насыщенные глубокие свечения
 *    из исходного мокапа (HTML рефы — rgba 92,31,31 / 92,42,34 / 15,70,64 /
 *    90,68,22).
 *
 * Сборщик [sceneColors] возвращает готовую структуру в зависимости от
 * `isSystemInDarkTheme()`.
 */
private data class SceneColors(
    // Общая подложка экрана и карточки нижнего дока.
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    // Текстовые роли заголовков и подписей.
    val textPrimary: Color,
    val textSecondary: Color,
    // Пунктирные концентрические кольца в кластере.
    val dashedRing: Color,
    // Свечения сцен (см. SceneGlow).
    val glowCoralRed: Color,  // BtDisabled (верх + низ)
    val glowCoral: Color,     // Host/Guest активные сцены — тёплая «голова»
    val glowTeal: Color,      // Host/Guest + Completed — холодное пятно снизу
    val glowAmber: Color,     // IncomingTransfer — янтарь сверху
    // Центральный круг и иконка BT-перечёркнут.
    val disabledCenter: Color,
    val disabledIcon: Color,
    // Красный баннер ошибки.
    val errorBannerBg: Color,
    val errorBannerText: Color,
    val errorBannerDismiss: Color,
    // Маленькая «info/QR» иконка в шапке (поверх ничейного фона).
    val topBarIconBg: Color
)

@Composable
private fun sceneColors(): SceneColors {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        SceneColors(
            background = DarkBackground,
            cardBackground = DarkSurface.copy(alpha = 0.92f),
            cardBorder = DarkBorder.copy(alpha = 0.5f),
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            dashedRing = Color.White.copy(alpha = 0.10f),
            // Тёмные свечения — без изменений, как в HTML-мокапах.
            glowCoralRed = Color(0xFF5C1F1F),
            glowCoral = Color(0xFF5C2A22),
            glowTeal = Color(0xFF0F4640),
            glowAmber = Color(0xFF5A4416),
            disabledCenter = Color(0xFF2A2F36),
            disabledIcon = Color(0xFFA8AEB1),
            errorBannerBg = Color(0xFF5C1F1F).copy(alpha = 0.92f),
            errorBannerText = Color(0xFFFEE2E2),
            errorBannerDismiss = Color.White,
            topBarIconBg = Color.White.copy(alpha = 0.06f)
        )
    } else {
        SceneColors(
            background = Slate50,
            cardBackground = Color.White,
            cardBorder = Slate300,
            textPrimary = Slate950,
            textSecondary = Slate600,
            dashedRing = Slate950.copy(alpha = 0.10f),
            // Светлые свечения — пастельные тёплые пятна, чтобы поверх белого
            // не отдавало серой пылью; используем существующие *Light-токены.
            glowCoralRed = Color(0xFFFCA5A5).copy(alpha = 0.55f),
            glowCoral = CoralLight.copy(alpha = 0.45f),
            glowTeal = TealLight.copy(alpha = 0.40f),
            glowAmber = GoldenLight.copy(alpha = 0.40f),
            disabledCenter = Slate200,
            disabledIcon = Slate600,
            errorBannerBg = Color(0xFFFEE2E2),
            errorBannerText = Color(0xFF991B1B),
            errorBannerDismiss = Color(0xFF991B1B),
            topBarIconBg = Slate100
        )
    }
}

// Действия, требующие предварительной проверки permissions / BT-enable /
// discoverable. По завершении гейтов экран дёргает соответствующий VM-метод.
private sealed interface PendingRelayAction {
    // Гость: нужно стать discoverable + поднять RFCOMM-listener.
    data object Receive : PendingRelayAction
    // Хост: просто запустить discovery — discoverable не нужен.
    data object Send : PendingRelayAction
    // Хост тапнул пира в кластере — открываем коннект и шлём intent.
    data class SendTo(val address: String) : PendingRelayAction
}

@Composable
fun RelayScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (tripId: String) -> Unit,
    viewModel: RelayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<PendingRelayAction?>(null) }

    // Auto-navigate в TripDetails после успешного обмена. Срабатывает на ОБЕИХ
    // сторонах: гость использует `lastReceivedTripId` (заполняется в
    // applyIncomingPayload), хост — `lastHostSendCompletedAt` + локальный tripId.
    // Даём пользователю 1.5с полюбоваться сценой Completed.
    val receivedTripId = uiState.lastReceivedTripId
    LaunchedEffect(receivedTripId) {
        if (receivedTripId != null) {
            android.util.Log.d(
                "RelayScreen",
                "auto-navigate (guest) armed for tripId=$receivedTripId (delay 1500ms)"
            )
            delay(1500)
            android.util.Log.d("RelayScreen", "auto-navigating (guest) to trip $receivedTripId")
            onNavigateToTrip(receivedTripId)
            viewModel.consumeReceivedTrip()
        }
    }
    val hostCompletedAt = uiState.lastHostSendCompletedAt
    val hostTripId = uiState.trip?.id
    LaunchedEffect(hostCompletedAt, hostTripId, uiState.isReceiveOnly) {
        if (!uiState.isReceiveOnly && hostCompletedAt != null && hostTripId != null) {
            android.util.Log.d(
                "RelayScreen",
                "auto-navigate (host) armed for tripId=$hostTripId (delay 1500ms)"
            )
            delay(1500)
            android.util.Log.d("RelayScreen", "auto-navigating (host) to trip $hostTripId")
            onNavigateToTrip(hostTripId)
            viewModel.consumeHostSendCompleted()
        }
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode > 0) {
            // Discoverable окно успешно открыто — запускаем receive-server.
            viewModel.startReceiving()
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
                        // 3600c — максимум, который Android принимает без
                        // обрезки в системные дефолты (часто 120/300). При
                        // 120c пользователь успевал зайти на хост, дать
                        // permission'ы, нажать «Найти», и окно discoverable
                        // уже истекало — guest оставался в RFCOMM-слушании,
                        // но host его не видел.
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600)
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
        val hasPerms = context.hasBluetoothPermissions()
        val btOn = context.isBluetoothEnabled()
        android.util.Log.d(
            "RelayScreen",
            "requestOrRun: action=$action hasPerms=$hasPerms btOn=$btOn isBusy=${uiState.isConnecting || uiState.isTransferring || uiState.isApplyingPackage}"
        )
        if (!hasPerms) {
            pendingAction = action
            if (permissions.isNotEmpty()) {
                android.util.Log.d("RelayScreen", "requestOrRun: launching permission dialog for ${permissions.joinToString()}")
                permissionLauncher.launch(permissions)
            }
            return
        }

        if (!btOn) {
            pendingAction = action
            android.util.Log.d("RelayScreen", "requestOrRun: launching enable-bluetooth dialog")
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        pendingAction = null
        android.util.Log.d("RelayScreen", "requestOrRun: running action directly")
        runGrantedAction(action)
    }

    // Авто-запуск discovery (хост) / receiving (гость) при входе. По мокапам
    // промежуточной idle-сцены нет — экран открывается уже в активном
    // состоянии (HostScanning / GuestReceiving).
    //
    // Гейтим по флагам isHosting/isDiscovering, чтобы эффект не зациклился.
    // Также гейтим по isConnecting/isTransferring: после тапа по пиру
    // менеджер останавливает discovery → isDiscovering=false → перезапуск
    // бы убил свежезапущенный коннект.
    LaunchedEffect(
        uiState.isBluetoothEnabled,
        uiState.isReceiveOnly,
        uiState.isHosting,
        uiState.isDiscovering,
        uiState.isConnecting,
        uiState.isTransferring
    ) {
        if (!uiState.isBluetoothEnabled) return@LaunchedEffect
        if (!context.hasBluetoothPermissions()) return@LaunchedEffect
        if (uiState.isConnecting || uiState.isTransferring) {
            android.util.Log.d(
                "RelayScreen",
                "auto-start: skipped (connect/transfer in flight)"
            )
            return@LaunchedEffect
        }
        if (uiState.isReceiveOnly) {
            // Гость — серверная сторона, нужен discoverable + RFCOMM-listener.
            if (!uiState.isHosting) {
                android.util.Log.d("RelayScreen", "auto-start: kicking receive (requestOrRun)")
                requestOrRun(PendingRelayAction.Receive)
            }
        } else {
            // Хост — клиентская сторона, просто сканируем.
            if (!uiState.isDiscovering) {
                android.util.Log.d("RelayScreen", "auto-start: kicking discovery")
                requestOrRun(PendingRelayAction.Send)
            }
        }
    }

    RelayContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToTrip = onNavigateToTrip,
        onRefresh = viewModel::refreshBluetoothState,
        onEnableBluetooth = {
            android.util.Log.d("RelayScreen", "onEnableBluetooth tap → requestOrRun(Receive/Send)")
            // BT-выключатель важен и для гостя, и для хоста. На госте идём
            // в Receive-флоу (discoverable + listen), на хосте — в Send (scan).
            requestOrRun(if (uiState.isReceiveOnly) PendingRelayAction.Receive else PendingRelayAction.Send)
        },
        onAcceptIncoming = viewModel::acceptIncoming,
        onStopReceiving = viewModel::stopReceiving,
        onStartDiscovery = {
            android.util.Log.d("RelayScreen", "onStartDiscovery tap")
            requestOrRun(PendingRelayAction.Send)
        },
        onStopDiscovery = viewModel::stopDiscovery,
        onSendTo = { address ->
            android.util.Log.d("RelayScreen", "onSendTo tap address=$address")
            requestOrRun(PendingRelayAction.SendTo(address))
        },
        onClearError = viewModel::clearError,
        onClearMergeResult = viewModel::clearMergeResult,
        onConsumeHostSendCompleted = viewModel::consumeHostSendCompleted
    )
}

// Сцены экрана relay. Один enum упрощает чтение `pickScene(uiState)` и
// гарантирует, что мы покрыли все ветки. HostIdle/GuestIdle намеренно
// отсутствуют: экран автозапускает discovery/receiving при входе (см.
// LaunchedEffect в RelayScreen) и сразу попадает в активную сцену.
private enum class RelayScene {
    BtDisabled,
    HostScanning,
    GuestReceiving,
    IncomingTransfer,
    Completed
}

private fun pickScene(uiState: RelayUiState): RelayScene {
    // Завершённая передача (есть mergeResult) перебивает всё — пока
    // пользователь не закрыл диалог-итог, показываем «всё ушло».
    if (uiState.mergeResult != null) return RelayScene.Completed

    // Долговечная метка завершения: ставится из outgoingAcks (хост получил
    // APPLIED ack от гостя ИЛИ гость отправил APPLIED ack хосту). До
    // consumeHostSendCompleted держим Completed.
    if (uiState.lastHostSendCompletedAt != null) {
        android.util.Log.d(
            "RelayScreen",
            "scene=Completed (transfer completed @ ${uiState.lastHostSendCompletedAt})"
        )
        return RelayScene.Completed
    }

    if (!uiState.isBluetoothEnabled) return RelayScene.BtDisabled

    if (uiState.isReceiveOnly) {
        // Гость: если хост уже инициировал передачу (pendingIncomingIntent или
        // активный transfer) — IncomingTransfer; иначе пассивный
        // GuestReceiving («жду телефон рядом»).
        val hasIncoming = uiState.pendingIncomingIntent != null ||
            uiState.isApplyingPackage ||
            uiState.isTransferring
        return if (hasIncoming) RelayScene.IncomingTransfer else RelayScene.GuestReceiving
    }

    // Хост: сканирует, тапает пира, передаёт. Промежуточная idle-сцена не
    // нужна — `LaunchedEffect` кикает discovery при входе.
    return RelayScene.HostScanning
}

@Composable
private fun RelayContent(
    uiState: RelayUiState,
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (tripId: String) -> Unit,
    onRefresh: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onAcceptIncoming: () -> Unit,
    onStopReceiving: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onSendTo: (String) -> Unit,
    onClearError: () -> Unit,
    onClearMergeResult: () -> Unit,
    onConsumeHostSendCompleted: () -> Unit = {}
) {
    val scene = pickScene(uiState)
    val errorMessage = uiState.syncError ?: uiState.transportError

    // Auto-navigate забирает Completed-сцену через 1.5с (см. RelayScreen),
    // дополнительного таймера здесь не нужно.

    val colors = sceneColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Свечение задаёт настроение сцены: красный для BtOff, тёплый коралл+
        // бирюза для активных, янтарь+коралл для входящей, бирюза с обеих
        // сторон для Completed.
        SceneGlow(scene)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Шапка: миниатюрная стрелка «назад» слева + контекстная иконка
            // справа (info для BtOff, scan-grid для активных сцен).
            RelayTopBar(
                scene = scene,
                onNavigateBack = onNavigateBack,
                onRefresh = onRefresh
            )

            // Тело экрана + ошибки поверх.
            Box(modifier = Modifier.fillMaxSize()) {
                when (scene) {
                    RelayScene.BtDisabled -> BtDisabledScene(
                        isReceiveOnly = uiState.isReceiveOnly,
                        devices = uiState.devices,
                        onEnableBluetooth = onEnableBluetooth
                    )

                    RelayScene.HostScanning -> HostScanningScene(
                        trip = uiState.trip,
                        devices = uiState.devices,
                        isBusy = uiState.isConnecting || uiState.isTransferring || uiState.isApplyingPackage,
                        connectedDeviceName = uiState.connectedDeviceName,
                        statusMessage = uiState.statusMessage,
                        onSendTo = onSendTo,
                        onCancel = {
                            onStopDiscovery()
                            onNavigateBack()
                        }
                    )

                    RelayScene.GuestReceiving -> GuestReceivingScene(
                        statusMessage = uiState.statusMessage,
                        onCancel = {
                            onStopReceiving()
                            onNavigateBack()
                        }
                    )

                    RelayScene.IncomingTransfer -> IncomingTransferScene(
                        pendingIntent = uiState.pendingIncomingIntent,
                        senderName = uiState.pendingIncomingIntent?.senderDisplayName
                            ?: uiState.connectedDeviceName,
                        trip = uiState.trip,
                        statusMessage = uiState.statusMessage,
                        showAcceptButton = uiState.pendingIncomingIntent != null,
                        onAccept = onAcceptIncoming
                    )

                    RelayScene.Completed -> CompletedScene(
                        peerName = uiState.connectedDeviceName,
                        devices = uiState.devices,
                        trip = uiState.trip,
                        mergeResult = uiState.mergeResult,
                        // Auto-navigate в RelayScreen сделает переход через
                        // 1.5с, кнопки тут — fallback для редких рекомпозиций.
                        onDone = {
                            val receivedId = uiState.lastReceivedTripId
                            val hostTripId = uiState.trip?.id
                            when {
                                receivedId != null -> {
                                    android.util.Log.d(
                                        "RelayScreen",
                                        "CompletedScene.onDone → navigateToTrip($receivedId)"
                                    )
                                    onNavigateToTrip(receivedId)
                                }
                                !uiState.isReceiveOnly && hostTripId != null -> {
                                    android.util.Log.d(
                                        "RelayScreen",
                                        "CompletedScene.onDone (host) → navigateToTrip($hostTripId)"
                                    )
                                    onNavigateToTrip(hostTripId)
                                }
                                else -> {
                                    android.util.Log.d("RelayScreen", "CompletedScene.onDone → navigateBack")
                                    onClearMergeResult()
                                    onConsumeHostSendCompleted()
                                    onNavigateBack()
                                }
                            }
                        }
                    )
                }

                // Карточка с ошибкой — поверх любой сцены, в самом верху
                // контентной области.
                errorMessage?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ErrorBanner(message = it, onClose = onClearError)
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneGlow(scene: RelayScene) {
    // Свечение — две радиалки, как в CSS мокапов: тёплая «голова» наверху
    // (at 50% ~28%) и большое пятно снизу-справа (at 80% ~82%). Используем
    // BoxWithConstraints, чтобы вычислить центры в пикселях по реальному
    // размеру экрана.
    data class GlowSpec(
        val top: Color,
        val bottom: Color,
        val topFracX: Float,
        val topFracY: Float,
        val bottomFracX: Float,
        val bottomFracY: Float
    )

    val colors = sceneColors()
    val spec = when (scene) {
        RelayScene.BtDisabled -> GlowSpec(
            top = colors.glowCoralRed,
            bottom = colors.glowCoralRed,
            topFracX = 0.50f, topFracY = 0.30f,
            bottomFracX = 0.80f, bottomFracY = 0.85f
        )
        RelayScene.HostScanning -> GlowSpec(
            top = colors.glowCoral,
            bottom = colors.glowTeal,
            topFracX = 0.50f, topFracY = 0.22f,
            bottomFracX = 0.80f, bottomFracY = 0.78f
        )
        RelayScene.GuestReceiving -> GlowSpec(
            top = colors.glowCoral,
            bottom = colors.glowTeal,
            topFracX = 0.50f, topFracY = 0.28f,
            bottomFracX = 0.80f, bottomFracY = 0.82f
        )
        RelayScene.IncomingTransfer -> GlowSpec(
            top = colors.glowAmber,
            bottom = colors.glowCoral,
            topFracX = 0.50f, topFracY = 0.22f,
            bottomFracX = 0.30f, bottomFracY = 0.55f
        )
        RelayScene.Completed -> GlowSpec(
            top = colors.glowTeal,
            bottom = colors.glowTeal,
            topFracX = 0.50f, topFracY = 0.28f,
            bottomFracX = 0.80f, bottomFracY = 0.85f
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val diag = kotlin.math.sqrt(widthPx * widthPx + heightPx * heightPx)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(spec.top.copy(alpha = 0.95f), Color.Transparent),
                        center = Offset(widthPx * spec.topFracX, heightPx * spec.topFracY),
                        radius = diag * 0.55f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(spec.bottom.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(widthPx * spec.bottomFracX, heightPx * spec.bottomFracY),
                        radius = diag * 0.55f
                    )
                )
        )
    }
}

@Composable
private fun RelayTopBar(
    scene: RelayScene,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = sceneColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Назад",
                tint = colors.textPrimary
            )
        }

        // Правая иконка: info для BtOff, scan-grid (QrCode2 — самый близкий
        // материал-иконку) для активных сцен.
        val icon = when (scene) {
            RelayScene.BtDisabled -> Icons.Rounded.Info
            else -> Icons.Rounded.QrCode2
        }
        Surface(
            color = colors.topBarIconBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onRefresh)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Обновить",
                    tint = colors.textPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// region Scene shell

/**
 * Универсальный каркас сцены. Сверху — пилюля статуса и крупный заголовок.
 * Между ним и нижним доком — кластер (BoxWithConstraints, который сам
 * центрирует «ТЫ» с орбитой пиров). Нижний док прижат к низу.
 */
@Composable
private fun RelaySceneBody(
    pillText: String,
    pillAccent: Color,
    headline: String,
    cluster: @Composable () -> Unit,
    bottom: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        StatusPill(text = pillText, accentColor = pillAccent)
        Spacer(modifier = Modifier.height(14.dp))
        SceneHeadline(headline)
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            cluster()
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.padding(bottom = 24.dp)) {
            bottom()
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = text,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SceneHeadline(text: String) {
    Text(
        text = text,
        color = sceneColors().textPrimary,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

// endregion

// region Cluster — единый компонент с пятью визуальными вариантами

/**
 * Состояние кластера. Каждое значение полностью определяет, как выглядит
 * центр (ТЫ/чек/BT-перечёркнут) и какие пиры подсвечены/приглушены.
 */
private sealed interface ClusterState {
    data object BtDisabled : ClusterState
    // Scanning — единое состояние для активных «эфирных» сцен: хост сканирует
    // соседей (peers заполнен), гость пассивно ждёт (peers пуст). Пульс
    // рисуется одинаково в обоих случаях.
    data object Scanning : ClusterState
    data class Receiving(val senderAddress: String?) : ClusterState
    data class Completed(val recipientAddress: String?) : ClusterState
}

/**
 * Один пир на орбите. `accent` детерминированный по адресу — между
 * рекомпозициями цвет аватарки не «прыгает».
 */
private data class ClusterPeer(
    val address: String,
    val initial: Char,
    val name: String,
    val deviceModel: String?,
    val accent: Color
)

/**
 * Конвертация BT-устройства в визуальную модель. Имя у нас приходит
 * в формате «Сергей Xiaomi 13» — режем по первому пробелу: первое слово
 * идёт в `name`, всё остальное — в `deviceModel`.
 */
private fun BluetoothRelayDevice.toClusterPeer(): ClusterPeer {
    val displayName = name.ifBlank { address }
    val initial = displayName.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '?'
    val firstWord = displayName.split(' ').firstOrNull()?.ifBlank { null } ?: displayName
    val rest = displayName.substringAfter(' ', missingDelimiterValue = "").ifBlank { null }
    val accent = PeerPalette[(address.hashCode() and 0x7fffffff) % PeerPalette.size]
    return ClusterPeer(
        address = address,
        initial = initial,
        name = firstWord,
        deviceModel = rest,
        accent = accent
    )
}

/**
 * Угол пира (в градусах, 0 = top, по часовой стрелке) для индекса в круге
 * из `count` элементов. Углы подобраны под мокапы:
 *  • n=1 — северо-восток (~55°), как в мокапе 02 с одним пиром
 *  • n=2 — диагональ NE+SW (45° и 225°)
 *  • n=3 — N + 120° смещения (0/120/240)
 *  • n=4 — NE/SE/SW/NW (45/135/225/315)
 *  • n>=5 — равномерно по окружности
 */
private fun peerAngleDeg(index: Int, count: Int): Float {
    if (count <= 0) return 0f
    return when (count) {
        1 -> 55f
        2 -> if (index == 0) 45f else 225f
        3 -> index * 120f
        4 -> 45f + index * 90f
        else -> (360f / count) * index
    }
}

/**
 * Главный компонент кластера. Размер фиксирован — 300dp × 300dp, чтобы
 * текстовые подписи помещались наружу от орбиты. Центр — 120dp коралл/
 * бирюза/серый, пиры — 52dp на радиусе ~80dp (то есть половина пира уходит
 * под центральный круг — «тугой» кластер мокапа).
 */
@Composable
private fun RelayCluster(
    state: ClusterState,
    peers: List<ClusterPeer>,
    modifier: Modifier = Modifier,
    onPeerTap: (peerAddress: String) -> Unit = {}
) {
    val sortedPeers = remember(peers) { peers.sortedBy { it.address } }

    BoxWithConstraints(
        modifier = modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        val centerSize = 120.dp
        val peerSize = 52.dp
        // Раньше пиры сидели на 80dp и наполовину уходили под центральный круг
        // (центр радиус 60dp, пир радиус 26dp → перекрытие 6dp). По правке —
        // отодвигаем на 110dp, остаётся видимый зазор ~24dp между краем центра
        // и краем пира.
        val peerOrbitDp: Dp = 110.dp

        // Слой 0: пунктирные концентрические кольца. Координаты соответствуют
        // viewBox -160..160, поэтому центр Box — центр SVG, шаг радиусов
        // 40/75/110/145dp подбирается под видимую область.
        DashedRings(
            radiiDp = listOf(40.dp, 75.dp, 110.dp, 145.dp),
            color = sceneColors().dashedRing
        )

        // Слой 1: пульсы (только в Scanning).
        if (state is ClusterState.Scanning) {
            ScanningPulse(maxRadiusDp = 130.dp, minRadiusDp = centerSize / 2)
        }

        // Слой 2: пунктирная коралловая линия от sender'a к центру (Receiving).
        if (state is ClusterState.Receiving && state.senderAddress != null) {
            val senderIndex = sortedPeers.indexOfFirst { it.address == state.senderAddress }
            if (senderIndex >= 0) {
                DashedSenderLine(
                    angleDeg = peerAngleDeg(senderIndex, sortedPeers.size),
                    fromCenterDp = centerSize / 2,
                    toPeerCenterDp = peerOrbitDp,
                    color = PeerAccentCoral
                )
            }
        }

        // Слой 3: пиры. Сортируем по адресу — стабильный порядок между
        // рекомпозициями. Для каждого считаем угол и смещение в dp.
        sortedPeers.forEachIndexed { index, peer ->
            val angleDeg = peerAngleDeg(index, sortedPeers.size)
            val highlighted = when (state) {
                is ClusterState.Receiving -> peer.address == state.senderAddress
                is ClusterState.Completed -> peer.address == state.recipientAddress
                else -> null
            }
            val alpha = when (state) {
                ClusterState.BtDisabled -> 0.4f
                is ClusterState.Receiving -> if (highlighted == true) 1f else 0.32f
                is ClusterState.Completed -> if (highlighted == true) 1f else 0.32f
                else -> 1f
            }
            // BtDisabled — выключенный «эфир»; в мокапе пиры стоят неподвижно.
            // Во всех остальных состояниях пиры дышат: меняем радиус ±5dp и
            // угол ±2° с разными периодами по индексу — никакой пир не повторяет
            // соседа.
            val animate = state !is ClusterState.BtDisabled
            ClusterPeerView(
                peer = peer,
                angleDeg = angleDeg,
                orbitRadiusDp = peerOrbitDp,
                avatarSize = peerSize,
                alpha = alpha,
                animate = animate,
                animationSeed = index,
                onTap = { onPeerTap(peer.address) }
            )
        }

        // Слой 4: центральный круг — поверх всего, чтобы пиры визуально
        // «уходили» под него.
        ClusterCenter(state = state, size = centerSize)
    }
}

/**
 * Пунктирные концентрические окружности фона. Простой Canvas рисует кольца
 * заданного радиуса от центра Box'а. Цвет и dash — как в HTML.
 */
@Composable
private fun DashedRings(radiiDp: List<Dp>, color: Color) {
    val density = LocalDensity.current
    val radiiPx = radiiDp.map { with(density) { it.toPx() } }
    val strokeWidthPx = with(density) { 0.6.dp.toPx() }
    val dashOn = with(density) { 2.dp.toPx() }
    val dashOff = with(density) { 3.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        radiiPx.forEach { r ->
            drawCircle(
                color = color,
                radius = r,
                center = center,
                style = Stroke(
                    width = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
                )
            )
        }
    }
}

/**
 * Пульсирующие кольца для Scanning. 3 фазово-разнесённые окружности,
 * расходятся от края центрального круга до `maxRadiusDp`. alpha линейно
 * падает от 0.55 к 0.
 */
@Composable
private fun ScanningPulse(maxRadiusDp: Dp, minRadiusDp: Dp) {
    val transition = rememberInfiniteTransition(label = "relay-pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "relay-pulse-phase"
    )

    val density = LocalDensity.current
    val maxPx = with(density) { maxRadiusDp.toPx() }
    val minPx = with(density) { minRadiusDp.toPx() }
    val strokeWidthPx = with(density) { 1.5.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val phases = floatArrayOf(0f, 1f / 3f, 2f / 3f)
        phases.forEach { offset ->
            val t = ((phase + offset) % 1f)
            val radius = minPx + (maxPx - minPx) * t
            val alpha = (1f - t).coerceIn(0f, 1f) * 0.55f
            if (alpha > 0.01f) {
                drawCircle(
                    color = PeerAccentCoral.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }
    }
}

/**
 * Пунктирная коралловая линия от центра кластера до пира-отправителя
 * (Receiving). Угол берётся из `peerAngleDeg`.
 */
@Composable
private fun DashedSenderLine(
    angleDeg: Float,
    fromCenterDp: Dp,
    toPeerCenterDp: Dp,
    color: Color
) {
    val density = LocalDensity.current
    val fromPx = with(density) { fromCenterDp.toPx() }
    val toPx = with(density) { toPeerCenterDp.toPx() }
    val dashOn = with(density) { 4.dp.toPx() }
    val dashOff = with(density) { 5.dp.toPx() }
    val strokeWidthPx = with(density) { 1.5.dp.toPx() }
    val angleRad = Math.toRadians((angleDeg - 90f).toDouble())

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Линию начинаем от внешней границы центрального круга, заканчиваем
        // в центре аватарки пира.
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val start = Offset(center.x + fromPx * cosA, center.y + fromPx * sinA)
        val end = Offset(center.x + toPx * cosA, center.y + toPx * sinA)
        drawLine(
            color = color.copy(alpha = 0.7f),
            start = start,
            end = end,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
        )
    }
}

/**
 * Центральный круг кластера: коралл/бирюза/серый + текст «ТЫ»/чек/BT-slash.
 */
@Composable
private fun ClusterCenter(state: ClusterState, size: Dp) {
    val color = when (state) {
        ClusterState.BtDisabled -> sceneColors().disabledCenter
        is ClusterState.Completed -> PeerAccentTeal
        else -> PeerAccentCoral
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ClusterState.BtDisabled -> BluetoothSlashed(size = size * 0.42f)
            is ClusterState.Completed -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f)
            )
            else -> Text(
                text = "ТЫ",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Иконка Bluetooth с диагональной коралловой чертой поверх — рисуется
 * через Canvas, потому что в `Icons.Rounded` готового перечёркнутого
 * варианта нет.
 */
@Composable
private fun BluetoothSlashed(size: Dp) {
    val colors = sceneColors()
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.Bluetooth,
            contentDescription = null,
            tint = colors.disabledIcon,
            modifier = Modifier.size(size)
        )
        val strokePx = with(LocalDensity.current) { 2.4.dp.toPx() }
        Canvas(modifier = Modifier.size(size)) {
            drawLine(
                color = PeerAccentCoral,
                start = Offset(this.size.width * 0.10f, this.size.height * 0.90f),
                end = Offset(this.size.width * 0.90f, this.size.height * 0.10f),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Аватарка + подпись пира. Размещаем через offset от центра кластера:
 *   x = orbit · cos(angle - 90°)
 *   y = orbit · sin(angle - 90°)
 * Подпись (имя + модель) кладём СНАРУЖИ — со стороны, противоположной
 * центру кластера. Для верхней половины — над аватаркой, для нижней —
 * под ней; горизонтально подпись смещаем тоже наружу от центра.
 */
@Composable
private fun ClusterPeerView(
    peer: ClusterPeer,
    angleDeg: Float,
    orbitRadiusDp: Dp,
    avatarSize: Dp,
    alpha: Float,
    animate: Boolean = false,
    animationSeed: Int = 0,
    onTap: () -> Unit
) {
    val density = LocalDensity.current
    val orbitPx = with(density) { orbitRadiusDp.toPx() }

    // Лёгкое «дыхание» пира: фаза по индексу, период 4.2с/4.8с/5.4с/6.0с —
    // пиры не дрейфуют синхронно, кластер «живой», но не шумный. Амплитуда —
    // ±5dp по радиусу и ±2.5° по углу. Когда animate=false (BtDisabled или
    // превью без анимации) — фаза = 0, пир сидит ровно на orbit/angleDeg.
    val infinite = rememberInfiniteTransition(label = "peer-orbit-$animationSeed")
    val periodMs = 4200 + animationSeed * 600
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing)
        ),
        label = "peer-phase-$animationSeed"
    )
    val phaseOffset = (animationSeed * 0.9f) // сдвиг старта между пирами
    val radialWobbleDp = if (animate) (sin(phase + phaseOffset) * 5f).toFloat() else 0f
    val angularWobbleDeg = if (animate) (cos(phase * 0.7f + phaseOffset) * 2.5f).toFloat() else 0f

    val effectiveAngleRad = Math.toRadians(((angleDeg + angularWobbleDeg) - 90f).toDouble())
    val effectiveOrbitPx = orbitPx + with(density) { radialWobbleDp.dp.toPx() }
    val offsetX = (effectiveOrbitPx * cos(effectiveAngleRad)).toFloat()
    val offsetY = (effectiveOrbitPx * sin(effectiveAngleRad)).toFloat()

    // Определяем сторону подписи: верх — y < 0, низ — y > 0; лево — x < 0,
    // право — x > 0. Имя ставим ровно по горизонтали от центра аватарки
    // в сторону «наружу» (то есть туда, куда смотрит угол).
    val isTop = offsetY < 0
    val isRight = offsetX >= 0

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        // Аватарка — заливной круг с белой буквой.
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(peer.accent)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peer.initial.toString(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Подпись располагается за пределами аватарки, ровно по той стороне,
        // куда направлен угол. Сдвиг — половина аватарки + небольшой зазор.
        val labelOffsetY = (avatarSize / 2 + 22.dp) * (if (isTop) -1f else 1f)
        val colors = sceneColors()
        Column(
            modifier = Modifier
                .offset(y = labelOffsetY)
                .padding(horizontal = 4.dp),
            horizontalAlignment = if (isRight) Alignment.Start else Alignment.End
        ) {
            Text(
                text = peer.name,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isRight) TextAlign.Start else TextAlign.End
            )
            if (peer.deviceModel != null) {
                Text(
                    text = peer.deviceModel,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isRight) TextAlign.Start else TextAlign.End
                )
            }
        }
    }
}

// endregion

// region Per-scene composables

@Composable
private fun BtDisabledScene(
    isReceiveOnly: Boolean,
    devices: List<BluetoothRelayDevice>,
    onEnableBluetooth: () -> Unit
) {
    val peers = remember(devices) {
        // Отфильтровываем анонимные MAC-only записи: современные Android-
        // телефоны для приватности рекламируются с рандомным MAC и без имени,
        // и BT-сканер ловит их соседями. Реальные TripFlow-пиры в discoverable
        // обычно отдают имя сразу или через ACTION_NAME_CHANGED за 100-500мс,
        // поэтому пропадание name==address из UI безопасно — настоящий пир
        // появится с задержкой максимум полсекунды.
        devices.filter { it.name != it.address }.map { it.toClusterPeer() }
    }
    RelaySceneBody(
        pillText = "ЭФИР ВЫКЛЮЧЕН",
        pillAccent = PeerAccentCoral,
        headline = if (isReceiveOnly) {
            "Включи Bluetooth, чтобы найти телефон"
        } else {
            "Включи Bluetooth, чтобы выйти в эфир"
        },
        cluster = {
            RelayCluster(state = ClusterState.BtDisabled, peers = peers)
        },
        bottom = {
            BtDisabledBottomCard(onEnableBluetooth = onEnableBluetooth)
        }
    )
}

@Composable
private fun HostScanningScene(
    trip: Trip?,
    devices: List<BluetoothRelayDevice>,
    isBusy: Boolean,
    connectedDeviceName: String?,
    statusMessage: String?,
    onSendTo: (String) -> Unit,
    onCancel: () -> Unit
) {
    // Считаем только пиров с именем — соседские анонимные MAC'и из эфира
    // не должны крутить чип «РЯДОМ N».
    val namedDevices = devices.filter { it.name != it.address }
    val nearbyCount = namedDevices.size
    val pillText = if (nearbyCount > 0) "В ЭФИРЕ · РЯДОМ $nearbyCount" else "В ЭФИРЕ"
    val title = when {
        connectedDeviceName != null -> "Передаю $connectedDeviceName"
        nearbyCount > 0 -> "Тапни телефон,\nчтобы поделиться"
        else -> "Жду телефоны рядом…"
    }
    val peers = remember(devices) {
        // Отфильтровываем анонимные MAC-only записи: современные Android-
        // телефоны для приватности рекламируются с рандомным MAC и без имени.
        devices.filter { it.name != it.address }.map { it.toClusterPeer() }
    }
    RelaySceneBody(
        pillText = pillText,
        pillAccent = PeerAccentTeal,
        headline = title,
        cluster = {
            RelayCluster(
                state = ClusterState.Scanning,
                peers = peers,
                onPeerTap = { address -> if (!isBusy) onSendTo(address) }
            )
        },
        bottom = {
            HostScanningBottomCard(
                trip = trip,
                statusMessage = statusMessage,
                connectedDeviceName = connectedDeviceName,
                onCancel = onCancel
            )
        }
    )
}

@Composable
private fun GuestReceivingScene(
    statusMessage: String?,
    onCancel: () -> Unit
) {
    // Гость пассивно ждёт — peers ВСЕГДА пусты. Только центр «ТЫ» + пульс,
    // чтобы пользователь понимал, что устройство в эфире и готово принять.
    RelaySceneBody(
        pillText = "ЖДУ ОТПРАВКУ",
        pillAccent = PeerAccentTeal,
        headline = "Жду телефон\nрядом…",
        cluster = {
            RelayCluster(
                state = ClusterState.Scanning,
                peers = emptyList()
            )
        },
        bottom = {
            GuestReceivingBottomCard(
                statusMessage = statusMessage,
                onCancel = onCancel
            )
        }
    )
}

@Composable
private fun IncomingTransferScene(
    pendingIntent: IncomingTransferIntent?,
    senderName: String?,
    trip: Trip?,
    statusMessage: String?,
    showAcceptButton: Boolean,
    onAccept: () -> Unit
) {
    // На входящей передаче кластер пустой (peers=[]) — sender нам известен
    // только по INTENT'у, его BT-устройства в списке не будет, потому что
    // гость не сканирует. Так что dashed-sender-line не рисуем.
    val headline = when {
        pendingIntent != null -> "${pendingIntent.senderDisplayName} хочет\nподелиться поездкой"
        senderName != null -> "$senderName хочет\nподелиться поездкой"
        else -> "Принимаю поездку…"
    }
    RelaySceneBody(
        pillText = "ВХОДЯЩАЯ ПЕРЕДАЧА",
        pillAccent = GoldenAccent,
        headline = headline,
        cluster = {
            RelayCluster(
                state = ClusterState.Receiving(senderAddress = null),
                peers = emptyList()
            )
        },
        bottom = {
            IncomingBottomCard(
                pendingIntent = pendingIntent,
                trip = trip,
                statusMessage = statusMessage,
                showAcceptButton = showAcceptButton,
                onAccept = onAccept
            )
        }
    )
}

@Composable
private fun CompletedScene(
    peerName: String?,
    devices: List<BluetoothRelayDevice>,
    trip: Trip?,
    mergeResult: com.triloo.data.model.RelayMergeResult?,
    onDone: () -> Unit
) {
    val peers = remember(devices) {
        // На стороне хоста кластер может содержать BT-устройства (мы их
        // сканировали). На стороне гостя peers пуст. В обоих случаях
        // используем для подсветки получателя/отправителя.
        devices.filter { it.name != it.address }.map { it.toClusterPeer() }
    }
    val recipientAddress = remember(peerName, devices) {
        if (peerName == null) null
        else devices.firstOrNull {
            it.name.equals(peerName, ignoreCase = true) ||
                it.name.split(' ').firstOrNull()?.equals(peerName, ignoreCase = true) == true
        }?.address
    }
    val recipientPeer = peers.firstOrNull { it.address == recipientAddress }
    RelaySceneBody(
        pillText = "ПЕРЕДАНО",
        pillAccent = PeerAccentTeal,
        headline = if (peerName != null) "Поездка теперь\nу $peerName" else "Передача завершена",
        cluster = {
            RelayCluster(
                state = ClusterState.Completed(recipientAddress = recipientAddress),
                peers = peers
            )
        },
        bottom = {
            CompletedBottomCard(
                trip = trip,
                mergeResult = mergeResult,
                recipientPeer = recipientPeer,
                recipientFallbackName = peerName,
                onDone = onDone
            )
        }
    )
}

// endregion

// region Bottom cards

@Composable
private fun BtDisabledBottomCard(onEnableBluetooth: () -> Unit) {
    val colors = sceneColors()
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PeerAccentCoral.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFE25B4B),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Bluetooth выключен",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Relay работает без интернета · до 10 м · AES-256",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            CoralButton(
                text = "Включить Bluetooth",
                icon = Icons.Rounded.Bluetooth,
                onClick = onEnableBluetooth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HostScanningBottomCard(
    trip: Trip?,
    statusMessage: String?,
    connectedDeviceName: String?,
    onCancel: () -> Unit
) {
    val colors = sceneColors()
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PeerAccentCoral.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = PeerAccentCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = trip?.let { formatTripTitle(it) } ?: "Поездка готова",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    connectedDeviceName != null && statusMessage != null -> statusMessage
                    connectedDeviceName != null -> "Передаю $connectedDeviceName"
                    else -> "Тапни пира, чтобы поделиться"
                }
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary
                )
            ) {
                Text("Отменить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GuestReceivingBottomCard(
    statusMessage: String?,
    onCancel: () -> Unit
) {
    val colors = sceneColors()
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.topBarIconBg),
                contentAlignment = Alignment.Center
            ) {
                ScanningDots()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Держи телефоны рядом",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusMessage ?: "До 10 м · жду отправку",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary
                )
            ) {
                Text("Отменить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Три анимированные точки — индикатор активности «ещё ищу». Каждая точка
 * пульсирует фазово-разнесённо.
 */
@Composable
private fun ScanningDots() {
    val transition = rememberInfiniteTransition(label = "relay-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "relay-dots-phase"
    )
    val dotColor = sceneColors().textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { index ->
            val offset = index / 3f
            val t = ((phase + offset) % 1f)
            val a = 0.3f + 0.7f * (1f - kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = a))
            )
        }
    }
}

@Composable
private fun IncomingBottomCard(
    pendingIntent: IncomingTransferIntent?,
    trip: Trip?,
    statusMessage: String?,
    showAcceptButton: Boolean,
    onAccept: () -> Unit
) {
    val colors = sceneColors()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = colors.cardBackground,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PeerAccentCoral.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = PeerAccentCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val tripTitle = pendingIntent?.tripName?.takeIf { it.isNotBlank() }
                        ?: trip?.let { formatTripTitle(it) }
                        ?: "Получаю поездку…"
                    Text(
                        text = tripTitle,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        pendingIntent != null -> "Готов принять поездку"
                        statusMessage != null -> statusMessage
                        else -> "Пакет передаётся по Bluetooth"
                    }
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Одна кнопка «Принять» — кнопки «Отклонить» нет по дизайну, отказ
        // делается неявно через таймаут (CONSENT_TIMEOUT_MS) или системный
        // back. После accept consentProvider резолвится в true, и менеджер
        // начинает читать PACKAGE.
        if (showAcceptButton) {
            CoralButton(
                text = "Принять",
                icon = null,
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
        }
    }
}

@Composable
private fun CompletedBottomCard(
    trip: Trip?,
    mergeResult: com.triloo.data.model.RelayMergeResult?,
    recipientPeer: ClusterPeer?,
    recipientFallbackName: String?,
    onDone: () -> Unit
) {
    val colors = sceneColors()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = colors.cardBackground,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Маленький бирюзовый аватар-индикатор получателя.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(recipientPeer?.accent ?: PeerAccentTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (recipientPeer?.initial ?: recipientFallbackName?.firstOrNull()?.uppercaseChar() ?: 'A').toString(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val title = when {
                        recipientPeer != null -> buildString {
                            append(recipientPeer.name)
                            if (recipientPeer.deviceModel != null) {
                                append(" · ${recipientPeer.deviceModel}")
                            }
                        }
                        recipientFallbackName != null -> recipientFallbackName
                        else -> "Получатель"
                    }
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatCompletedSubtitle(trip, mergeResult),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = PeerAccentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        CoralButton(
            text = "Готово",
            icon = null,
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
    }
}

// endregion

@Composable
private fun CoralButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PeerAccentCoral,
            contentColor = Color.White,
            disabledContainerColor = PeerAccentCoral.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorBanner(message: String, onClose: () -> Unit) {
    val colors = sceneColors()
    Surface(
        color = colors.errorBannerBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = colors.errorBannerText,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = colors.errorBannerText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("Скрыть", color = colors.errorBannerDismiss, fontSize = 13.sp)
            }
        }
    }
}

private fun formatTripTitle(trip: Trip): String {
    val days = trip.durationDays
    val word = pluralizeDaysWord(days)
    return "${trip.name} · $days $word"
}

private fun formatCompletedSubtitle(
    trip: Trip?,
    mergeResult: com.triloo.data.model.RelayMergeResult?
): String {
    val parts = buildList {
        if (trip != null) {
            add("Получила «${trip.name} · ${trip.durationDays} ${pluralizeDaysWord(trip.durationDays)}»")
        } else {
            add("Получила поездку")
        }
        mergeResult?.let { add("обновлено: ${it.updated}, добавлено: ${it.inserted}") }
    }
    return parts.joinToString(" · ")
}

private fun pluralizeDaysWord(count: Int): String = when {
    count % 100 in 11..19 -> "дней"
    count % 10 == 1 -> "день"
    count % 10 in 2..4 -> "дня"
    else -> "дней"
}

private fun executeRelayAction(
    action: PendingRelayAction,
    context: Context,
    uiState: RelayUiState,
    viewModel: RelayViewModel,
    onRequireDiscoverable: () -> Unit
) {
    android.util.Log.d(
        "RelayScreen",
        "executeRelayAction: action=$action btSupported=${uiState.isBluetoothSupported} btOn=${context.isBluetoothEnabled()}"
    )
    if (!uiState.isBluetoothSupported) {
        android.util.Log.w("RelayScreen", "executeRelayAction: aborted — bluetooth not supported")
        return
    }
    if (!context.isBluetoothEnabled()) {
        android.util.Log.w("RelayScreen", "executeRelayAction: aborted — bluetooth not enabled")
        return
    }

    when (action) {
        // Гость: discoverable окно сначала, потом callback дёрнет startReceiving.
        PendingRelayAction.Receive -> onRequireDiscoverable()
        // Хост: просто запускаем discovery — он сам RFCOMM-client.
        PendingRelayAction.Send -> {
            android.util.Log.d("RelayScreen", "executeRelayAction: calling viewModel.startDiscovery()")
            viewModel.startDiscovery()
        }
        // Хост тапнул конкретного пира — открываем коннект и шлём intent.
        is PendingRelayAction.SendTo -> viewModel.sendTrip(action.address)
    }
}

private fun bluetoothPermissions(): Array<String> {
    return when {
        // По стандарту Android 12+ с usesPermissionFlags="neverForLocation"
        // ACCESS_FINE_LOCATION не нужен. На практике Samsung One UI 4 / 5 и
        // некоторые сборки MIUI всё равно валят запрос с
        //   E BluetoothUtils: Need ACCESS_FINE_LOCATION permission to get scan results
        // даже когда BLUETOOTH_SCAN выдан. Поэтому просим оба permission'а —
        // на стоковом Android это не больно, а на Samsung без этого discovery
        // не возвращает результатов.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
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

// region Previews

// Список «звёздного» окружения для @Preview — 4 знакомых имени из мокапов
// v3. Реальные устройства имеют формат «Имя Модель», что после toClusterPeer()
// разворачивается в name=«Имя», deviceModel=«Модель …».
private val PreviewSampleDevices = listOf(
    BluetoothRelayDevice("Сергей Xiaomi 13", "AA:BB:CC:DD:EE:01", isBonded = false),
    BluetoothRelayDevice("Аня iPhone 14", "AA:BB:CC:DD:EE:02", isBonded = false),
    BluetoothRelayDevice("Маша Galaxy S23", "AA:BB:CC:DD:EE:03", isBonded = false),
    BluetoothRelayDevice("Тимур Pixel 7", "AA:BB:CC:DD:EE:04", isBonded = false),
)

@Preview(name = "Bluetooth disabled (host)", backgroundColor = 0xFF0D1117, showBackground = true)
@Composable
private fun PreviewBtDisabledHost() {
    TrilooTheme(darkTheme = true) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = false,
                isReceiveOnly = false,
                devices = PreviewSampleDevices
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Host scanning", backgroundColor = 0xFF0D1117, showBackground = true)
@Composable
private fun PreviewHostScanning() {
    TrilooTheme(darkTheme = true) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isHosting = true,
                isReceiveOnly = false,
                trip = sampleTrip(),
                devices = PreviewSampleDevices
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Guest discovering", backgroundColor = 0xFF0D1117, showBackground = true)
@Composable
private fun PreviewGuestDiscovering() {
    TrilooTheme(darkTheme = true) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isReceiveOnly = true,
                isDiscovering = true,
                // По мокапу 02 показаны 2 пира — берём Аню и Тимура.
                devices = listOf(PreviewSampleDevices[1], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Incoming transfer", backgroundColor = 0xFF0D1117, showBackground = true)
@Composable
private fun PreviewIncomingTransfer() {
    TrilooTheme(darkTheme = true) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isReceiveOnly = true,
                isApplyingPackage = true,
                connectedDeviceName = "Тимур",
                statusMessage = "67 KB передаются",
                // По мокапу 03 — три пира (Сергей, Маша, Тимур).
                devices = listOf(PreviewSampleDevices[0], PreviewSampleDevices[2], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Completed", backgroundColor = 0xFF0D1117, showBackground = true)
@Composable
private fun PreviewCompleted() {
    TrilooTheme(darkTheme = true) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isHosting = true,
                isReceiveOnly = false,
                trip = sampleTrip(),
                connectedDeviceName = "Аня",
                statusMessage = "Пакет успешно применён на Аня",
                lastHostSendCompletedAt = 1L,
                devices = listOf(PreviewSampleDevices[1], PreviewSampleDevices[2], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

// ─── Светлая тема ───────────────────────────────────────────────────────────
// Те же 5 состояний, но с `TrilooTheme(darkTheme = false)` и пастельной
// подложкой Slate50 (0xFFFAFBFC). По мокапам цвета свечений в светлой теме
// мягче, а текст — почти чёрный (Slate950). Цель — убедиться, что бирюзовый
// и коралловый акценты остаются читаемыми, а контрастность подписей пиров
// достаточна на белом фоне.

@Preview(name = "Bluetooth disabled (host) · Light", backgroundColor = 0xFFFAFBFC, showBackground = true)
@Composable
private fun PreviewBtDisabledHostLight() {
    TrilooTheme(darkTheme = false) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = false,
                isReceiveOnly = false,
                devices = PreviewSampleDevices
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Host scanning · Light", backgroundColor = 0xFFFAFBFC, showBackground = true)
@Composable
private fun PreviewHostScanningLight() {
    TrilooTheme(darkTheme = false) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isHosting = true,
                isReceiveOnly = false,
                trip = sampleTrip(),
                devices = PreviewSampleDevices
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Guest discovering · Light", backgroundColor = 0xFFFAFBFC, showBackground = true)
@Composable
private fun PreviewGuestDiscoveringLight() {
    TrilooTheme(darkTheme = false) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isReceiveOnly = true,
                isDiscovering = true,
                devices = listOf(PreviewSampleDevices[1], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Incoming transfer · Light", backgroundColor = 0xFFFAFBFC, showBackground = true)
@Composable
private fun PreviewIncomingTransferLight() {
    TrilooTheme(darkTheme = false) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isReceiveOnly = true,
                isApplyingPackage = true,
                connectedDeviceName = "Тимур",
                statusMessage = "67 KB передаются",
                devices = listOf(PreviewSampleDevices[0], PreviewSampleDevices[2], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

@Preview(name = "Completed · Light", backgroundColor = 0xFFFAFBFC, showBackground = true)
@Composable
private fun PreviewCompletedLight() {
    TrilooTheme(darkTheme = false) {
        RelayContent(
            uiState = RelayUiState(
                isBluetoothEnabled = true,
                isHosting = true,
                isReceiveOnly = false,
                trip = sampleTrip(),
                connectedDeviceName = "Аня",
                statusMessage = "Пакет успешно применён на Аня",
                lastHostSendCompletedAt = 1L,
                devices = listOf(PreviewSampleDevices[1], PreviewSampleDevices[2], PreviewSampleDevices[3])
            ),
            onNavigateBack = {},
            onNavigateToTrip = {},
            onRefresh = {},
            onEnableBluetooth = {},
            onAcceptIncoming = {},
            onStopReceiving = {},
            onStartDiscovery = {},
            onStopDiscovery = {},
            onSendTo = {},
            onClearError = {},
            onClearMergeResult = {}
        )
    }
}

private fun sampleTrip(): Trip = Trip(
    id = "sample",
    name = "Грузия",
    destination = "Тбилиси",
    startDate = LocalDate.now(),
    endDate = LocalDate.now().plusDays(8)
)

// endregion
