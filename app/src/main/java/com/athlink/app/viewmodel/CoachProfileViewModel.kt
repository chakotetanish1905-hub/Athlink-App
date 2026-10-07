package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.CoachProfileCompletion
import com.athlink.app.data.model.CoachProfileCompletionResult
import com.athlink.app.data.model.CoachProfileSnapshot
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.model.User
import com.athlink.app.data.repository.CoachRepository
import com.athlink.app.data.repository.SessionRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachProfileState(
    val isLoading: Boolean = false,
    /** Null until loaded, or when the coach has no `coaches/{uid}` document. */
    val profile: CoachProfileSnapshot? = null,
    val completion: CoachProfileCompletionResult? = null,
    val completedSessions: Int = 0,
    /** Loaded successfully but no coach profile document exists for this account. */
    val notFound: Boolean = false,
    val error: String? = null
)

/** State for the signed-in coach's own profile screen (and, later, the dashboard card). */
@HiltViewModel
class CoachProfileViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CoachProfileState())
    val state: StateFlow<CoachProfileState> = _state.asStateFlow()

    private var loadedFor: String? = null

    /** Loads once per user; call [refresh] to force a reload. */
    fun load(user: User) {
        if (loadedFor == user.uid && _state.value.error == null) return
        loadedFor = user.uid
        fetch(user)
    }

    fun refresh(user: User) {
        loadedFor = user.uid
        fetch(user)
    }

    private fun fetch(user: User) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val sessionsJob = async { sessionRepository.getCoachSessions(user.uid) }
            coachRepository.getOwnProfile(user.uid, user.email)
                .onSuccess { snapshot ->
                    val completed = sessionsJob.await().getOrDefault(emptyList())
                        .count { it.coachId == user.uid && it.status == SessionStatus.COMPLETED }
                    _state.value = CoachProfileState(
                        profile = snapshot,
                        completion = snapshot?.let { CoachProfileCompletion.calculate(it) },
                        completedSessions = completed,
                        notFound = snapshot == null
                    )
                }
                .onFailure { e ->
                    sessionsJob.cancel()
                    _state.value = _state.value.copy(isLoading = false, error = e.toUserMessage())
                }
        }
    }

    private fun Throwable.toUserMessage(): String = when {
        this is FirebaseNetworkException -> "No internet connection. Check your connection and try again."
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have permission to view this profile. Try logging out and back in."
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Couldn't reach the server. Please try again."
        else -> "Couldn't load your profile. Please try again."
    }
}
