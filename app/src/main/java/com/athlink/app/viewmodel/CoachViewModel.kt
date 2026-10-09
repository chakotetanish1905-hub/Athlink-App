package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.Coach
import com.athlink.app.data.repository.CoachRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachListState(
    val isLoading: Boolean = false,
    val coaches: List<Coach> = emptyList(),
    val filteredCoaches: List<Coach> = emptyList(),
    val selectedCoach: Coach? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedSport: String? = null
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachRepository: CoachRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CoachListState())
    val state: StateFlow<CoachListState> = _state.asStateFlow()

    init { loadCoaches() }

    fun loadCoaches() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = coachRepository.getCoaches()
            result.onSuccess { coaches ->
                _state.value = _state.value.copy(
                    isLoading = false, coaches = coaches, filteredCoaches = coaches
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = com.athlink.app.utils.ErrorMessages.from(e, "Couldn't load coaches. Please try again."))
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(searchQuery = query)
            applyFilters()
        }
    }

    fun filterBySport(sport: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedSport = sport)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = _state.value.searchQuery
        val sport = _state.value.selectedSport
        val filtered = _state.value.coaches.filter { coach ->
            (query.isEmpty() || coach.name.contains(query, true) || coach.sport.contains(query, true)) &&
            (sport == null || coach.sport.equals(sport, true))
        }
        _state.value = _state.value.copy(filteredCoaches = filtered)
    }

    fun selectCoach(coach: Coach) {
        _state.value = _state.value.copy(selectedCoach = coach)
    }

    fun getRecommendedCoaches(): List<Coach> =
        _state.value.coaches.sortedByDescending { it.rating }.take(5)
}
