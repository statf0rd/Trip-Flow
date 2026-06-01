package com.triloo.data.sync

import com.triloo.BuildConfig
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.remote.BackendTripApi
import com.triloo.data.remote.JoinByInviteRequest
import com.triloo.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteTripInviteRepository @Inject constructor(
    private val backendTripApi: BackendTripApi,
    private val serverSessionRepository: ServerSessionRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val onlineSyncRepository: OnlineSyncRepository
) {

    suspend fun joinByInviteCode(inviteCode: String, displayName: String?): Result<String> {
        val token = resolveAuthorizedToken() ?: return Result.failure(
            IllegalStateException("Войдите по e-mail, чтобы присоединиться к поездке онлайн")
        )

        return runCatching {
            val response = backendTripApi.joinByInviteCode(
                authorization = token.asBearer(),
                request = JoinByInviteRequest(
                    inviteCode = inviteCode.trim().uppercase(),
                    displayName = displayName?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            // Join changes the server snapshot at response.serverUpdatedAt.
            // Pulling from the previous cursor is required; setting lastSyncAt
            // first would skip the exact snapshot that contains the new member.
            onlineSyncRepository.pullRemoteChanges(sinceOverride = 0L)
            response.tripId
        }.recoverCatching { error ->
            throw error.toUserFacingError()
        }
    }

    private suspend fun resolveAuthorizedToken(): String? {
        if (BuildConfig.APP_TRILOO_BACKEND_URL.isBlank()) return null
        if (!appSettingsRepository.settings.first().syncEnabled) return null
        return serverSessionRepository.getSession().authToken
    }

    private fun String.asBearer(): String = "Bearer $this"

    private fun Throwable.toUserFacingError(): Throwable {
        return when (this) {
            is IOException -> IllegalStateException("Не удалось связаться с сервером")
            is HttpException -> IllegalStateException(
                when (code()) {
                    401 -> "Нужно заново войти в аккаунт"
                    404 -> "Поездка по коду не найдена"
                    409 -> "Вы уже состоите в этой поездке"
                    else -> message()
                }
            )
            else -> this
        }
    }
}
