package com.athlink.app.data.model

/**
 * Business rules for coach verification. The client uses these to drive the UI; the SAME rules
 * are enforced server-side in `firestore.rules`, because client-side status is never trusted.
 * If you change a transition here, change the rules too (see ATHLINK_COACH_FIREBASE_SCHEMA.md).
 *
 *            coach submits                 admin
 *  NOT_SUBMITTED ───────────▶ UNDER_REVIEW ──────▶ VERIFIED ──admin──▶ SUSPENDED / EXPIRED
 *  ACTION_REQUIRED ─────────▶      │  admin                         (EXPIRED ──coach──▶ UNDER_REVIEW)
 *  REJECTED ────────────────▶      ├──────▶ ACTION_REQUIRED
 *  EXPIRED ─────────────────▶      └──────▶ REJECTED
 */
object CoachVerificationPolicy {

    /** Statuses from which the coach may (re)submit. */
    val COACH_SUBMITTABLE_FROM: Set<VerificationStatus> = setOf(
        VerificationStatus.NOT_SUBMITTED,
        VerificationStatus.ACTION_REQUIRED,
        VerificationStatus.REJECTED,
        VerificationStatus.EXPIRED
    )

    /** Admin-only transitions. */
    private val ADMIN_TRANSITIONS: Map<VerificationStatus, Set<VerificationStatus>> = mapOf(
        VerificationStatus.UNDER_REVIEW to setOf(
            VerificationStatus.ACTION_REQUIRED, VerificationStatus.VERIFIED, VerificationStatus.REJECTED
        ),
        VerificationStatus.ACTION_REQUIRED to setOf(VerificationStatus.REJECTED),
        VerificationStatus.VERIFIED to setOf(VerificationStatus.SUSPENDED, VerificationStatus.EXPIRED),
        VerificationStatus.SUSPENDED to setOf(VerificationStatus.VERIFIED, VerificationStatus.REJECTED),
        VerificationStatus.EXPIRED to setOf(VerificationStatus.SUSPENDED)
    )

    fun coachMayTransition(from: VerificationStatus, to: VerificationStatus): Boolean =
        to == VerificationStatus.UNDER_REVIEW && from in COACH_SUBMITTABLE_FROM

    fun adminMayTransition(from: VerificationStatus, to: VerificationStatus): Boolean =
        ADMIN_TRANSITIONS[from]?.contains(to) == true

    /**
     * The coach may edit verification evidence (ID, safety documents, qualification documents)
     * only when nothing is being reviewed and nothing is approved yet.
     */
    fun coachMayEditEvidence(status: VerificationStatus): Boolean = status in COACH_SUBMITTABLE_FROM

    /**
     * Profile-status changes the coach may make:
     *  - DRAFT -> SUBMITTED (together with submitting for verification)
     *  - ACTIVE -> DEACTIVATED (pause the profile)
     *  - DEACTIVATED -> ACTIVE only while verificationStatus is VERIFIED
     * Everything else (making a profile ACTIVE the first time, SUSPENDED) is admin-only.
     */
    fun coachMayChangeProfileStatus(
        from: ProfileStatus,
        to: ProfileStatus,
        verification: VerificationStatus
    ): Boolean = when {
        from == ProfileStatus.DRAFT && to == ProfileStatus.SUBMITTED -> true
        from == ProfileStatus.ACTIVE && to == ProfileStatus.DEACTIVATED -> true
        from == ProfileStatus.DEACTIVATED && to == ProfileStatus.ACTIVE -> verification == VerificationStatus.VERIFIED
        else -> false
    }

    /**
     * A coach appears in player search / can be booked only when BOTH the profile is ACTIVE and
     * verification is VERIFIED. (An ACTIVE + UNDER_REVIEW combination is never valid.)
     */
    fun isDiscoverable(profile: ProfileStatus, verification: VerificationStatus): Boolean =
        profile == ProfileStatus.ACTIVE && verification == VerificationStatus.VERIFIED

    /**
     * Level from the four area statuses. Each level requires the one below.
     *  L1: identity verified.
     *  L2: L1 + (qualifications verified OR experience verified).
     *  L3: L2 + safeguarding verified.
     */
    fun deriveLevel(
        identity: CheckStatus,
        qualification: CheckStatus,
        experience: CheckStatus,
        safeguarding: CheckStatus
    ): VerificationLevel {
        if (identity != CheckStatus.VERIFIED) return VerificationLevel.LEVEL_0_REGISTERED
        val coachVerified = qualification == CheckStatus.VERIFIED || experience == CheckStatus.VERIFIED
        if (!coachVerified) return VerificationLevel.LEVEL_1_IDENTITY_VERIFIED
        if (safeguarding != CheckStatus.VERIFIED) return VerificationLevel.LEVEL_2_COACH_VERIFIED
        return VerificationLevel.LEVEL_3_SAFEGUARDING_VERIFIED
    }

    /**
     * Badges a player may see. Derived from the area statuses (not from the stored level), so a
     * badge can never appear for something that is only self-declared. Nothing is shown unless
     * the overall status is VERIFIED.
     */
    fun badges(
        overall: VerificationStatus,
        identity: CheckStatus,
        qualification: CheckStatus,
        experience: CheckStatus,
        safeguarding: CheckStatus
    ): List<VerificationBadge> {
        if (overall != VerificationStatus.VERIFIED) return emptyList()
        return when (deriveLevel(identity, qualification, experience, safeguarding)) {
            VerificationLevel.LEVEL_0_REGISTERED -> emptyList()
            VerificationLevel.LEVEL_1_IDENTITY_VERIFIED -> listOf(VerificationBadge.IDENTITY_VERIFIED)
            VerificationLevel.LEVEL_2_COACH_VERIFIED ->
                listOf(VerificationBadge.IDENTITY_VERIFIED, VerificationBadge.COACH_VERIFIED)
            VerificationLevel.LEVEL_3_SAFEGUARDING_VERIFIED ->
                listOf(VerificationBadge.IDENTITY_VERIFIED, VerificationBadge.COACH_VERIFIED, VerificationBadge.SAFEGUARDING_VERIFIED)
        }
    }
}

/** Configurable submission requirements. */
object CoachProfileRules {
    /** At least one qualification with a certificate is needed to submit. */
    const val REQUIRE_QUALIFICATION = true
    /** References are optional for now; flip to require one for submission. */
    const val REQUIRE_REFERENCE = false
    /** Child protection + police verification are required when coaching any minors' age group. */
    fun safeguardingRequired(ageGroups: List<String>): Boolean =
        ageGroups.mapNotNull { AgeGroup.fromStored(it) }.any { it.involvesMinors }
}
