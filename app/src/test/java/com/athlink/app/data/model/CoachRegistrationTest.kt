package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachRegistrationTest {

    private val form = CoachRegistration(
        name = " Rajiv Sharma ", email = "Raj@Example.com", password = "secret123", confirmPassword = "secret123",
        phone = "+91 98765-43210", city = "Mumbai", state = "Maharashtra", sport = "Cricket",
        specializations = "Batting, Fielding, Batting", experienceYears = "8",
        coachingLevels = setOf(CoachingLevel.ADVANCED, CoachingLevel.BEGINNER),
        certifications = "BCCI Level 1", hourlyRate = "800",
        bio = "Former state-level cricketer coaching batting technique."
    )

    @Test
    fun validFormHasNoErrors() {
        assertTrue(form.validate().isEmpty())
    }

    @Test
    fun publicCoachStartsAsDraftAndUnverified() {
        val coach = form.toCoach("u1", now = 1L)
        assertEquals(ProfileStatus.DRAFT, coach.profile)
        assertEquals(VerificationStatus.NOT_SUBMITTED, coach.verification)
        assertEquals(VerificationLevel.LEVEL_0_REGISTERED, coach.level)
        assertEquals(CheckStatus.NOT_SUBMITTED, coach.identityCheck)
        assertEquals("India", coach.country)
        assertEquals(listOf("Batting", "Fielding"), coach.specializations)
        assertEquals(listOf("BEGINNER", "ADVANCED"), coach.coachingLevels)
        assertEquals(0f, coach.rating)
        assertEquals(0, coach.reviewCount)
        assertTrue(coach.badges.isEmpty())
    }

    @Test
    fun contactDetailsGoOnlyToPrivateProfile() {
        val coachFields = Coach::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("email must not be in public Coach", "email" !in coachFields)
        assertTrue("phone must not be in public Coach", "phone" !in coachFields)

        val private = form.toPrivateProfile("u1", now = 1L)
        assertEquals("raj@example.com", private.email)
        assertEquals("+919876543210", private.phone)
        assertEquals("Rajiv Sharma", private.fullLegalName)
    }
}
