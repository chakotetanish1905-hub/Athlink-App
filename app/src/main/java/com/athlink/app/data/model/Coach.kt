package com.athlink.app.data.model

import java.util.Date

/**
 * PUBLIC coaching profile, stored at `coaches/{uid}` (same uid as `users/{uid}`).
 *
 * Everything in this document can be read by players once the profile is ACTIVE, because
 * Firestore rules cannot hide individual fields. NEVER add private data here (email, phone, DOB,
 * legal name, ID details, document URLs). Those live in [CoachPrivateProfile] and
 * [CoachVerification].
 *
 * Backward compatibility: every field has a default, so documents written by older app versions
 * (which only had uid/name/sport/bio/experience/rating/reviewCount/hourlyRate/profileImageUrl/
 * location/specializations/totalEarnings/available) still deserialize. Old documents may also
 * contain `email`/`phone`; those are ignored here and removed on the coach's next profile save.
 *
 * Status fields are Strings; read them via the extensions in CoachProfileExtensions.kt
 * (`coach.verification`, `coach.profile`, ...), which map unknown/legacy values safely.
 *
 * Do NOT add computed `val x get() = ...` properties to this class: Firestore serializes public
 * getters as fields. Put derived values in CoachProfileExtensions.kt instead.
 */
data class Coach(
    // ── Identity (public part) ───────────────────────────────────────────
    val uid: String = "",
    /** Display name shown to players. The legal name is in [CoachPrivateProfile]. */
    val name: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val languages: List<String> = emptyList(),

    // ── Sport / coaching ────────────────────────────────────────────────
    /** Primary sport. */
    val sport: String = "",
    val secondarySports: List<String> = emptyList(),
    val specializations: List<String> = emptyList(),
    /** [AgeGroup] names. */
    val ageGroups: List<String> = emptyList(),
    /** [CoachingLevel] names (skill levels coached). */
    val coachingLevels: List<String> = emptyList(),
    /** Years of coaching experience. */
    val experience: Int = 0,
    val currentOrganisation: String = "",
    val previousOrganisations: List<String> = emptyList(),
    val coachingPosition: String = "",
    val competitionExperience: String = "",
    val coachingPhilosophy: String = "",
    /** Self-declared. Never shown as verified. */
    val achievements: List<String> = emptyList(),
    /**
     * LEGACY free-text certifications from the first signup form. Self-declared, never verified.
     * Structured qualifications live in `coaches/{uid}/qualifications`.
     */
    val certifications: List<String> = emptyList(),

    // ── Pricing (denormalised) ──────────────────────────────────────────
    /**
     * "From" price shown on cards and used by legacy booking. Kept equal to the lowest active
     * service price once the coach has services. Full pricing is in `coaches/{uid}/services`.
     */
    val hourlyRate: Double = 0.0,

    // ── Location (coaching area only, never a home address) ──────────────
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val coachingArea: String = "",
    val trainingVenue: String = "",
    /** Display string, e.g. "Andheri, Mumbai". Kept for existing screens. */
    val location: String = "",
    /** Coarsened (~100 m) coordinates of the coaching area/venue, if the coach chose to set them. */
    val lat: Double? = null,
    val lng: Double? = null,
    val geohash: String = "",

    // ── Status (admin-controlled except where noted) ─────────────────────
    /** [ProfileStatus] name. */
    val profileStatus: String = ProfileStatus.DRAFT.name,
    /** [VerificationStatus] name. Legacy "PENDING" reads as NOT_SUBMITTED. */
    val verificationStatus: String = VerificationStatus.NOT_SUBMITTED.name,
    /** [VerificationLevel] name. */
    val verificationLevel: String = VerificationLevel.LEVEL_0_REGISTERED.name,
    /** [CheckStatus] names. */
    val identityStatus: String = CheckStatus.NOT_SUBMITTED.name,
    val qualificationStatus: String = CheckStatus.NOT_SUBMITTED.name,
    val experienceStatus: String = CheckStatus.NOT_SUBMITTED.name,
    val safeguardingStatus: String = CheckStatus.NOT_SUBMITTED.name,
    /** Written by the client from [CoachProfileCompletion]; informational only. */
    val profileCompletionPercentage: Int = 0,

    // ── Stats (server/admin-maintained; coach cannot write) ───────────────
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val totalEarnings: Double = 0.0,
    val isAvailable: Boolean = true,

    // ── Timestamps ───────────────────────────────────────────────────────
    /** Client epoch millis (kept as Long for compatibility with existing documents). */
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Server timestamps (Firestore Timestamp <-> java.util.Date). */
    val submittedAt: Date? = null,
    val verifiedAt: Date? = null,
    val expiresAt: Date? = null
)
