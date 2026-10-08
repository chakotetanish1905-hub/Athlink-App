package com.athlink.app.data.model

import java.util.Date

/**
 * Metadata for one uploaded verification document: `organisationDocuments/{documentId}`.
 * The file itself is in PRIVATE Cloud Storage at [storagePath]
 * (`organisation_documents/{organisationId}/{documentId}/{fileName}`) and is never exposed through
 * a public download URL. Readable by the owner and admins only.
 *
 * The owner writes the descriptive fields; [verificationStatus], [reviewedAt], [reviewedBy] and
 * [reviewNotes] are admin-only (enforced in firestore.rules).
 */
data class OrganisationDocument(
    val documentId: String = "",
    val organisationId: String = "",
    /** Always equals [organisationId] (one organisation per account); lets rules check ownership cheaply. */
    val ownerUid: String = "",
    /** [OrganisationDocumentType] name. */
    val documentType: String = "",
    val storagePath: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val documentNumber: String? = null,
    val issuingAuthority: String? = null,
    /** yyyy-MM-dd */
    val issuedDate: String? = null,
    /** yyyy-MM-dd; null = does not expire. */
    val expiryDate: String? = null,
    /** [DocumentReviewStatus] name. */
    val verificationStatus: String = DocumentReviewStatus.PENDING_MANUAL_REVIEW.name,
    val uploadedAt: Date? = null,
    val reviewedAt: Date? = null,
    val reviewedBy: String? = null,
    val reviewNotes: String? = null
)

val OrganisationDocument.type: OrganisationDocumentType? get() = OrganisationDocumentType.fromStored(documentType)
val OrganisationDocument.reviewStatus: DocumentReviewStatus get() = DocumentReviewStatus.fromStored(verificationStatus)

enum class OrganisationDocumentType(val label: String) {
    REGISTRATION_CERTIFICATE("Registration certificate"),
    INCORPORATION_CERTIFICATE("Certificate of incorporation"),
    PAN_DOCUMENT("PAN card / PAN allotment letter"),
    GST_DOCUMENT("GST registration certificate"),
    ADDRESS_PROOF("Address proof"),
    AUTHORIZATION_LETTER("Authorisation letter"),
    GOVERNMENT_ORDER("Government order / official letter"),
    SPORTS_AFFILIATION("Sports federation affiliation"),
    SPORTS_LICENSE("Sports licence"),
    ACCREDITATION("Accreditation"),
    SPORTS_RECOGNITION("Sports authority recognition"),
    OTHER_SUPPORTING_DOCUMENT("Other supporting document");

    companion object {
        fun fromStored(value: String?): OrganisationDocumentType? = entries.firstOrNull { it.name == value }
    }
}

/** A file the user picked on the device, before it is uploaded. Produced by the UI layer. */
data class PickedFile(
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long
)
