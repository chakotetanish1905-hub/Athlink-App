package com.athlink.app.data.model

import java.util.Date

/**
 * PUBLIC organisation profile: `organisations/{organisationId}`.
 *
 * `organisationId` is the owner's Firebase Auth uid (one organisation per account). That makes
 * impersonation impossible at the rules level: you can only ever create / edit the organisation
 * whose id is your own uid.
 *
 * Any signed-in user can read this document (players see who runs an event), so it must never
 * contain private data. Firestore rules cannot hide individual fields. Registration numbers, PAN,
 * GSTIN, the registered address, official contact details and the PIN code live in
 * [OrganisationVerification] (owner + admin only).
 *
 * The status fields ([verificationStatus], [verificationLevel], [verifiedAt],
 * [verificationExpiresAt]) are ADMIN-ONLY. The owner can never set them; the only exceptions are
 * the two transitions in [OrganisationVerificationPolicy.ownerMayTransition], which rules check.
 *
 * Every field has a default so partial / older documents deserialize. Do not add computed
 * `val x get()` properties here (Firestore would store them); use the extensions below.
 */
data class Organisation(
    val organisationId: String = "",
    val ownerUid: String = "",
    val legalName: String = "",
    val displayName: String = "",
    /** [OrganisationType] name. */
    val organisationType: String = "",
    /** First entry = primary sport. */
    val sports: List<String> = emptyList(),
    val description: String = "",
    val logoUrl: String = "",
    val website: String = "",
    /** [OrganisationLevel] name. */
    val organisationLevel: String = "",

    // ── Public location (no private address here) ───────────────────────
    val country: String = "",
    val state: String = "",
    val district: String = "",
    val city: String = "",
    val locality: String = "",
    /** e.g. "Andheri West, Mumbai". Shown to players instead of the registered address. */
    val publicAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,

    val yearEstablished: Int? = null,

    // ── Admin-only status ────────────────────────────────────────────────
    val verificationStatus: String = OrganisationVerificationStatus.UNVERIFIED.name,
    val verificationLevel: String = OrganisationVerificationLevel.LEVEL_0_UNVERIFIED.name,
    val verifiedAt: Date? = null,
    val verificationExpiresAt: Date? = null,

    val createdAt: Date? = null,
    val updatedAt: Date? = null
)

val Organisation.type: OrganisationType? get() = OrganisationType.fromStored(organisationType)
val Organisation.storedStatus: OrganisationVerificationStatus get() = OrganisationVerificationStatus.fromStored(verificationStatus)
val Organisation.level: OrganisationVerificationLevel get() = OrganisationVerificationLevel.fromStored(verificationLevel)
val Organisation.primarySport: String get() = sports.firstOrNull().orEmpty()

/** Status after applying expiry: a verified organisation past [Organisation.verificationExpiresAt] is EXPIRED. */
fun Organisation.effectiveStatus(now: Date = Date()): OrganisationVerificationStatus =
    OrganisationVerificationPolicy.effectiveStatus(storedStatus, verificationExpiresAt, now)

/** "City, District, State" style label for public display. */
val Organisation.displayLocation: String
    get() = publicAddress.ifBlank {
        listOf(locality, city, district, state).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
    }

/**
 * PRIVATE verification record: `organisationVerification/{organisationId}`.
 * Owner + admin read. The owner fills the evidence fields while verification is editable;
 * the check flags, [status], [verificationLevel], review fields and [rejectionReason] are admin-only.
 * ([status] / [verificationLevel] mirror the public document for the admin review view.)
 */
data class OrganisationVerification(
    val verificationId: String = "",
    val organisationId: String = "",
    val ownerUid: String = "",

    // ── Official contact (verified manually by the reviewer) ─────────────
    val officialEmail: String = "",
    val officialPhone: String = "",

    // ── Legal / registration ─────────────────────────────────────────────
    val registrationNumber: String = "",
    /** Human-readable registration type, e.g. "Society registration" (derived from the org type). */
    val registrationType: String = "",
    val issuingAuthority: String = "",
    val pan: String = "",
    val gstRegistered: Boolean = false,
    val gstin: String = "",
    val registeredAddress: String = "",
    /** Proprietor / owner name for small academies without incorporation. */
    val proprietorName: String = "",

    // ── Government organisations only ────────────────────────────────────
    val governmentDepartmentName: String = "",
    /** [GovernmentLevel] name. */
    val governmentLevel: String = "",
    val ministryOrDepartment: String = "",
    val authorisationReferenceNumber: String = "",

    // ── Facility (private: exact address and PIN) ────────────────────────
    val facilityAddress: String = "",
    val pincode: String = "",

    // ── Sports legitimacy (counts are approximate, optional) ─────────────
    val yearsActive: Int? = null,
    val approxCoaches: Int? = null,
    val approxAthletes: Int? = null,

    /** Owner confirms the information is true and they are authorised (set on submit). */
    val declarationAccepted: Boolean = false,

    // ── Admin-only review fields ─────────────────────────────────────────
    val contactVerified: Boolean = false,
    val legalEntityVerified: Boolean = false,
    val representativeVerified: Boolean = false,
    val addressVerified: Boolean = false,
    val sportsCredentialVerified: Boolean = false,
    val status: String = OrganisationVerificationStatus.UNVERIFIED.name,
    val verificationLevel: String = OrganisationVerificationLevel.LEVEL_0_UNVERIFIED.name,
    val submittedAt: Date? = null,
    val reviewedAt: Date? = null,
    val reviewedBy: String? = null,
    val reviewNotes: String? = null,
    val rejectionReason: String? = null,

    val updatedAt: Date? = null
)

/**
 * Authorised representative: `organisationRepresentatives/{representativeId}`.
 * One primary representative per organisation, so `representativeId == organisationId`.
 * Owner + admin read. [verificationStatus] is admin-only. No residential address or personal
 * identity documents are collected.
 */
data class OrganisationRepresentative(
    val representativeId: String = "",
    val organisationId: String = "",
    val ownerUid: String = "",
    val fullName: String = "",
    val designation: String = "",
    /** Government organisations: the officer's department. */
    val department: String = "",
    val officialEmail: String = "",
    val officialPhone: String = "",
    /** [RepresentativeRelationship] name. */
    val relationshipToOrganisation: String = "",
    /** [AuthorisationEvidenceType] name. */
    val authorisationEvidenceType: String = "",
    /** Id of the AUTHORIZATION_LETTER / GOVERNMENT_ORDER document, once uploaded. */
    val authorizationDocumentId: String = "",
    /** [DocumentReviewStatus] name; admin-only. */
    val verificationStatus: String = DocumentReviewStatus.PENDING_MANUAL_REVIEW.name,
    val createdAt: Date? = null,
    val updatedAt: Date? = null
)

/**
 * Sports-legitimacy claim: `organisationAffiliations/{affiliationId}`. One primary record per
 * organisation (`affiliationId == organisationId`) in this version; the collection allows more later.
 * A private academy may have no federation affiliation ([hasFederationAffiliation] = false) and
 * prove legitimacy with another credential instead.
 */
data class OrganisationAffiliation(
    val affiliationId: String = "",
    val organisationId: String = "",
    val ownerUid: String = "",
    val hasFederationAffiliation: Boolean = false,
    val governingBodyName: String = "",
    val affiliationNumber: String = "",
    /** Accreditation / licence / recognition, free text (e.g. "SAI recognised academy"). */
    val accreditationDetails: String = "",
    /** [DocumentReviewStatus] name; admin-only. */
    val verificationStatus: String = DocumentReviewStatus.PENDING_MANUAL_REVIEW.name,
    val updatedAt: Date? = null
)

/**
 * Append-only audit entry: `verificationAuditLogs/{logId}`. Never updated or deleted.
 * Admin decisions are written by the admin tool (Admin SDK). The app may only append its OWN
 * SUBMITTED / RESUBMITTED / DOCUMENT_UPLOADED entries, which rules validate field by field.
 */
data class VerificationAuditLog(
    val logId: String = "",
    val organisationId: String = "",
    /** [AuditAction] name. */
    val action: String = "",
    val previousStatus: String = "",
    val newStatus: String = "",
    val performedBy: String = "",
    val performedAt: Date? = null,
    val reason: String = "",
    val notes: String = "",
    /** "APP" (owner action) or "ADMIN" (admin tool). */
    val source: String = ""
)

enum class AuditAction {
    SUBMITTED, DOCUMENT_UPLOADED, DOCUMENT_REVIEWED, APPROVED, REJECTED, RESUBMITTED,
    SUSPENDED, REACTIVATED, EXPIRED, REVERIFIED;

    companion object {
        /** Actions the organisation itself may append (everything else is admin-only). */
        val OWNER_ACTIONS = setOf(SUBMITTED, RESUBMITTED, DOCUMENT_UPLOADED)
    }
}

/** Everything the app knows about the signed-in organisation, loaded together. */
data class OrganisationSnapshot(
    /** Null when the account has no `organisations/{uid}` document yet (older accounts). */
    val organisation: Organisation?,
    val verification: OrganisationVerification?,
    val representative: OrganisationRepresentative?,
    val affiliation: OrganisationAffiliation?,
    val documents: List<OrganisationDocument>
)
