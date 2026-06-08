package com.triloo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Place
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripStatus
import com.triloo.data.notifications.TripNotificationScheduler
import com.triloo.data.relay.RelayRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.settings.AppSettingsRepository
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripRepositoryTest {

    private lateinit var context: android.content.Context
    private lateinit var database: TrilooDatabase
    private lateinit var repository: TripRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val userProfileRepository = UserProfileRepository(context)
        userProfileRepository.setAuthenticated(
            userId = "alice",
            displayName = "Alice",
            email = "alice@triloo.app"
        )
        database = Room.inMemoryDatabaseBuilder(context, TrilooDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = AppSettingsRepository(context)
        settings.setNotificationsEnabled(true)
        val scheduler = TripNotificationScheduler(
            context = context,
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            settingsRepository = settings
        )
        val relayRepository = RelayRepository(
            database = database,
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            expenseDao = database.expenseDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = userProfileRepository
        )
        val onlineSyncRepository = OnlineSyncRepository(
            onlineSyncApi = StubOnlineSyncApi(),
            backendTripApi = StubBackendTripApi(),
            relayRepository = relayRepository,
            serverSessionRepository = ServerSessionRepository(context),
            appSettingsRepository = AppSettingsRepository(context),
            tripDao = database.tripDao()
        )
        repository = TripRepository(
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = userProfileRepository,
            tripNotificationScheduler = scheduler,
            onlineSyncRepository = onlineSyncRepository
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork()
        database.close()
    }

    @Test
    fun createTripGeneratesDaysForDateRange() = runBlocking {
        val trip = sampleTrip(spanDays = 2) // startDate..startDate+2 включительно -> 3 дня
        repository.createTrip(trip)

        assertNotNull(repository.getTripById(trip.id))
        val days = repository.getTripDays(trip.id).sortedBy { it.dayNumber }
        assertEquals(3, days.size)
        assertEquals(listOf(1, 2, 3), days.map { it.dayNumber })
    }

    @Test
    fun updateTripPersistsChanges() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)

        repository.updateTrip(trip.copy(name = "Обновлённая"))

        assertEquals("Обновлённая", repository.getTripById(trip.id)?.name)
    }

    @Test
    fun updateTripStatusChangesStatus() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)

        repository.updateTripStatus(trip.id, TripStatus.COMPLETED)

        assertEquals(TripStatus.COMPLETED, repository.getTripById(trip.id)?.status)
    }

    @Test
    fun deleteTripRemovesItAndLogsDeletion() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)

        repository.deleteTrip(trip.id)

        assertNull(repository.getTripById(trip.id))
        val deletions = database.deletionLogDao().getDeletionsForTrip(trip.id)
        assertTrue(deletions.any { it.entityType == RelayEntityType.TRIP && it.entityId == trip.id })
    }

    @Test
    fun addPlaceAssignsIncrementingOrderIndex() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id

        repository.addPlace(samplePlace("p1", trip.id, dayId))
        repository.addPlace(samplePlace("p2", trip.id, dayId))

        val places = repository.getPlacesByDay(dayId).sortedBy { it.orderIndex }
        assertEquals(2, places.size)
        assertEquals(listOf(0, 1), places.map { it.orderIndex })
    }

    @Test
    fun updatePlacePersistsChanges() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id
        repository.addPlace(samplePlace("p1", trip.id, dayId))

        repository.updatePlace(samplePlace("p1", trip.id, dayId, name = "Новое имя"))

        assertEquals("Новое имя", repository.getPlaceById("p1")?.name)
    }

    @Test
    fun deletePlaceLogsDeletion() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id
        repository.addPlace(samplePlace("p1", trip.id, dayId))

        repository.deletePlace("p1")

        assertNull(repository.getPlaceById("p1"))
        val deletions = database.deletionLogDao().getDeletionsForTrip(trip.id)
        assertTrue(deletions.any { it.entityType == RelayEntityType.PLACE && it.entityId == "p1" })
    }

    @Test
    fun markPlaceVisitedSetsFlag() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id
        repository.addPlace(samplePlace("p1", trip.id, dayId))

        repository.markPlaceVisited("p1", true)

        assertTrue(repository.getPlaceById("p1")?.isVisited == true)
    }

    @Test
    fun reorderPlacesUpdatesOrder() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id
        repository.addPlace(samplePlace("p1", trip.id, dayId))
        repository.addPlace(samplePlace("p2", trip.id, dayId))

        repository.reorderPlaces(dayId, listOf("p2", "p1"))

        val ordered = repository.getPlacesByDay(dayId).sortedBy { it.orderIndex }.map { it.id }
        assertEquals(listOf("p2", "p1"), ordered)
    }

    @Test
    fun updateTripDayPersistsChanges() = runBlocking {
        val trip = sampleTrip()
        repository.createTrip(trip)
        val day = repository.getTripDays(trip.id).first()

        repository.updateTripDay(day.copy(dayNumber = 5))

        assertEquals(5, repository.getTripDayById(day.id)?.dayNumber)
    }

    @Test
    fun addParticipantBootstrapsOwnerAndMarksGroupTrip() = runBlocking {
        val trip = sampleTrip(ownerId = "alice") // не-групповая, владелец = текущий пользователь
        repository.createTrip(trip)

        repository.addParticipant(
            Participant(tripId = trip.id, userId = "alice", displayName = "Alice", role = ParticipantRole.OWNER)
        )

        assertEquals(1, repository.getParticipants(trip.id).size)
        assertTrue(repository.getTripById(trip.id)?.isGroupTrip == true)
    }

    @Test
    fun addParticipantAllowsSelfJoinByInvite() = runBlocking {
        val trip = sampleTrip(id = "trip-join", isGroup = true, ownerId = "someone")
        repository.createTrip(trip)

        // alice сам(а) присоединяется по приглашению.
        repository.addParticipant(
            Participant(tripId = trip.id, userId = "alice", displayName = "Alice", role = ParticipantRole.MEMBER)
        )

        assertTrue(repository.getParticipants(trip.id).any { it.userId == "alice" })
    }

    @Test
    fun addParticipantDeniedForMemberAddingOthers() {
        val trip = sampleTrip(id = "trip-perm", isGroup = true, ownerId = "someone")
        runBlocking {
            repository.createTrip(trip)
            database.tripDao().insertParticipant(
                Participant(tripId = trip.id, userId = "alice", displayName = "Alice", role = ParticipantRole.MEMBER)
            )
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.addParticipant(
                    Participant(tripId = trip.id, userId = "bob", displayName = "Bob")
                )
            }
        }
    }

    @Test
    fun removeParticipantDeletesAndLogs() = runBlocking {
        val trip = sampleTrip(id = "trip-rm", isGroup = true, ownerId = "alice")
        repository.createTrip(trip)
        database.tripDao().insertParticipant(
            Participant(tripId = trip.id, userId = "alice", displayName = "Alice", role = ParticipantRole.OWNER)
        )
        database.tripDao().insertParticipant(
            Participant(tripId = trip.id, userId = "bob", displayName = "Bob", role = ParticipantRole.MEMBER)
        )

        repository.removeParticipant(trip.id, "bob")

        assertNull(database.tripDao().getParticipant(trip.id, "bob"))
        val deletions = database.deletionLogDao().getDeletionsForTrip(trip.id)
        assertTrue(deletions.any { it.entityType == RelayEntityType.PARTICIPANT && it.entityId == "bob" })
    }

    @Test
    fun updateParticipantOnlineStatusAndLocationForSelf() = runBlocking {
        val trip = sampleTrip(id = "trip-self", isGroup = true, ownerId = "alice")
        repository.createTrip(trip)
        database.tripDao().insertParticipant(
            Participant(tripId = trip.id, userId = "alice", displayName = "Alice", role = ParticipantRole.OWNER)
        )

        repository.updateParticipantOnlineStatus(trip.id, "alice", true)
        repository.updateParticipantLocation(trip.id, "alice", latitude = 10.0, longitude = 20.0)

        val alice = repository.getParticipants(trip.id).first { it.userId == "alice" }
        assertTrue(alice.isOnline)
        assertEquals(10.0, alice.lastLatitude ?: 0.0, 0.001)
        assertEquals(20.0, alice.lastLongitude ?: 0.0, 0.001)
    }

    @Test
    fun managementDeniedForNonParticipantOnGroupTrip() {
        val trip = sampleTrip(id = "trip-denied", isGroup = true, ownerId = "someone")
        runBlocking { repository.createTrip(trip) }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.updateTrip(trip.copy(name = "X")) }
        }
    }

    @Test
    fun observeAndGetterQueriesEmitCurrentData() = runBlocking {
        val trip = sampleTrip(spanDays = 1) // 2 дня
        repository.createTrip(trip)
        val dayId = repository.getTripDays(trip.id).first().id
        repository.addPlace(samplePlace("p1", trip.id, dayId))
        database.tripDao().insertParticipant(
            Participant(tripId = trip.id, userId = "alice", displayName = "Alice")
        )

        assertTrue(repository.observeAllTrips().first().isNotEmpty())
        assertNotNull(repository.observeTripById(trip.id).first())
        assertEquals(2, repository.observeTripDays(trip.id).first().size)
        assertEquals(1, repository.observePlacesByTrip(trip.id).first().size)
        assertEquals(1, repository.observePlacesByDay(dayId).first().size)
        assertTrue(repository.observeParticipants(trip.id).first().isNotEmpty())
        assertTrue(repository.observeAllParticipants().first().isNotEmpty())

        // Глобальные счётчики и фильтры по времени — smoke-вызовы (покрытие passthrough'ей).
        repository.observeTotalDayCount().first()
        repository.observeTotalPlaceCount().first()
        repository.observeUpcomingTrips().first()
        repository.observePastTrips().first()
        repository.observeCurrentTrip().first()

        assertEquals(1, repository.getAllTrips().size)
        assertNotNull(repository.getPlaceById("p1"))
        assertEquals(1, repository.getPlacesByTrip(trip.id).size)
        assertEquals(2, repository.getTripDays(trip.id).size)
        assertTrue(repository.getParticipants(trip.id).isNotEmpty())
        assertNull(repository.getTripByInviteCode("no-such-code"))
    }

    private fun sampleTrip(
        id: String = "trip-1",
        isGroup: Boolean = false,
        ownerId: String? = null,
        spanDays: Long = 0
    ): Trip = Trip(
        id = id,
        name = "Trip",
        destination = "Dest",
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusDays(spanDays),
        baseCurrency = "EUR",
        isGroupTrip = isGroup,
        ownerId = ownerId
    )

    private fun samplePlace(
        id: String,
        tripId: String,
        dayId: String,
        name: String = "Place"
    ): Place = Place(
        id = id,
        tripId = tripId,
        tripDayId = dayId,
        name = name,
        latitude = 1.0,
        longitude = 2.0
    )
}
