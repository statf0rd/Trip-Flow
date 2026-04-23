package com.triloo.data.accommodation

import com.google.gson.Gson
import com.triloo.BuildConfig
import com.triloo.data.ai.OpenAiService
import com.triloo.data.remote.GeoapifyApi
import com.triloo.data.remote.GeoapifyGeocodeFeature
import com.triloo.data.remote.GeoapifyPlaceFeature
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Подбирает варианты проживания через Geoapify и ранжирует их по эвристикам/AI.
 */
@Singleton
class AccommodationRecommendationService @Inject constructor(
    private val geoapifyApi: GeoapifyApi,
    private val openAiService: OpenAiService,
    private val gson: Gson
) {

    suspend fun recommend(request: AccommodationRequest): List<AccommodationRecommendation> {
        val destination = request.destination.trim()
        if (destination.isBlank() || !hasValidGeoapifyKey()) return emptyList()

        val candidates = loadCandidates(destination)
        if (candidates.isEmpty()) return emptyList()

        val heuristicRecommendations = rankHeuristically(request, candidates)
        val aiRecommendations = rankWithAi(request, candidates)

        return mergeRankings(
            aiRecommendations = aiRecommendations,
            heuristicRecommendations = heuristicRecommendations
        )
    }

    private suspend fun loadCandidates(destination: String): List<AccommodationCandidate> = coroutineScope {
        val destinationContext = resolveDestinationContext(destination) ?: return@coroutineScope emptyList()
        val response = runCatching {
            geoapifyApi.searchPlaces(
                categories = "accommodation",
                filter = destinationContext.filter,
                bias = destinationContext.bias,
                limit = MAX_CANDIDATES,
                apiKey = BuildConfig.APP_GEOAPIFY_API_KEY
            )
        }.getOrNull() ?: return@coroutineScope emptyList()

        response.features
            .mapNotNull { it.toCandidate() }
            .filter { it.looksLikeAccommodation() }
            .distinctBy { it.placeId }
            .take(MAX_DETAILS_REQUESTS)
    }

    private suspend fun resolveDestinationContext(destination: String): DestinationContext? {
        val cityMatch = runCatching {
            geoapifyApi.geocodeSearch(
                text = destination,
                type = "city",
                apiKey = BuildConfig.APP_GEOAPIFY_API_KEY
            )
        }.getOrNull()?.features?.firstOrNull()

        val resolved = cityMatch ?: runCatching {
            geoapifyApi.geocodeSearch(
                text = destination,
                type = null,
                apiKey = BuildConfig.APP_GEOAPIFY_API_KEY
            )
        }.getOrNull()?.features?.firstOrNull()

        return resolved?.toDestinationContext()
    }

    private suspend fun rankWithAi(
        request: AccommodationRequest,
        candidates: List<AccommodationCandidate>
    ): List<AccommodationRecommendation>? {
        val prompt = buildAiPrompt(request, candidates)
        val json = openAiService.generateJson(
            systemPrompt = AI_SYSTEM_PROMPT,
            userPrompt = prompt,
            maxTokens = 700
        ) ?: return null

        val parsed = runCatching {
            gson.fromJson(json, AccommodationAiResponse::class.java)
        }.getOrNull() ?: return null

        if (parsed.recommendations.isEmpty()) return null

        val candidatesById = candidates.associateBy { it.placeId }
        return parsed.recommendations.mapNotNull { entry ->
            val candidate = candidatesById[entry.placeId] ?: return@mapNotNull null
            candidate.toRecommendation(
                reason = entry.reason.ifBlank {
                    buildFallbackReason(candidate, request)
                },
                source = RecommendationSource.AI
            )
        }
    }

    private fun rankHeuristically(
        request: AccommodationRequest,
        candidates: List<AccommodationCandidate>
    ): List<AccommodationRecommendation> {
        val targetPriceLevel = estimateTargetPriceLevel(request)
        return candidates
            .sortedByDescending { candidate ->
                scoreCandidate(candidate, targetPriceLevel)
            }
            .map { candidate ->
                candidate.toRecommendation(
                    reason = buildFallbackReason(candidate, request),
                    source = RecommendationSource.HEURISTIC
                )
            }
    }

    private fun mergeRankings(
        aiRecommendations: List<AccommodationRecommendation>?,
        heuristicRecommendations: List<AccommodationRecommendation>
    ): List<AccommodationRecommendation> {
        val merged = LinkedHashMap<String, AccommodationRecommendation>()
        aiRecommendations.orEmpty().forEach { merged[it.placeId] = it }
        heuristicRecommendations.forEach { recommendation ->
            merged.putIfAbsent(recommendation.placeId, recommendation)
        }
        return merged.values.take(MAX_RECOMMENDATIONS)
    }

    private fun scoreCandidate(
        candidate: AccommodationCandidate,
        targetPriceLevel: Int
    ): Double {
        val qualityScore = when (candidate.starLevel) {
            null -> 1.0
            else -> candidate.starLevel.coerceIn(1, 5) / 5.0 * 2.4
        }
        val budgetScore = when (val priceLevel = candidate.priceLevel) {
            null -> 0.5
            else -> when (abs(priceLevel - targetPriceLevel)) {
                0 -> 1.9
                1 -> 1.2
                2 -> 0.4
                else -> 0.1
            }
        }
        val distanceScore = when (val distance = candidate.distanceMeters) {
            null -> 0.2
            in 0..700 -> 0.9
            in 701..1500 -> 0.65
            in 1501..3000 -> 0.4
            else -> 0.15
        }
        val completenessScore = listOf(
            candidate.website,
            candidate.phoneNumber,
            candidate.openingHours,
            candidate.starLevel,
            candidate.address.takeIf { it.isNotBlank() }
        ).count { it != null } * 0.12

        return qualityScore + budgetScore + distanceScore + completenessScore
    }

    private fun buildFallbackReason(
        candidate: AccommodationCandidate,
        request: AccommodationRequest
    ): String {
        val nights = request.nights
        val budgetBadge = candidate.priceLevel?.toBudgetBadge()
        return when {
            candidate.starLevel != null && budgetBadge != null ->
                "${candidate.starLevel}★ и похоже на вариант с $budgetBadge для поездки на $nights ${pluralizeNights(nights)}."
            budgetBadge != null && candidate.distanceMeters != null ->
                "Похоже на вариант с $budgetBadge и находится в ${candidate.distanceMeters} м от центра поиска."
            candidate.starLevel != null ->
                "${candidate.starLevel}★ и есть достаточно данных по объекту, чтобы рассматривать его как базовый вариант."
            candidate.website != null ->
                "Есть официальный сайт и понятные контактные данные, выглядит как рабочий вариант проживания."
            else ->
                "Найден реальный объект проживания в Geoapify, подходит как стартовый вариант для выбора."
        }
    }

    private fun estimateTargetPriceLevel(request: AccommodationRequest): Int {
        val budgetPerNight = request.budget / request.nights.coerceAtLeast(1)
        return when (request.currency.uppercase()) {
            "USD" -> when {
                budgetPerNight < 80 -> 1
                budgetPerNight < 160 -> 2
                budgetPerNight < 280 -> 3
                else -> 4
            }
            "EUR" -> when {
                budgetPerNight < 75 -> 1
                budgetPerNight < 150 -> 2
                budgetPerNight < 260 -> 3
                else -> 4
            }
            "RUB" -> when {
                budgetPerNight < 7_000 -> 1
                budgetPerNight < 14_000 -> 2
                budgetPerNight < 24_000 -> 3
                else -> 4
            }
            "TRY" -> when {
                budgetPerNight < 2_500 -> 1
                budgetPerNight < 5_000 -> 2
                budgetPerNight < 8_500 -> 3
                else -> 4
            }
            "THB" -> when {
                budgetPerNight < 2_600 -> 1
                budgetPerNight < 5_200 -> 2
                budgetPerNight < 8_800 -> 3
                else -> 4
            }
            "AED" -> when {
                budgetPerNight < 300 -> 1
                budgetPerNight < 650 -> 2
                budgetPerNight < 1_100 -> 3
                else -> 4
            }
            else -> 2
        }
    }

    private fun buildAiPrompt(
        request: AccommodationRequest,
        candidates: List<AccommodationCandidate>
    ): String {
        val candidatesJson = gson.toJson(
            candidates.map {
                mapOf(
                    "placeId" to it.placeId,
                    "name" to it.name,
                    "address" to it.address,
                    "starLevel" to it.starLevel,
                    "estimatedBudgetLevel" to it.priceLevel,
                    "distanceMeters" to it.distanceMeters,
                    "website" to it.website
                )
            }
        )

        return """
            destination: ${request.destination}
            startDate: ${request.startDate}
            endDate: ${request.endDate}
            nights: ${request.nights}
            totalBudget: ${request.budget}
            currency: ${request.currency}
            Важно: priceLevel — это эвристическая оценка сегмента, а не реальная цена номера.
            candidates: $candidatesJson
        """.trimIndent()
    }

    private fun GeoapifyGeocodeFeature.toDestinationContext(): DestinationContext? {
        val properties = properties ?: return null
        val placeId = properties.placeId ?: return null
        val coordinates = geometry?.coordinates.orEmpty()
        if (coordinates.size < 2) return null
        val longitude = coordinates[0]
        val latitude = coordinates[1]
        return DestinationContext(
            filter = "place:$placeId",
            bias = "proximity:$longitude,$latitude"
        )
    }

    private fun GeoapifyPlaceFeature.toCandidate(): AccommodationCandidate? {
        val properties = properties ?: return null
        val placeId = properties.placeId ?: return null
        val coordinates = geometry?.coordinates.orEmpty()
        if (coordinates.size < 2) return null

        val starLevel = properties.accommodation?.stars
            ?: properties.datasource?.raw?.stars
        val categories = properties.categories.orEmpty()
        val name = properties.name
            ?: properties.addressLine1
            ?: return null
        val website = properties.website
            ?: properties.datasource?.raw?.contactWebsite
            ?: properties.datasource?.raw?.website

        return AccommodationCandidate(
            placeId = placeId,
            name = name,
            address = properties.formatted
                ?: listOfNotNull(properties.addressLine1, properties.addressLine2).joinToString(", "),
            latitude = coordinates[1],
            longitude = coordinates[0],
            rating = null,
            starLevel = starLevel,
            priceLevel = estimatePriceLevel(name, categories, starLevel),
            distanceMeters = properties.distance,
            website = website,
            phoneNumber = properties.contact?.phone ?: properties.datasource?.raw?.contactPhone,
            openingHours = properties.openingHours ?: properties.datasource?.raw?.openingHours,
            photoUrl = null,
            categoryTokens = categories
        )
    }

    private fun AccommodationCandidate.looksLikeAccommodation(): Boolean {
        val haystack = buildString {
            append(name.lowercase())
            append(' ')
            append(address.lowercase())
            append(' ')
            append(categoryTokens.joinToString(" ").lowercase())
        }
        return "accommodation" in categoryTokens || ACCOMMODATION_KEYWORDS.any { haystack.contains(it) }
    }

    private fun estimatePriceLevel(
        name: String,
        categories: List<String>,
        starLevel: Int?
    ): Int {
        val haystack = "${name.lowercase()} ${categories.joinToString(" ").lowercase()}"
        if (starLevel != null) {
            return when {
                starLevel <= 2 -> 1
                starLevel == 3 -> 2
                starLevel == 4 -> 3
                else -> 4
            }
        }
        return when {
            BUDGET_KEYWORDS.any { haystack.contains(it) } -> 1
            MIDRANGE_KEYWORDS.any { haystack.contains(it) } -> 2
            PREMIUM_KEYWORDS.any { haystack.contains(it) } -> 4
            else -> 2
        }
    }

    private fun AccommodationCandidate.toRecommendation(
        reason: String,
        source: RecommendationSource
    ): AccommodationRecommendation {
        return AccommodationRecommendation(
            placeId = placeId,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            starLevel = starLevel,
            priceLevel = priceLevel,
            website = website,
            photoUrl = photoUrl,
            reason = reason,
            source = source
        )
    }

    private fun Int.toBudgetBadge(): String {
        return when (this) {
            0, 1 -> "эконом-сегментом"
            2 -> "средним бюджетом"
            3 -> "комфортным бюджетом"
            else -> "премиальным бюджетом"
        }
    }

    private fun pluralizeNights(count: Int): String {
        return when {
            count % 100 in 11..19 -> "ночей"
            count % 10 == 1 -> "ночь"
            count % 10 in 2..4 -> "ночи"
            else -> "ночей"
        }
    }

    private fun hasValidGeoapifyKey(): Boolean {
        return BuildConfig.APP_GEOAPIFY_API_KEY.isNotBlank()
    }

    companion object {
        private const val MAX_DETAILS_REQUESTS = 8
        private const val MAX_CANDIDATES = 18
        private const val MAX_RECOMMENDATIONS = 4
        private val ACCOMMODATION_KEYWORDS = listOf(
            "hotel",
            "отел",
            "apart",
            "апарт",
            "hostel",
            "хостел",
            "inn",
            "suite",
            "resort",
            "lodge",
            "guest house",
            "гостиниц"
        )
        private val BUDGET_KEYWORDS = listOf(
            "hostel",
            "хостел",
            "budget",
            "capsule",
            "эконом"
        )
        private val MIDRANGE_KEYWORDS = listOf(
            "apart",
            "апарт",
            "boutique",
            "guest house"
        )
        private val PREMIUM_KEYWORDS = listOf(
            "lux",
            "luxury",
            "premium",
            "grand",
            "palace",
            "four seasons",
            "kempinski",
            "ritz",
            "hyatt"
        )

        private const val AI_SYSTEM_PROMPT = """
            Ты ранжируешь реальные варианты проживания для поездки.
            Нельзя выдумывать новые отели или placeId.
            Используй только кандидатов из входных данных.
            Учитывай, что priceLevel в данных — это эвристическая оценка бюджетного сегмента, а не реальная цена номера.
            Верни JSON вида:
            {
              "recommendations": [
                {
                  "placeId": "id",
                  "reason": "короткая причина на русском до 120 символов"
                }
              ]
            }
            Выбери от 3 до 4 лучших вариантов с учетом бюджета, даты и общего баланса локации/уровня объекта.
        """
    }
}

/**
 * Запрос на подбор проживания для поездки.
 */
data class AccommodationRequest(
    val destination: String,
    val budget: Double,
    val currency: String,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    val nights: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)
}

/**
 * Карточка рекомендации проживания для показа в мастере создания поездки.
 */
data class AccommodationRecommendation(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val rating: Float?,
    val starLevel: Int? = null,
    val priceLevel: Int?,
    val website: String?,
    val photoUrl: String?,
    val reason: String,
    val source: RecommendationSource
)

/**
 * Источник, который внёс основной вклад в рекомендацию.
 */
enum class RecommendationSource {
    AI,
    HEURISTIC
}

private data class DestinationContext(
    val filter: String,
    val bias: String?
)

private data class AccommodationCandidate(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val rating: Float?,
    val starLevel: Int?,
    val priceLevel: Int?,
    val distanceMeters: Int?,
    val website: String?,
    val phoneNumber: String?,
    val openingHours: String?,
    val photoUrl: String?,
    val categoryTokens: List<String> = emptyList()
)

private data class AccommodationAiResponse(
    val recommendations: List<AccommodationAiRecommendation> = emptyList()
)

private data class AccommodationAiRecommendation(
    val placeId: String = "",
    val reason: String = ""
)
