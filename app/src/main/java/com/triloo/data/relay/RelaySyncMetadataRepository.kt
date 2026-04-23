package com.triloo.data.relay

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.relaySyncDataStore by preferencesDataStore(name = "relay_sync_metadata")

/**
 * Хранит небольшой журнал уже применённых relay packageId,
 * чтобы повторный импорт того же пакета не приводил к повторному merge.
 */
@Singleton
class RelaySyncMetadataRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.relaySyncDataStore

    data class TripSyncState(
        val lastMergedChangeCursor: Long = 0L,
        val hasCompleteSnapshot: Boolean = false
    )

    suspend fun hasAppliedPackage(packageId: String): Boolean {
        return appliedPackages().first().contains(packageId)
    }

    suspend fun markPackageApplied(packageId: String) {
        dataStore.edit { prefs ->
            val updated = buildList {
                add(packageId)
                addAll(
                    (prefs[Keys.APPLIED_PACKAGES] ?: "")
                        .split(SEPARATOR)
                        .filter { it.isNotBlank() && it != packageId }
                )
            }.take(MAX_APPLIED_PACKAGES)
            prefs[Keys.APPLIED_PACKAGES] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun getTripSyncState(tripId: String): TripSyncState {
        return tripSyncStates().first()[tripId] ?: TripSyncState()
    }

    suspend fun markTripPackageApplied(
        tripId: String,
        packageId: String,
        changeCursor: Long,
        isFullSnapshot: Boolean
    ) {
        dataStore.edit { prefs ->
            val updatedPackages = buildList {
                add(packageId)
                addAll(
                    (prefs[Keys.APPLIED_PACKAGES] ?: "")
                        .split(SEPARATOR)
                        .filter { it.isNotBlank() && it != packageId }
                )
            }.take(MAX_APPLIED_PACKAGES)
            prefs[Keys.APPLIED_PACKAGES] = updatedPackages.joinToString(SEPARATOR)

            val states = parseTripStates(prefs[Keys.TRIP_SYNC_STATES])
            val current = states[tripId] ?: TripSyncState()
            states[tripId] = TripSyncState(
                lastMergedChangeCursor = maxOf(current.lastMergedChangeCursor, changeCursor),
                hasCompleteSnapshot = current.hasCompleteSnapshot || isFullSnapshot
            )
            prefs[Keys.TRIP_SYNC_STATES] = serializeTripStates(states)
        }
    }

    private fun appliedPackages() = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            (prefs[Keys.APPLIED_PACKAGES] ?: "")
                .split(SEPARATOR)
                .filter { it.isNotBlank() }
                .toSet()
        }

    private fun tripSyncStates() = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseTripStates(prefs[Keys.TRIP_SYNC_STATES]) }

    private fun parseTripStates(raw: String?): MutableMap<String, TripSyncState> {
        if (raw.isNullOrBlank()) return linkedMapOf()
        return raw.split(STATE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size != 3) return@mapNotNull null
                val tripId = parts[0]
                val cursor = parts[1].toLongOrNull() ?: return@mapNotNull null
                val hasCompleteSnapshot = parts[2] == "1"
                tripId to TripSyncState(
                    lastMergedChangeCursor = cursor,
                    hasCompleteSnapshot = hasCompleteSnapshot
                )
            }
            .toMap(linkedMapOf())
            .toMutableMap()
    }

    private fun serializeTripStates(states: Map<String, TripSyncState>): String {
        return states.entries.take(MAX_TRIP_STATES).joinToString(STATE_SEPARATOR) { entry ->
            buildString {
                append(entry.key)
                append(FIELD_SEPARATOR)
                append(entry.value.lastMergedChangeCursor)
                append(FIELD_SEPARATOR)
                append(if (entry.value.hasCompleteSnapshot) "1" else "0")
            }
        }
    }

    private object Keys {
        val APPLIED_PACKAGES = stringPreferencesKey("applied_relay_packages")
        val TRIP_SYNC_STATES = stringPreferencesKey("trip_sync_states")
    }

    private companion object {
        const val MAX_APPLIED_PACKAGES = 50
        const val MAX_TRIP_STATES = 100
        const val SEPARATOR = ","
        const val STATE_SEPARATOR = "|"
        const val FIELD_SEPARATOR = "~"
    }
}
