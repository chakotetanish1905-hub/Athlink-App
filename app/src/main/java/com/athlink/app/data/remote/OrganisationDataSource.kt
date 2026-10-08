package com.athlink.app.data.remote

import com.athlink.app.data.model.AuditAction
import com.athlink.app.data.model.DocumentChunks
import com.athlink.app.data.model.DocumentReviewStatus
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.OrganisationAffiliation
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationDocumentType
import com.athlink.app.data.model.OrganisationDraft
import com.athlink.app.data.model.OrganisationRepresentative
import com.athlink.app.data.model.OrganisationSnapshot
import com.athlink.app.data.model.OrganisationVerification
import com.athlink.app.data.model.OrganisationVerificationLevel
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.PickedFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.Blob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All Firestore / Auth calls for organisation verification. Documents are stored privately in
 * Firestore (chunked), so everything works on the free Spark plan without Cloud Storage.
 *
 * Every write sends only owner-editable fields, plus the two owner status transitions that
 * firestore.rules allow (UNVERIFIED -> CONTACT_VERIFIED, editable -> UNDER_REVIEW). Approval,
 * rejection, suspension and expiry are done by the admin tool, never by the app.
 */
@Singleton
class OrganisationDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val fileReader: FileBytesReader
) {

    private fun orgRef(id: String) = firestore.collection(FirestorePaths.ORGANISATIONS).document(id)
    private fun verificationRef(id: String) = firestore.collection(FirestorePaths.ORGANISATION_VERIFICATION).document(id)
    private fun representativeRef(id: String) = firestore.collection(FirestorePaths.ORGANISATION_REPRESENTATIVES).document(id)
    private fun affiliationRef(id: String) = firestore.collection(FirestorePaths.ORGANISATION_AFFILIATIONS).document(id)
    private fun userRef(uid: String) = firestore.collection(FirestorePaths.USERS).document(uid)
    private val documents get() = firestore.collection(FirestorePaths.ORGANISATION_DOCUMENTS)
    private val auditLogs get() = firestore.collection(FirestorePaths.VERIFICATION_AUDIT_LOGS)

    // ── Read ────────────────────────────────────────────────────────────

    /** Loads every organisation document the owner can read. Missing documents are `null`, not errors. */
    suspend fun getSnapshot(organisationId: String): Result<OrganisationSnapshot> = try {
        val org = orgRef(organisationId).get().await()
        val verification = verificationRef(organisationId).get().await()
        val representative = representativeRef(organisationId).get().await()
        val affiliation = affiliationRef(organisationId).get().await()
        // Query on ownerUid so it matches the ownership condition in firestore.rules.
        val docs = documents.whereEqualTo("ownerUid", organisationId).get().await()
        Result.success(
            OrganisationSnapshot(
                organisation = org.takeIf { it.exists() }?.toObject(Organisation::class.java),
                verification = verification.takeIf { it.exists() }?.toObject(OrganisationVerification::class.java),
                representative = representative.takeIf { it.exists() }?.toObject(OrganisationRepresentative::class.java),
                affiliation = affiliation.takeIf { it.exists() }?.toObject(OrganisationAffiliation::class.java),
                documents = docs.documents.mapNotNull { it.toObject(OrganisationDocument::class.java) }
                    .sortedBy { it.uploadedAt }
            )
        )
    } catch (e: Exception) { Result.failure(e) }

    /** Public profile of any organisation (e.g. shown next to its events). */
    suspend fun getOrganisation(organisationId: String): Result<Organisation?> = try {
        val doc = orgRef(organisationId).get().await()
        Result.success(doc.takeIf { it.exists() }?.toObject(Organisation::class.java))
    } catch (e: Exception) { Result.failure(e) }

    // ── Save draft (resume later) ───────────────────────────────────────

    /**
     * Saves everything typed so far. Creates any document that doesn't exist yet (older
     * organisation accounts have no `organisations/{uid}`), with the initial UNVERIFIED status
     * that rules require on create. Also links `users/{uid}.organisationId` if it is missing.
     */
    suspend fun saveDraft(
        organisationId: String,
        draft: OrganisationDraft,
        existing: OrganisationSnapshot?,
        linkUser: Boolean
    ): Result<Unit> = try {
        val batch = firestore.batch()
        writeDraft(batch, organisationId, draft, existing)
        if (linkUser) batch.update(userRef(organisationId), "organisationId", organisationId)
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Writes each organisation document ONCE. [orgExtra] / [verificationExtra] (the submit status
     * fields) are merged into the same update, so rules evaluate one coherent change per document.
     */
    private fun writeDraft(
        batch: WriteBatch,
        id: String,
        draft: OrganisationDraft,
        existing: OrganisationSnapshot?,
        orgExtra: Map<String, Any?> = emptyMap(),
        verificationExtra: Map<String, Any?> = emptyMap()
    ) {
        val authorityDocId = authorityDocumentId(draft, existing?.documents.orEmpty())
        upsert(batch, orgRef(id), existing?.organisation != null, draft.organisationFields(id) + orgExtra, OrganisationDocs.organisationCreateDefaults())
        upsert(batch, verificationRef(id), existing?.verification != null, draft.verificationFields(id) + verificationExtra, OrganisationDocs.verificationCreateDefaults())
        upsert(batch, representativeRef(id), existing?.representative != null,
            draft.representativeFields(id, authorityDocId), OrganisationDocs.reviewableCreateDefaults(withCreatedAt = true))
        upsert(batch, affiliationRef(id), existing?.affiliation != null,
            draft.affiliationFields(id), OrganisationDocs.reviewableCreateDefaults(withCreatedAt = false))
    }

    /** `update` (owner fields + updatedAt) when the document exists, otherwise `set` with create defaults. */
    private fun upsert(batch: WriteBatch, ref: DocumentReference, exists: Boolean, fields: Map<String, Any?>, createDefaults: Map<String, Any?>) {
        if (exists) batch.update(ref, fields + ("updatedAt" to FieldValue.serverTimestamp()))
        else batch.set(ref, createDefaults + fields)
    }

    private fun authorityDocumentId(draft: OrganisationDraft, docs: List<OrganisationDocument>): String {
        val preferred = if (draft.isGovernment)
            listOf(OrganisationDocumentType.GOVERNMENT_ORDER, OrganisationDocumentType.AUTHORIZATION_LETTER)
        else listOf(OrganisationDocumentType.AUTHORIZATION_LETTER)
        return preferred.firstNotNullOfOrNull { t -> docs.firstOrNull { it.documentType == t.name }?.documentId }.orEmpty()
    }

    // ── Status transitions the owner may make ───────────────────────────

    /** UNVERIFIED -> CONTACT_VERIFIED. Rules only accept it if the ID token says `email_verified`. */
    suspend fun markContactVerified(organisationId: String): Result<Unit> = try {
        val status = OrganisationVerificationStatus.CONTACT_VERIFIED
        val level = OrganisationVerificationPolicy.levelForOwnerTransition(status)
        val batch = firestore.batch().update(orgRef(organisationId), statusFields("verificationStatus", status, level))
        // The private record may not exist yet (nothing saved); its mirror is then created on first save.
        if (verificationRef(organisationId).get().await().exists()) {
            batch.update(verificationRef(organisationId), statusFields("status", status, level))
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Saves the final draft and submits it for review in ONE batch:
     * documents -> UNDER_REVIEW (level 1), `submittedAt` = server time, plus an audit entry.
     */
    suspend fun submitForReview(
        organisationId: String,
        draft: OrganisationDraft,
        existing: OrganisationSnapshot,
        previousStatus: OrganisationVerificationStatus,
        linkUser: Boolean
    ): Result<Unit> = try {
        val status = OrganisationVerificationStatus.UNDER_REVIEW
        val level = OrganisationVerificationPolicy.levelForOwnerTransition(status)
        require(existing.organisation != null && existing.verification != null) { "Save the draft before submitting" }
        val batch = firestore.batch()
        writeDraft(
            batch, organisationId, draft, existing,
            orgExtra = statusFields("verificationStatus", status, level),
            verificationExtra = statusFields("status", status, level) +
                mapOf("submittedAt" to FieldValue.serverTimestamp(), "declarationAccepted" to true)
        )
        if (linkUser) batch.update(userRef(organisationId), "organisationId", organisationId)
        val action = when (previousStatus) {
            OrganisationVerificationStatus.REJECTED, OrganisationVerificationStatus.EXPIRED -> AuditAction.RESUBMITTED
            else -> AuditAction.SUBMITTED
        }
        appendAudit(batch, organisationId, action, previousStatus.name, status.name, notes = "")
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    private fun statusFields(statusKey: String, status: OrganisationVerificationStatus, level: OrganisationVerificationLevel) = mapOf(
        statusKey to status.name,
        "verificationLevel" to level.name,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun appendAudit(batch: WriteBatch, organisationId: String, action: AuditAction, previous: String, next: String, notes: String) {
        val ref = auditLogs.document()
        batch.set(ref, mapOf(
            "logId" to ref.id,
            "organisationId" to organisationId,
            "action" to action.name,
            "previousStatus" to previous,
            "newStatus" to next,
            "performedBy" to organisationId,
            "performedAt" to FieldValue.serverTimestamp(),
            "reason" to "",
            "notes" to notes,
            "source" to "APP"
        ))
    }

    // ── Contact (account email) verification ────────────────────────────

    suspend fun sendVerificationEmail(): Result<Unit> = try {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        user.sendEmailVerification().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Reloads the Auth user and force-refreshes the ID token, so `request.auth.token.email_verified`
     * is current in Firestore rules. Returns whether the email is verified.
     */
    suspend fun refreshEmailVerified(): Result<Boolean> = try {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        user.reload().await()
        val refreshed = auth.currentUser ?: user
        refreshed.getIdToken(true).await()
        Result.success(refreshed.isEmailVerified)
    } catch (e: Exception) { Result.failure(e) }

    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true
    val accountEmail: String get() = auth.currentUser?.email.orEmpty()

    // ── Documents (private, stored inside Firestore: works on the Spark plan) ──

    /**
     * Stores [file] privately WITHOUT Cloud Storage: metadata in `organisationDocuments/{id}`
     * and the bytes in `organisationDocuments/{id}/chunks/{n}` (see [DocumentChunks]).
     * Photos over the limit are compressed first. Metadata, chunks and the audit entry are
     * written in ONE batch, so a document is either fully stored or not at all.
     * Emits progress 0..1 and finally the stored [OrganisationDocument].
     */
    fun uploadDocument(
        organisationId: String,
        type: OrganisationDocumentType,
        file: PickedFile
    ): Flow<UploadEvent> = flow {
        emit(UploadEvent.Progress(0.05f))
        val prepared = fileReader.prepareDocument(file.uri, file.mimeType)
        if (prepared.bytes.size > DocumentChunks.MAX_STORED_BYTES) {
            throw InvalidUploadException(
                if (prepared.mimeType == "application/pdf") "PDF must be under ${DocumentChunks.MAX_STORED_BYTES / (1024 * 1024)} MB. Try compressing it or uploading a photo instead."
                else "This file is too large even after compression."
            )
        }
        emit(UploadEvent.Progress(0.35f))
        val parts = DocumentChunks.split(prepared.bytes)

        val metaRef = documents.document()
        val documentId = metaRef.id
        val doc = OrganisationDocument(
            documentId = documentId,
            organisationId = organisationId,
            ownerUid = organisationId,
            documentType = type.name,
            storagePath = DocumentChunks.storagePath(documentId),
            storageBackend = DocumentChunks.STORAGE_BACKEND,
            chunkCount = parts.size,
            fileName = OrganisationDocs.displayFileName(file.fileName, prepared.mimeType),
            mimeType = prepared.mimeType,
            sizeBytes = prepared.bytes.size.toLong(),
            verificationStatus = DocumentReviewStatus.PENDING_MANUAL_REVIEW.name
        )
        val batch = firestore.batch()
        batch.set(metaRef, OrganisationDocs.documentCreateFields(doc))
        parts.forEachIndexed { i, part ->
            batch.set(metaRef.collection(FirestorePaths.CHUNKS).document(i.toString()), mapOf("index" to i, "data" to Blob.fromBytes(part)))
        }
        appendAudit(batch, organisationId, AuditAction.DOCUMENT_UPLOADED, "", "", notes = "${type.name}:$documentId")
        emit(UploadEvent.Progress(0.6f))
        batch.commit().await()
        emit(UploadEvent.Progress(1f))
        emit(UploadEvent.Done(doc))
    }

    /** Owner-editable descriptive metadata (number, issuer, dates). */
    suspend fun updateDocumentDetails(
        documentId: String,
        documentNumber: String?,
        issuingAuthority: String?,
        issuedDate: String?,
        expiryDate: String?
    ): Result<Unit> = try {
        documents.document(documentId).update(mapOf(
            "documentNumber" to documentNumber?.trim()?.ifBlank { null },
            "issuingAuthority" to issuingAuthority?.trim()?.ifBlank { null },
            "issuedDate" to issuedDate?.trim()?.ifBlank { null },
            "expiryDate" to expiryDate?.trim()?.ifBlank { null }
        )).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /** Removes a document before submission: its chunks and metadata in one batch. */
    suspend fun removeDocument(document: OrganisationDocument): Result<Unit> = try {
        val metaRef = documents.document(document.documentId)
        val batch = firestore.batch()
        val count = document.chunkCount.coerceIn(0, DocumentChunks.MAX_CHUNKS)
        for (i in 0 until count) batch.delete(metaRef.collection(FirestorePaths.CHUNKS).document(i.toString()))
        batch.delete(metaRef)
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── Logo (public, small data URI on the organisation document) ──────

    suspend fun uploadLogo(organisationId: String, file: PickedFile): Result<String> = try {
        val dataUri = fileReader.prepareLogoDataUri(file.uri)
        orgRef(organisationId).update(mapOf("logoUrl" to dataUri, "updatedAt" to FieldValue.serverTimestamp())).await()
        Result.success(dataUri)
    } catch (e: Exception) { Result.failure(e) }
}

/** Upload rejected before anything was written (file too large, unreadable...). */
class InvalidUploadException(message: String) : IllegalArgumentException(message)

sealed interface UploadEvent {
    data class Progress(val fraction: Float) : UploadEvent
    data class Done(val document: OrganisationDocument) : UploadEvent
}

/** Create-time document shapes that firestore.rules check field by field. */
object OrganisationDocs {

    /** Initial admin-owned fields of `organisations/{id}`. */
    fun organisationCreateDefaults(): Map<String, Any?> = mapOf(
        "logoUrl" to "",
        "verificationStatus" to OrganisationVerificationStatus.UNVERIFIED.name,
        "verificationLevel" to OrganisationVerificationLevel.LEVEL_0_UNVERIFIED.name,
        "verifiedAt" to null,
        "verificationExpiresAt" to null,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    /** Minimal `organisations/{uid}` written at signup, before onboarding. */
    fun initialOrganisation(uid: String, displayName: String): Map<String, Any?> = organisationCreateDefaults() + mapOf(
        "organisationId" to uid,
        "ownerUid" to uid,
        "legalName" to "",
        "displayName" to displayName.trim(),
        "organisationType" to "",
        "sports" to emptyList<String>(),
        "description" to "",
        "website" to "",
        "organisationLevel" to "",
        "country" to "",
        "state" to "",
        "district" to "",
        "city" to "",
        "locality" to "",
        "publicAddress" to "",
        "latitude" to null,
        "longitude" to null,
        "yearEstablished" to null
    )

    fun verificationCreateDefaults(): Map<String, Any?> = mapOf(
        "contactVerified" to false,
        "legalEntityVerified" to false,
        "representativeVerified" to false,
        "addressVerified" to false,
        "sportsCredentialVerified" to false,
        "status" to OrganisationVerificationStatus.UNVERIFIED.name,
        "verificationLevel" to OrganisationVerificationLevel.LEVEL_0_UNVERIFIED.name,
        "submittedAt" to null,
        "reviewedAt" to null,
        "reviewedBy" to null,
        "reviewNotes" to null,
        "rejectionReason" to null,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun reviewableCreateDefaults(withCreatedAt: Boolean): Map<String, Any?> = buildMap {
        put("verificationStatus", DocumentReviewStatus.PENDING_MANUAL_REVIEW.name)
        if (withCreatedAt) put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun documentCreateFields(doc: OrganisationDocument): Map<String, Any?> = mapOf(
        "documentId" to doc.documentId,
        "organisationId" to doc.organisationId,
        "ownerUid" to doc.ownerUid,
        "documentType" to doc.documentType,
        "storagePath" to doc.storagePath,
        "storageBackend" to doc.storageBackend,
        "chunkCount" to doc.chunkCount,
        "fileName" to doc.fileName,
        "mimeType" to doc.mimeType,
        "sizeBytes" to doc.sizeBytes,
        "documentNumber" to null,
        "issuingAuthority" to null,
        "issuedDate" to null,
        "expiryDate" to null,
        "verificationStatus" to DocumentReviewStatus.PENDING_MANUAL_REVIEW.name,
        "uploadedAt" to FieldValue.serverTimestamp(),
        "reviewedAt" to null,
        "reviewedBy" to null,
        "reviewNotes" to null
    )

    /** Original name, with the extension corrected if a photo was converted to JPEG. */
    fun displayFileName(original: String, storedMime: String): String {
        val base = original.substringBeforeLast('.').ifBlank { "document" }.take(80)
        return if (storedMime == "image/jpeg" && !original.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }) "$base.jpg"
        else original.take(100)
    }

    /** Storage object name: letters, digits, dot, dash, underscore; keeps a sensible extension. */
    fun safeFileName(original: String, mimeType: String?): String {
        val ext = when (mimeType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> original.substringAfterLast('.', "bin").lowercase()
        }
        val base = original.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "document" }
        return "$base.$ext"
    }
}
