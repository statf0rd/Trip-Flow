package com.triloo.ui.relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.ui.components.ButtonStyle
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.qr.generateQrBitmap
import com.triloo.ui.theme.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(
    onNavigateBack: () -> Unit,
    onScanRelay: () -> Unit,
    qrResult: String? = null,
    onConsumeQrResult: () -> Unit = {},
    viewModel: RelayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var manualPayload by remember { mutableStateOf("") }
    var chunkIndex by remember { mutableIntStateOf(0) }
    val mergeResult = uiState.mergeResult

    LaunchedEffect(qrResult) {
        if (!qrResult.isNullOrBlank()) {
            viewModel.handleRelayQrPayload(qrResult)
            onConsumeQrResult()
        }
    }

    if (mergeResult != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearMergeResult,
            title = { Text("Синхронизация завершена") },
            text = {
                Text(
                    "Добавлено: ${mergeResult.inserted}\n" +
                        "Обновлено: ${mergeResult.updated}\n" +
                        "Удалено: ${mergeResult.deleted}"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearMergeResult) {
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
                            text = "Triloo Relay",
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
                    IconButton(onClick = viewModel::refreshExport) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Обновить"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Отправить") },
                    icon = { Icon(Icons.Rounded.Upload, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Получить") },
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    val chunks = uiState.exportChunks
                    LaunchedEffect(chunks.size) {
                        if (chunkIndex > (chunks.size - 1).coerceAtLeast(0)) {
                            chunkIndex = 0
                        }
                    }
                    val currentChunk = chunks.getOrNull(chunkIndex)
                    val qrBitmap: ImageBitmap? = remember(currentChunk) {
                        currentChunk?.let { generateQrBitmap(it) }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.exportError != null) {
                            Text(
                                text = uiState.exportError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Error
                            )
                        } else if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(320.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Relay QR"
                                )
                            }

                            if (chunks.size > 1) {
                                Text(
                                    text = "QR ${chunkIndex + 1} из ${chunks.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TrilooButton(
                                        text = "Назад",
                                        onClick = { chunkIndex = (chunkIndex - 1).coerceAtLeast(0) },
                                        enabled = chunkIndex > 0,
                                        style = ButtonStyle.Ghost
                                    )
                                    TrilooButton(
                                        text = "Вперёд",
                                        onClick = {
                                            chunkIndex = (chunkIndex + 1).coerceAtMost(chunks.size - 1)
                                        },
                                        enabled = chunkIndex < chunks.size - 1,
                                        style = ButtonStyle.Ghost
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrilooButton(
                            text = "Сканировать Relay QR",
                            onClick = onScanRelay,
                            isLoading = uiState.isMerging,
                            icon = Icons.Rounded.Sync,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.scanTotal > 0) {
                            Text(
                                text = "Сканировано ${uiState.scanProgress} из ${uiState.scanTotal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        uiState.scanError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Или вставьте QR-строку вручную",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = manualPayload,
                            onValueChange = { manualPayload = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("TRILOO|RELAY|v1|...") },
                            minLines = 3
                        )

                        TrilooButton(
                            text = "Импортировать из текста",
                            onClick = {
                                viewModel.handleRelayQrPayload(manualPayload.trim())
                                manualPayload = ""
                            },
                            enabled = manualPayload.isNotBlank(),
                            style = ButtonStyle.Ghost,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
