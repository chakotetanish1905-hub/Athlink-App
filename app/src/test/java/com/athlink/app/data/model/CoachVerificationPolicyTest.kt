package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachVerificationPolicyTest {

    @Test
    fun legacyAndUnknownStatusesNeverReadAsVerified() {
        assertEquals(VerificationStatus.NOT_SUBMITTED, VerificationStatus.fromStored("PENDING"))
        assertEquals(VerificationStatus.NOT_SUBMITTED, VerificationStatus.fromStored(null))
        assertEquals(VerificationStatus.NOT_SUBMITTED, VerificationStatus.fromStored("verified"))
        assertEquals(VerificationStatus.NOT_SUBMITTED, VerificationStatus.fromStored("VERIFIED "))
        assertEquals(VerificationLevel.LEVEL_0_REGISTERED, VerificationLevel.fromStored("LEVEL_9"))
        assertEquals(CheckStatus.NOT_SUBMITTED, CheckStatus.fromStored("APPROVED"))
        assertEquals(ProfileStatus.DRAFT, ProfileStatus.fromStored(null))
    }

    @Test
    fun oldCoachDocumentDefaultsAreSafe() {
        val legacy = Coach(uid = "c1", verificationStatus = "PENDING")
        assertEquals(VerificationStatus.NOT_SUBMITTED, legacy.verification)
        assertEquals(ProfileStatus.DRAFT, legacy.profile)
        assertTrue(legacy.hasLegacyVerificationStatus)
        assertFalse(legacy.isDiscoverable)
        assertTrue(legacy.badges.isEmpty())
    }

    @Test
    fun coachCanOnlyMoveToUnderReview() {
        for (from in VerificationStatus.entries) for (to in VerificationStatus.entries) {
            val allowed = CoachVerificationPolicy.coachMayTransition(from, to)
            if (to != VerificationStatus.UNDER_REVIEW) assertFalse("$from -> $to", allowed)
        }
        assertTrue(CoachVerificationPolicy.coachMayTransition(VerificationStatus.NOT_SUBMITTED, VerificationStatus.UNDER_REVIEW))
        assertTrue(CoachVerificationPolicy.coachMayTransition(VerificationStatus.ACTION_REQUIRED, VerificationStatus.UNDER_REVIEW))
        assertTrue(CoachVerificationPolicy.coachMayTransition(VerificationStatus.EXPIRED, VerificationStatus.UNDER_REVIEW))
        assertFalse(CoachVerificationPolicy.coachMayTransition(VerificationStatus.VERIFIED, VerificationStatus.UNDER_REVIEW))
        assertFalse(CoachVerificationPolicy.coachMayTransition(VerificationStatus.SUSPENDED, VerificationStatus.UNDER_REVIEW))
        assertFalse(CoachVerificationPolicy.coachMayTransition(VerificationStatus.UNDER_REVIEW, VerificationStatus.UNDER_REVIEW))
    }

    @Test
    fun adminTransitions() {
        assertTrue(CoachVerificationPolicy.adminMayTransition(VerificationStatus.UNDER_REVIEW, VerificationStatus.VERIFIED))
        assertTrue(CoachVerificationPolicy.adminMayTransition(VerificationStatus.UNDER_REVIEW, VerificationStatus.ACTION_REQUIRED))
        assertTrue(CoachVerificationPolicy.adminMayTransition(VerificationStatus.VERIFIED, VerificationStatus.EXPIRED))
        assertFalse(CoachVerificationPolicy.adminMayTransition(VerificationStatus.NOT_SUBMITTED, VerificationStatus.VERIFIED))
    }

    @Test
    fun evidenceIsLockedWhileUnderReviewOrVerified() {
        assertTrue(CoachVerificationPolicy.coachMayEditEvidence(VerificationStatus.NOT_SUBMITTED))
        assertTrue(CoachVerificationPolicy.coachMayEditEvidence(VerificationStatus.ACTION_REQUIRED))
        assertFalse(CoachVerificationPolicy.coachMayEditEvidence(VerificationStatus.UNDER_REVIEW))
        assertFalse(CoachVerificationPolicy.coachMayEditEvidence(VerificationStatus.VERIFIED))
        assertFalse(CoachVerificationPolicy.coachMayEditEvidence(VerificationStatus.SUSPENDED))
    }

    @Test
    fun profileStatusRules() {
        val p = CoachVerificationPolicy
        assertTrue(p.coachMayChangeProfileStatus(ProfileStatus.DRAFT, ProfileStatus.SUBMITTED, VerificationStatus.NOT_SUBMITTED))
        assertFalse(p.coachMayChangeProfileStatus(ProfileStatus.SUBMITTED, ProfileStatus.ACTIVE, VerificationStatus.UNDER_REVIEW))
        assertFalse(p.coachMayChangeProfileStatus(ProfileStatus.DRAFT, ProfileStatus.ACTIVE, VerificationStatus.NOT_SUBMITTED))
        assertTrue(p.coachMayChangeProfileStatus(ProfileStatus.ACTIVE, ProfileStatus.DEACTIVATED, VerificationStatus.VERIFIED))
        assertTrue(p.coachMayChangeProfileStatus(ProfileStatus.DEACTIVATED, ProfileStatus.ACTIVE, VerificationStatus.VERIFIED))
        assertFalse(p.coachMayChangeProfileStatus(ProfileStatus.DEACTIVATED, ProfileStatus.ACTIVE, VerificationStatus.SUSPENDED))
        assertFalse(p.coachMayChangeProfileStatus(ProfileStatus.SUSPENDED, ProfileStatus.ACTIVE, VerificationStatus.VERIFIED))
    }

    @Test
    fun discoverableNeedsActiveAndVerified() {
        assertTrue(CoachVerificationPolicy.isDiscoverable(ProfileStatus.ACTIVE, VerificationStatus.VERIFIED))
        assertFalse(CoachVerificationPolicy.isDiscoverable(ProfileStatus.ACTIVE, VerificationStatus.UNDER_REVIEW))
        assertFalse(CoachVerificationPolicy.isDiscoverable(ProfileStatus.SUBMITTED, VerificationStatus.VERIFIED))
        assertFalse(CoachVerificationPolicy.isDiscoverable(ProfileStatus.DEACTIVATED, VerificationStatus.VERIFIED))
    }

    @Test
    fun levelsAreCumulativeAndIdentityAloneIsNotCoachVerified() {
        val v = CheckStatus.VERIFIED
        val n = CheckStatus.NOT_SUBMITTED
        val p = CoachVerificationPolicy
        assertEquals(VerificationLevel.LEVEL_0_REGISTERED, p.deriveLevel(n, v, v, v))
        assertEquals(VerificationLevel.LEVEL_1_IDENTITY_VERIFIED, p.deriveLevel(v, n, n, v))
        assertEquals(VerificationLevel.LEVEL_2_COACH_VERIFIED, p.deriveLevel(v, v, n, n))
        assertEquals(VerificationLevel.LEVEL_2_COACH_VERIFIED, p.deriveLevel(v, n, v, CheckStatus.PENDING))
        assertEquals(VerificationLevel.LEVEL_3_SAFEGUARDING_VERIFIED, p.deriveLevel(v, v, n, v))

        assertEquals(listOf(VerificationBadge.IDENTITY_VERIFIED),
            p.badges(VerificationStatus.VERIFIED, v, CheckStatus.PENDING, n, n))
        assertTrue(p.badges(VerificationStatus.UNDER_REVIEW, v, v, v, v).isEmpty())
        assertTrue(p.badges(VerificationStatus.SUSPENDED, v, v, v, v).isEmpty())
        assertEquals(3, p.badges(VerificationStatus.VERIFIED, v, v, v, v).size)
    }

    @Test
    fun safeguardingRequiredOnlyForMinors() {
        assertFalse(CoachProfileRules.safeguardingRequired(listOf("ADULTS")))
        assertTrue(CoachProfileRules.safeguardingRequired(listOf("ADULTS", "UNDER_14")))
        assertFalse(CoachProfileRules.safeguardingRequired(listOf("SOMETHING_ELSE")))
    }
}
