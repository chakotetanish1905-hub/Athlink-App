package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

private typealias S = OrganisationVerificationStatus
private typealias L = OrganisationVerificationLevel

class OrganisationVerificationPolicyTest {

    private val now = Date(1_800_000_000_000L)
    private val past = Date(now.time - 86_400_000L)
    private val future = Date(now.time + 86_400_000L)

    @Test
    fun unknownOrMissingValuesAreNeverTrusted() {
        assertEquals(S.UNVERIFIED, S.fromStored(null))
        assertEquals(S.UNVERIFIED, S.fromStored("verified"))
        assertEquals(S.UNVERIFIED, S.fromStored("PENDING"))
        assertEquals(L.LEVEL_0_UNVERIFIED, L.fromStored("LEVEL_9"))
        assertEquals(DocumentReviewStatus.PENDING_MANUAL_REVIEW, DocumentReviewStatus.fromStored("APPROVED"))
        // A brand-new / legacy organisation document is unverified and cannot publish.
        val legacy = Organisation(organisationId = "o1")
        assertEquals(S.UNVERIFIED, legacy.effectiveStatus(now))
        assertFalse(OrganisationVerificationPolicy.canPublishEvents(legacy, now))
        assertFalse(OrganisationVerificationPolicy.canPublishEvents(null, now))
    }

    @Test
    fun ownerCanNeverMarkThemselvesVerified() { // TEST 12 (logic level)
        for (from in S.entries) for (to in S.entries) for (email in listOf(true, false)) {
            val allowed = OrganisationVerificationPolicy.ownerMayTransition(from, to, email)
            if (to in OrganisationVerificationPolicy.TRUSTED || to == S.REJECTED || to == S.SUSPENDED || to == S.EXPIRED) {
                assertFalse("$from -> $to", allowed)
            }
            if (!email) assertFalse("needs verified email: $from -> $to", allowed)
        }
        assertTrue(OrganisationVerificationPolicy.ownerMayTransition(S.UNVERIFIED, S.CONTACT_VERIFIED, true))
        assertTrue(OrganisationVerificationPolicy.ownerMayTransition(S.CONTACT_VERIFIED, S.UNDER_REVIEW, true))
        assertTrue(OrganisationVerificationPolicy.ownerMayTransition(S.UNVERIFIED, S.UNDER_REVIEW, true))
        assertFalse(OrganisationVerificationPolicy.ownerMayTransition(S.UNDER_REVIEW, S.UNDER_REVIEW, true))
        assertFalse(OrganisationVerificationPolicy.ownerMayTransition(S.SUSPENDED, S.UNDER_REVIEW, true))
        assertFalse(OrganisationVerificationPolicy.ownerMayTransition(S.VERIFIED, S.UNDER_REVIEW, true))
        // Owner transitions never raise the level above 1.
        S.entries.forEach { assertTrue(OrganisationVerificationPolicy.levelForOwnerTransition(it).rank <= 1) }
    }

    @Test
    fun rejectedAndExpiredCanBeCorrectedAndResubmitted() { // TEST 14, TEST 17
        assertTrue(OrganisationVerificationPolicy.canEditVerification(S.REJECTED))
        assertTrue(OrganisationVerificationPolicy.ownerMayTransition(S.REJECTED, S.UNDER_REVIEW, true))
        assertTrue(OrganisationVerificationPolicy.ownerMayTransition(S.EXPIRED, S.UNDER_REVIEW, true))
        assertFalse(OrganisationVerificationPolicy.canEditVerification(S.UNDER_REVIEW))
        assertFalse(OrganisationVerificationPolicy.canEditVerification(S.VERIFIED))
        assertFalse(OrganisationVerificationPolicy.canEditVerification(S.SUSPENDED))
    }

    @Test
    fun adminTransitions() {
        assertTrue(OrganisationVerificationPolicy.adminMayTransition(S.UNDER_REVIEW, S.VERIFIED))
        assertTrue(OrganisationVerificationPolicy.adminMayTransition(S.UNDER_REVIEW, S.OFFICIAL_GOVERNMENT))
        assertTrue(OrganisationVerificationPolicy.adminMayTransition(S.UNDER_REVIEW, S.REJECTED))
        assertTrue(OrganisationVerificationPolicy.adminMayTransition(S.VERIFIED, S.SUSPENDED))
        assertTrue(OrganisationVerificationPolicy.adminMayTransition(S.SUSPENDED, S.VERIFIED))
        assertFalse(OrganisationVerificationPolicy.adminMayTransition(S.UNVERIFIED, S.VERIFIED))
        assertFalse(OrganisationVerificationPolicy.adminMayTransition(S.REJECTED, S.VERIFIED))
        assertTrue(OrganisationVerificationPolicy.mayBeOfficialGovernment(OrganisationType.GOVERNMENT_SPORTS_AUTHORITY))
        assertFalse(OrganisationVerificationPolicy.mayBeOfficialGovernment(OrganisationType.NATIONAL_SPORTS_FEDERATION))
        assertFalse(OrganisationVerificationPolicy.mayBeOfficialGovernment(OrganisationType.SPORTS_ACADEMY))
        assertFalse(OrganisationVerificationPolicy.mayBeOfficialGovernment(null))
    }

    @Test
    fun publishingRequiresTrustedUnexpiredStatusAndLevel2() { // TEST 5, 15, 16, 17
        fun org(status: S, level: L, expires: Date? = null) =
            Organisation(organisationId = "o", verificationStatus = status.name, verificationLevel = level.name, verificationExpiresAt = expires)

        assertTrue(OrganisationVerificationPolicy.canPublishEvents(org(S.VERIFIED, L.LEVEL_2_ORGANISATION_VERIFIED), now))
        assertTrue(OrganisationVerificationPolicy.canPublishEvents(org(S.OFFICIAL_GOVERNMENT, L.LEVEL_3_OFFICIAL_GOVERNMENT, future), now))
        // Status alone is not enough: the level must match.
        assertFalse(OrganisationVerificationPolicy.canPublishEvents(org(S.VERIFIED, L.LEVEL_1_CONTACT_VERIFIED), now))
        // Expired by date, even though the stored status still says VERIFIED.
        assertFalse(OrganisationVerificationPolicy.canPublishEvents(org(S.VERIFIED, L.LEVEL_2_ORGANISATION_VERIFIED, past), now))
        assertEquals(S.EXPIRED, org(S.VERIFIED, L.LEVEL_2_ORGANISATION_VERIFIED, past).effectiveStatus(now))
        for (s in listOf(S.UNVERIFIED, S.CONTACT_VERIFIED, S.UNDER_REVIEW, S.REJECTED, S.SUSPENDED, S.EXPIRED)) {
            assertFalse("$s", OrganisationVerificationPolicy.canPublishEvents(org(s, L.LEVEL_2_ORGANISATION_VERIFIED), now))
            assertTrue(OrganisationVerificationPolicy.publishBlockedReason(s) != null)
        }
        assertNull(OrganisationVerificationPolicy.publishBlockedReason(S.VERIFIED))
    }

    @Test
    fun draftsAllowedExceptWhenSuspended() {
        S.entries.forEach { assertEquals(it != S.SUSPENDED, OrganisationVerificationPolicy.canCreateDrafts(it)) }
    }

    @Test
    fun governmentBadgeOnlyForLevel3() {
        assertEquals(OrganisationBadge.OFFICIAL_GOVERNMENT, OrganisationVerificationPolicy.badge(S.OFFICIAL_GOVERNMENT, L.LEVEL_3_OFFICIAL_GOVERNMENT))
        // Status says official but the level doesn't: never show the government badge.
        assertEquals(OrganisationBadge.VERIFIED, OrganisationVerificationPolicy.badge(S.OFFICIAL_GOVERNMENT, L.LEVEL_2_ORGANISATION_VERIFIED))
        // VERIFIED with level 0 is inconsistent data: no trust badge.
        assertEquals(OrganisationBadge.PENDING, OrganisationVerificationPolicy.badge(S.VERIFIED, L.LEVEL_0_UNVERIFIED))
        assertEquals(OrganisationBadge.PENDING, OrganisationVerificationPolicy.badge(S.UNVERIFIED, L.LEVEL_3_OFFICIAL_GOVERNMENT))
        assertEquals(OrganisationBadge.UNDER_REVIEW, OrganisationVerificationPolicy.badge(S.UNDER_REVIEW, L.LEVEL_1_CONTACT_VERIFIED))
    }

    @Test
    fun loginLanding() {
        assertEquals(OrganisationLanding.ONBOARDING, OrganisationVerificationPolicy.landing(null, now))
        fun land(s: S, expires: Date? = null) = OrganisationVerificationPolicy.landing(
            Organisation(verificationStatus = s.name, verificationLevel = L.LEVEL_2_ORGANISATION_VERIFIED.name, verificationExpiresAt = expires), now
        )
        assertEquals(OrganisationLanding.ONBOARDING, land(S.UNVERIFIED))
        assertEquals(OrganisationLanding.ONBOARDING, land(S.CONTACT_VERIFIED))
        assertEquals(OrganisationLanding.STATUS, land(S.UNDER_REVIEW))
        assertEquals(OrganisationLanding.STATUS, land(S.REJECTED))
        assertEquals(OrganisationLanding.STATUS, land(S.SUSPENDED))
        assertEquals(OrganisationLanding.DASHBOARD, land(S.VERIFIED))
        assertEquals(OrganisationLanding.STATUS, land(S.VERIFIED, past))
    }

    @Test
    fun expiredDocumentsDetected() {
        val docs = listOf(
            OrganisationDocument(documentId = "a", expiryDate = "2020-01-01"),
            OrganisationDocument(documentId = "b", expiryDate = null),
            OrganisationDocument(documentId = "c", expiryDate = "2099-01-01")
        )
        assertEquals(listOf("a"), OrganisationVerificationPolicy.expiredDocuments(docs, "2026-10-08").map { it.documentId })
    }
}
