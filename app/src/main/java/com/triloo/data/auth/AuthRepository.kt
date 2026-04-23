package com.triloo.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Контракт репозитория авторизации.
 * Использует Firebase при наличии конфигурации и локальную реализацию как резервный вариант.
 */
interface AuthRepository {
    /** Текущее состояние авторизации. */
    val authState: Flow<AuthState>
    
    /** Текущий пользователь или `null`, если вход не выполнен. */
    val currentUser: User?
    
    /** Выполняет вход по e-mail и паролю. */
    suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User>
    
    /** Создаёт пользователя по e-mail и паролю. */
    suspend fun signUpWithEmail(data: SignUpData): AuthResult<User>

    /** Завершает текущую сессию. */
    suspend fun signOut()
    
    /** Отправляет письмо для сброса пароля. */
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>
    
    /** Обновляет публичные данные профиля. */
    suspend fun updateProfile(displayName: String?, avatarUrl: String?): AuthResult<User>
    
    /** Удаляет текущую учётную запись. */
    suspend fun deleteAccount(): AuthResult<Unit>
}

/**
 * Локальная реализация с e-mail и паролем.
 * Используется как резервный вариант, когда server/Firebase не настроены.
 */
@Singleton
class LocalAuthRepository @Inject constructor() : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState.asStateFlow()
    
    private var _currentUser: User? = null
    override val currentUser: User? get() = _currentUser
    
    // Локальное хранилище пользователей для режима разработки.
    private val fakeUsers = mutableMapOf<String, Pair<User, String>>() // email -> (user, password)
    
    init {
        // Добавляем тестового пользователя.
        val testUser = User(
            id = "test-user-id",
            email = "test@triloo.app",
            displayName = "Тестовый Пользователь"
        )
        fakeUsers[testUser.email] = testUser to "password123"
    }
    
    override suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User> {
        _authState.value = AuthState.Loading
        
        // Имитируем сетевую задержку.
        delay(1000)
        
        // Проверяем формат e-mail.
        if (!isValidEmail(credentials.email)) {
            _authState.value = AuthState.Error(AuthError.InvalidEmail.message)
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        
        // Проверяем, существует ли пользователь.
        val userEntry = fakeUsers[credentials.email.lowercase()]
        if (userEntry == null) {
            _authState.value = AuthState.Error(AuthError.UserNotFound.message)
            return AuthResult.Failure(AuthError.UserNotFound)
        }
        
        // Проверяем пароль.
        val (user, password) = userEntry
        if (password != credentials.password) {
            _authState.value = AuthState.Error(AuthError.WrongPassword.message)
            return AuthResult.Failure(AuthError.WrongPassword)
        }
        
        // Возвращаем успешный результат.
        _currentUser = user.copy(lastLoginAt = System.currentTimeMillis())
        _authState.value = AuthState.Authenticated(_currentUser!!)
        return AuthResult.Success(_currentUser!!)
    }
    
    override suspend fun signUpWithEmail(data: SignUpData): AuthResult<User> {
        _authState.value = AuthState.Loading
        
        // Имитируем сетевую задержку.
        delay(1000)
        
        // Проверяем e-mail.
        if (!isValidEmail(data.email)) {
            _authState.value = AuthState.Error(AuthError.InvalidEmail.message)
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        
        // Проверяем пароль.
        if (data.password.length < 6) {
            _authState.value = AuthState.Error(AuthError.WeakPassword.message)
            return AuthResult.Failure(AuthError.WeakPassword)
        }
        
        // Проверяем, не занят ли e-mail.
        if (fakeUsers.containsKey(data.email.lowercase())) {
            _authState.value = AuthState.Error(AuthError.EmailAlreadyInUse.message)
            return AuthResult.Failure(AuthError.EmailAlreadyInUse)
        }
        
        // Создаём нового пользователя.
        val newUser = User(
            email = data.email.lowercase(),
            displayName = data.displayName
        )
        fakeUsers[newUser.email] = newUser to data.password
        
        _currentUser = newUser
        _authState.value = AuthState.Authenticated(newUser)
        return AuthResult.Success(newUser)
    }
    
    override suspend fun signOut() {
        delay(500)
        _currentUser = null
        _authState.value = AuthState.Unauthenticated
    }
    
    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        delay(1000)
        
        if (!isValidEmail(email)) {
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        
        // В реальной реализации письмо уйдёт через Firebase или backend.
        // Пока просто возвращаем успешный результат.
        return AuthResult.Success(Unit)
    }
    
    override suspend fun updateProfile(displayName: String?, avatarUrl: String?): AuthResult<User> {
        val user = _currentUser ?: return AuthResult.Failure(AuthError.UserNotFound)
        
        delay(500)
        
        val updatedUser = user.copy(
            displayName = displayName ?: user.displayName,
            avatarUrl = avatarUrl ?: user.avatarUrl
        )
        
        _currentUser = updatedUser
        fakeUsers[user.email] = updatedUser to (fakeUsers[user.email]?.second ?: "")
        _authState.value = AuthState.Authenticated(updatedUser)
        
        return AuthResult.Success(updatedUser)
    }
    
    override suspend fun deleteAccount(): AuthResult<Unit> {
        val user = _currentUser ?: return AuthResult.Failure(AuthError.UserNotFound)
        
        delay(1000)
        
        fakeUsers.remove(user.email)
        _currentUser = null
        _authState.value = AuthState.Unauthenticated
        
        return AuthResult.Success(Unit)
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
