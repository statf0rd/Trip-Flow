package com.trip.flow.ui.grouptrips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trip.flow.data.model.Trip
import com.trip.flow.ui.components.SectionHeader
import com.trip.flow.ui.components.TripFlowButton
import com.trip.flow.ui.components.TripFlowCard
import com.trip.flow.ui.components.TripFlowChip
import com.trip.flow.ui.theme.Error
import com.trip.flow.ui.theme.Slate500
import com.trip.flow.ui.theme.Slate600
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTripsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Групповые поездки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "Присоединиться по коду")
            }

            item {
                JoinByCodeCard(
                    inviteCode = uiState.inviteCode,
                    displayName = uiState.displayName,
                    isJoining = uiState.isJoining,
                    error = uiState.error,
                    onInviteCodeChange = viewModel::updateInviteCode,
                    onDisplayNameChange = viewModel::updateDisplayName,
                    onJoin = viewModel::joinByInviteCode
                )
            }

            item {
                SectionHeader(title = "Ваши групповые поездки")
            }

            if (groupTrips.isEmpty()) {
                item {
                    TripFlowCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = null,
                                tint = Slate600
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Пока нет групповых поездок",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Создайте групповую поездку или присоединитесь по коду",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = groupTrips,
                    key = { it.id }
                ) { trip ->
                    GroupTripItem(
                        trip = trip,
                        onClick = { onNavigateToTrip(trip.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinByCodeCard(
    inviteCode: String,
    displayName: String,
    isJoining: Boolean,
    error: String?,
    onInviteCodeChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onJoin: () -> Unit
) {
    TripFlowCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Введите код приглашения и имя",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600
        )

        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        TripFlowButton(
            text = "Присоединиться",
            onClick = onJoin,
            enabled = inviteCode.isNotBlank() && displayName.isNotBlank(),
            isLoading = isJoining,
            icon = Icons.Rounded.QrCode2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GroupTripItem(
    trip: Trip,
    onClick: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))

    TripFlowCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trip.destination} • ${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                Spacer(modifier = Modifier.height(10.dp))
                TripFlowChip(
                    text = "Код: ${trip.inviteCode}",
                    emoji = "🔗"
                )
            }
        }
    }
}
