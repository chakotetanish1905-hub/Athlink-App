package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.OnboardingStep
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationDocumentType
import com.athlink.app.data.model.OrganisationDraft
import com.athlink.app.data.model.OrganisationSnapshot
import com.athlink.app.data.model.OrganisationValidators
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.User
import com.athlink.app.data.model.effectiveStatus
import com.athlink.app.data.model.storedStatus
import com.athlink.app.data.remote.UploadEvent
import com.athlink.app.data.repository.EmailNotVerifiedException
import com.athlink.app.data.repository.InvalidFileException
import com.athlink.app.data.repository.OrganisationRepository
import com.athlink.app.data.repository.VerificationLockedException
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One upload in progress (or failed) on the documents step. */
data class UploadUi(
    val localId: String,
    val requirementKey: String,
    val type: OrganisationDocumentType,
    val fileName: String,
    val progress: Float = 0f,
    val error: String? = null,
    /** Set when this upload replaces an existing document (deleted after success). */
    val replacing: OrganisationDocument? = null
)

data class OnboardingState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    /** Furthest step reached, so the progress bar can show completed steps. */
    val furthestStep: OnboardingStep = OnboardingStep.IDENTITY,
    val draft: OrganisationDraft = OrganisationDraft(),
    val errors: Map<OrgField, String> = emptyMap(),
    val snapshot: OrganisationSnapshot? = null,
    val documents: List<OrganisationDocument> = emptyList(),
    val uploads: List<UploadUi> = emptyList(),
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val logoUploading: Boolean = false,
    val emailVerified: Boolean = false,
    val accountEmail: String = "",
    val message: String? = null,
    /** True after a successful submission; the screen then navigates to the status screen. */
    val submitted: Boolean = false
) {
    /** Effective status (a verified organisation past its expiry date is EXPIRED and may re-verify). */
    val status: OrganisationVerificationStatus
        get() = snapshot?.organisation?.effectiveStatus() ?: OrganisationVerificationStatus.UNVERIFIED
    /** False while UNDER_REVIEW / VERIFIED / SUSPENDED: the form is read-only. */
    val editable: Boolean get() = OrganisationVerificationPolicy.canEditVerification(status)
    val isResubmission: Boolean
        get() = status == OrganisationVerificationStatus.REJECTED || status == OrganisationVerificationStatus.EXPIRED
    val rejectionReason: String? get() = snapshot?.verification?.rejectionReason?.takeIf { it.isNotBlank() }
    val busy: Boolean get() = isSaving || isSubmitting || uploads.any { it.error == null }
}

@HiltViewModel
class OrganisationOnboardingViewModel @Inject constructor(
    private val repository: OrganisationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var user: User? = null
    private val uploadJobs = mutableMapOf<String, Job>()

    private val orgId: String get() = user?.let { it.organisationId.ifBlank { it.uid } }.orEmpty()

    fun load(user: User) {
        if (this.user?.uid == user.uid && _state.value.loadError == null && !_state.value.isLoading) return
        this.user = user
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val emailVerified = repository.refreshEmailVerified().getOrDefault(repository.isEmailVerified)
            repository.getSnapshot(orgId)
                .onSuccess { snap ->
                    val draft = OrganisationDraft.fromSnapshot(snap, user.name, user.email)
                    // Resume at the first step that still needs work.
                    val resumeAt = draft.firstInvalidStep(snap.documents) ?: OnboardingStep.REVIEW
                    _state.update {
                        it.copy(
                            isLoading = false, snapshot = snap, draft = draft, documents = snap.documents,
                            step = if (snap.organisation?.legalName.isNullOrBlank()) OnboardingStep.IDENTITY else resumeAt,
                            furthestStep = resumeAt,
                            emailVerified = emailVerified,
                            accountEmail = repository.accountEmail.ifBlank { user.email }
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, loadError = ErrorMessages.from(e, "Couldn't load your verification. Please try again.", "loadOnboarding")) }
                }
        }
    }

    fun retry() { user?.let { u -> this.user = null; load(u) } }

    /** Applies an edit and clears the error of the edited field. */
    fun update(field: OrgField? = null, transform: (OrganisationDraft) -> OrganisationDraft) {
        if (!_state.value.editable) return
        _state.update { s -> s.copy(draft = transform(s.draft), errors = if (field != null) s.errors - field else s.errors) }
    }

    /** Validates the current step, saves the draft, then moves on. */
    fun next() {
        val s = _state.value
        val errors = s.draft.validate(s.step, s.documents)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, message = "Please fix the highlighted fields.") }
            return
        }
        val nextStep = OnboardingStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        save { moveTo(nextStep) }
    }

    fun back() {
        val prev = OnboardingStep.entries.getOrNull(_state.value.step.ordinal - 1) ?: return
        // Going back never loses data: the draft stays in memory; save quietly if editable.
        moveTo(prev)
    }

    /** Jump from the review screen (or progress bar) to a step the user already reached. */
    fun goTo(step: OnboardingStep) {
        if (step.ordinal <= _state.value.furthestStep.ordinal || !_state.value.editable) moveTo(step)
    }

    private fun moveTo(step: OnboardingStep) {
        _state.update {
            it.copy(
                step = step, errors = emptyMap(),
                furthestStep = if (step.ordinal > it.furthestStep.ordinal) step else it.furthestStep
            )
        }
    }

    /** Explicit "Save draft" from the top bar. */
    fun saveDraft() = save { _state.update { it.copy(message = "Draft saved. You can continue later.") } }

    private fun save(onSaved: () -> Unit) {
        val s = _state.value
        val u = user ?: return
        if (!s.editable) { onSaved(); return }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            repository.saveDraft(orgId, s.draft, s.snapshot, linkUser = u.organisationId.isBlank())
                .onSuccess {
                    // Reload so `exists` flags / server timestamps are current for the next save.
                    repository.getSnapshot(orgId).onSuccess { snap ->
                        _state.update { it.copy(snapshot = snap, documents = snap.documents) }
                    }
                    if (u.organisationId.isBlank()) user = u.copy(organisationId = u.uid)
                    _state.update { it.copy(isSaving = false) }
                    onSaved()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = messageFor(e, "Couldn't save your draft.")) } }
        }
    }

    // ── Documents ───────────────────────────────────────────────────────

    /**
     * Validates and uploads a picked file for [requirementKey] as [type]. If [replacing] is set,
     * the old document is removed after the new one is stored.
     */
    fun uploadDocument(requirementKey: String, type: OrganisationDocumentType, file: PickedFile, replacing: OrganisationDocument? = null) {
        if (!_state.value.editable) return
        OrganisationValidators.document(file.mimeType, file.sizeBytes)?.let { msg ->
            _state.update { it.copy(message = msg) }
            return
        }
        val localId = UUID.randomUUID().toString()
        val upload = UploadUi(localId, requirementKey, type, file.fileName, replacing = replacing)
        _state.update { it.copy(uploads = it.uploads + upload) }
        uploadJobs[localId] = viewModelScope.launch {
            // Organisation documents must exist before Storage rules allow uploads.
            if (_state.value.snapshot?.organisation == null) {
                val saved = repository.saveDraft(orgId, _state.value.draft, _state.value.snapshot, linkUser = user?.organisationId.isNullOrBlank())
                if (saved.isFailure) { failUpload(localId, saved.exceptionOrNull()!!); return@launch }
                repository.getSnapshot(orgId).onSuccess { snap -> _state.update { it.copy(snapshot = snap) } }
            }
            repository.uploadDocument(orgId, type, file)
                .catch { e -> failUpload(localId, e) }
                .collect { event ->
                    when (event) {
                        is UploadEvent.Progress -> _state.update { s ->
                            s.copy(uploads = s.uploads.map { if (it.localId == localId) it.copy(progress = event.fraction) else it })
                        }
                        is UploadEvent.Done -> {
                            _state.update { s ->
                                s.copy(
                                    uploads = s.uploads.filterNot { it.localId == localId },
                                    documents = s.documents + event.document,
                                    errors = s.errors - OrgField.DOCUMENTS
                                )
                            }
                            replacing?.let { old -> removeDocumentInternal(old, quiet = true) }
                        }
                    }
                }
            uploadJobs.remove(localId)
        }
    }

    private fun failUpload(localId: String, e: Throwable) {
        val msg = messageFor(e, "Upload failed. Please try again.")
        _state.update { s -> s.copy(uploads = s.uploads.map { if (it.localId == localId) it.copy(error = msg) else it }) }
    }

    fun dismissUpload(localId: String) {
        uploadJobs.remove(localId)?.cancel()
        _state.update { s -> s.copy(uploads = s.uploads.filterNot { it.localId == localId }) }
    }

    fun removeDocument(document: OrganisationDocument) = removeDocumentInternal(document, quiet = false)

    private fun removeDocumentInternal(document: OrganisationDocument, quiet: Boolean) {
        if (!_state.value.editable) return
        viewModelScope.launch {
            repository.removeDocument(document)
                .onSuccess { _state.update { s -> s.copy(documents = s.documents.filterNot { it.documentId == document.documentId }) } }
                .onFailure { e -> if (!quiet) _state.update { it.copy(message = messageFor(e, "Couldn't remove the document.")) } }
        }
    }

    fun updateDocumentDetails(document: OrganisationDocument, number: String, authority: String, issued: String, expiry: String) {
        OrganisationValidators.optionalDate(issued, "the issue date")?.let { m -> _state.update { it.copy(message = m) }; return }
        OrganisationValidators.optionalDate(expiry, "the expiry date")?.let { m -> _state.update { it.copy(message = m) }; return }
        viewModelScope.launch {
            repository.updateDocumentDetails(document.documentId, number, authority, issued, expiry)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            documents = s.documents.map {
                                if (it.documentId == document.documentId) it.copy(
                                    documentNumber = number.ifBlank { null }, issuingAuthority = authority.ifBlank { null },
                                    issuedDate = issued.ifBlank { null }, expiryDate = expiry.ifBlank { null }
                                ) else it
                            },
                            message = "Document details saved."
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = messageFor(e, "Couldn't save the document details.")) } }
        }
    }

    fun uploadLogo(file: PickedFile) {
        viewModelScope.launch {
            _state.update { it.copy(logoUploading = true) }
            if (_state.value.snapshot?.organisation == null) {
                repository.saveDraft(orgId, _state.value.draft, _state.value.snapshot, linkUser = user?.organisationId.isNullOrBlank())
                repository.getSnapshot(orgId).onSuccess { snap -> _state.update { it.copy(snapshot = snap) } }
            }
            repository.uploadLogo(orgId, file)
                .onSuccess { url -> _state.update { it.copy(logoUploading = false, draft = it.draft.copy(logoUrl = url), message = "Logo uploaded.") } }
                .onFailure { e -> _state.update { it.copy(logoUploading = false, message = messageFor(e, "Logo upload failed.")) } }
        }
    }

    // ── Contact verification ────────────────────────────────────────────

    fun sendVerificationEmail() {
        viewModelScope.launch {
            repository.sendVerificationEmail()
                .onSuccess { _state.update { it.copy(message = "Verification email sent to ${it.accountEmail}.") } }
                .onFailure { e -> _state.update { it.copy(message = messageFor(e, "Couldn't send the email. Try again in a minute.")) } }
        }
    }

    fun refreshEmailVerified() {
        viewModelScope.launch {
            val verified = repository.refreshEmailVerified().getOrDefault(false)
            _state.update { it.copy(emailVerified = verified, message = if (verified) "Email verified." else "Not verified yet. Open the link in the email first.") }
            val org = _state.value.snapshot?.organisation
            if (verified && org != null && org.storedStatus == OrganisationVerificationStatus.UNVERIFIED) {
                repository.markContactVerified(orgId).onSuccess {
                    repository.getSnapshot(orgId).onSuccess { snap -> _state.update { it.copy(snapshot = snap) } }
                }
            }
        }
    }

    // ── Submit ──────────────────────────────────────────────────────────

    fun submit() {
        val s = _state.value
        val u = user ?: return
        if (s.isSubmitting || s.submitted) return // duplicate tap
        val errors = s.draft.validate(OnboardingStep.REVIEW, s.documents)
        if (errors.isNotEmpty()) {
            val firstBad = s.draft.firstInvalidStep(s.documents)
            _state.update { it.copy(errors = errors, message = firstBad?.let { st -> "Complete \"${st.title}\" before submitting." } ?: "Confirm the declaration to submit.") }
            return
        }
        if (s.uploads.isNotEmpty()) { _state.update { it.copy(message = "Wait for uploads to finish.") }; return }
        val snapshot = s.snapshot ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            // Make sure the token carries email_verified before rules check it.
            repository.refreshEmailVerified()
            repository.submitForReview(orgId, s.draft, snapshot, linkUser = u.organisationId.isBlank())
                .onSuccess { _state.update { it.copy(isSubmitting = false, submitted = true) } }
                .onFailure { e -> _state.update { it.copy(isSubmitting = false, message = messageFor(e, "Couldn't submit. Please try again.")) } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(e: Throwable, fallback: String): String = when (e) {
        is VerificationLockedException -> when (e.status) {
            OrganisationVerificationStatus.UNDER_REVIEW -> "Your verification is under review, so details are locked."
            OrganisationVerificationStatus.VERIFIED, OrganisationVerificationStatus.OFFICIAL_GOVERNMENT -> "Your organisation is already verified."
            OrganisationVerificationStatus.SUSPENDED -> "Your organisation is suspended. Contact Athlink support."
            else -> "These details can't be changed right now."
        }
        is EmailNotVerifiedException -> "Verify your account email first (see the box above)."
        is InvalidFileException -> e.message ?: fallback
        is com.athlink.app.data.remote.InvalidUploadException -> e.message ?: fallback
        else -> ErrorMessages.from(e, fallback, "onboarding")
    }
}
