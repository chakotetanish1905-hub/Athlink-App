package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.User
import com.athlink.app.data.model.effectiveStatus
import com.athlink.app.data.repository.EventRepository
import com.athlink.app.data.repository.OrganisationRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

enum class EventAction { DRAFT_SAVED, PUBLISHED, DRAFT_DELETED }

data class OrganisationEventsState(
    val isLoading: Boolean = false,
    val organisation: Organisation? = null,
    val published: List<Event> = emptyList(),
    val drafts: List<Event> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    /** One-shot result of the last save / publish, consumed by the screen. */
    val lastAction: EventAction? = null
) {
    val status: OrganisationVerificationStatus
        get() = organisation?.effectiveStatus(Date()) ?: OrganisationVerificationStatus.UNVERIFIED
    val canPublish: Boolean get() = OrganisationVerificationPolicy.canPublishEvents(organisation)
    val canDraft: Boolean get() = OrganisationVerificationPolicy.canCreateDrafts(status)
    val publishBlockedReason: String? get() = if (canPublish) null else OrganisationVerificationPolicy.publishBlockedReason(status)
    val totalRegistered: Int get() = published.sumOf { it.registeredCount }
    val totalRevenue: Double get() = published.sumOf { it.fees * it.registeredCount }
}

/**
 * The signed-in organisation's OWN events (published + drafts), straight from Firestore with no
 * dummy fallback, plus draft / publish actions gated by verification.
 */
@HiltViewModel
class OrganisationEventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val organisationRepository: OrganisationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrganisationEventsState())
    val state: StateFlow<OrganisationEventsState> = _state.asStateFlow()

    private var user: User? = null
    private val orgId get() = user?.let { it.organisationId.ifBlank { it.uid } }.orEmpty()

    /** Called whenever a screen is shown, so returning from create / publish shows fresh data. */
    fun load(user: User) {
        this.user = user
        refresh()
    }

    fun refresh() {
        if (user == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val orgJob = async { organisationRepository.getOrganisation(orgId) }
            val publishedJob = async { eventRepository.getOrganisationEvents(orgId) }
            val draftsJob = async { eventRepository.getEventDrafts(orgId) }
            val org = orgJob.await()
            val published = publishedJob.await()
            val drafts = draftsJob.await()
            val failure = org.exceptionOrNull() ?: published.exceptionOrNull() ?: drafts.exceptionOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    organisation = org.getOrNull() ?: it.organisation,
                    published = published.getOrDefault(it.published),
                    drafts = drafts.getOrDefault(it.drafts),
                    error = failure?.let { e -> ErrorMessages.from(e, "Couldn't load your events. Pull down to retry.", "loadOrgEvents") }
                )
            }
        }
    }

    /** Stamps ownership and the organisation's CURRENT public identity onto the event. */
    private fun stamp(event: Event): Event {
        val u = user!!
        val org = _state.value.organisation
        return event.copy(
            organisationId = orgId,
            organisationName = org?.displayName?.ifBlank { null } ?: u.name,
            organisationVerificationLevel = org?.verificationLevel.orEmpty(),
            publishedByUid = u.uid
        )
    }

    fun saveDraft(event: Event) {
        if (user == null) return
        if (!_state.value.canDraft) { _state.update { it.copy(message = "Your organisation is suspended, so drafts are disabled.") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            eventRepository.saveEventDraft(stamp(event).copy(createdAt = event.createdAt.takeIf { event.id.isNotBlank() } ?: System.currentTimeMillis()))
                .onSuccess { _state.update { it.copy(isSaving = false, lastAction = EventAction.DRAFT_SAVED) }; refresh() }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = ErrorMessages.from(e, "Couldn't save the draft.", "saveDraft")) } }
        }
    }

    /**
     * Publishes a new event or an existing draft. Checked here for a clear message, and enforced
     * again by firestore.rules (status, level, expiry, name).
     */
    fun publish(event: Event, fromDraftId: String? = null) {
        if (user == null) return
        val s = _state.value
        if (!s.canPublish) { _state.update { it.copy(message = s.publishBlockedReason) }; return }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            eventRepository.publishEvent(stamp(event).copy(createdAt = System.currentTimeMillis()), fromDraftId)
                .onSuccess { _state.update { it.copy(isSaving = false, lastAction = EventAction.PUBLISHED) }; refresh() }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSaving = false, message = ErrorMessages.from(e, "Couldn't publish the event.", "publishEvent"))
                    }
                    refresh() // status may have changed (e.g. suspended / expired)
                }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            eventRepository.deleteEventDraft(draftId)
                .onSuccess { _state.update { it.copy(lastAction = EventAction.DRAFT_DELETED, drafts = it.drafts.filterNot { d -> d.id == draftId }) } }
                .onFailure { e -> _state.update { it.copy(message = ErrorMessages.from(e, "Couldn't delete the draft.", "deleteDraft")) } }
        }
    }

    fun draftById(id: String?): Event? = id?.let { did -> _state.value.drafts.firstOrNull { it.id == did } }

    fun consumeAction() = _state.update { it.copy(lastAction = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }
}
