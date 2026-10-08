package com.athlink.app.data.repository

import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationDocumentType
import com.athlink.app.data.model.OrganisationDraft
import com.athlink.app.data.model.OrganisationSnapshot
import com.athlink.app.data.model.OrganisationValidators
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.effectiveStatus
import com.athlink.app.data.remote.OrganisationDataSource
import com.athlink.app.data.remote.UploadEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Organisation profile + verification. Never falls back to dummy data: an organisation must see
 * exactly what is stored for it.
 */
@Singleton
class OrganisationRepository @Inject constructor(
    private val source: OrganisationDataSource
) {
    suspend fun getSnapshot(organisationId: String): Result<OrganisationSnapshot> = source.getSnapshot(organisationId)

    suspend fun getOrganisation(organisationId: String): Result<Organisation?> = source.getOrganisation(organisationId)

    /**
     * Saves the onboarding draft. Refuses (without a network call) when verification is locked,
     * so the user gets a clear message instead of a permission error.
     */
    suspend fun saveDraft(
        organisationId: String,
        draft: OrganisationDraft,
        existing: OrganisationSnapshot?,
        linkUser: Boolean
    ): Result<Unit> {
        // Effective status: a verified organisation past its expiry date may re-verify (rules agree).
        val status = existing?.organisation?.effectiveStatus() ?: OrganisationVerificationStatus.UNVERIFIED
        if (!OrganisationVerificationPolicy.canEditVerification(status)) {
            return Result.failure(VerificationLockedException(status))
        }
        return source.saveDraft(organisationId, draft, existing, linkUser)
    }

    suspend fun markContactVerified(organisationId: String): Result<Unit> = source.markContactVerified(organisationId)

    /** Validates everything once more and submits. Duplicate / locked submissions are refused. */
    suspend fun submitForReview(
        organisationId: String,
        draft: OrganisationDraft,
        existing: OrganisationSnapshot,
        linkUser: Boolean
    ): Result<Unit> {
        val status = existing.organisation?.effectiveStatus() ?: OrganisationVerificationStatus.UNVERIFIED
        if (!OrganisationVerificationPolicy.canSubmit(status)) return Result.failure(VerificationLockedException(status))
        if (!source.isEmailVerified) return Result.failure(EmailNotVerifiedException())
        // Submission updates existing documents only; create any that are missing first.
        var current = existing
        if (current.organisation == null || current.verification == null || current.representative == null || current.affiliation == null) {
            source.saveDraft(organisationId, draft, current, linkUser).onFailure { return Result.failure(it) }
            current = source.getSnapshot(organisationId).getOrElse { return Result.failure(it) }
        }
        return source.submitForReview(organisationId, draft, current, status, linkUser)
    }

    suspend fun sendVerificationEmail(): Result<Unit> = source.sendVerificationEmail()
    suspend fun refreshEmailVerified(): Result<Boolean> = source.refreshEmailVerified()
    val isEmailVerified: Boolean get() = source.isEmailVerified
    val accountEmail: String get() = source.accountEmail

    /** Validates the file locally first (type + size), then uploads. */
    fun uploadDocument(organisationId: String, type: OrganisationDocumentType, file: PickedFile): Flow<UploadEvent> {
        OrganisationValidators.document(file.mimeType, file.sizeBytes)?.let { message ->
            return flow { throw InvalidFileException(message) }
        }
        return source.uploadDocument(organisationId, type, file)
    }

    suspend fun updateDocumentDetails(
        documentId: String, documentNumber: String?, issuingAuthority: String?, issuedDate: String?, expiryDate: String?
    ): Result<Unit> = source.updateDocumentDetails(documentId, documentNumber, issuingAuthority, issuedDate, expiryDate)

    suspend fun removeDocument(document: OrganisationDocument): Result<Unit> = source.removeDocument(document)

    suspend fun uploadLogo(organisationId: String, file: PickedFile): Result<String> {
        OrganisationValidators.logo(file.mimeType, file.sizeBytes)?.let { return Result.failure(InvalidFileException(it)) }
        return source.uploadLogo(organisationId, file)
    }
}

class VerificationLockedException(val status: OrganisationVerificationStatus) :
    IllegalStateException("Verification is ${status.name}; details are locked")

class EmailNotVerifiedException : IllegalStateException("Account email not verified")

class InvalidFileException(message: String) : IllegalArgumentException(message)
