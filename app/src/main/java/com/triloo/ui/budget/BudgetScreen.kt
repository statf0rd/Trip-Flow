package com.triloo.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Индекс таба «Расходы» в [com.triloo.ui.tripdetails.TripDetailsScreen]:
 * 0 — План, 1 — Карта, 2 — Расходы.
 */
const val TRIP_DETAILS_EXPENSES_TAB_INDEX: Int = 2

/**
 * Таб «Бюджет» работает как роутер: если есть текущая поездка — открываем её,
 * иначе ближайшую предстоящую; обе сразу на табе «Расходы». Если поездок нет
 * вовсе — остаётся экран-заглушка с CTA-текстом.
 */
@Composable
fun BudgetScreen(
    onNavigateToTrip: (tripId: String, initialTab: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetRouterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val open = state as? BudgetRouteState.OpenTrip ?: return@LaunchedEffect
        onNavigateToTrip(open.tripId, TRIP_DETAILS_EXPENSES_TAB_INDEX)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state) {
            // Пока поток Room ещё не выдал значение или мы уже улетаем в
            // поездку — отдаём пустой кадр, чтобы не мигнуть заглушкой
            // за полсекунды до открытия TripDetails.
            BudgetRouteState.Loading, is BudgetRouteState.OpenTrip -> {
                Box(modifier = Modifier.fillMaxSize())
            }
            BudgetRouteState.Empty -> EmptyBudgetPlaceholder()
        }
    }
}

@Composable
private fun EmptyBudgetPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Payments,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Бюджет",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Здесь появится сводка, как только у вас будет хотя бы одна поездка.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
