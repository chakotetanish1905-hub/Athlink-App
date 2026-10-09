package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.BookingSlot
import com.athlink.app.data.model.BookingSlots
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachAvailability
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.remote.SlotTakenException
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
import java.time.LocalDate
import javax.inject.Inject

/** Booking a real coach: dates and slots come only from the coach's weekly availability. */
data class BookingState(
    val isLoading: Boolean = true,
    val coach: Coach? = null,
    /** False when the coach can't be booked (not verified / not active / missing). */
    val bookable: Boolean = false,
    val availability: List<CoachAvailability> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val slots: List<BookingSlot> = emptyList(),
    val selectedSlot: BookingSlot? = null,
    /** Slots this screen learned are taken (booking refused), hidden from the list. */
    val takenSlotIds: Set<String> = emptySet(),
    val notes: String = "",
    val isBooking: Boolean = false,
    val booked: Session? = null,
    val error: String? = null
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()
    private var loadedFor: String? = null

    fun load(coachId: String, force: Boolean = false) {
        if (!force && loadedFor == coachId) return
        loadedFor = coachId
        viewModelScope.launch {
            _state.value = BookingState(isLoading = true)
            val coachResult = async { coachRepository.getCoachById(coachId) }
            val availabilityResult = async { sessionRepository.getCoachAvailability(coachId) }
            val coach = coachResult.await().getOrNull()?.let { if (it.uid.isBlank()) it.copy(uid = coachId) else it }
            val availability = availabilityResult.await()
            if (coach == null) {
                _state.value = BookingState(isLoading = false, error = "This coach couldn't be loaded. Please try again.")
                return@launch
            }
            val ranges = availability.getOrDefault(emptyList())
            val dates = BookingSlots.bookableDates(ranges)
            val first = dates.firstOrNull()
            _state.value = BookingState(
                isLoading = false,
                coach = coach,
                bookable = SessionPolicy.isBookable(coach),
                availability = ranges,
                dates = dates,
                selectedDate = first,
                slots = first?.let { BookingSlots.slotsFor(it, ranges) }.orEmpty(),
                error = availability.exceptionOrNull()?.let { ErrorMessages.from(it, "Couldn't load this coach's availability.") }
            )
        }
    }

    fun selectDate(date: LocalDate) = _state.update {
        it.copy(selectedDate = date, slots = visibleSlots(date, it), selectedSlot = null, error = null)
    }

    fun selectSlot(slot: BookingSlot) = _state.update { it.copy(selectedSlot = slot, error = null) }

    fun updateNotes(notes: String) = _state.update { it.copy(notes = notes.take(300)) }

    private fun visibleSlots(date: LocalDate, s: BookingState): List<BookingSlot> {
        val coachId = s.coach?.uid.orEmpty()
        val d = date.format(SessionPolicy.DATE)
        return BookingSlots.slotsFor(date, s.availability)
            .filterNot { SessionPolicy.slotId(coachId, d, it.startTime) in s.takenSlotIds }
    }

    fun book(playerId: String, playerName: String) {
        val s = _state.value
        val coach = s.coach ?: return
        val date = s.selectedDate ?: return
        val slot = s.selectedSlot ?: return
        if (!s.bookable || s.isBooking) return
        val session = Session(
            coachId = coach.uid,
            coachName = coach.name,
            playerId = playerId,
            playerName = playerName.trim().take(60),
            sport = coach.sport,
            date = date.format(SessionPolicy.DATE),
            timeSlot = slot.label,
            startTime = slot.startTime,
            endTime = slot.endTime,
            status = SessionStatus.PENDING,
            price = coach.hourlyRate,
            location = coach.location.ifBlank { listOf(coach.coachingArea, coach.city).filter { it.isNotBlank() }.joinToString(", ") },
            notes = s.notes.trim()
        )
        viewModelScope.launch {
            _state.update { it.copy(isBooking = true, error = null) }
            sessionRepository.bookSession(session)
                .onSuccess { saved -> _state.update { it.copy(isBooking = false, booked = saved) } }
                .onFailure { e ->
                    _state.update { st ->
                        if (e is SlotTakenException) {
                            val taken = st.takenSlotIds + SessionPolicy.slotId(coach.uid, session.date, slot.startTime)
                            val next = st.copy(takenSlotIds = taken)
                            next.copy(isBooking = false, selectedSlot = null, slots = visibleSlots(date, next), error = e.message)
                        } else {
                            st.copy(isBooking = false, error = ErrorMessages.from(e, "Booking failed. Please try again."))
                        }
                    }
                }
        }
    }

    fun consumeBooked() = _state.update { it.copy(booked = null) }
}
