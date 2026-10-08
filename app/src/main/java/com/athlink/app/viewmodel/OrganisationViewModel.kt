package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.OrganisationBadge
import com.athlink.app.data.model.OrganisationLanding
import com.athlink.app.data.model.OrganisationSnapshot
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.User
import com.athlink.app.data.model.effectiveStatus
import com.athlink.app.data.model.storedStatus
import com.athlink.app.data.repository.OrganisationRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class OrganisationState(
    val isLoading: Boolean = false,
    val snapshot: OrganisationSnapshot? = null,
    val error: String? = null,
    val emailVerified: Boolean = false,
    val accountEmail: String = "",
    val message: String? = null,
    val isWorking: Boolean = false
) {
    val organisation get() = snapshot?.organisation
    /** Status after applying the expiry date. */
    val status: OrganisationVerificationStatus
        get() = organisation?.effectiveStatus(Date()) ?: OrganisationVerificationStatus.UNVERIFIED
    val badge: OrganisationBadge get() = OrganisationVerificationPolicy.badge(organisation)
    val canPublish: Boolean get() = OrganisationVerificationPolicy.canPublishEvents(organisation)
    val canEditVerification: Boolean get() = OrganisationVerificationPolicy.canEditVerification(status)
    val landing: OrganisationLanding get() = OrganisationVerificationPolicy.landing(organisation)
    val loaded: Boolean get() = snapshot != null
}

/**
 * Read-side state for the signed-in organisation: login gate, dashboard header, verification
 * status screen and organisation profile. Real Firestore data only.
 */
@HiltViewModel
class OrganisationViewModel @Inject constructor(
    private val repository: OrganisationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrganisationState())
    val state: StateFlow<OrganisationState> = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(user: User) {
        if (loadedFor == user.uid && _state.value.error == null && _state.value.loaded) return
        refresh(user)
    }

    fun refresh(user: User) {
        loadedFor = user.uid
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val emailVerified = repository.refreshEmailVerified().getOrDefault(repository.isEmailVerified)
            repository.getSnapshot(user.organisationId.ifBlank { user.uid })
                .onSuccess { snap ->
                    _state.value = _state.value.copy(
                        isLoading = false, snapshot = snap, emailVerified = emailVerified,
                        accountEmail = repository.accountEmail.ifBlank { user.email }
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = ErrorMessages.from(e, "Couldn't load your organisation. Please try again.", "loadOrganisation")
                    )
                }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true)
            val result = repository.sendVerificationEmail()
            _state.value = _state.value.copy(
                isWorking = false,
                message = result.fold(
                    { "Verification email sent to ${_state.value.accountEmail}. Open the link, then tap \"I've verified\"." },
                    { ErrorMessages.from(it, "Couldn't send the email. Try again in a minute.", "sendVerificationEmail") }
                )
            )
        }
    }

    /** Re-checks the account email and, if verified, records CONTACT_VERIFIED (rules check the token). */
    fun confirmEmailVerified(user: User) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true)
            val verified = repository.refreshEmailVerified().getOrDefault(false)
            if (!verified) {
                _state.value = _state.value.copy(isWorking = false, emailVerified = false,
                    message = "Your email isn't verified yet. Open the link in the email we sent, then try again.")
                return@launch
            }
            val org = _state.value.organisation
            if (org != null && org.storedStatus == OrganisationVerificationStatus.UNVERIFIED) {
                repository.markContactVerified(org.organisationId.ifBlank { user.uid }).onFailure { e ->
                    _state.value = _state.value.copy(isWorking = false,
                        message = ErrorMessages.from(e, "Couldn't update your verification status.", "markContactVerified"))
                    return@launch
                }
            }
            _state.value = _state.value.copy(isWorking = false, emailVerified = true, message = "Email verified.")
            refresh(user)
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
