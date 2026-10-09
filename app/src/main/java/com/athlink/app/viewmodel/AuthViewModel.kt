package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.CoachField
import com.athlink.app.data.model.CoachRegistration
import com.athlink.app.data.model.PlayerSignupField
import com.athlink.app.data.model.PlayerSignupForm
import com.athlink.app.data.model.User
import com.athlink.app.data.model.UserRole
import com.athlink.app.data.repository.AuthRepository
import com.athlink.app.utils.ErrorMessages
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
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
    val isLoggedIn: Boolean = false,
    /** Per-field validation errors for the coach registration form. */
    val coachFieldErrors: Map<CoachField, String> = emptyMap(),
    /** Per-field validation errors for the player account form. */
    val playerFieldErrors: Map<PlayerSignupField, String> = emptyMap(),
    /** True until the stored session (if any) has been resolved at app start. */
    val isCheckingSession: Boolean = false
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
            _state.value = _state.value.copy(isLoading = true, isCheckingSession = true)
            viewModelScope.launch {
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
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = authRepository.login(email.trim(), password)
            result.onSuccess { user ->
                _state.value = AuthState(user = user, isLoggedIn = true)
            }.onFailure { e ->
                _state.value = AuthState(error = ErrorMessages.auth(e, "Login failed. Please try again.", forLogin = true))
            }
        }
    }

    /**
     * Player account creation (step 1). Validates centrally, then creates the Auth account and the
     * private `users/{uid}` record (role PLAYER, profile INCOMPLETE). Navigation then sends the
     * player into onboarding. Returns false (and fills [AuthState.playerFieldErrors]) when invalid.
     * Ignored while a request is already running, so a double tap can't create two attempts.
     */
    fun registerPlayer(form: PlayerSignupForm): Boolean {
        if (_state.value.isLoading) return false
        val errors = form.validate()
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(playerFieldErrors = errors, error = null)
            return false
        }
        _state.value = _state.value.copy(isLoading = true, error = null, playerFieldErrors = emptyMap())
        viewModelScope.launch {
            authRepository.registerPlayer(form)
                .onSuccess { user -> _state.value = AuthState(user = user, isLoggedIn = true) }
                .onFailure { e ->
                    val fieldErrors = when (e) {
                        is FirebaseAuthUserCollisionException -> mapOf(PlayerSignupField.EMAIL to "An account with this email already exists.")
                        is FirebaseAuthWeakPasswordException -> mapOf(PlayerSignupField.PASSWORD to "That password is too weak. Use at least 6 characters.")
                        is FirebaseAuthInvalidCredentialsException -> mapOf(PlayerSignupField.EMAIL to "Enter a valid email address.")
                        else -> emptyMap()
                    }
                    _state.value = AuthState(
                        error = if (fieldErrors.isEmpty()) ErrorMessages.auth(e, "We couldn't create your account. Please try again.") else null,
                        playerFieldErrors = fieldErrors
                    )
                }
        }
        return true
    }

    /** Clears the error shown under a player signup field once the user edits it. */
    fun clearPlayerFieldError(field: PlayerSignupField) {
        val current = _state.value.playerFieldErrors
        if (field in current) _state.value = _state.value.copy(playerFieldErrors = current - field)
    }

    /** Replaces the signed-in user after their profile changed (e.g. onboarding completed). */
    fun onUserUpdated(user: User) {
        val current = _state.value.user ?: return
        if (current.uid == user.uid) _state.value = _state.value.copy(user = user)
    }

    @Deprecated("Players use registerPlayer (consent + onboarding); coaches and organisations have their own flows.")
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

    /**
     * Validates the coach form and, if it is valid, creates the coach account
     * (Auth user + `users/{uid}` + `coaches/{uid}`).
     * Returns false (and fills [AuthState.coachFieldErrors]) when validation fails.
     */
    fun registerCoach(registration: CoachRegistration): Boolean {
        val errors = registration.validate()
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(coachFieldErrors = errors, error = null)
            return false
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, coachFieldErrors = emptyMap())
            authRepository.registerCoach(registration)
                .onSuccess { user ->
                    _state.value = AuthState(user = user, isLoggedIn = true)
                }.onFailure { e ->
                    val fieldErrors = when (e) {
                        is FirebaseAuthUserCollisionException -> mapOf(CoachField.EMAIL to "An account with this email already exists")
                        is FirebaseAuthWeakPasswordException -> mapOf(CoachField.PASSWORD to "Password is too weak")
                        is FirebaseAuthInvalidCredentialsException -> mapOf(CoachField.EMAIL to "Enter a valid email address")
                        else -> emptyMap()
                    }
                    _state.value = AuthState(
                        error = when {
                            fieldErrors.isNotEmpty() -> null
                            e is FirebaseNetworkException -> "No internet connection. Please try again."
                            else -> e.message ?: "Registration failed"
                        },
                        coachFieldErrors = fieldErrors
                    )
                }
        }
        return true
    }

    /**
     * Creates an organisation account: Auth user + `users/{uid}` + an UNVERIFIED
     * `organisations/{uid}`. The organisation is then routed into onboarding / verification.
     */
    fun registerOrganisation(name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authRepository.registerOrganisation(name, email, password)
                .onSuccess { user -> _state.value = AuthState(user = user, isLoggedIn = true) }
                .onFailure { e ->
                    _state.value = AuthState(error = when (e) {
                        is FirebaseAuthUserCollisionException -> "An account with this email already exists"
                        is FirebaseAuthWeakPasswordException -> "Password is too weak (use at least 6 characters)"
                        is FirebaseAuthInvalidCredentialsException -> "Enter a valid email address"
                        is FirebaseNetworkException -> "No internet connection. Please try again."
                        else -> "Registration failed. Please try again."
                    })
                }
        }
    }

    /** Clears the error shown under a coach form field once the user edits it. */
    fun clearCoachFieldError(field: CoachField) {
        val current = _state.value.coachFieldErrors
        if (field in current) _state.value = _state.value.copy(coachFieldErrors = current - field)
    }

    fun logout() {
        authRepository.logout()
        _state.value = AuthState(isLoggedIn = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, coachFieldErrors = emptyMap(), playerFieldErrors = emptyMap())
    }
}
