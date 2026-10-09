package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.AcademyDirectory
import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.AcademyRequestForm
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.PreferredTime
import com.athlink.app.data.model.displayLocation
import com.athlink.app.data.repository.AcademyRepository
import com.athlink.app.data.repository.PlayerRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Academy directory for players (verified organisations, incl. the 96 imported academies). */
data class AcademyListState(
    val isLoading: Boolean = true,
    val all: List<Organisation> = emptyList(),
    val city: String? = null,
    val sport: String? = null,
    /** "INDOOR" / "OUTDOOR" / null. */
    val venueCategory: String? = null,
    val query: String = "",
    val error: String? = null
) {
    val cities: List<String> get() = AcademyDirectory.cities(all)
    val sports: List<String> get() = AcademyDirectory.sports(all)
    val results: List<Organisation> get() = AcademyDirectory.filter(all, city, sport, venueCategory, query)
}

/** One academy + the session request form. */
data class AcademyDetailState(
    val isLoading: Boolean = true,
    val academy: Organisation? = null,
    val form: AcademyRequestForm = AcademyRequestForm(),
    val errors: Map<AcademyRequestForm.Field, String> = emptyMap(),
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AcademyViewModel @Inject constructor(
    private val academyRepository: AcademyRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _list = MutableStateFlow(AcademyListState())
    val list: StateFlow<AcademyListState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(AcademyDetailState())
    val detail: StateFlow<AcademyDetailState> = _detail.asStateFlow()

    private var listLoaded = false

    /** Loads the directory once; the city filter starts at the player's own city when it is listed. */
    fun loadList(playerId: String, refresh: Boolean = false) {
        if (listLoaded && !refresh) return
        listLoaded = true
        viewModelScope.launch {
            _list.update { it.copy(isLoading = true, error = null) }
            val academies = academyRepository.getAcademies(refresh)
            val playerCity = if (playerId.isBlank()) null else playerRepository.getPlayer(playerId).getOrNull()?.city
            academies
                .onSuccess { orgs ->
                    _list.update {
                        it.copy(
                            isLoading = false, all = orgs,
                            city = if (refresh) it.city else AcademyDirectory.defaultCity(playerCity, orgs)
                        )
                    }
                }
                .onFailure { e ->
                    _list.update { it.copy(isLoading = false, error = ErrorMessages.from(e, "Couldn't load academies. Please try again.")) }
                }
        }
    }

    fun setCity(city: String?) = _list.update { it.copy(city = city) }
    fun setSport(sport: String?) = _list.update { it.copy(sport = sport) }
    fun setVenueCategory(category: String?) = _list.update { it.copy(venueCategory = category) }
    fun setQuery(query: String) = _list.update { it.copy(query = query) }

    // ── Detail + request ─────────────────────────────────────────────────

    fun openAcademy(id: String) {
        if (_detail.value.academy?.organisationId == id && !_detail.value.sent) return
        viewModelScope.launch {
            _detail.value = AcademyDetailState(isLoading = true)
            academyRepository.getAcademy(id)
                .onSuccess { org ->
                    _detail.value = AcademyDetailState(
                        isLoading = false, academy = org,
                        form = AcademyRequestForm(sport = org?.sports?.firstOrNull().orEmpty()),
                        error = if (org == null) "This academy is no longer listed." else null
                    )
                }
                .onFailure { e ->
                    _detail.value = AcademyDetailState(isLoading = false, error = ErrorMessages.from(e, "Couldn't load this academy."))
                }
        }
    }

    fun updateForm(transform: (AcademyRequestForm) -> AcademyRequestForm) = _detail.update {
        it.copy(form = transform(it.form), errors = emptyMap(), error = null)
    }

    fun setRequestSport(sport: String) = updateForm { it.copy(sport = sport) }
    fun setDate(date: String) = updateForm { it.copy(preferredDate = date) }
    fun setTime(time: PreferredTime) = updateForm { it.copy(preferredTime = time) }
    fun setMessage(message: String) = updateForm { it.copy(message = message.take(AcademyRequestForm.MAX_MESSAGE + 20)) }
    fun setPhone(phone: String) = updateForm { it.copy(contactPhone = phone.take(16)) }

    fun sendRequest(playerId: String, playerName: String) {
        val s = _detail.value
        val org = s.academy ?: return
        if (s.isSending) return
        val errors = s.form.validate(org)
        if (errors.isNotEmpty()) { _detail.update { it.copy(errors = errors) }; return }
        val request = AcademyRequest(
            organisationId = org.organisationId,
            organisationName = org.displayName,
            organisationLocation = org.displayLocation.take(150),
            organisationOwnerUid = org.ownerUid,
            playerId = playerId,
            playerName = playerName.trim().take(60),
            sport = s.form.sport,
            preferredDate = s.form.preferredDate,
            preferredTime = s.form.preferredTime.name,
            message = s.form.message.trim(),
            contactPhone = s.form.contactPhone.filter { !it.isWhitespace() }
        )
        viewModelScope.launch {
            _detail.update { it.copy(isSending = true, error = null) }
            academyRepository.sendRequest(request)
                .onSuccess { _detail.update { it.copy(isSending = false, sent = true) } }
                .onFailure { e -> _detail.update { it.copy(isSending = false, error = ErrorMessages.from(e, "Couldn't send the request. Please try again.")) } }
        }
    }

    /** After the "sent" confirmation: start a fresh form for the same academy. */
    fun resetRequest() = _detail.update {
        it.copy(sent = false, form = AcademyRequestForm(sport = it.academy?.sports?.firstOrNull().orEmpty()), errors = emptyMap())
    }
}
