package com.triloo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.auth.AuthRepository
import com.triloo.data.auth.AuthResult
import com.triloo.data.auth.SignInCredentials
import com.triloo.data.auth.SignUpData
import com.triloo.data.auth.User
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = authRepository.signInWithEmail(SignInCredentials(email, password))) {
                is AuthResult.Success -> {
                    onAuthSuccess(result.data)
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = authRepository.signUpWithEmail(SignUpData(email, password, name))) {
                is AuthResult.Success -> {
                    onAuthSuccess(result.data)
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, error = null) }
                    onSuccess()
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
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
        userProfileRepository.setAuthenticated(user.displayName, user.email)
        _uiState.update { it.copy(isLoading = false, error = null, currentUser = user) }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null
)
