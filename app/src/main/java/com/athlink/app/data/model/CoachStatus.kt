package com.athlink.app.data.model

/*
 * Status types for the verified-coach profile.
 *
 * All of these are stored in Firestore as their `name` String (never as the enum itself) so that
 *  - old documents with unknown/legacy values still deserialize, and
 *  - security rules can compare plain strings.
 * Always read them through the `fromStored` helpers below, which map anything unknown to the
 * SAFEST value (never to a verified state).
 */

/**
 * Overall verification state of a coach (`coaches/{uid}.verificationStatus`).
 *
 * Coach-initiated:  NOT_SUBMITTED / ACTION_REQUIRED / REJECTED / EXPIRED -> UNDER_REVIEW
 * Admin-only:       everything else (see [CoachVerificationPolicy]).
 *
 * Legacy: documents created by the first coach-registration version stored "PENDING". Nothing was
 * ever reviewed at that point, so "PENDING" is read as [NOT_SUBMITTED].
 */
enum class VerificationStatus(val label: String) {
    NOT_SUBMITTED("Not submitted"),
    UNDER_REVIEW("Under review"),
    ACTION_REQUIRED("Action required"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    SUSPENDED("Suspended"),
    EXPIRED("Expired");

    companion object {
        const val LEGACY_PENDING = "PENDING"

        fun fromStored(value: String?): VerificationStatus =
            entries.firstOrNull { it.name == value } ?: NOT_SUBMITTED
    }
}

/**
 * Status of one verification area (identity, qualifications, experience, safeguarding) or of a
 * single item (one qualification, one safety document, one reference).
 */
enum class CheckStatus(val label: String) {
    NOT_SUBMITTED("Not submitted"),
    PENDING("Pending review"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    EXPIRED("Expired");

    companion object {
        fun fromStored(value: String?): CheckStatus =
            entries.firstOrNull { it.name == value } ?: NOT_SUBMITTED
    }
}

/**
 * What Athlink has actually verified. Each level includes the ones below it.
 * Level 1 says NOTHING about coaching qualifications.
 */
enum class VerificationLevel(val rank: Int, val label: String) {
    LEVEL_0_REGISTERED(0, "Registered"),
    LEVEL_1_IDENTITY_VERIFIED(1, "Identity verified"),
    LEVEL_2_COACH_VERIFIED(2, "Coach verified"),
    LEVEL_3_SAFEGUARDING_VERIFIED(3, "Safeguarding verified");

    companion object {
        fun fromStored(value: String?): VerificationLevel =
            entries.firstOrNull { it.name == value } ?: LEVEL_0_REGISTERED
    }
}

/**
 * Whether the profile is live. Kept separate from verification: a coach can be VERIFIED and
 * still DEACTIVATED (paused), and a coach must never be ACTIVE while UNDER_REVIEW.
 */
enum class ProfileStatus(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    DEACTIVATED("Deactivated");

    companion object {
        fun fromStored(value: String?): ProfileStatus =
            entries.firstOrNull { it.name == value } ?: DRAFT
    }
}

/** Age groups a coach works with. The first three involve minors (safeguarding applies). */
enum class AgeGroup(val label: String, val involvesMinors: Boolean) {
    UNDER_10("Under 10", true),
    UNDER_14("Under 14", true),
    UNDER_18("Under 18", true),
    ADULTS("Adults", false);

    companion object {
        fun fromStored(value: String?): AgeGroup? = entries.firstOrNull { it.name == value }
    }
}

/** Verification badge shown to players, with the "What does this mean?" text. */
enum class VerificationBadge(val title: String, val explanation: String) {
    IDENTITY_VERIFIED(
        "Identity Verified",
        "Athlink has checked this coach's government ID. This does not cover coaching qualifications."
    ),
    COACH_VERIFIED(
        "Coach Verified",
        "Athlink has verified this coach's identity and the coaching qualification(s) or experience marked as verified on this profile."
    ),
    SAFEGUARDING_VERIFIED(
        "Safeguarding Verified",
        "Athlink has reviewed the applicable safeguarding requirements (child-protection training and police/background verification) for this coach."
    );
}
