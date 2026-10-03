package com.athlink.app.data.model

import com.athlink.app.utils.Geohash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachValidatorsTest {

    private val today = LocalDate.of(2026, 10, 3)
    private val v = CoachValidators

    @Test
    fun dateOfBirth() {
        assertNull(v.dateOfBirth("1990-05-20", today))
        assertNull(v.dateOfBirth("2008-10-03", today))            // exactly 18
        assertNotNull(v.dateOfBirth("2008-10-04", today))         // 1 day short of 18
        assertNotNull(v.dateOfBirth("2027-01-01", today))         // future
        assertNotNull(v.dateOfBirth("1990-02-30", today))         // impossible date
        assertNotNull(v.dateOfBirth("20/05/1990", today))         // wrong format
        assertNotNull(v.dateOfBirth("1900-01-01", today))         // > 100 years
        assertNotNull(v.dateOfBirth("", today))
    }

    @Test
    fun names() {
        assertNull(v.fullLegalName("Rajiv K. Sharma"))
        assertNull(v.fullLegalName("Ananya D'Souza-Iyer"))
        assertNotNull(v.fullLegalName("R2D2"))
        assertNotNull(v.fullLegalName("  "))
        assertNotNull(v.fullLegalName("a".repeat(81)))
        assertNull(v.displayName("Coach Raj"))
        assertNotNull(v.displayName("R"))
    }

    @Test
    fun phoneAndId() {
        assertNull(v.phone("+91 98765-43210"))
        assertNotNull(v.phone("12345"))
        assertNull(v.idLast4("4321"))
        assertNotNull(v.idLast4("12345"))
        assertNotNull(v.idLast4("12-4"))
    }

    @Test
    fun experience() {
        assertNull(v.experienceYears("8", ageYears = 30))
        assertNotNull(v.experienceYears("-1"))
        assertNotNull(v.experienceYears("61"))
        assertNotNull(v.experienceYears("ten"))
        assertNotNull(v.experienceYears("20", ageYears = 25))
    }

    @Test
    fun servicesAndPricing() {
        assertNull(v.price("500"))
        assertNotNull(v.price("0"))
        assertNotNull(v.price("-10"))
        assertNotNull(v.price("abc"))
        assertNull(v.durationMinutes(60))
        assertNotNull(v.durationMinutes(0))
        assertNotNull(v.durationMinutes(62))
        assertNull(v.maxPlayers(1, ServiceType.INDIVIDUAL))
        assertNotNull(v.maxPlayers(3, ServiceType.INDIVIDUAL))
        assertNull(v.maxPlayers(8, ServiceType.GROUP))
        assertNotNull(v.maxPlayers(1, ServiceType.TEAM))

        val ok = CoachService(serviceId = "s1", title = "1-on-1 batting", price = 500.0, venueArea = "Andheri")
        assertTrue(v.service(ok).isEmpty())
        assertTrue("venue required in person", "venueArea" in v.service(ok.copy(venueArea = "")))
        assertTrue(v.service(ok.copy(venueArea = "", mode = ServiceMode.ONLINE.name)).isEmpty())
        assertTrue("equipmentDescription" in v.service(ok.copy(equipmentRequired = true)))
    }

    @Test
    fun qualifications() {
        val q = CoachQualification(
            qualificationId = "q1", title = "BCCI Level 1", issuingOrganization = "BCCI",
            certificateNumber = "BCCI/L1/2021/0042", sport = "Cricket",
            issueDate = "2021-04-10", expiryDate = "2027-04-10", documentPath = "coachVerification/u/qualifications/q1/c.pdf"
        )
        assertTrue(v.qualification(q, today).isEmpty())
        assertTrue("certificateNumber" in v.qualification(q.copy(certificateNumber = ""), today))
        assertTrue("issueDate" in v.qualification(q.copy(issueDate = "2027-01-01"), today))
        assertTrue("expiryDate" in v.qualification(q.copy(expiryDate = "2020-01-01"), today))   // before issue
        assertTrue("expiryDate" in v.qualification(q.copy(expiryDate = "2026-01-01"), today))   // expired
        assertTrue(v.qualification(q.copy(expiryDate = ""), today).isEmpty())                   // no expiry
        assertTrue("document" in v.qualification(q.copy(documentPath = ""), today))

        val dup = q.copy(qualificationId = "q2", certificateNumber = " bcci/l1/2021/0042 ")
        assertTrue(v.isDuplicateQualification(dup, listOf(q)))
        assertFalse(v.isDuplicateQualification(q, listOf(q)))   // same item being edited
    }

    @Test
    fun availability() {
        fun slot(id: String, day: Weekday, s: String, e: String) =
            CoachAvailability(availabilityId = id, dayOfWeek = day.name, startTime = s, endTime = e)

        assertNull(v.slot(slot("a", Weekday.MONDAY, "10:00", "13:00")))
        assertNotNull(v.slot(slot("a", Weekday.MONDAY, "13:00", "10:00")))
        assertNotNull(v.slot(slot("a", Weekday.MONDAY, "10:00", "10:15")))
        assertNotNull(v.slot(slot("a", Weekday.MONDAY, "25:00", "26:00")))
        assertNotNull(v.slot(slot("a", Weekday.MONDAY, "9:00", "10:00")))

        val errors = v.availability(listOf(
            slot("a", Weekday.MONDAY, "10:00", "13:00"),
            slot("b", Weekday.MONDAY, "12:00", "14:00"),
            slot("c", Weekday.TUESDAY, "12:00", "14:00"),
            slot("d", Weekday.MONDAY, "13:00", "15:00")
        ))
        assertEquals(setOf("b", "d"), errors.keys)   // b overlaps a; d overlaps b
    }

    @Test
    fun documents() {
        assertNull(v.document("application/pdf", 1_000_000))
        assertNotNull(v.document("application/msword", 1_000))
        assertNotNull(v.document(null, 1_000))
        assertNotNull(v.document("image/png", 0))
        assertNotNull(v.document("image/png", 6L * 1024 * 1024))
        assertNull(v.photo("image/webp", 500_000))
        assertNotNull(v.photo("application/pdf", 500_000))
    }

    @Test
    fun geohash() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        assertEquals("te7ud2", Geohash.encode(19.076, 72.8777))   // Mumbai, default precision 6
        assertEquals(19.076, Geohash.coarsen(19.07612345), 0.0)
    }
}
