package com.triloo.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SharedLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

/**
 * Отдаёт текущие координаты устройства потоком для live-геошаринга в поездке.
 */
@Singleton
class LocationSharingManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMillis: Long = 15_000L,
        minIntervalMillis: Long = 8_000L
    ): Flow<SharedLocationPoint> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMillis
        )
            .setMinUpdateIntervalMillis(minIntervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        SharedLocationPoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    trySend(
                        SharedLocationPoint(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            timestamp = it.time.takeIf { time -> time > 0 } ?: System.currentTimeMillis()
                        )
                    )
                }
            }

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
