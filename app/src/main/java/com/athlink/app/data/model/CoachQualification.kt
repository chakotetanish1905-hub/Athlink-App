package com.athlink.app.data.model

import java.util.Date

/**
 * One coaching qualification: `coaches/{uid}/qualifications/{qualificationId}`.
 *
 * Visibility: the coach and admins can read all of them. Players can read ONLY documents whose
 * [verificationStatus] is VERIFIED (enforced by security rules and by the player query).
 *
 * The certificate file is NOT linked by a public download URL. [documentPath] is the private
 * Cloud Storage path (`coachVerification/{uid}/qualifications/{qualificationId}/...`), readable
 * only by the owner and admins.
 *
 * The coach may edit a qualification only while it is not VERIFIED and the overall verification
 * is not UNDER_REVIEW. [verificationStatus], [verifiedAt], [verifiedBy] and [rejectionReason]
 * are admin-only.
 */
data class CoachQualification(
    val qualificationId: String = "",
    val title: String = "",
    val issuingOrganization: String = "",
    val certificateNumber: String = "",
    val level: String = "",
    val sport: String = "",
    /** ISO-8601 date, yyyy-MM-dd. */
    val issueDate: String = "",
    /** ISO-8601 date, yyyy-MM-dd; empty = does not expire. */
    val expiryDate: String = "",

    val documentPath: String = "",
    val documentFileName: String = "",
    val documentMimeType: String = "",

    /** [CheckStatus] name. Coach can only ever write NOT_SUBMITTED or PENDING. */
    val verificationStatus: String = CheckStatus.NOT_SUBMITTED.name,
    val verifiedAt: Date? = null,
    val verifiedBy: String = "",
    val rejectionReason: String = "",

    /** [QualificationSource] name. */
    val source: String = QualificationSource.ONBOARDING.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class QualificationSource {
    /** Added in the onboarding flow. */
    ONBOARDING,
    /** Pre-filled from the free-text `certifications` entered at first signup. */
    LEGACY_SIGNUP
}
