package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.repository.AcademyRepository
import com.athlink.app.data.repository.SessionRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A player's or coach's real bookings (no dummy data). [upcoming] / [history] are derived with
 * [SessionPolicy]; players also see their academy requests here.
 */
data class SessionState(
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val academyRequests: List<AcademyRequest> = emptyList(),
    val updatingId: String? = null,
    val error: String? = null,
    val message: String? = null
) {
    val upcoming: List<Session> get() = SessionPolicy.upcoming(sessions)
    val history: List<Session> get() = SessionPolicy.history(sessions)
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val academyRepository: AcademyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Coach bookings and academy requests of a player. */
    fun loadPlayerSessions(playerId: String) {
        if (playerId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val sessions = async { sessionRepository.getPlayerSessions(playerId) }
            val requests = async { academyRepository.getPlayerRequests(playerId) }
            val s = sessions.await()
            val r = requests.await()
            _state.update {
                it.copy(
                    isLoading = false, loaded = true,
                    sessions = s.getOrDefault(it.sessions),
                    academyRequests = r.getOrDefault(it.academyRequests),
                    error = (s.exceptionOrNull() ?: r.exceptionOrNull())?.let { e -> ErrorMessages.from(e, "Something went wrong. Please try again.") }
                )
            }
        }
    }

    fun loadCoachSessions(coachId: String) {
        if (coachId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            sessionRepository.getCoachSessions(coachId)
                .onSuccess { list -> _state.update { it.copy(isLoading = false, loaded = true, sessions = list) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, loaded = true, error = ErrorMessages.from(e, "Something went wrong. Please try again.")) } }
        }
    }

    /** Coach decision on a booking (rules allow only [SessionPolicy.coachMayTransition] moves). */
    fun updateStatus(sessionId: String, status: SessionStatus) {
        val current = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        if (!SessionPolicy.coachMayTransition(current.status, status)) return
        changeStatus(current, status, "Session ${status.label.lowercase()}")
    }

    /** Player cancels their own upcoming booking. */
    fun cancelBooking(sessionId: String) {
        val current = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        if (!SessionPolicy.playerMayCancel(current)) return
        changeStatus(current, SessionStatus.CANCELLED, "Booking cancelled")
    }

    private fun changeStatus(session: Session, status: SessionStatus, done: String) {
        viewModelScope.launch {
            _state.update { it.copy(updatingId = session.id, error = null) }
            sessionRepository.updateSessionStatus(session.id, status)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            updatingId = null, message = done,
                            sessions = s.sessions.map { if (it.id == session.id) it.copy(status = status) else it }
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(updatingId = null, error = ErrorMessages.from(e, "Something went wrong. Please try again.")) } }
        }
    }

    /** Player withdraws a pending academy request. */
    fun cancelAcademyRequest(requestId: String) {
        viewModelScope.launch {
            _state.update { it.copy(updatingId = requestId, error = null) }
            academyRepository.cancelRequest(requestId)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            updatingId = null, message = "Request withdrawn",
                            academyRequests = s.academyRequests.map {
                                if (it.requestId == requestId) it.copy(status = com.athlink.app.data.model.AcademyRequestStatus.CANCELLED.name) else it
                            }
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(updatingId = null, error = ErrorMessages.from(e, "Something went wrong. Please try again.")) } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}
