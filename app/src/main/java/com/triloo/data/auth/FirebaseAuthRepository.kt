package com.triloo.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.userProfileChangeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    private var _currentUser: User? = null
    override val currentUser: User? get() = _currentUser

    init {
        updateFromFirebaseUser(firebaseAuth.currentUser)
        firebaseAuth.addAuthStateListener { auth ->
            updateFromFirebaseUser(auth.currentUser)
        }
    }

    override suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User> {
        _authState.value = AuthState.Loading
        return runCatching {
            firebaseAuth
                .signInWithEmailAndPassword(credentials.email.trim(), credentials.password)
                .await()
            val user = firebaseAuth.currentUser?.toUser()
                ?: return AuthResult.Failure(AuthError.Unknown("Не удалось получить пользователя"))
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
            firebaseAuth
                .createUserWithEmailAndPassword(data.email.trim(), data.password)
                .await()
            val firebaseUser = firebaseAuth.currentUser
                ?: return AuthResult.Failure(AuthError.Unknown("Не удалось создать пользователя"))
            if (data.displayName.isNotBlank()) {
                val profileUpdate = userProfileChangeRequest {
                    displayName = data.displayName.trim()
                }
                firebaseUser.updateProfile(profileUpdate).await()
                firebaseUser.reload().await()
            }
            val user = (firebaseAuth.currentUser ?: firebaseUser).toUser()
            _currentUser = user
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        }.getOrElse { error ->
            val authError = error.toAuthError()
            _authState.value = AuthState.Error(authError.message)
            AuthResult.Failure(authError)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<User> {
        _authState.value = AuthState.Loading
        if (idToken.isBlank()) {
            val message = "Google Sign-In не настроен (проверьте GOOGLE_WEB_CLIENT_ID)"
            _authState.value = AuthState.Error(message)
            return AuthResult.Failure(AuthError.Unknown(message))
        }

        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            val user = firebaseAuth.currentUser?.toUser()
                ?: return AuthResult.Failure(AuthError.Unknown("Не удалось получить пользователя"))
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
        firebaseAuth.signOut()
        _currentUser = null
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return runCatching {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(Unit)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    override suspend fun updateProfile(displayName: String?, avatarUrl: String?): AuthResult<User> {
        val firebaseUser = firebaseAuth.currentUser
            ?: return AuthResult.Failure(AuthError.UserNotFound)

        return runCatching {
            val profileUpdate = userProfileChangeRequest {
                displayName?.trim()?.takeIf { it.isNotBlank() }?.let { this.displayName = it }
                avatarUrl?.trim()?.takeIf { it.isNotBlank() }?.let { photoUri = android.net.Uri.parse(it) }
            }
            firebaseUser.updateProfile(profileUpdate).await()
            firebaseUser.reload().await()
            val user = (firebaseAuth.currentUser ?: firebaseUser).toUser()
            _currentUser = user
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    override suspend fun deleteAccount(): AuthResult<Unit> {
        val firebaseUser = firebaseAuth.currentUser
            ?: return AuthResult.Failure(AuthError.UserNotFound)

        return runCatching {
            firebaseUser.delete().await()
            _currentUser = null
            _authState.value = AuthState.Unauthenticated
            AuthResult.Success(Unit)
        }.getOrElse { error ->
            AuthResult.Failure(error.toAuthError())
        }
    }

    private fun updateFromFirebaseUser(firebaseUser: FirebaseUser?) {
        _currentUser = firebaseUser?.toUser()
        _authState.value = if (_currentUser == null) {
            AuthState.Unauthenticated
        } else {
            AuthState.Authenticated(_currentUser!!)
        }
    }

    private fun FirebaseUser.toUser(): User {
        val safeEmail = email ?: "unknown@triloo.app"
        val resolvedName = displayName?.takeIf { it.isNotBlank() }
            ?: safeEmail.substringBefore("@").ifBlank { "Пользователь" }
        return User(
            id = uid,
            email = safeEmail,
            displayName = resolvedName,
            avatarUrl = photoUrl?.toString(),
            phoneNumber = phoneNumber,
            createdAt = metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = metadata?.lastSignInTimestamp ?: System.currentTimeMillis()
        )
    }

    private fun Throwable.toAuthError(): AuthError {
        return when (this) {
            is FirebaseAuthException -> when (errorCode) {
                "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
                "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
                "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
                "ERROR_WRONG_PASSWORD" -> AuthError.WrongPassword
                "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkError
                else -> AuthError.Unknown(message)
            }
            else -> AuthError.Unknown(message)
        }
    }
}
