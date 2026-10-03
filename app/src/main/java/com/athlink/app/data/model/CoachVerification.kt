package com.athlink.app.data.model

import java.util.Date

/**
 * PRIVATE personal details of a coach: `coachPrivate/{uid}`.
 * Readable/writable by the coach; readable by admins. Never readable by players.
 */
data class CoachPrivateProfile(
    val uid: String = "",
    val fullLegalName: String = "",
    /** ISO-8601 date, yyyy-MM-dd. */
    val dateOfBirth: String = "",
    val email: String = "",
    /** Normalised: leading '+' kept, digits only. */
    val phone: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * PRIVATE verification evidence: `coachVerification/{uid}`.
 *
 * The coach can read it and fill the evidence fields while verification is editable
 * (NOT_SUBMITTED / ACTION_REQUIRED / REJECTED / EXPIRED). All `status` fields,
 * [actionRequiredMessage] and [rejectionReason] are written only by admins.
 * Internal reviewer notes are NOT here; they are in [CoachVerificationReview] (admin-only).
 *
 * No government ID number is ever stored: only the ID type, last 4 characters (to match the
 * document) and the private Storage path of the uploaded (masked, for Aadhaar) document.
 */
data class CoachVerification(
    val uid: String = "",
    val identity: IdentityEvidence = IdentityEvidence(),
    val firstAid: SafetyEvidence = SafetyEvidence(),
    val childProtection: SafetyEvidence = SafetyEvidence(),
    val policeVerification: SafetyEvidence = SafetyEvidence(),
    val codeOfConductAccepted: Boolean = false,
    val codeOfConductVersion: String = "",
    val codeOfConductAcceptedAt: Date? = null,
    /** Message from the reviewer telling the coach what to fix (shown to the coach). */
    val actionRequiredMessage: String = "",
    val rejectionReason: String = "",
    val submittedAt: Date? = null,
    val reviewedAt: Date? = null,
    val updatedAt: Long = 0L
)

data class IdentityEvidence(
    /** [GovernmentIdType] name. */
    val idType: String = "",
    /** Last 4 characters of the ID, for matching against the document. Never the full number. */
    val idLast4: String = "",
    val documentPath: String = "",
    val documentMimeType: String = "",
    /** [CheckStatus] name; admin-only. */
    val status: String = CheckStatus.NOT_SUBMITTED.name
)

/** First aid, child protection or police verification evidence. */
data class SafetyEvidence(
    /** False = coach states they do not have it (optional items only). */
    val provided: Boolean = false,
    val provider: String = "",
    val referenceNumber: String = "",
    /** yyyy-MM-dd */
    val issueDate: String = "",
    /** yyyy-MM-dd; empty = no expiry. */
    val expiryDate: String = "",
    val documentPath: String = "",
    val documentMimeType: String = "",
    /** [CheckStatus] name; admin-only. */
    val status: String = CheckStatus.NOT_SUBMITTED.name
)

enum class GovernmentIdType(val label: String, val hint: String) {
    AADHAAR_MASKED("Aadhaar (masked)", "Upload the masked Aadhaar that shows only the last 4 digits."),
    PAN("PAN card", ""),
    PASSPORT("Passport", ""),
    DRIVING_LICENCE("Driving licence", ""),
    VOTER_ID("Voter ID", "");

    companion object {
        fun fromStored(value: String?): GovernmentIdType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * ADMIN-ONLY review record: `coachVerification/{uid}/admin/review`.
 * Neither players nor the coach can read or write it.
 */
data class CoachVerificationReview(
    val reviewerId: String = "",
    val internalNotes: String = "",
    val decision: String = "",
    val decidedAt: Date? = null,
    val rejectedAt: Date? = null
)

/** Current Code of Conduct version the coach must accept. Bump to require re-acceptance. */
object CodeOfConduct {
    const val VERSION = "2026-10"
}
