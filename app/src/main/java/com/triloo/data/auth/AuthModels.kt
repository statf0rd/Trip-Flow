package com.triloo.data.auth

import java.util.UUID

/**
 * Модель пользователя в текущем слое авторизации.
 *
 * TODO: Расширить дополнительными полями после появления backend.
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
 * Состояние процесса авторизации.
 */
sealed class AuthState {
    /** Пользователь не авторизован. */
    object Unauthenticated : AuthState()
    
    /** Авторизация выполняется. */
    object Loading : AuthState()
    
    /** Пользователь успешно авторизован. */
    data class Authenticated(val user: User) : AuthState()
    
    /** Во время авторизации произошла ошибка. */
    data class Error(val message: String) : AuthState()
}

/**
 * Данные для входа по e-mail и паролю.
 */
data class SignInCredentials(
    val email: String,
    val password: String
)

/**
 * Данные для регистрации нового пользователя.
 */
data class SignUpData(
    val email: String,
    val password: String,
    val displayName: String
)

/**
 * Поддерживаемые провайдеры авторизации.
 */
enum class AuthProvider {
    EMAIL
}

/**
 * Обёртка результата операций авторизации.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Failure(val error: AuthError) : AuthResult<Nothing>()
}

/**
 * Типы ошибок авторизации.
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

