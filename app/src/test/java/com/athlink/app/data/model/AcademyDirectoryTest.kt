package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class AcademyDirectoryTest {

    private val now = Date(1_800_000_000_000L)

    private fun org(
        name: String, city: String, sports: List<String>, category: String,
        rating: Double? = null, reviews: Int? = null, locality: String = "Gotri",
        status: OrganisationVerificationStatus = OrganisationVerificationStatus.VERIFIED,
        expires: Date? = null
    ) = Organisation(
        organisationId = "dir-$name", displayName = name, city = city, locality = locality, sports = sports,
        venueCategory = category, googleRating = rating, googleReviewCount = reviews,
        verificationStatus = status.name, verificationExpiresAt = expires, listingSource = DIRECTORY_LISTING_SOURCE
    )

    private val list = listOf(
        org("Arjun Badminton", "Vadodara", listOf("Badminton"), "INDOOR", 5.0, 34, "Tandalja"),
        org("MDK Cricket", "Vadodara", listOf("Cricket"), "OUTDOOR", 4.9, 44),
        org("Ace Badminton Stars", "Vadodara", listOf("Badminton"), "INDOOR"),
        org("Sama Indoor", "Vadodara", listOf("Table Tennis"), "INDOOR", 4.4, 2839, "Alkapuri"),
        org("SK Cricket", "Surat", listOf("Cricket"), "OUTDOOR", 4.8, 185, "Dindoli"),
        org("Nurture", "Vadodara", listOf("Badminton", "Tennis"), "INDOOR", 5.0, 39)
    )

    @Test
    fun onlyVerifiedUnexpiredOrganisationsAreListed() {
        assertTrue(AcademyDirectory.isListed(list[0], now))
        assertTrue(AcademyDirectory.isListed(list[0].copy(verificationStatus = "OFFICIAL_GOVERNMENT"), now))
        assertFalse(AcademyDirectory.isListed(list[0].copy(verificationStatus = "UNDER_REVIEW"), now))
        assertFalse(AcademyDirectory.isListed(list[0].copy(verificationStatus = "SUSPENDED"), now))
        assertFalse(AcademyDirectory.isListed(list[0].copy(verificationExpiresAt = Date(now.time - 1)), now))
    }

    @Test
    fun filtersCombineAndRatedAcademiesComeFirst() {
        val badminton = AcademyDirectory.filter(list, city = "vadodara", sport = "Badminton")
        // Equal 5.0 rating: more reviews first; unrated last.
        assertEquals(listOf("Nurture", "Arjun Badminton", "Ace Badminton Stars"), badminton.map { it.displayName })
        assertEquals(listOf("SK Cricket"), AcademyDirectory.filter(list, city = "Surat").map { it.displayName })
        assertEquals(4, AcademyDirectory.filter(list, venueCategory = "INDOOR").size)
        assertEquals(listOf("Sama Indoor"), AcademyDirectory.filter(list, query = "alkapuri").map { it.displayName })
        assertEquals(listOf("Nurture"), AcademyDirectory.filter(list, sport = "Tennis").map { it.displayName })
        assertEquals(6, AcademyDirectory.filter(list).size)
    }

    @Test
    fun citiesSportsAndDefaultCity() {
        assertEquals(listOf("Surat", "Vadodara"), AcademyDirectory.cities(list))
        assertEquals(listOf("Cricket", "Badminton", "Tennis", "Table Tennis"), AcademyDirectory.sports(list))
        assertEquals("Vadodara", AcademyDirectory.defaultCity(" vadodara ", list))
        assertNull(AcademyDirectory.defaultCity("Pune", list))
        assertNull(AcademyDirectory.defaultCity(null, list))
    }

    @Test
    fun requestFormValidation() {
        val today = LocalDate.of(2026, 10, 12)
        val academy = list[5]
        val ok = AcademyRequestForm(sport = "Tennis", preferredDate = "2026-10-15", contactPhone = "98765 43210")
        assertTrue(ok.validate(academy, today).isEmpty())
        assertTrue(ok.copy(contactPhone = "").validate(academy, today).isEmpty())
        assertTrue(ok.copy(contactPhone = "+919876543210").validate(academy, today).isEmpty())

        val bad = AcademyRequestForm(sport = "Cricket", preferredDate = "2026-10-01", message = "x".repeat(301), contactPhone = "12345")
        assertEquals(
            setOf(AcademyRequestForm.Field.SPORT, AcademyRequestForm.Field.DATE, AcademyRequestForm.Field.MESSAGE, AcademyRequestForm.Field.PHONE),
            bad.validate(academy, today).keys
        )
        assertTrue(AcademyRequestForm.Field.DATE in AcademyRequestForm(sport = "Tennis", preferredDate = "").validate(academy, today))
        assertTrue(AcademyRequestForm.Field.DATE in AcademyRequestForm(sport = "Tennis", preferredDate = "2027-01-30").validate(academy, today))
        assertTrue(AcademyRequestForm.Field.SPORT in AcademyRequestForm().validate(academy, today))
    }

    @Test
    fun unknownStoredValuesAreSafe() {
        assertEquals(AcademyRequestStatus.PENDING, AcademyRequestStatus.fromStored("WHATEVER"))
        assertEquals(PreferredTime.EVENING, PreferredTime.fromStored(null))
    }
}
