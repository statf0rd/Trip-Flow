package com.triloo.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.settings.ThemeMode
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
        onThemeSelected = viewModel::setThemeMode
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
    onThemeSelected: (ThemeMode) -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }

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
        containerColor = MaterialTheme.colorScheme.background
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
                    subtitle = "RUB — Российский рубль",
                    onClick = { /* TODO: Currency picker */ }
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
                    subtitle = "Русский",
                    onClick = { /* TODO: Language picker */ }
                )
                SettingsSwitchItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Уведомления",
                    subtitle = "Утренний план, за 1 час до места, окна между событиями",
                    checked = true,
                    onCheckedChange = { /* TODO */ }
                )
            }

            SettingsSection(title = "Данные") {
                SettingsItem(
                    icon = Icons.Rounded.CloudSync,
                    title = "Синхронизация",
                    subtitle = "Автоматическое резервное копирование",
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    icon = Icons.Rounded.FileDownload,
                    title = "Экспорт данных",
                    subtitle = "Скачать все поездки и расходы",
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Очистить данные",
                    subtitle = "Удалить все локальные данные",
                    onClick = { /* TODO: Confirmation dialog */ },
                    isDestructive = true
                )
            }

            SettingsSection(title = "О приложении") {
                SettingsItem(
                    icon = Icons.Rounded.Info,
                    title = "Версия",
                    subtitle = "1.0.0",
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
                    onClick = { /* TODO: Open Play Store */ }
                )
                SettingsItem(
                    icon = Icons.Rounded.Feedback,
                    title = "Обратная связь",
                    subtitle = "Сообщить о проблеме",
                    onClick = { /* TODO: Open email */ }
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
            onThemeSelected = {}
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
            color = CoralPrimary,
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
