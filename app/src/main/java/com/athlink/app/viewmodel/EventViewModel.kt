package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.Event
import com.athlink.app.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventState(
    val isLoading: Boolean = false,
    val events: List<Event> = emptyList(),
    val error: String? = null,
    val createSuccess: Boolean = false
)

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EventState())
    val state: StateFlow<EventState> = _state.asStateFlow()

    init { loadEvents() }

    fun loadEvents() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            eventRepository.getEvents().onSuccess { events ->
                _state.value = _state.value.copy(isLoading = false, events = events)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createEvent(event: Event) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            eventRepository.createEvent(event).onSuccess {
                _state.value = _state.value.copy(isLoading = false, createSuccess = true)
                loadEvents()
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearCreateSuccess() { _state.value = _state.value.copy(createSuccess = false) }
}
