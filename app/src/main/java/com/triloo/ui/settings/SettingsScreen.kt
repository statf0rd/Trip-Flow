package com.triloo.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                        text = "Настройки",
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
        ) {
            SettingsSection(title = "Аккаунт") {
                SettingsItem(
                    icon = Icons.Rounded.AccountCircle,
                    title = "Профиль",
                    subtitle = "Войдите для синхронизации данных",
                    onClick = onNavigateToAuth
                )
                SettingsItem(
                    icon = Icons.Rounded.Group,
                    title = "Групповые поездки",
                    subtitle = "Приглашения и участники",
                    onClick = onNavigateToGroupTrips
                )
            }

            SettingsSection(title = "Настройки") {
                SettingsItem(
                    icon = Icons.Rounded.AttachMoney,
                    title = "Валюта по умолчанию",
                    subtitle = uiState.defaultCurrency,
                    onClick = { showCurrencyDialog = true }
                )
                SettingsItem(
                    icon = Icons.Rounded.DarkMode,
                    title = "Тема",
                    subtitle = uiState.themeMode.displayName,
                    onClick = { showThemeDialog = true }
                )
                SettingsItem(
                    icon = Icons.Rounded.Language,
                    title = "Язык",
                    subtitle = uiState.language.displayName,
                    onClick = { showLanguageDialog = true }
                )
                SettingsSwitchItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Уведомления",
                    subtitle = "Утренний план, за 1 час до места, окна между событиями",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PERMISSION_GRANTED
                        if (!granted) {
                                safeAction("Не удалось запросить разрешение") {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                return@SettingsSwitchItem
                            }
                        }
                        onNotificationsToggle(enabled)
                    }
                )
            }

            SettingsSection(title = "Данные") {
                SettingsSwitchItem(
                    icon = Icons.Rounded.CloudSync,
                    title = "Синхронизация",
                    subtitle = "Автоматическое резервное копирование",
                    checked = uiState.syncEnabled,
                    onCheckedChange = onSyncToggle
                )
                SettingsItem(
                    icon = Icons.Rounded.FileDownload,
                    title = "Экспорт данных",
                    subtitle = "Скачать все поездки и расходы",
                    onClick = {
                        safeAction("Не удалось открыть экспорт") {
                            exportLauncher.launch("triloo-export.json")
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Очистить данные",
                    subtitle = "Удалить все локальные данные",
                    onClick = { showClearDataDialog = true },
                    isDestructive = true
                )
            }

            SettingsSection(title = "О приложении") {
                SettingsItem(
                    icon = Icons.Rounded.Info,
                    title = "Версия",
                    subtitle = BuildConfig.VERSION_NAME,
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Rounded.Description,
                    title = "Политика конфиденциальности",
                    onClick = onNavigateToPrivacyPolicy
                )
                SettingsItem(
                    icon = Icons.Rounded.Star,
                    title = "Оценить приложение",
                    onClick = { safeAction("Не удалось открыть магазин") { openPlayStore(context) } }
                )
                SettingsItem(
                    icon = Icons.Rounded.Feedback,
                    title = "Обратная связь",
                    subtitle = "Сообщить о проблеме",
                    onClick = { safeAction("Не удалось открыть почту") { openFeedbackEmail(context) } }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeSelected(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = {
                                    onThemeSelected(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showCurrencyDialog) {
        val currencies = buildCurrencyOptions(uiState.defaultCurrency)
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Валюта по умолчанию") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCurrencySelected(currency)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.defaultCurrency == currency,
                                onClick = {
                                    onCurrencySelected(currency)
                                    showCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = currency)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Язык") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.language == language,
                                onClick = {
                                    onLanguageSelected(language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = language.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Закрыть")
                }
            }
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

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TrilooTheme {
        SettingsContent(
            uiState = PreviewData.settingsState.copy(themeMode = ThemeMode.DARK),
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate700,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
        
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
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
    if (marketIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(marketIntent)
        return
    }
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
    if (webIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(webIntent)
    }
}

private fun openFeedbackEmail(context: android.content.Context) {
    val mailUri = Uri.parse("mailto:hello@triloo.app?subject=Triloo%20Feedback")
    val intent = Intent(Intent.ACTION_SENDTO, mailUri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
