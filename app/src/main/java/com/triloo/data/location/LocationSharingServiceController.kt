package com.triloo.data.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LocationSharingSessionState(
    val tripId: String? = null,
    val isActive: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null
)

@Singleton
class LocationSharingServiceController @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val _sessionState = MutableStateFlow(LocationSharingSessionState())
    val sessionState: StateFlow<LocationSharingSessionState> = _sessionState.asStateFlow()

    fun startSharing(tripId: String) {
        _sessionState.value = LocationSharingSessionState(
            tripId = tripId,
            isActive = true,
            statusMessage = "Запускаем фоновый геошаринг...",
            error = null
        )
        val intent = Intent(appContext, LocationSharingForegroundService::class.java)
            .setAction(LocationSharingForegroundService.ACTION_START)
            .putExtra(LocationSharingForegroundService.EXTRA_TRIP_ID, tripId)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopSharing() {
        val intent = Intent(appContext, LocationSharingForegroundService::class.java)
            .setAction(LocationSharingForegroundService.ACTION_STOP)
        appContext.startService(intent)
    }

    fun updateState(state: LocationSharingSessionState) {
        _sessionState.value = state
    }
}
