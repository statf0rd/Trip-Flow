package com.triloo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.auth.AuthRepository
import com.triloo.data.auth.AuthResult
import com.triloo.data.auth.SignInCredentials
import com.triloo.data.auth.SignUpData
import com.triloo.data.auth.User
import com.triloo.data.sync.LocalIdentityMigrationRepository
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Координирует сценарии входа и синхронизирует успешную авторизацию с локальным профилем.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val localIdentityMigrationRepository: LocalIdentityMigrationRepository,
    private val onlineSyncRepository: OnlineSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val isSubmitting = AtomicBoolean(false)

    fun signIn(email: String, password: String) {
        if (!isSubmitting.compareAndSet(false, true)) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = authRepository.signInWithEmail(SignInCredentials(email, password))) {
                    is AuthResult.Success -> {
                        onAuthSuccess(result.data)
                    }
                    is AuthResult.Failure -> {
                        _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    }
                }
            } finally {
                isSubmitting.set(false)
            }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        if (!isSubmitting.compareAndSet(false, true)) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = authRepository.signUpWithEmail(SignUpData(email, password, name))) {
                    is AuthResult.Success -> {
                        onAuthSuccess(result.data)
                    }
                    is AuthResult.Failure -> {
                        _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    }
                }
            } finally {
                isSubmitting.set(false)
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit) {
        if (!isSubmitting.compareAndSet(false, true)) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = authRepository.sendPasswordResetEmail(email)) {
                    is AuthResult.Success -> {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                        onSuccess()
                    }
                    is AuthResult.Failure -> {
                        _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    }
                }
            } finally {
                isSubmitting.set(false)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            userProfileRepository.signOut()
            _uiState.update { it.copy(currentUser = null, isLoading = false, error = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun onAuthSuccess(user: User) {
        val previousProfile = userProfileRepository.getProfile()
        userProfileRepository.setAuthenticated(user.id, user.displayName, user.email)
        localIdentityMigrationRepository.migrateDeviceIdentity(previousProfile.deviceId, user)
        onlineSyncRepository.bootstrapSync()
        _uiState.update { it.copy(isLoading = false, error = null, currentUser = user) }
    }
}

/**
 * Состояние экранов авторизации.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null
)
