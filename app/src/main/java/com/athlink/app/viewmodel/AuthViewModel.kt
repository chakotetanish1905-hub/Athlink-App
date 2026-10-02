package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.User
import com.athlink.app.data.model.UserRole
import com.athlink.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        if (authRepository.isLoggedIn) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true)
                val result = authRepository.getCurrentUser()
                result.onSuccess { user ->
                    _state.value = AuthState(user = user, isLoggedIn = true)
                }.onFailure {
                    _state.value = AuthState(isLoggedIn = false)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = authRepository.login(email, password)
            result.onSuccess { user ->
                _state.value = AuthState(user = user, isLoggedIn = true)
            }.onFailure { e ->
                _state.value = AuthState(error = e.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, email: String, password: String, role: UserRole) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = authRepository.register(name, email, password, role)
            result.onSuccess { user ->
                _state.value = AuthState(user = user, isLoggedIn = true)
            }.onFailure { e ->
                _state.value = AuthState(error = e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _state.value = AuthState(isLoggedIn = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
