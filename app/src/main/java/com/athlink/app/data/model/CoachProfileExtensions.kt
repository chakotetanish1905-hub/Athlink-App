package com.athlink.app.data.model

/*
 * Typed, safe views over Coach's String status fields.
 * These are extensions (not class members) on purpose: Firestore would serialize member getters.
 */

val Coach.verification: VerificationStatus get() = VerificationStatus.fromStored(verificationStatus)
val Coach.profile: ProfileStatus get() = ProfileStatus.fromStored(profileStatus)
val Coach.level: VerificationLevel get() = VerificationLevel.fromStored(verificationLevel)
val Coach.identityCheck: CheckStatus get() = CheckStatus.fromStored(identityStatus)
val Coach.qualificationCheck: CheckStatus get() = CheckStatus.fromStored(qualificationStatus)
val Coach.experienceCheck: CheckStatus get() = CheckStatus.fromStored(experienceStatus)
val Coach.safeguardingCheck: CheckStatus get() = CheckStatus.fromStored(safeguardingStatus)

/** True only for ACTIVE + VERIFIED coaches. */
val Coach.isDiscoverable: Boolean
    get() = CoachVerificationPolicy.isDiscoverable(profile, verification)

/** Badges a player may see, derived from what was actually verified. */
val Coach.badges: List<VerificationBadge>
    get() = CoachVerificationPolicy.badges(verification, identityCheck, qualificationCheck, experienceCheck, safeguardingCheck)

/** Whether the coach can still edit ID / safety / qualification evidence. */
val Coach.canEditEvidence: Boolean
    get() = CoachVerificationPolicy.coachMayEditEvidence(verification)

/** Stored value was the legacy "PENDING" from the first signup version. */
val Coach.hasLegacyVerificationStatus: Boolean
    get() = verificationStatus == VerificationStatus.LEGACY_PENDING

/** Lowest active service price, used to keep [Coach.hourlyRate] ("from" price) in sync. */
fun List<CoachService>.lowestActivePrice(): Double? =
    filter { it.active && it.price > 0 }.minOfOrNull { it.price }

/** Display string for existing screens, e.g. "Andheri, Mumbai". */
fun Coach.displayLocation(): String = listOf(coachingArea, city)
    .filter { it.isNotBlank() }
    .joinToString(", ")
    .ifBlank { location }
