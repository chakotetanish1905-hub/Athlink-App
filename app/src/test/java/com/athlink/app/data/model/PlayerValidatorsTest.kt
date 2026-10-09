package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlayerValidatorsTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val v = PlayerValidators

    @Test
    fun fullName() {
        assertNull(v.fullName("Asha Patel"))
        assertNull(v.fullName("  Ravi  "))                 // trimmed
        assertNull(v.fullName("D'Souza-Rao"))
        assertNotNull(v.fullName(""))
        assertNotNull(v.fullName("   "))
        assertNotNull(v.fullName("A"))                     // too short
        assertNotNull(v.fullName("x".repeat(61)))          // too long
        assertNotNull(v.fullName("Asha123"))               // digits
    }

    @Test
    fun email() {
        assertNull(v.email("player@athlink.in"))
        assertNull(v.email("  a.b+c@x.co  "))
        assertNotNull(v.email(""))
        assertNotNull(v.email("not-an-email"))
        assertNotNull(v.email("a@b"))
    }

    @Test
    fun passwordIsOnlyCheckedForPresenceAndMatch() {
        assertNull(v.password("x"))                        // Firebase enforces strength
        assertNotNull(v.password(""))
        assertNull(v.confirmPassword("secret1", "secret1"))
        assertNotNull(v.confirmPassword("secret1", "secret2"))
        assertNotNull(v.confirmPassword("secret1", ""))
    }

    @Test
    fun dateOfBirth() {
        assertNull(v.dateOfBirth("2008-03-12", today))     // 18
        assertNull(v.dateOfBirth("2015-01-01", today))     // 11: minors allowed
        assertNull(v.dateOfBirth("2021-10-09", today))     // exactly 5
        assertNotNull(v.dateOfBirth("", today))
        assertNotNull(v.dateOfBirth("12/03/2008", today))  // not ISO
        assertNotNull(v.dateOfBirth("2008-02-30", today))  // impossible date
        assertNotNull(v.dateOfBirth("2026-10-10", today))  // future
        assertNotNull(v.dateOfBirth("2022-01-01", today))  // under 5
        assertNotNull(v.dateOfBirth("1900-01-01", today))  // over 100
    }

    @Test
    fun stateMustComeFromListInIndia() {
        assertNull(v.state("India", "Gujarat"))
        assertNotNull(v.state("India", ""))
        assertNotNull(v.state("India", "Gujrat"))           // typo not accepted in India
        assertNull(v.state("Nepal", "Bagmati"))             // free text abroad
        assertNotNull(v.state("Nepal", ""))
    }

    @Test
    fun cityAndCountry() {
        assertNull(v.city("Surat"))
        assertNull(v.city("Navi Mumbai"))
        assertNotNull(v.city(""))
        assertNotNull(v.city("S"))
        assertNotNull(v.city("Surat 395007"))               // no pin codes
        assertNull(v.country("India"))
        assertNotNull(v.country(""))
    }

    @Test
    fun optionalPhone() {
        assertNull(v.optionalPhone(""))
        assertNull(v.optionalPhone("98765 43210"))
        assertNull(v.optionalPhone("+91-98765-43210"))
        assertNotNull(v.optionalPhone("12345"))
        assertNotNull(v.optionalPhone("call me"))
    }

    @Test
    fun sports() {
        assertNull(v.primarySport("Cricket"))
        assertNotNull(v.primarySport(""))
        assertNotNull(v.primarySport("Quidditch"))
        assertNull(v.secondarySports("Cricket", listOf("Football", "Tennis")))
        assertNotNull(v.secondarySports("Cricket", listOf("Cricket")))
        assertNotNull(v.secondarySports("Cricket", listOf("Football", "Tennis", "Hockey", "Chess", "Boxing", "Kabaddi")))
    }

    @Test
    fun levelGoalsAndChoices() {
        assertNotNull(v.skillLevel(null))
        assertNull(v.skillLevel(SkillLevel.BEGINNER))
        assertNotNull(v.goals(emptyList()))
        assertNull(v.goals(listOf(PlayerGoal.FIND_COACH)))
        assertNotNull(v.requiredChoice<CoachingFormat>(null, "x"))
    }

    @Test
    fun experienceCannotExceedAge() {
        assertNull(v.yearsOfExperience("", 15))
        assertNull(v.yearsOfExperience("5", 15))
        assertNotNull(v.yearsOfExperience("16", 15))
        assertNotNull(v.yearsOfExperience("-1", 30))
        assertNotNull(v.yearsOfExperience("two", 30))
    }

    @Test
    fun optionalTextLimits() {
        assertNull(v.optionalText("", "bio", 500))
        assertNull(v.optionalText("x".repeat(500), "bio", 500))
        assertNotNull(v.optionalText("x".repeat(501), "bio", 500))
        assertNotNull(v.achievements(List(11) { "Won $it" }))
        assertNotNull(v.achievements(listOf("x".repeat(101))))
    }

    @Test
    fun ageAndMinorDetection() {
        assertEquals(18, PlayerAge.ageOf("2008-10-09", today))
        assertEquals(17, PlayerAge.ageOf("2008-10-10", today))
        assertTrue(PlayerAge.isMinor("2008-10-10", today))
        assertFalse(PlayerAge.isMinor("2008-10-09", today))
        assertTrue(PlayerAge.isMinor("", today))           // unknown DOB is never assumed adult
        assertEquals("12 Mar 2008", PlayerAge.display("2008-03-12"))
    }

    @Test
    fun regionIsDerivedFromState() {
        assertEquals("West India", PlayerLocation.regionFor("India", "Gujarat"))
        assertEquals("North-East India", PlayerLocation.regionFor("india", "Assam"))
        assertEquals("", PlayerLocation.regionFor("India", ""))
        assertEquals("International", PlayerLocation.regionFor("Nepal", "Bagmati"))
        assertEquals(36, PlayerLocation.INDIAN_STATES.size)
    }
}
