package com.triloo.data.auth

import com.triloo.data.remote.BackendAuthApi
import com.triloo.data.remote.BackendCurrentUserResponse
import com.triloo.data.remote.BackendSignInRequest
import com.triloo.data.remote.BackendSignUpRequest
import com.triloo.data.remote.BackendUpdateProfileRequest
import com.triloo.data.remote.BackendUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendAuthRepository @Inject constructor(
    private val backendAuthApi: BackendAuthApi,
    private val serverSessionRepository: ServerSessionRepository
) : AuthRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    private var _currentUser: User? = null
    override val currentUser: User? get() = _currentUser

    init {
        repositoryScope.launch {
            serverSessionRepository.session.collect { session ->
                _currentUser = session.user
                _authState.value = if (session.user == null) {
                    AuthState.Unauthenticated
                } else {
                    AuthState.Authenticated(session.user)
                }
            }
        }
    }

    override suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User> {
        _authState.value = AuthState.Loading
        return runCatching {
            val response = backendAuthApi.signIn(
                BackendSignInRequest(
                    email = credentials.email.trim(),
                    password = credentials.password
                )
            )
            val user = response.user.toUser()
            serverSessionRepository.saveSession(response.token, user)
            _currentUser = user
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        }.getOrElse { error ->
            val authError = error.toAuthError()
            _authState.value = AuthState.Error(authError.message)
            AuthResult.Failure(authError)
        }
    }

    override suspend fun signUpWithEmail(data: SignUpData): AuthResult<User> {
        _authState.value = AuthState.Loading
        return runCatching {
            val response = backendAuthApi.signUp(
                BackendSignUpRequest(
                    email = data.email.trim(),
                    password = data.password,
                    displayName = data.displayName.trim()
                )
            )
            val user = response.user.toUser()
            serverSessionRepository.saveSession(response.token, user)
            _currentUser = user
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        }.getOrElse { error ->
            val authError = error.toAuthError()
            _authState.value = AuthState.Error(authError.message)
            AuthResult.Failure(authError)
        }
    }

    override suspend fun signOut() {
        val token = serverSessionRepository.getSession().authToken
        runCatching {
            if (token != null) {
                backendAuthApi.signOut(token.asBearer())
            }
        }
        _currentUser = null
        serverSessionRepository.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return runCatching {
            backendAuthApi.sendPasswordReset(com.triloo.data.remote.BackendPasswordResetRequest(email.trim()))
            AuthResult.Success(Unit)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    override suspend fun updateProfile(displayName: String?, avatarUrl: String?): AuthResult<User> {
        val token = serverSessionRepository.getSession().authToken
            ?: return AuthResult.Failure(AuthError.UserNotFound)

        return runCatching {
            val response: BackendCurrentUserResponse = backendAuthApi.updateProfile(
                authorization = token.asBearer(),
                request = BackendUpdateProfileRequest(
                    displayName = displayName?.trim(),
                    avatarUrl = avatarUrl?.trim()
                )
            )
            val user = response.user.toUser()
            serverSessionRepository.updateUser(user)
            _currentUser = user
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    override suspend fun deleteAccount(): AuthResult<Unit> {
        val token = serverSessionRepository.getSession().authToken
            ?: return AuthResult.Failure(AuthError.UserNotFound)

        return runCatching {
            backendAuthApi.deleteAccount(token.asBearer())
            _currentUser = null
            serverSessionRepository.clearSession()
            _authState.value = AuthState.Unauthenticated
            AuthResult.Success(Unit)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    private fun BackendUser.toUser(): User {
        return User(
            id = id,
            email = email,
            displayName = displayName,
            avatarUrl = avatarUrl,
            phoneNumber = phoneNumber,
            preferredCurrency = preferredCurrency,
            createdAt = createdAt,
            lastLoginAt = lastLoginAt
        )
    }

    private fun String.asBearer(): String = "Bearer $this"

    private fun Throwable.toAuthError(): AuthError {
        return when (this) {
            is IOException -> AuthError.NetworkError
            is HttpException -> when (code()) {
                400 -> AuthError.Unknown("Некорректные данные")
                401 -> AuthError.WrongPassword
                404 -> AuthError.UserNotFound
                409 -> AuthError.EmailAlreadyInUse
                else -> AuthError.Unknown(message())
            }
            else -> AuthError.Unknown(message)
        }
    }
}
