package com.triloo.ui.grouptrips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Trip
import com.triloo.ui.PreviewData
import com.triloo.ui.components.JourneySceneBackground
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.components.TrilooCard
import com.triloo.ui.components.isJourneySceneLight
import com.triloo.ui.components.selectJourneyScene
import com.triloo.ui.theme.CoralLight
import com.triloo.ui.theme.CoralPrimary
import com.triloo.ui.theme.Error
import com.triloo.ui.theme.GoldenAccent
import com.triloo.ui.theme.GoldenLight
import com.triloo.ui.theme.Slate500
import com.triloo.ui.theme.Slate600
import com.triloo.ui.theme.TealLight
import com.triloo.ui.theme.TealSecondary
import com.triloo.ui.theme.TrilooShapes
import com.triloo.ui.theme.TrilooTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Экран групповых поездок: приветствие, быстрый вход по коду/Bluetooth,
 * фильтр и крупные hero-карточки с обложкой места, аватарами участников
 * и кодом приглашения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTripsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onNavigateToJoinByBluetooth: () -> Unit = {},
    viewModel: GroupTripsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groupTrips by viewModel.groupTrips.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.joinedTripId) {
        uiState.joinedTripId?.let { tripId ->
            onNavigateToTrip(tripId)
            viewModel.consumeJoinedTripNavigation()
        }
    }

    GroupTripsContent(
        uiState = uiState,
        groupTrips = groupTrips,
        onNavigateBack = onNavigateBack,
        onNavigateToTrip = onNavigateToTrip,
        onOpenJoinByCode = viewModel::openJoinByCodeSheet,
        onDismissJoinByCode = viewModel::dismissJoinByCodeSheet,
        onNavigateToJoinByBluetooth = onNavigateToJoinByBluetooth,
        onInviteCodeChange = viewModel::updateInviteCode,
        onDisplayNameChange = viewModel::updateDisplayName,
        onJoin = viewModel::joinByInviteCode,
        onFilterChange = viewModel::setFilter
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTripsContent(
    uiState: GroupTripsUiState,
    groupTrips: List<GroupTripSummary>,
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onOpenJoinByCode: () -> Unit,
    onDismissJoinByCode: () -> Unit,
    onNavigateToJoinByBluetooth: () -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onJoin: () -> Unit,
    onFilterChange: (TripFilter) -> Unit
) {
    val today = remember { LocalDate.now() }
    val filtered = remember(groupTrips, uiState.filter) {
        groupTrips.filter { summary ->
            when (uiState.filter) {
                TripFilter.ACTIVE -> !summary.trip.isPast
                TripFilter.ARCHIVE -> summary.trip.isPast
            }
        }.sortedBy { it.trip.startDate }
    }
    val activeCount = remember(groupTrips) {
        groupTrips.count { !it.trip.isPast }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Группы",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            // bottom = 100.dp — резерв под плавающий LiquidGlassNavBar
            // (70dp высота + 18dp отступ + insets), без него последние карточки
            // уходят под нав-бар на корневых табах.
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GreetingCard(
                    displayName = uiState.userDisplayName,
                    activeCount = activeCount
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    JoinActionCard(
                        title = "По коду или QR",
                        subtitle = "6 знаков или скан",
                        icon = Icons.Rounded.QrCodeScanner,
                        accent = CoralPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenJoinByCode
                    )
                    JoinActionCard(
                        title = "По Bluetooth",
                        subtitle = "Без интернета",
                        icon = Icons.Rounded.Bluetooth,
                        accent = TealSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToJoinByBluetooth
                    )
                }
            }

            item {
                TripsSectionHeader(
                    filter = uiState.filter,
                    onFilterChange = onFilterChange
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyTripsCard(filter = uiState.filter)
                }
            } else {
                items(
                    items = filtered,
                    key = { it.trip.id }
                ) { summary ->
                    GroupTripCard(
                        summary = summary,
                        today = today,
                        onClick = { onNavigateToTrip(summary.trip.id) }
                    )
                }
            }
        }
    }

    if (uiState.showJoinByCodeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissJoinByCode,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Text(
                    text = "Присоединиться по коду",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Введите 6-значный код приглашения. Сканирование QR появится позже — пока вставьте код вручную.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
                Spacer(modifier = Modifier.height(20.dp))
                JoinByCodeForm(
                    inviteCode = uiState.inviteCode,
                    displayName = uiState.displayName,
                    isJoining = uiState.isJoining,
                    error = uiState.error,
                    onInviteCodeChange = onInviteCodeChange,
                    onDisplayNameChange = onDisplayNameChange,
                    onJoin = onJoin
                )
            }
        }
    }
}

@Composable
private fun GreetingCard(
    displayName: String,
    activeCount: Int
) {
    val initials = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }
    val greetingName = displayName.trim().ifBlank { "путешественник" }
    TrilooCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(CoralPrimary, CoralLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Привет, $greetingName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pluralizeActiveTrips(activeCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun JoinActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TrilooCard(
        modifier = modifier.heightIn(min = 96.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Slate600
        )
    }
}

@Composable
private fun TripsSectionHeader(
    filter: TripFilter,
    onFilterChange: (TripFilter) -> Unit
) {
    val nextFilter = remember(filter) {
        when (filter) {
            TripFilter.ACTIVE -> TripFilter.ARCHIVE
            TripFilter.ARCHIVE -> TripFilter.ACTIVE
        }
    }
    val chipLabel = when (filter) {
        TripFilter.ACTIVE -> "Активные"
        TripFilter.ARCHIVE -> "Архив"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ваши поездки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = TrilooShapes.chip,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { onFilterChange(nextFilter) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = chipLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyTripsCard(filter: TripFilter) {
    val (title, subtitle) = when (filter) {
        TripFilter.ACTIVE -> "Пока нет активных групповых поездок" to
            "Создайте групповую поездку или присоединитесь по коду"
        TripFilter.ARCHIVE -> "Архив пуст" to
            "Завершённые групповые поездки появятся здесь"
    }
    TrilooCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = Slate600
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTripCard(
    summary: GroupTripSummary,
    today: LocalDate,
    onClick: () -> Unit
) {
    val trip = summary.trip
    val scene = remember(trip.destination) { selectJourneyScene(trip.destination) }
    val isLight = remember(scene) { isJourneySceneLight(scene) }
    val titleColor = if (isLight) Color(0xFF1A0A07) else Color.White
    val subtitleColor = if (isLight) Color(0xFF1A0A07).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.92f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = TrilooShapes.Md,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        // `matchParentSize` обязан стоять на сцене и скриме: иначе Canvas
        // получает 0×0 (внешний Box wrap_content), фон не рисуется, и
        // карточка падает обратно на Surface-цвет — текст становится
        // нечитаемым.
        Box(modifier = Modifier.fillMaxWidth()) {
            JourneySceneBackground(
                scene = scene,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isLight) {
                                listOf(Color.Transparent, Color(0xFF1A0A07).copy(alpha = 0.22f))
                            } else {
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.36f))
                            }
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = trip.destination,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TripBadge(
                        summary = summary,
                        today = today,
                        isLight = isLight
                    )
                }

                Spacer(modifier = Modifier.weight(1f, fill = true))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    ParticipantsRow(
                        summary = summary,
                        isLight = isLight,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    InviteCodePill(
                        code = trip.inviteCode,
                        isLight = isLight
                    )
                }
            }
        }
    }
}

@Composable
private fun TripBadge(
    summary: GroupTripSummary,
    today: LocalDate,
    isLight: Boolean
) {
    val (label, accent) = badgeFor(summary, today)
    val containerColor = if (isLight) {
        Color.White.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.42f)
    }
    val textColor = if (isLight) Color(0xFF1A0A07) else Color.White
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ParticipantsRow(
    summary: GroupTripSummary,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    val captionColor = if (isLight) Color(0xFF1A0A07).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.85f)
    val borderColor = if (isLight) Color.White else Color(0xFF1A0A07).copy(alpha = 0.45f)
    val names = summary.participants.map { it.displayName }
    Column(modifier = modifier) {
        if (names.isNotEmpty()) {
            GroupAvatarStack(
                names = names,
                borderColor = borderColor
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = participantsCaption(summary),
            style = MaterialTheme.typography.labelSmall,
            color = captionColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupAvatarStack(
    names: List<String>,
    borderColor: Color,
    maxVisible: Int = 4,
    avatarSize: Dp = 24.dp
) {
    val palette = listOf(
        CoralPrimary to CoralLight,
        TealSecondary to TealLight,
        GoldenAccent to GoldenLight
    )
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        names.take(maxVisible).forEachIndexed { index, name ->
            val (start, end) = palette[index % palette.size]
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(1.5.dp, borderColor, CircleShape)
                    .background(brush = Brush.linearGradient(colors = listOf(start, end))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (names.size > maxVisible) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(1.5.dp, borderColor, CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${names.size - maxVisible}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InviteCodePill(
    code: String,
    isLight: Boolean
) {
    val containerColor = if (isLight) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.42f)
    val textColor = if (isLight) Color(0xFF1A0A07) else Color.White
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun JoinByCodeForm(
    inviteCode: String,
    displayName: String,
    isJoining: Boolean,
    error: String?,
    onInviteCodeChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onJoin: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = inviteCode,
            onValueChange = onInviteCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Код приглашения") },
            placeholder = { Text("Например: A1B2C3", color = Slate500) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = Slate500
                )
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ваше имя") },
            placeholder = { Text("Например: Стас", color = Slate500) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Slate500
                )
            },
            singleLine = true
        )

        error?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Error
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        TrilooButton(
            text = "Присоединиться",
            onClick = onJoin,
            enabled = inviteCode.isNotBlank() && displayName.isNotBlank(),
            isLoading = isJoining,
            icon = Icons.Rounded.Login,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun pluralizeActiveTrips(count: Int): String {
    val rem10 = count % 10
    val rem100 = count % 100
    val word = when {
        rem100 in 11..14 -> "активных поездок"
        rem10 == 1 -> "активная поездка"
        rem10 in 2..4 -> "активные поездки"
        else -> "активных поездок"
    }
    return "$count $word"
}

private fun badgeFor(summary: GroupTripSummary, today: LocalDate): Pair<String, Color> {
    val trip = summary.trip
    if (summary.isOwner) {
        return when {
            trip.isPast -> "Завершена" to Slate500
            trip.isOngoing -> "Сейчас" to TealSecondary
            else -> {
                val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt().coerceAtLeast(0)
                "Через $days ${pluralizeDays(days)}" to CoralPrimary
            }
        }
    }
    return when (summary.currentUserRole) {
        ParticipantRole.OWNER -> "Организатор" to CoralPrimary
        ParticipantRole.ADMIN -> "Админ" to GoldenAccent
        ParticipantRole.MEMBER -> "Участник" to TealSecondary
        null -> "Гость" to Slate500
    }
}

private fun pluralizeDays(days: Int): String {
    val rem10 = days % 10
    val rem100 = days % 100
    return when {
        rem100 in 11..14 -> "дней"
        rem10 == 1 -> "день"
        rem10 in 2..4 -> "дня"
        else -> "дней"
    }
}

private fun participantsCaption(summary: GroupTripSummary): String {
    val trip = summary.trip
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    val people = summary.participants.size.coerceAtLeast(1)
    val peopleWord = pluralizePeople(people)
    val days = trip.durationDays
    val daysWord = pluralizeDays(days)
    val dates = "${trip.startDate.format(formatter)} — ${trip.endDate.format(formatter)}"
    return "$people $peopleWord · $dates · $days $daysWord"
}

private fun pluralizePeople(count: Int): String {
    val rem10 = count % 10
    val rem100 = count % 100
    return when {
        rem100 in 11..14 -> "человек"
        rem10 == 1 -> "человек"
        rem10 in 2..4 -> "человека"
        else -> "человек"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun GroupTripsScreenPreview() {
    TrilooTheme {
        val today = LocalDate.now()
        val summaries = listOf(
            GroupTripSummary(
                trip = PreviewData.trip.copy(
                    id = "group-1",
                    name = "Туапсе",
                    destination = "Краснодарский край",
                    inviteCode = "9Y66G6",
                    startDate = today.plusDays(8),
                    endDate = today.plusDays(14),
                    isGroupTrip = true,
                    ownerId = "user-self"
                ),
                participants = listOf(
                    PreviewData.participants[0].copy(displayName = "Аня"),
                    PreviewData.participants[1].copy(displayName = "Кирилл"),
                    PreviewData.participants[0].copy(displayName = "Маша", userId = "u3"),
                    PreviewData.participants[0].copy(displayName = "Толя", userId = "u4")
                ),
                currentUserRole = ParticipantRole.OWNER,
                isOwner = true
            ),
            GroupTripSummary(
                trip = PreviewData.secondTrip.copy(
                    id = "group-2",
                    name = "Горный Алтай · сплав",
                    destination = "Республика Алтай, Горно-Алтайск",
                    inviteCode = "TR8K2X",
                    startDate = today.plusDays(40),
                    endDate = today.plusDays(49),
                    isGroupTrip = true,
                    ownerId = "user-other"
                ),
                participants = listOf(
                    PreviewData.participants[0].copy(displayName = "Аня"),
                    PreviewData.participants[1].copy(displayName = "Кирилл"),
                    PreviewData.participants[0].copy(displayName = "Илья", userId = "u3")
                ),
                currentUserRole = ParticipantRole.MEMBER,
                isOwner = false
            )
        )
        GroupTripsContent(
            uiState = GroupTripsUiState(userDisplayName = "Артём"),
            groupTrips = summaries,
            onNavigateBack = {},
            onNavigateToTrip = {},
            onOpenJoinByCode = {},
            onDismissJoinByCode = {},
            onNavigateToJoinByBluetooth = {},
            onInviteCodeChange = {},
            onDisplayNameChange = {},
            onJoin = {},
            onFilterChange = {}
        )
    }
}
