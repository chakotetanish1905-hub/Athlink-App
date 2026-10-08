package com.athlink.app.data.model

import java.util.Date

/**
 * Business rules for organisation verification. The UI uses these to decide what to show; the
 * SAME rules are enforced server-side in `firestore.rules` and in the admin tool
 * (`tools/admin/policy.js`), because client-side status is never trusted. Change all three together.
 *
 *              owner (email verified)          owner submits                admin
 *  UNVERIFIED ───────────────────▶ CONTACT_VERIFIED ───────────▶ UNDER_REVIEW ─────▶ VERIFIED / OFFICIAL_GOVERNMENT
 *      └──────────────── owner submits ─────────────────────────▶     │  admin           │ admin
 *  REJECTED ── owner resubmits ──────────────────────────────────▶    └─────▶ REJECTED   ├─────▶ SUSPENDED ──admin──▶ VERIFIED / OFFICIAL / REJECTED
 *  EXPIRED ─── owner re-verifies ────────────────────────────────▶                       └─────▶ EXPIRED ──admin──▶ SUSPENDED
 */
object OrganisationVerificationPolicy {

    /** Statuses in which the owner may edit verification evidence and submit. */
    val EDITABLE: Set<OrganisationVerificationStatus> = setOf(
        OrganisationVerificationStatus.UNVERIFIED,
        OrganisationVerificationStatus.CONTACT_VERIFIED,
        OrganisationVerificationStatus.REJECTED,
        OrganisationVerificationStatus.EXPIRED
    )

    /** Statuses that grant event-publishing trust. */
    val TRUSTED: Set<OrganisationVerificationStatus> = setOf(
        OrganisationVerificationStatus.VERIFIED,
        OrganisationVerificationStatus.OFFICIAL_GOVERNMENT
    )

    private val ADMIN_TRANSITIONS: Map<OrganisationVerificationStatus, Set<OrganisationVerificationStatus>> = mapOf(
        OrganisationVerificationStatus.UNDER_REVIEW to setOf(
            OrganisationVerificationStatus.VERIFIED,
            OrganisationVerificationStatus.OFFICIAL_GOVERNMENT,
            OrganisationVerificationStatus.REJECTED
        ),
        OrganisationVerificationStatus.VERIFIED to setOf(
            OrganisationVerificationStatus.SUSPENDED, OrganisationVerificationStatus.EXPIRED
        ),
        OrganisationVerificationStatus.OFFICIAL_GOVERNMENT to setOf(
            OrganisationVerificationStatus.SUSPENDED, OrganisationVerificationStatus.EXPIRED
        ),
        OrganisationVerificationStatus.SUSPENDED to setOf(
            OrganisationVerificationStatus.VERIFIED,
            OrganisationVerificationStatus.OFFICIAL_GOVERNMENT,
            OrganisationVerificationStatus.REJECTED
        ),
        OrganisationVerificationStatus.EXPIRED to setOf(OrganisationVerificationStatus.SUSPENDED)
    )

    /**
     * Owner-initiated transitions. [emailVerified] must come from the Firebase ID token
     * (`email_verified`), which rules read from `request.auth.token`; the client cannot fake it.
     */
    fun ownerMayTransition(
        from: OrganisationVerificationStatus,
        to: OrganisationVerificationStatus,
        emailVerified: Boolean
    ): Boolean = emailVerified && when (to) {
        OrganisationVerificationStatus.CONTACT_VERIFIED -> from == OrganisationVerificationStatus.UNVERIFIED
        OrganisationVerificationStatus.UNDER_REVIEW -> from in EDITABLE
        else -> false
    }

    fun adminMayTransition(from: OrganisationVerificationStatus, to: OrganisationVerificationStatus): Boolean =
        ADMIN_TRANSITIONS[from]?.contains(to) == true

    /** OFFICIAL_GOVERNMENT may only ever be granted to a government organisation type. */
    fun mayBeOfficialGovernment(type: OrganisationType?): Boolean = type?.isGovernment == true

    /** Level the owner's own transition results in (never above level 1). */
    fun levelForOwnerTransition(to: OrganisationVerificationStatus): OrganisationVerificationLevel = when (to) {
        OrganisationVerificationStatus.CONTACT_VERIFIED,
        OrganisationVerificationStatus.UNDER_REVIEW -> OrganisationVerificationLevel.LEVEL_1_CONTACT_VERIFIED
        else -> OrganisationVerificationLevel.LEVEL_0_UNVERIFIED
    }

    /** Level an admin approval grants. */
    fun levelForApproval(to: OrganisationVerificationStatus): OrganisationVerificationLevel = when (to) {
        OrganisationVerificationStatus.OFFICIAL_GOVERNMENT -> OrganisationVerificationLevel.LEVEL_3_OFFICIAL_GOVERNMENT
        OrganisationVerificationStatus.VERIFIED -> OrganisationVerificationLevel.LEVEL_2_ORGANISATION_VERIFIED
        else -> throw IllegalArgumentException("$to is not an approval status")
    }

    /** A trusted status past its expiry date is treated as EXPIRED everywhere (UI and rules). */
    fun effectiveStatus(
        stored: OrganisationVerificationStatus,
        expiresAt: Date?,
        now: Date = Date()
    ): OrganisationVerificationStatus =
        if (stored in TRUSTED && expiresAt != null && !expiresAt.after(now)) OrganisationVerificationStatus.EXPIRED
        else stored

    fun canEditVerification(status: OrganisationVerificationStatus): Boolean = status in EDITABLE

    fun canSubmit(status: OrganisationVerificationStatus): Boolean = status in EDITABLE

    /** Drafts are private, so any organisation may keep them except a suspended one. */
    fun canCreateDrafts(status: OrganisationVerificationStatus): Boolean =
        status != OrganisationVerificationStatus.SUSPENDED

    /**
     * Publishing public events requires a trusted, unexpired status AND level >= 2.
     * Pass the EFFECTIVE status (see [effectiveStatus]).
     */
    fun canPublishEvents(status: OrganisationVerificationStatus, level: OrganisationVerificationLevel): Boolean =
        status in TRUSTED && level.rank >= OrganisationVerificationLevel.LEVEL_2_ORGANISATION_VERIFIED.rank

    fun canPublishEvents(org: Organisation?, now: Date = Date()): Boolean =
        org != null && canPublishEvents(org.effectiveStatus(now), org.level)

    /** Message explaining why publishing is blocked, or null when allowed. */
    fun publishBlockedReason(status: OrganisationVerificationStatus): String? = when (status) {
        OrganisationVerificationStatus.VERIFIED, OrganisationVerificationStatus.OFFICIAL_GOVERNMENT -> null
        OrganisationVerificationStatus.UNDER_REVIEW ->
            "Your verification is under review. You can save drafts and publish once you're verified."
        OrganisationVerificationStatus.REJECTED ->
            "Your verification was rejected. Fix the issues and resubmit to publish events."
        OrganisationVerificationStatus.SUSPENDED ->
            "Your organisation is suspended. Publishing is disabled."
        OrganisationVerificationStatus.EXPIRED ->
            "Your verification has expired. Re-verify to publish events again."
        OrganisationVerificationStatus.UNVERIFIED, OrganisationVerificationStatus.CONTACT_VERIFIED ->
            "Only verified organisations can publish public events. Complete verification to publish."
    }

    /** Badge to show. Derived from the effective status AND the level, never chosen by the user. */
    fun badge(status: OrganisationVerificationStatus, level: OrganisationVerificationLevel): OrganisationBadge = when {
        status == OrganisationVerificationStatus.OFFICIAL_GOVERNMENT &&
            level == OrganisationVerificationLevel.LEVEL_3_OFFICIAL_GOVERNMENT -> OrganisationBadge.OFFICIAL_GOVERNMENT
        status in TRUSTED && level.rank >= OrganisationVerificationLevel.LEVEL_2_ORGANISATION_VERIFIED.rank ->
            OrganisationBadge.VERIFIED
        status == OrganisationVerificationStatus.UNDER_REVIEW -> OrganisationBadge.UNDER_REVIEW
        status == OrganisationVerificationStatus.REJECTED -> OrganisationBadge.REJECTED
        status == OrganisationVerificationStatus.SUSPENDED -> OrganisationBadge.SUSPENDED
        status == OrganisationVerificationStatus.EXPIRED -> OrganisationBadge.EXPIRED
        else -> OrganisationBadge.PENDING
    }

    fun badge(org: Organisation?, now: Date = Date()): OrganisationBadge =
        if (org == null) OrganisationBadge.PENDING else badge(org.effectiveStatus(now), org.level)

    /** Where a signed-in organisation lands after login / signup. */
    fun landing(org: Organisation?, now: Date = Date()): OrganisationLanding {
        if (org == null) return OrganisationLanding.ONBOARDING
        return when (org.effectiveStatus(now)) {
            OrganisationVerificationStatus.UNVERIFIED,
            OrganisationVerificationStatus.CONTACT_VERIFIED -> OrganisationLanding.ONBOARDING
            OrganisationVerificationStatus.VERIFIED,
            OrganisationVerificationStatus.OFFICIAL_GOVERNMENT -> OrganisationLanding.DASHBOARD
            OrganisationVerificationStatus.UNDER_REVIEW,
            OrganisationVerificationStatus.REJECTED,
            OrganisationVerificationStatus.SUSPENDED,
            OrganisationVerificationStatus.EXPIRED -> OrganisationLanding.STATUS
        }
    }

    /** Documents (or the organisation) whose credential expiry date has passed. */
    fun expiredDocuments(documents: List<OrganisationDocument>, todayIso: String): List<OrganisationDocument> =
        documents.filter { doc -> !doc.expiryDate.isNullOrBlank() && doc.expiryDate < todayIso }
}

enum class OrganisationLanding { ONBOARDING, STATUS, DASHBOARD }

/** Reusable badge model. Only [OFFICIAL_GOVERNMENT] may look like a government seal. */
enum class OrganisationBadge(val title: String, val explanation: String) {
    PENDING(
        "Verification pending",
        "This organisation has not completed Athlink verification yet."
    ),
    UNDER_REVIEW(
        "Under review",
        "This organisation has submitted its documents and Athlink is reviewing them."
    ),
    VERIFIED(
        "Verified organisation",
        "Athlink has checked this organisation's registration, representative, address and sports credentials."
    ),
    OFFICIAL_GOVERNMENT(
        "Official government organisation",
        "Athlink has confirmed this is an official government sports body and that the representative is authorised by it."
    ),
    REJECTED(
        "Verification rejected",
        "Athlink could not verify this organisation with the information provided."
    ),
    SUSPENDED(
        "Suspended",
        "This organisation's verification is suspended."
    ),
    EXPIRED(
        "Verification expired",
        "This organisation's verification has expired and must be renewed."
    )
}
