package com.triloo.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triloo.ui.components.TrilooCard
import com.triloo.ui.theme.GoldenDark
import com.triloo.ui.theme.GoldenSubtle
import com.triloo.ui.theme.Slate100
import com.triloo.ui.theme.Slate400
import com.triloo.ui.theme.Slate600
import com.triloo.ui.theme.Slate700
import com.triloo.ui.theme.TealSecondary
import com.triloo.ui.theme.TealSubtle
import com.triloo.ui.theme.TrilooShapes
import kotlinx.coroutines.launch

/**
 * Шторка «Что хотите сделать?» с тремя действиями: личная поездка, групповая
 * поездка, присоединиться по коду. Раньше жила приватно внутри
 * `TripListScreen`, теперь поднята на уровень `MainActivity`, потому что
 * триггерится из центральной FAB-кнопки в нижней нав-панели — то есть с
 * любого таба, не только со списка поездок.
 *
 * Каждое действие — навигация: внутрь шторки поднимать ViewModel-зависимости
 * не нужно, поэтому композабл принимает плоские callback'и и сам
 * управляет анимированным закрытием листа перед переходом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripModalSheet(
    onDismiss: () -> Unit,
    onCreatePersonalTrip: () -> Unit,
    onCreateGroupTrip: () -> Unit,
    onJoinGroupTrip: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun hideThen(action: () -> Unit) {
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
        ) {
            Text(
                text = "Что хотите сделать?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Создайте свою поездку или присоединитесь к чужой по коду",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(16.dp))

            CreateTripTypeItem(
                icon = Icons.Rounded.Person,
                iconTint = Slate700,
                iconBackground = Slate100,
                title = "Личная поездка",
                description = "План и расходы только для вас",
                onClick = { hideThen(onCreatePersonalTrip) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            CreateTripTypeItem(
                icon = Icons.Rounded.Group,
                iconTint = TealSecondary,
                iconBackground = TealSubtle,
                title = "Групповая поездка",
                description = "Приглашения по коду и общие расходы",
                onClick = { hideThen(onCreateGroupTrip) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR-флоу удалён, поэтому и иконка/описание теперь без QR-намёков —
            // присоединение только по введённому коду организатора.
            CreateTripTypeItem(
                icon = Icons.Rounded.Login,
                iconTint = GoldenDark,
                iconBackground = GoldenSubtle,
                title = "Присоединиться по коду",
                description = "Введите код от организатора",
                onClick = { hideThen(onJoinGroupTrip) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CreateTripTypeItem(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    TrilooCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = TrilooShapes.Sm,
                color = iconBackground
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Slate400
            )
        }
    }
}
