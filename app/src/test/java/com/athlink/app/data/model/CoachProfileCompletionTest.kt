package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachProfileCompletionTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun complete(ageGroups: List<String> = listOf(AgeGroup.ADULTS.name)) = CoachProfileSnapshot(
        coach = Coach(
            uid = "u1", name = "Coach Raj", profileImageUrl = "https://example.invalid/p.jpg",
            bio = "Former state-level cricketer coaching batting technique for 8 years.",
            languages = listOf("English", "Hindi"),
            sport = "Cricket", specializations = listOf("Batting"), ageGroups = ageGroups,
            coachingLevels = listOf(CoachingLevel.BEGINNER.name), experience = 8,
            coachingPosition = "Head batting coach",
            city = "Mumbai", state = "Maharashtra", country = "India", coachingArea = "Andheri"
        ),
        privateProfile = CoachPrivateProfile(
            uid = "u1", fullLegalName = "Rajiv Sharma", dateOfBirth = "1990-05-20",
            email = "raj@example.com", phone = "+919876543210"
        ),
        verification = CoachVerification(
            identity = IdentityEvidence(
                idType = GovernmentIdType.PAN.name, idLast4 = "234F",
                documentPath = "coachVerification/u1/identity/pan.jpg"
            ),
            codeOfConductAccepted = true, codeOfConductVersion = CodeOfConduct.VERSION
        ),
        qualifications = listOf(
            CoachQualification(
                qualificationId = "q1", title = "BCCI Level 1", issuingOrganization = "BCCI",
                certificateNumber = "L1-0042", sport = "Cricket", issueDate = "2021-04-10",
                documentPath = "coachVerification/u1/qualifications/q1/c.pdf"
            )
        ),
        services = listOf(CoachService(serviceId = "s1", title = "1-on-1", price = 500.0, venueArea = "Andheri")),
        availability = listOf(CoachAvailability(availabilityId = "a1", dayOfWeek = "MONDAY", startTime = "10:00", endTime = "13:00"))
    )

    @Test
    fun emptyProfileIsZeroAndCannotSubmit() {
        val r = CoachProfileCompletion.calculate(CoachProfileSnapshot(), today)
        assertEquals(0, r.percentage)
        assertFalse(r.canSubmit)
        assertTrue(r.completedSections.isEmpty())
        assertTrue("Primary sport" in r.missingFields)
    }

    @Test
    fun completeAdultCoachIs100AndCanSubmit() {
        val r = CoachProfileCompletion.calculate(complete(), today)
        assertEquals(r.missingFields.toString(), 100, r.percentage)
        assertTrue(r.canSubmit)
        assertEquals(CoachProfileSection.entries.toSet(), r.completedSections)
        assertTrue("First aid / CPR certificate" in r.recommendations)
    }

    @Test
    fun coachingMinorsRequiresSafeguarding() {
        val r = CoachProfileCompletion.calculate(complete(listOf(AgeGroup.UNDER_14.name)), today)
        assertFalse(r.canSubmit)
        assertFalse(r.isSectionComplete(CoachProfileSection.SAFETY))
        assertEquals(
            listOf("Child protection / safeguarding certificate", "Police / background verification"),
            r.missingIn(CoachProfileSection.SAFETY)
        )
        assertTrue(r.percentage in 1..99)

        val withDocs = complete(listOf(AgeGroup.UNDER_14.name)).let { s ->
            s.copy(verification = s.verification.copy(
                childProtection = SafetyEvidence(provided = true, documentPath = "x/cp.pdf"),
                policeVerification = SafetyEvidence(provided = true, documentPath = "x/pv.pdf")
            ))
        }
        assertTrue(CoachProfileCompletion.calculate(withDocs, today).canSubmit)
    }

    @Test
    fun expiredSafetyDocumentDoesNotCount() {
        val s = complete(listOf(AgeGroup.UNDER_10.name)).let {
            it.copy(verification = it.verification.copy(
                childProtection = SafetyEvidence(provided = true, documentPath = "x", expiryDate = "2025-01-01"),
                policeVerification = SafetyEvidence(provided = true, documentPath = "y")
            ))
        }
        val r = CoachProfileCompletion.calculate(s, today)
        assertEquals(listOf("Child protection / safeguarding certificate"), r.missingIn(CoachProfileSection.SAFETY))
    }

    @Test
    fun invalidQualificationAndSlotsAreFlagged() {
        val s = complete().let {
            it.copy(
                qualifications = it.qualifications + CoachQualification(qualificationId = "q2", title = "Fitness"),
                availability = it.availability + CoachAvailability(availabilityId = "a2", dayOfWeek = "MONDAY", startTime = "12:00", endTime = "14:00")
            )
        }
        val r = CoachProfileCompletion.calculate(s, today)
        assertFalse(r.canSubmit)
        assertTrue("1 qualification has missing or invalid details" in r.missingFields)
        assertTrue("Fix overlapping or invalid time slots" in r.missingFields)
    }

    @Test
    fun outdatedCodeOfConductMustBeReaccepted() {
        val s = complete().let { it.copy(verification = it.verification.copy(codeOfConductVersion = "2020-01")) }
        assertTrue("Code of Conduct accepted" in CoachProfileCompletion.calculate(s, today).missingFields)
    }

    @Test
    fun inactiveServiceDoesNotCount() {
        val s = complete().let { it.copy(services = it.services.map { sv -> sv.copy(active = false) }) }
        assertTrue("At least one service with price" in CoachProfileCompletion.calculate(s, today).missingFields)
    }
}
