package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.AcademyRequestStatus
import com.athlink.app.data.model.statusEnum
import com.athlink.app.data.repository.AcademyRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Session requests players sent to the signed-in organisation (its id is the owner's uid). */
data class OrgRequestsState(
    val isLoading: Boolean = true,
    val requests: List<AcademyRequest> = emptyList(),
    val replyDrafts: Map<String, String> = emptyMap(),
    val updatingId: String? = null,
    val error: String? = null
) {
    val pending: List<AcademyRequest> get() = requests.filter { it.statusEnum == AcademyRequestStatus.PENDING }
    val answered: List<AcademyRequest> get() = requests.filter { it.statusEnum != AcademyRequestStatus.PENDING }
}

@HiltViewModel
class OrgRequestsViewModel @Inject constructor(
    private val academyRepository: AcademyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrgRequestsState())
    val state: StateFlow<OrgRequestsState> = _state.asStateFlow()

    fun load(ownerUid: String) {
        if (ownerUid.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            academyRepository.getOrganisationRequests(ownerUid)
                .onSuccess { list -> _state.update { it.copy(isLoading = false, requests = list) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = ErrorMessages.from(e, "Couldn't load requests.")) } }
        }
    }

    fun setReply(requestId: String, text: String) = _state.update {
        it.copy(replyDrafts = it.replyDrafts + (requestId to text.take(300)))
    }

    fun respond(requestId: String, accept: Boolean) {
        val status = if (accept) AcademyRequestStatus.ACCEPTED else AcademyRequestStatus.DECLINED
        val note = _state.value.replyDrafts[requestId].orEmpty().trim()
        viewModelScope.launch {
            _state.update { it.copy(updatingId = requestId, error = null) }
            academyRepository.respond(requestId, status, note)
                .onSuccess {
                    _state.update { s ->
                        s.copy(updatingId = null, requests = s.requests.map {
                            if (it.requestId == requestId) it.copy(status = status.name, responseNote = note) else it
                        })
                    }
                }
                .onFailure { e -> _state.update { it.copy(updatingId = null, error = ErrorMessages.from(e, "Couldn't save your answer.")) } }
        }
    }
}
