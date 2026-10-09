package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.AvailabilityRules
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachAvailability
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.Weekday
import com.athlink.app.data.repository.CoachRepository
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

/** The signed-in coach's visibility to players and weekly availability. */
data class CoachScheduleState(
    val isLoading: Boolean = true,
    val coach: Coach? = null,
    val ranges: List<CoachAvailability> = emptyList(),
    val day: Weekday = Weekday.MONDAY,
    val start: String = "17:00",
    val end: String = "19:00",
    val isSaving: Boolean = false,
    val formError: String? = null,
    val error: String? = null,
    val message: String? = null
) {
    /** Players can find and book this coach. */
    val visibleToPlayers: Boolean get() = coach?.let { SessionPolicy.isBookable(it) } == true
}

@HiltViewModel
class CoachScheduleViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CoachScheduleState())
    val state: StateFlow<CoachScheduleState> = _state.asStateFlow()

    fun load(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val coach = async { coachRepository.getCoachById(uid) }
            val ranges = async { sessionRepository.getCoachAvailability(uid) }
            val c = coach.await(); val r = ranges.await()
            _state.update {
                it.copy(
                    isLoading = false,
                    coach = c.getOrNull(),
                    ranges = r.getOrDefault(emptyList()),
                    error = (c.exceptionOrNull() ?: r.exceptionOrNull())?.let { e -> ErrorMessages.from(e, "Couldn't load your schedule.") }
                )
            }
        }
    }

    fun setDay(day: Weekday) = _state.update { it.copy(day = day, formError = null) }
    fun setStart(t: String) = _state.update { it.copy(start = t, formError = null) }
    fun setEnd(t: String) = _state.update { it.copy(end = t, formError = null) }

    fun addRange(uid: String) {
        val s = _state.value
        if (s.isSaving) return
        AvailabilityRules.problem(s.day, s.start, s.end, s.ranges)?.let { msg ->
            _state.update { it.copy(formError = msg) }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, formError = null, error = null) }
            sessionRepository.saveAvailability(uid, CoachAvailability(dayOfWeek = s.day.name, startTime = s.start, endTime = s.end, enabled = true))
                .onSuccess { saved -> _state.update { it.copy(isSaving = false, ranges = it.ranges + saved, message = "Added ${s.day.label} ${s.start}–${s.end}") } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = ErrorMessages.from(e, "Couldn't save. Please try again.")) } }
        }
    }

    fun deleteRange(uid: String, range: CoachAvailability) {
        viewModelScope.launch {
            sessionRepository.deleteAvailability(uid, range.availabilityId)
                .onSuccess { _state.update { it.copy(ranges = it.ranges.filterNot { r -> r.availabilityId == range.availabilityId }, message = "Removed") } }
                .onFailure { e -> _state.update { it.copy(error = ErrorMessages.from(e, "Couldn't remove. Please try again.")) } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}
