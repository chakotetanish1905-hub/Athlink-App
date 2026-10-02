package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionState(
    val isLoading: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val error: String? = null,
    val bookingSuccess: Boolean = false,
    val selectedDate: String = "",
    val selectedTimeSlot: String = "",
    val availableTimeSlots: List<String> = DummyData.timeSlots
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun loadPlayerSessions(playerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = sessionRepository.getPlayerSessions(playerId)
            result.onSuccess { sessions ->
                _state.value = _state.value.copy(isLoading = false, sessions = sessions)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadCoachSessions(coachId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = sessionRepository.getCoachSessions(coachId)
            result.onSuccess { sessions ->
                _state.value = _state.value.copy(isLoading = false, sessions = sessions)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectDate(date: String) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun selectTimeSlot(slot: String) {
        _state.value = _state.value.copy(selectedTimeSlot = slot)
    }

    fun bookSession(coach: Coach, playerId: String, playerName: String) {
        viewModelScope.launch {
            val session = Session(
                coachId = coach.uid,
                coachName = coach.name,
                playerId = playerId,
                playerName = playerName,
                sport = coach.sport,
                date = _state.value.selectedDate,
                timeSlot = _state.value.selectedTimeSlot,
                status = SessionStatus.PENDING,
                price = coach.hourlyRate,
                location = coach.location
            )
            _state.value = _state.value.copy(isLoading = true)
            val result = sessionRepository.bookSession(session)
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, bookingSuccess = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateStatus(sessionId: String, status: SessionStatus) {
        viewModelScope.launch {
            sessionRepository.updateSessionStatus(sessionId, status)
            val updated = _state.value.sessions.map {
                if (it.id == sessionId) it.copy(status = status) else it
            }
            _state.value = _state.value.copy(sessions = updated)
        }
    }

    fun clearBookingSuccess() {
        _state.value = _state.value.copy(bookingSuccess = false, selectedDate = "", selectedTimeSlot = "")
    }
}
