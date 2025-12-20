package com.triloo.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication Repository Interface
 * 
 * TODO: Implement real authentication with Firebase Auth or custom backend
 */
interface AuthRepository {
    /** Current auth state */
    val authState: Flow<AuthState>
    
    /** Current user (null if not authenticated) */
    val currentUser: User?
    
    /** Sign in with email and password */
    suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User>
    
    /** Sign up with email and password */
    suspend fun signUpWithEmail(data: SignUpData): AuthResult<User>
    
    /** Sign in with Google */
    suspend fun signInWithGoogle(idToken: String): AuthResult<User>
    
    /** Sign out */
    suspend fun signOut()
    
    /** Send password reset email */
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>
    
    /** Update user profile */
    suspend fun updateProfile(displayName: String?, avatarUrl: String?): AuthResult<User>
    
    /** Delete user account */
    suspend fun deleteAccount(): AuthResult<Unit>
}

/**
 * Fake implementation for development
 * 
 * TODO: Replace with real implementation (Firebase/custom backend)
 */
@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState.asStateFlow()
    
    private var _currentUser: User? = null
    override val currentUser: User? get() = _currentUser
    
    // Fake user storage for development
    private val fakeUsers = mutableMapOf<String, Pair<User, String>>() // email -> (user, password)
    
    init {
        // Add a test user
        val testUser = User(
            id = "test-user-id",
            email = "test@triloo.app",
            displayName = "Тестовый Пользователь"
        )
        fakeUsers[testUser.email] = testUser to "password123"
    }
    
    override suspend fun signInWithEmail(credentials: SignInCredentials): AuthResult<User> {
        _authState.value = AuthState.Loading
        
        // Simulate network delay
        delay(1000)
        
        // Validate email format
        if (!isValidEmail(credentials.email)) {
            _authState.value = AuthState.Error(AuthError.InvalidEmail.message)
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        
        // Check if user exists
        val userEntry = fakeUsers[credentials.email.lowercase()]
        if (userEntry == null) {
            _authState.value = AuthState.Error(AuthError.UserNotFound.message)
            return AuthResult.Failure(AuthError.UserNotFound)
        }
        
        // Check password
        val (user, password) = userEntry
        if (password != credentials.password) {
            _authState.value = AuthState.Error(AuthError.WrongPassword.message)
            return AuthResult.Failure(AuthError.WrongPassword)
        }
        
        // Success
        _currentUser = user.copy(lastLoginAt = System.currentTimeMillis())
        _authState.value = AuthState.Authenticated(_currentUser!!)
        return AuthResult.Success(_currentUser!!)
    }
    
    override suspend fun signUpWithEmail(data: SignUpData): AuthResult<User> {
        _authState.value = AuthState.Loading
        
        // Simulate network delay
        delay(1000)
        
        // Validate email
        if (!isValidEmail(data.email)) {
            _authState.value = AuthState.Error(AuthError.InvalidEmail.message)
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        
        // Validate password
        if (data.password.length < 6) {
            _authState.value = AuthState.Error(AuthError.WeakPassword.message)
            return AuthResult.Failure(AuthError.WeakPassword)
        }
        
        // Check if email already in use
        if (fakeUsers.containsKey(data.email.lowercase())) {
            _authState.value = AuthState.Error(AuthError.EmailAlreadyInUse.message)
            return AuthResult.Failure(AuthError.EmailAlreadyInUse)
        }
        
        // Create new user
        val newUser = User(
            email = data.email.lowercase(),
            displayName = data.displayName
        )
        fakeUsers[newUser.email] = newUser to data.password
        
        _currentUser = newUser
        _authState.value = AuthState.Authenticated(newUser)
        return AuthResult.Success(newUser)
    }
    
    override suspend fun signInWithGoogle(idToken: String): AuthResult<User> {
        _authState.value = AuthState.Loading
        
        // Simulate network delay
        delay(1500)
        
        // TODO: Implement real Google Sign-In
        // For now, create a fake Google user
        val googleUser = User(
            id = "google-user-${idToken.take(8)}",
            email = "google.user@gmail.com",
            displayName = "Google Пользователь",
            avatarUrl = "https://lh3.googleusercontent.com/a/default-user"
        )
        
        _currentUser = googleUser
        _authState.value = AuthState.Authenticated(googleUser)
        return AuthResult.Success(googleUser)
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
        
        // In real implementation, send email via Firebase/backend
        // For now, just return success
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



