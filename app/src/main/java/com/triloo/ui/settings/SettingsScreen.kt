package com.triloo.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import kotlinx.coroutines.launch
import com.triloo.data.settings.AppLanguage
import com.triloo.data.settings.ThemeMode
import com.triloo.BuildConfig
import androidx.compose.ui.tooling.preview.Preview
import com.triloo.ui.PreviewData
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGroupTrips: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToGroupTrips = onNavigateToGroupTrips,
        onNavigateToAuth = onNavigateToAuth,
        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
        onThemeSelected = viewModel::setThemeMode,
        onCurrencySelected = viewModel::setDefaultCurrency,
        onLanguageSelected = viewModel::setLanguage,
        onNotificationsToggle = viewModel::setNotificationsEnabled,
        onSyncToggle = viewModel::setSyncEnabled,
        onExportData = viewModel::exportData,
        onClearData = viewModel::clearAllData
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: AppSettingsUiState,
    onNavigateBack: () -> Unit,
    onNavigateToGroupTrips: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onExportData: (Uri, (Boolean) -> Unit) -> Unit,
    onClearData: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    val safeAction: (String, () -> Unit) -> Unit = { message, action ->
        runCatching(action).onFailure {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        onExportData(uri) { success ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    if (success) "Экспорт завершён" else "Не удалось экспортировать данные"
                )
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsToggle(granted)
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Разрешение на уведомления не выдано")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Аккаунт",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    if (!uiState.isAuthenticated) {
                        IconButton(onClick = onNavigateToAuth) {
                            Icon(
                                imageVector = Icons.Rounded.Login,
                                contentDescription = "Войти"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AccountHeroCard(
                displayName = uiState.displayName,
                isAuthenticated = uiState.isAuthenticated,
                tripsCount = uiState.tripsCount,
                daysCount = uiState.daysCount,
                placesCount = uiState.placesCount,
                onSignIn = onNavigateToAuth
            )

            SectionLabel(title = "Поездка")
            CardSection {
                CardRow(
                    icon = Icons.Rounded.Group,
                    title = "Групповые поездки",
                    subtitle = "0 активных приглашений",
                    onClick = onNavigateToGroupTrips,
                    showChevron = true
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.AttachMoney,
                    title = "Валюта по умолчанию",
                    valueText = uiState.defaultCurrency,
                    onClick = { showCurrencyDialog = true }
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Уведомления",
                    subtitle = "Утром · перед местом · окна",
                    trailing = {
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                handleNotificationsToggle(
                                    enabled = enabled,
                                    context = context,
                                    onToggle = onNotificationsToggle,
                                    onRequestPermission = {
                                        safeAction("Не удалось запросить разрешение") {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    },
                    onClick = {
                        handleNotificationsToggle(
                            enabled = !uiState.notificationsEnabled,
                            context = context,
                            onToggle = onNotificationsToggle,
                            onRequestPermission = {
                                safeAction("Не удалось запросить разрешение") {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            }
                        )
                    }
                )
            }

            SectionLabel(title = "Внешний вид и язык")
            CardSection {
                CardRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Тема",
                    valueText = uiState.themeMode.displayName,
                    onClick = { showThemeDialog = true }
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.Language,
                    title = "Язык",
                    valueText = uiState.language.displayName,
                    onClick = { showLanguageDialog = true }
                )
            }

            SectionLabel(title = "Данные")
            CardSection {
                CardRow(
                    icon = Icons.Rounded.CloudSync,
                    title = "Резервное копирование",
                    trailing = {
                        Switch(
                            checked = uiState.syncEnabled,
                            onCheckedChange = onSyncToggle
                        )
                    },
                    onClick = { onSyncToggle(!uiState.syncEnabled) }
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "Экспорт данных",
                    onClick = {
                        safeAction("Не удалось открыть экспорт") {
                            exportLauncher.launch("triloo-export.json")
                        }
                    },
                    showChevron = true
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Очистить данные",
                    onClick = { showClearDataDialog = true },
                    isDestructive = true
                )
            }

            SectionLabel(title = "О приложении")
            CardSection {
                CardRow(
                    icon = Icons.Rounded.Info,
                    title = "Версия",
                    valueText = BuildConfig.VERSION_NAME,
                    onClick = {}
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.Description,
                    title = "Политика конфиденциальности",
                    onClick = onNavigateToPrivacyPolicy,
                    showChevron = true
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.Star,
                    title = "Оценить приложение",
                    onClick = {
                        safeAction("Не удалось открыть магазин") { openPlayStore(context) }
                    },
                    showChevron = true
                )
                CardDivider()
                CardRow(
                    icon = Icons.Rounded.Feedback,
                    title = "Обратная связь",
                    onClick = {
                        safeAction("Не удалось открыть почту") { openFeedbackEmail(context) }
                    },
                    showChevron = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        SettingsChoiceSheet(
            title = "Тема",
            options = ThemeMode.entries.toList(),
            selected = uiState.themeMode,
            displayName = { it.displayName },
            onSelect = onThemeSelected,
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCurrencyDialog) {
        val currencies = buildCurrencyOptions(uiState.defaultCurrency)
        SettingsChoiceSheet(
            title = "Валюта по умолчанию",
            options = currencies,
            selected = uiState.defaultCurrency,
            displayName = { it },
            onSelect = onCurrencySelected,
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showLanguageDialog) {
        SettingsChoiceSheet(
            title = "Язык",
            options = AppLanguage.entries.toList(),
            selected = uiState.language,
            displayName = { it.displayName },
            onSelect = onLanguageSelected,
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = Error
                )
            },
            title = { Text("Удалить данные?") },
            text = { Text("Все локальные поездки и расходы будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataDialog = false
                        onClearData { success ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Данные очищены" else "Не удалось очистить данные"
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun handleNotificationsToggle(
    enabled: Boolean,
    context: android.content.Context,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit
) {
    if (enabled && Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PERMISSION_GRANTED
        if (!granted) {
            onRequestPermission()
            return
        }
    }
    onToggle(enabled)
}

/**
 * Шапка экрана: аватар-инициал + имя/«Гость», CTA для входа, и счётчики
 * «N поездок · M дней · K мест». Фокус-блок дизайна.
 */
@Composable
private fun AccountHeroCard(
    displayName: String,
    isAuthenticated: Boolean,
    tripsCount: Int,
    daysCount: Int,
    placesCount: Int,
    onSignIn: () -> Unit
) {
    val resolvedName = displayName.takeIf { it.isNotBlank() } ?: "Гость"
    val initial = resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: "Г"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    TealSecondary.copy(alpha = 0.55f),
                                    CoralPrimary.copy(alpha = 0.55f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isAuthenticated) {
                            "Поездки синхронизируются между устройствами"
                        } else {
                            "Войдите, чтобы синхронизировать поездки"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isAuthenticated) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = TrilooShapes.Sm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Войти или создать аккаунт",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(value = tripsCount, label = pluralizeTrips(tripsCount), modifier = Modifier.weight(1f))
                StatTile(value = daysCount, label = pluralizeDays(daysCount), modifier = Modifier.weight(1f))
                StatTile(value = placesCount, label = pluralizePlaces(placesCount), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = TrilooShapes.Sm,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun CardSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * Универсальный ряд карточки настроек. Один из вариантов:
 *  - `valueText` — серое значение справа («Системная», «RUB», «1.0.5»)
 *  - `trailing` — кастомный composable справа (Switch и пр.)
 *  - `showChevron` — стрелка вправо (для navigate-to-…)
 *  - `isDestructive` — красная иконка/текст
 */
@Composable
private fun CardRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    valueText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Error else MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            trailing != null -> trailing()
            valueText != null -> Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            showChevron -> Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun pluralizeTrips(count: Int): String {
    return when {
        count % 100 in 11..19 -> "Поездок"
        count % 10 == 1 -> "Поездка"
        count % 10 in 2..4 -> "Поездки"
        else -> "Поездок"
    }
}

private fun pluralizeDays(count: Int): String {
    return when {
        count % 100 in 11..19 -> "Дней"
        count % 10 == 1 -> "День"
        count % 10 in 2..4 -> "Дня"
        else -> "Дней"
    }
}

private fun pluralizePlaces(count: Int): String {
    return when {
        count % 100 in 11..19 -> "Мест"
        count % 10 == 1 -> "Место"
        count % 10 in 2..4 -> "Места"
        else -> "Мест"
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun SettingsScreenPreview() {
    TrilooTheme {
        SettingsContent(
            uiState = PreviewData.settingsState.copy(
                themeMode = ThemeMode.DARK,
                tripsCount = 4,
                daysCount = 21,
                placesCount = 37
            ),
            onNavigateBack = {},
            onNavigateToGroupTrips = {},
            onNavigateToAuth = {},
            onNavigateToPrivacyPolicy = {},
            onThemeSelected = {},
            onCurrencySelected = {},
            onLanguageSelected = {},
            onNotificationsToggle = {},
            onSyncToggle = {},
            onExportData = { _, _ -> },
            onClearData = { _ -> }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissAfter(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            dismissAfter { onSelect(option) }
                        },
                    shape = TrilooShapes.Sm,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayName(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildCurrencyOptions(current: String?): List<String> {
    val normalized = current?.uppercase()
    val defaults = listOf("RUB", "USD", "EUR", "GEL", "TRY", "THB", "AED", "KZT", "JPY", "CNY")
    return listOfNotNull(normalized) + defaults.filter { it != normalized }
}

private fun openPlayStore(context: android.content.Context) {
    val packageName = context.packageName
    val marketUri = Uri.parse("market://details?id=$packageName")
    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(marketIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // нет Play Store — открываем веб
    }
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    val webIntent = Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(webIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        // ни маркета ни браузера
    }
}

private fun openFeedbackEmail(context: android.content.Context) {
    val mailUri = Uri.parse("mailto:hello@triloo.app?subject=Triloo%20Feedback")
    val intent = Intent(Intent.ACTION_SENDTO, mailUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        // нет почтового клиента
    }
}
