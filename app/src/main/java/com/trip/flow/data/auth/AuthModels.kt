package com.trip.flow.data.auth

import java.util.UUID

/**
 * User model for Trip Flow
 * 
 * TODO: Extend with additional fields when backend is implemented
 */
data class User(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val phoneNumber: String? = null,
    val preferredCurrency: String = "RUB",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

/**
 * Authentication state
 */
sealed class AuthState {
    /** User is not authenticated */
    object Unauthenticated : AuthState()
    
    /** Authentication is in progress */
    object Loading : AuthState()
    
    /** User is authenticated */
    data class Authenticated(val user: User) : AuthState()
    
    /** Authentication failed */
    data class Error(val message: String) : AuthState()
}

/**
 * Sign in credentials
 */
data class SignInCredentials(
    val email: String,
    val password: String
)

/**
 * Sign up data
 */
data class SignUpData(
    val email: String,
    val password: String,
    val displayName: String
)

/**
 * Auth provider types
 */
enum class AuthProvider {
    EMAIL,
    GOOGLE,
    APPLE // TODO: Implement later
}

/**
 * Result wrapper for auth operations
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Failure(val error: AuthError) : AuthResult<Nothing>()
}

/**
 * Auth error types
 */
sealed class AuthError(val message: String) {
    object InvalidEmail : AuthError("Неверный формат email")
    object WeakPassword : AuthError("Пароль слишком простой (минимум 6 символов)")
    object EmailAlreadyInUse : AuthError("Этот email уже используется")
    object UserNotFound : AuthError("Пользователь не найден")
    object WrongPassword : AuthError("Неверный пароль")
    object NetworkError : AuthError("Ошибка сети. Проверьте соединение")
    data class Unknown(val cause: String?) : AuthError(cause ?: "Неизвестная ошибка")
}

