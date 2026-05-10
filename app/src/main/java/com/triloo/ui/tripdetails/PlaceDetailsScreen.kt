package com.triloo.ui.tripdetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    // Поездка в журнале — все write-actions внутри PlaceDetails прячем
    // (Edit/Delete в шапке, иконка Edit в «Запланировано», тумблер
    // «Посещено»). Источник правды — Trip.isPast, тот же, что в TripDetails.
    val isReadOnly = uiState.trip?.isPast == true

    // Возвращаемся назад после успешного удаления.
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }
    
    // Hero-дизайн съедает TopAppBar в шапку: панель прозрачна и кнопки лежат
    // поверх градиента сцены. Иконки белые, чтобы читались на тёмной заливке.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    HeroIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад",
                        onClick = onNavigateBack
                    )
                },
                actions = {
                    if (!isReadOnly) {
                        HeroIconButton(
                            icon = Icons.Rounded.Edit,
                            contentDescription = "Редактировать",
                            onClick = { uiState.place?.let { onNavigateToEdit(it.id) } }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        HeroIconButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = "Удалить",
                            onClick = { showDeleteDialog = true },
                            tint = Color(0xFFFF8A8A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                    day = uiState.day,
                    distanceFromHotelMeters = uiState.distanceFromHotelMeters,
                    onMarkVisited = viewModel::toggleVisited,
                    onEdit = { uiState.place?.let { onNavigateToEdit(it.id) } },
                    topPadding = paddingValues.calculateTopPadding(),
                    isReadOnly = isReadOnly
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

/**
 * Дизайн «Place Hero» (V1):
 *  • верхний блок-сцена 220dp — категориальный градиент + декоративный
 *    силуэт (skyline / горы / купола в зависимости от категории);
 *  • заголовок и пилюли «категория · ★ rating» уже на основном фоне;
 *  • coral-карточка «Запланировано» с днём и временем + edit-кнопка;
 *  • 2×2 сетка инфо-плиток (часы / цена / время посещения / от жилья);
 *  • заметка и блок действий «На карте · Маршрут» в конце.
 *
 * TopAppBar делается прозрачным в [PlaceDetailsScreen] — его кнопки лежат
 * поверх hero, чтобы экран начинался прямо со сцены, без серой полосы.
 */
@Composable
private fun PlaceDetailsContent(
    place: Place,
    day: com.triloo.data.model.TripDay?,
    distanceFromHotelMeters: Int?,
    onMarkVisited: () -> Unit,
    onEdit: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    isReadOnly: Boolean = false
) {
    val context = LocalContext.current
    val deviceTimeFormat = remember(context) { resolveDeviceTimeFormat(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PlaceHero(place = place, topPadding = topPadding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Тайтл + адрес-сабтайтл.
            Column {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(place.address?.takeIf { it.isNotBlank() })
                        .firstOrNull() ?: place.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Coral-карточка «Запланировано» — основной call-to-glance в дизайне.
            place.scheduledTime?.let { time ->
                ScheduledCard(
                    day = day,
                    timeText = formatTimeDisplay(time, deviceTimeFormat),
                    onEdit = onEdit,
                    isReadOnly = isReadOnly
                )
            }

            // 2×2 сетка инфо-плиток.
            val tiles = buildList {
                place.openingHours?.takeIf { it.isNotBlank() }?.let { hours ->
                    add(InfoTileData(Icons.Rounded.AccessTime, "Часы", hours, null))
                }
                place.priceLevel?.let { priceLevel ->
                    add(InfoTileData(Icons.Rounded.AttachMoney, "Цена", formatPriceLevel(priceLevel), null))
                }
                place.estimatedDuration?.let { duration ->
                    add(InfoTileData(Icons.Rounded.Timer, "Время посещения", formatDurationLabel(duration), null))
                }
                distanceFromHotelMeters?.let { distance ->
                    val km = distance / 1000.0
                    val walkMin = (distance / 80.0).toInt().coerceAtLeast(1)
                    add(
                        InfoTileData(
                            Icons.Rounded.DirectionsWalk,
                            "От жилья",
                            if (km >= 1.0) String.format(Locale.US, "%.1f км", km) else "$distance м",
                            "$walkMin мин пешком"
                        )
                    )
                }
                place.rating?.let { rating ->
                    add(
                        InfoTileData(
                            Icons.Rounded.Star,
                            "Рейтинг",
                            String.format(Locale.US, "%.1f", rating),
                            null,
                            valueColor = GoldenAccent
                        )
                    )
                }
            }
            if (tiles.isNotEmpty()) InfoTileGrid(tiles)

            // Переключатель статуса посещения — теперь как обычный info-row.
            // В архивной поездке скрываем тумблер (toggleVisited — write-action),
            // но если место уже отмечено — показываем статичный индикатор «Посещено».
            if (!isReadOnly) {
                VisitedRow(isVisited = place.isVisited, onClick = onMarkVisited)
            } else if (place.isVisited) {
                VisitedRow(isVisited = true, onClick = {})
            }

            // Заметка.
            place.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Column {
                    SectionLabel("Заметка")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = TrilooShapes.Md,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = notes,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Сайт / телефон в одной мини-карточке (если есть).
            if (place.website != null || place.phoneNumber != null) {
                Column {
                    SectionLabel("Контакты")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = TrilooShapes.Md,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            place.website?.let { website ->
                                InfoRow(
                                    icon = Icons.Rounded.Language,
                                    label = "Сайт",
                                    value = website
                                )
                            }
                            if (place.website != null && place.phoneNumber != null) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
}

/**
 * Hero-баннер 220dp с категориальным градиентом, силуэтом «зданий» и пилюлями
 * «категория · ★ рейтинг» в нижнем-левом углу. Силуэт нарисован простыми
 * прямоугольниками — это работает для любой категории и не требует точного
 * шаблона города. Картинка [Place.photoUrl] (если есть) рисуется поверх,
 * перекрывая силуэт. TopAppBar отрисован screen-уровневым Scaffold'ом.
 */
@Composable
private fun PlaceHero(place: Place, topPadding: androidx.compose.ui.unit.Dp) {
    val accent = place.category.color
    val heroHeight = 220.dp

    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        // Базовый градиент категории.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.55f))
                    )
                )
        )

        // Силуэт «зданий» из 7 прямоугольников разной высоты — даёт ощущение
        // архитектурной сцены, не привязываясь к конкретному городу.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseY = h * 0.55f
            val rectW = w / 7f
            val heights = floatArrayOf(0.32f, 0.55f, 0.42f, 0.62f, 0.45f, 0.50f, 0.36f)
            heights.forEachIndexed { idx, ratio ->
                val left = idx * rectW
                val top = baseY + (h - baseY) * (1f - ratio)
                drawRect(
                    color = Color(0xFF1A0A07).copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(rectW * 0.86f, h - top)
                )
                // окошки — две колонки точек
                val cols = 2
                val rows = ((h - top) / (10.dp.toPx())).toInt().coerceAtLeast(2)
                for (r in 1 until rows) {
                    for (c in 0 until cols) {
                        val cx = left + rectW * 0.18f + c * rectW * 0.32f
                        val cy = top + r * 10.dp.toPx()
                        if (cy < h - 6.dp.toPx()) {
                            drawCircle(
                                Color(0xFFFFE9A8).copy(alpha = 0.65f),
                                radius = 1.2f * density,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
        }

        // Если у места есть фотография — рисуем её поверх (полупрозрачно, чтобы
        // оставить категориальный тон). Это не основной кейс — большинство
        // мест без photoUrl, и тогда видна декоративная сцена.
        place.photoUrl?.let { photoUrl ->
            AsyncImage(
                model = photoUrl,
                contentDescription = place.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )
        }

        // Полупрозрачное затемнение внизу, чтобы пилюли читались.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x99000000)),
                        startY = 0.55f * with(LocalDensity.current) { heroHeight.toPx() }
                    )
                )
        )

        // Пилюли в нижнем-левом углу.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroPill(
                icon = place.category.icon,
                text = place.category.displayName.uppercase(),
                background = accent,
                textColor = Color.White
            )
            place.rating?.let { rating ->
                HeroPill(
                    icon = Icons.Rounded.Star,
                    text = String.format(Locale.US, "%.1f", rating),
                    background = Color(0xFFFFE9A8),
                    textColor = Color(0xFF1A0A07)
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    icon: ImageVector,
    text: String,
    background: Color,
    textColor: Color
) {
    Surface(shape = TrilooShapes.pill, color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Coral-карточка «Запланировано · День 2 · вторник, 6 авг · 09:30». Самая
 * заметная по дизайну точка экрана — день и время выделены крупно.
 */
@Composable
private fun ScheduledCard(
    day: com.triloo.data.model.TripDay?,
    timeText: String,
    onEdit: () -> Unit,
    isReadOnly: Boolean = false
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.forLanguageTag("ru"))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrilooShapes.Md,
        color = CoralPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ЗАПЛАНИРОВАНО",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        if (day != null) {
                            append("День ${day.dayNumber} · ")
                            append(day.date.format(dateFormatter))
                            append(" · ")
                        }
                        append(timeText)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isReadOnly) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Изменить",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private data class InfoTileData(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val sub: String?,
    val valueColor: Color? = null
)

/**
 * Сетка 2 колонки × N строк (FlowRow с двумя элементами в каждой строке).
 * Каждая плитка — иконка-метка-значение, опциональная подмета.
 */
@Composable
private fun InfoTileGrid(tiles: List<InfoTileData>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    InfoTile(tile, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoTile(tile: InfoTileData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = TrilooShapes.Md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = tile.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = tile.value,
                style = MaterialTheme.typography.titleMedium,
                color = tile.valueColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            tile.sub?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VisitedRow(isVisited: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = TrilooShapes.Md,
        color = if (isVisited) TealSubtle else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isVisited) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isVisited) TealSecondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isVisited) "Посещено" else "Отметить как посещённое",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isVisited) TealDark else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun HeroIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Surface(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(40.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.Black.copy(alpha = 0.32f)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
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

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun PlaceDetailsScreenPreview() {
    TrilooTheme {
        PlaceDetailsContent(
            place = PreviewData.places.first(),
            day = null,
            distanceFromHotelMeters = 4500,
            onMarkVisited = {},
            onEdit = {},
            topPadding = 0.dp
        )
    }
}
