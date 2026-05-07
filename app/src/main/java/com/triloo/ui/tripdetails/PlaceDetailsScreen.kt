package com.triloo.ui.tripdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.ui.components.*
import com.triloo.ui.theme.*
import com.triloo.ui.PreviewData
import com.triloo.ui.theme.TrilooTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    viewModel: PlaceDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Возвращаемся назад после успешного удаления.
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали места", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Кнопка редактирования.
                    IconButton(onClick = { uiState.place?.let { onNavigateToEdit(it.id) } }) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Редактировать"
                        )
                    }
                    // Кнопка удаления.
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Удалить",
                            tint = Error
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
        
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(paddingValues))
            }
            uiState.place == null -> {
                EmptyState(
                    emoji = "🤷",
                    title = "Место не найдено",
                    subtitle = "Возможно, оно было удалено",
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                PlaceDetailsContent(
                    place = uiState.place!!,
                    onMarkVisited = viewModel::toggleVisited,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    // Диалог подтверждения удаления.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = Error
                )
            },
            title = {
                Text("Удалить место?")
            },
            text = {
                Text("Это действие нельзя отменить. Место будет удалено из плана поездки.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePlace()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun PlaceDetailsContent(
    place: Place,
    onMarkVisited: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceTimeFormat = remember(context) { resolveDeviceTimeFormat(context) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        place.photoUrl?.let { photoUrl ->
            AsyncImage(
                model = photoUrl,
                contentDescription = place.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(TrilooShapes.Lg),
                contentScale = ContentScale.Crop
            )
        }

        // Заголовок с иконкой категории.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(TrilooShapes.Md)
                    .background(place.category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = place.category.icon,
                    contentDescription = place.category.displayName,
                    tint = place.category.color,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrilooChip(
                        text = place.category.displayName,
                        icon = place.category.icon,
                        iconTint = place.category.color,
                        color = place.category.color.copy(alpha = 0.15f),
                        textColor = place.category.color
                    )
                    place.priceLevel?.let { priceLevel ->
                        TrilooChip(
                            text = formatPriceLevel(priceLevel),
                            color = GoldenSubtle,
                            textColor = GoldenAccent
                        )
                    }
                }
            }
        }
        
        // Переключатель статуса посещения.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TrilooShapes.Md,
            color = if (place.isVisited) TealSubtle else Slate100,
            onClick = onMarkVisited
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (place.isVisited) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (place.isVisited) TealSecondary else Slate500
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (place.isVisited) "Посещено ✓" else "Отметить как посещённое",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (place.isVisited) TealDark else Slate700
                )
            }
        }
        
        // Информационные карточки.
        TrilooCard {
            // Адрес.
            place.address?.let { address ->
                InfoRow(
                    icon = Icons.Rounded.LocationOn,
                    label = "Адрес",
                    value = address
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Время.
            place.scheduledTime?.let { time ->
                InfoRow(
                    icon = Icons.Rounded.Schedule,
                    label = "Запланировано",
                    value = formatTimeDisplay(time, deviceTimeFormat)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Длительность.
            place.estimatedDuration?.let { duration ->
                InfoRow(
                    icon = Icons.Rounded.Timer,
                    label = "Длительность",
                    value = formatDurationLabel(duration)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Рейтинг.
            place.rating?.let { rating ->
                InfoRow(
                    icon = Icons.Rounded.Star,
                    label = "Рейтинг",
                    value = String.format("%.1f", rating),
                    valueColor = GoldenAccent
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Часы работы.
            place.openingHours?.let { hours ->
                InfoRow(
                    icon = Icons.Rounded.AccessTime,
                    label = "Часы работы",
                    value = hours
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            
            // Координаты, по умолчанию в свёрнутом виде.
            InfoRow(
                icon = Icons.Rounded.MyLocation,
                label = "Координаты",
                value = "${String.format("%.5f", place.latitude)}, ${String.format("%.5f", place.longitude)}"
            )
        }
        
        // Заметки.
        place.notes?.let { notes ->
            TrilooCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Заметки",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        if (place.website != null || place.phoneNumber != null) {
            TrilooCard {
                place.website?.let { website ->
                    InfoRow(
                        icon = Icons.Rounded.Language,
                        label = "Сайт",
                        value = website
                    )
                }
                
                if (place.website != null && place.phoneNumber != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
                
                place.phoneNumber?.let { phone ->
                    InfoRow(
                        icon = Icons.Rounded.Phone,
                        label = "Телефон",
                        value = phone
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { openInMaps(context, place) },
                modifier = Modifier.weight(1f),
                shape = TrilooShapes.Sm
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("На карте")
            }
            
            OutlinedButton(
                onClick = { buildRoute(context, place) },
                modifier = Modifier.weight(1f),
                shape = TrilooShapes.Sm
            ) {
                Icon(
                    imageVector = Icons.Rounded.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Маршрут")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatPriceLevel(priceLevel: Int): String {
    val normalized = priceLevel.coerceIn(0, 4)
    if (normalized == 0) return "Бюджетно"
    return "Цена: ${"₽".repeat(normalized)}"
}

private fun openInMaps(context: android.content.Context, place: Place) {
    val query = Uri.encode(place.name)
    // Сначала пробуем системную geo-схему — её перехватывают любые установленные
    // карты (Google/Yandex/2GIS). Если ни одно приложение не отвечает, падаем на
    // веб-версию Google Maps; добавляем NEW_TASK на случай не-Activity Context.
    val geoIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($query)")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(geoIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // Нет приложения, обрабатывающего geo: — проваливаемся на веб.
    }

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
        )
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(webIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        // Без браузера и карт делать нечего.
    }
}

private fun buildRoute(context: android.content.Context, place: Place) {
    val navigationIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
    )
        .setPackage("com.google.android.apps.maps")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(navigationIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // Нет Google Maps — пробуем generic geo-route.
    }

    // Generic VIEW по google.navigation: подхватят и Yandex Maps/Navigator.
    val genericNavIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(genericNavIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // Падаем на веб-маршрут.
    }

    val webRouteIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}"
        )
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(webRouteIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        // Без карт и без браузера — тупик.
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Slate500,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Slate600
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
        }
    }
}

private fun getCategoryColor(category: PlaceCategory): Color {
    return when (category) {
        PlaceCategory.RESTAURANT, PlaceCategory.CAFE -> ExpenseFood
        PlaceCategory.MUSEUM, PlaceCategory.ATTRACTION -> CoralPrimary
        PlaceCategory.PARK, PlaceCategory.NATURE, PlaceCategory.BEACH -> TealSecondary
        PlaceCategory.SHOPPING -> TealSecondary
        PlaceCategory.NIGHTLIFE, PlaceCategory.BAR -> Color(0xFFEC4899)
        PlaceCategory.ENTERTAINMENT -> Color(0xFF8B5CF6)
        PlaceCategory.HOLIDAY -> Color(0xFFF97316)
        PlaceCategory.TRANSPORT -> Color(0xFF6366F1)
        PlaceCategory.VIEWPOINT -> GoldenAccent
        else -> Slate600
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailsScreenPreview() {
    TrilooTheme {
        PlaceDetailsContent(
            place = PreviewData.places.first(),
            onMarkVisited = {}
        )
    }
}
