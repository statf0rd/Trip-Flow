package com.triloo.ui

import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.model.SplitType
import com.triloo.data.model.TripStatus
import com.triloo.data.places.PlaceSuggestion
import com.triloo.ui.auth.AuthUiState
import com.triloo.ui.grouptrips.GroupTripsUiState
import com.triloo.ui.invite.InviteUiState
import com.triloo.ui.relay.RelayUiState
import com.triloo.ui.settings.AppSettingsUiState
import com.triloo.ui.tripdetails.AddExpenseUiState
import com.triloo.ui.tripdetails.AddPlaceUiState
import com.triloo.ui.tripdetails.DurationUnit
import com.triloo.ui.tripdetails.TimeFormat
import com.triloo.ui.tripdetails.TripDetailsUiState
import com.triloo.ui.trips.CreateTripUiState
import com.triloo.ui.trips.TripListUiState
import java.time.LocalDate

object PreviewData {
    private val startDate = LocalDate.of(2025, 5, 18)

    val trip: Trip = Trip(
        id = "trip-preview",
        name = "Лиссабон и Синтра",
        destination = "Португалия",
        startDate = startDate,
        endDate = startDate.plusDays(4),
        baseCurrency = "EUR",
        hotelName = "LX Boutique",
        budget = 1200.0,
        isGroupTrip = true,
        inviteCode = "TRIL00"
    )

    val secondTrip: Trip = trip.copy(
        id = "trip-upcoming",
        name = "Стамбул",
        destination = "Турция",
        startDate = startDate.plusDays(30),
        endDate = startDate.plusDays(37),
        status = TripStatus.PLANNING
    )

    val pastTrip: Trip = trip.copy(
        id = "trip-past",
        name = "Неаполь",
        destination = "Италия",
        startDate = startDate.minusDays(30),
        endDate = startDate.minusDays(23),
        status = TripStatus.COMPLETED
    )

    val days: List<TripDay> = listOf(
        TripDay(
            id = "day-1",
            tripId = trip.id,
            date = trip.startDate,
            dayNumber = 1,
            title = "Прибытие и центр"
        ),
        TripDay(
            id = "day-2",
            tripId = trip.id,
            date = trip.startDate.plusDays(1),
            dayNumber = 2,
            title = "Синтра"
        )
    )

    val places: List<Place> = listOf(
        Place(
            id = "place-1",
            tripId = trip.id,
            tripDayId = days[0].id,
            name = "A Brasileira",
            address = "Шиаду",
            latitude = 38.7106,
            longitude = -9.1423,
            category = PlaceCategory.CAFE,
            iconEmoji = "☕",
            orderIndex = 0,
            scheduledTime = "10:00",
            estimatedDuration = 60,
            rating = 4.7f
        ),
        Place(
            id = "place-2",
            tripId = trip.id,
            tripDayId = days[0].id,
            name = "Praça do Comércio",
            address = "Центр",
            latitude = 38.7079,
            longitude = -9.1366,
            category = PlaceCategory.ATTRACTION,
            iconEmoji = "🏛️",
            orderIndex = 1,
            scheduledTime = "13:30",
            estimatedDuration = 120,
            rating = 4.6f,
            isVisited = true
        ),
        Place(
            id = "place-3",
            tripId = trip.id,
            tripDayId = days[1].id,
            name = "Пена",
            address = "Синтра",
            latitude = 38.7879,
            longitude = -9.3906,
            category = PlaceCategory.MUSEUM,
            iconEmoji = "🏰",
            orderIndex = 0,
            scheduledTime = "09:00",
            estimatedDuration = 180,
            rating = 4.8f
        )
    )

    val participants: List<Participant> = listOf(
        Participant(
            tripId = trip.id,
            userId = "user-1",
            displayName = "Аня",
            role = ParticipantRole.ADMIN,
            isOnline = true
        ),
        Participant(
            tripId = trip.id,
            userId = "user-2",
            displayName = "Кирилл",
            role = ParticipantRole.MEMBER
        )
    )

    val expenses: List<Expense> = listOf(
        Expense(
            tripId = trip.id,
            description = "Пастел де ната",
            amount = 8.5,
            currency = "EUR",
            amountInBaseCurrency = 8.5,
            exchangeRate = 1.0,
            exchangeRateDate = trip.startDate,
            category = ExpenseCategory.FOOD,
            paidByUserId = "user-1",
            paidByName = "Аня",
            splitType = SplitType.PAYER_ONLY,
            date = trip.startDate
        ),
        Expense(
            tripId = trip.id,
            description = "Поезд до Синтры",
            amount = 12.0,
            currency = "EUR",
            amountInBaseCurrency = 12.0,
            exchangeRate = 1.0,
            exchangeRateDate = trip.startDate,
            category = ExpenseCategory.TRANSPORT,
            paidByUserId = "user-2",
            paidByName = "Кирилл",
            splitType = SplitType.EQUAL,
            date = trip.startDate.plusDays(1)
        )
    )

    val tripDetailsState: TripDetailsUiState = TripDetailsUiState(
        trip = trip,
        days = days,
        places = places,
        participants = participants,
        expenses = expenses,
        totalExpenses = expenses.sumOf { it.amountInBaseCurrency }
    )

    val tripListState: TripListUiState = TripListUiState(
        currentTrip = trip,
        upcomingTrips = listOf(secondTrip),
        pastTrips = listOf(pastTrip)
    )

    val createTripState: CreateTripUiState = CreateTripUiState(
        name = "Лиссабон и Синтра",
        destination = "Португалия",
        startDate = trip.startDate,
        endDate = trip.endDate,
        baseCurrency = trip.baseCurrency,
        hotelName = trip.hotelName.orEmpty(),
        budget = trip.budget,
        isGroupTrip = true
    )

    val addExpenseState: AddExpenseUiState = AddExpenseUiState(
        description = "Обед в Байрру Алту",
        amount = "32.00",
        currency = "EUR",
        category = ExpenseCategory.FOOD,
        paidBy = "Аня",
        date = trip.startDate,
        time = "14:30",
        notes = "С португальским вином"
    )

    val addPlaceState: AddPlaceUiState = AddPlaceUiState(
        name = "Miradouro da Graça",
        address = "Лиссабон",
        latitude = 38.7204,
        longitude = -9.133,
        category = PlaceCategory.VIEWPOINT,
        time = "18:30",
        timeFormat = TimeFormat.HOURS_24,
        durationValue = "1.0",
        durationUnit = DurationUnit.HOURS,
        notes = "Встретить закат",
        rating = 4.9f
    )

    val suggestions: List<PlaceSuggestion> = listOf(
        PlaceSuggestion(
            placeId = "s-1",
            name = "Time Out Market",
            address = "Cais do Sodré",
            category = PlaceCategory.RESTAURANT,
            latitude = 38.7078,
            longitude = -9.1466,
            rating = 4.6f
        ),
        PlaceSuggestion(
            placeId = "s-2",
            name = "Manteigaria",
            address = "Chiado",
            category = PlaceCategory.CAFE,
            latitude = 38.7087,
            longitude = -9.1396,
            rating = 4.8f
        )
    )

    val groupTripsState: GroupTripsUiState = GroupTripsUiState(
        inviteCode = "TRIL00",
        displayName = "Аня",
        inviteScanProgress = 1,
        inviteScanTotal = 3
    )

    val inviteState: InviteUiState = InviteUiState(
        tripName = trip.name,
        inviteCode = trip.inviteCode,
        chunks = listOf("chunk-1", "chunk-2")
    )

    val relayState: RelayUiState = RelayUiState(
        trip = trip,
        exportChunks = listOf("relay-1", "relay-2"),
        scanProgress = 1,
        scanTotal = 3
    )

    val authState: AuthUiState = AuthUiState()

    val settingsState: AppSettingsUiState = AppSettingsUiState()

    const val policyPlaceholder: String =
        "Triloo бережно относится к данным пользователей. " +
            "Этот текст — укороченная заглушка для предпросмотра экрана политики."
}
