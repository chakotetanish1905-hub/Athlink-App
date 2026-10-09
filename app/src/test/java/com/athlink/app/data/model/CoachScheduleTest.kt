package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachScheduleTest {

    private fun range(day: Weekday, s: String, e: String) =
        CoachAvailability(availabilityId = "$day$s", dayOfWeek = day.name, startTime = s, endTime = e)

    @Test
    fun validRangesAreAccepted() {
        assertNull(AvailabilityRules.problem(Weekday.MONDAY, "17:00", "19:00", emptyList()))
        assertNull(AvailabilityRules.problem(Weekday.MONDAY, "06:00", "07:00", listOf(range(Weekday.MONDAY, "17:00", "19:00"))))
        // Touching ranges do not overlap.
        assertNull(AvailabilityRules.problem(Weekday.MONDAY, "19:00", "20:00", listOf(range(Weekday.MONDAY, "17:00", "19:00"))))
    }

    @Test
    fun badRangesAreRejected() {
        assertNotNull(AvailabilityRules.problem(Weekday.MONDAY, "19:00", "17:00", emptyList()))
        assertNotNull(AvailabilityRules.problem(Weekday.MONDAY, "17:00", "17:30", emptyList()))
        assertNotNull(AvailabilityRules.problem(Weekday.MONDAY, "04:00", "06:00", emptyList()))
        assertNotNull(AvailabilityRules.problem(Weekday.MONDAY, "18:00", "20:00", listOf(range(Weekday.MONDAY, "17:00", "19:00"))))
        val full = listOf("06:00", "09:00", "12:00", "15:00").map { range(Weekday.TUESDAY, it, it.replaceFirst(Regex("^\\d\\d"), (it.take(2).toInt() + 1).toString().padStart(2, '0'))) }
        assertNotNull(AvailabilityRules.problem(Weekday.TUESDAY, "18:00", "19:00", full))
    }

    @Test
    fun timeOptionsAndGrouping() {
        assertEquals("05:00", AvailabilityRules.TIME_OPTIONS.first())
        assertEquals("23:00", AvailabilityRules.TIME_OPTIONS.last())
        val grouped = AvailabilityRules.byDay(listOf(range(Weekday.FRIDAY, "18:00", "19:00"), range(Weekday.MONDAY, "07:00", "08:00"), range(Weekday.FRIDAY, "06:00", "07:00")))
        assertEquals(listOf(Weekday.MONDAY, Weekday.FRIDAY), grouped.keys.toList())
        assertEquals(listOf("06:00", "18:00"), grouped.getValue(Weekday.FRIDAY).map { it.startTime })
    }

    @Test
    fun registeredUnapprovedCoachesArePreviewOnly() {
        val registered = Coach(uid = "abc", name = "Ravi", sport = "Cricket")
        assertTrue(SessionPolicy.isPreviewListed(registered))
        assertFalse(SessionPolicy.isBookable(registered))
        val approved = registered.copy(profileStatus = ProfileStatus.ACTIVE.name, verificationStatus = VerificationStatus.VERIFIED.name)
        assertFalse(SessionPolicy.isPreviewListed(approved))
        assertTrue(SessionPolicy.isBookable(approved))
        assertFalse(SessionPolicy.isPreviewListed(registered.copy(profileStatus = ProfileStatus.SUSPENDED.name)))
        assertFalse(SessionPolicy.isPreviewListed(registered.copy(verificationStatus = VerificationStatus.REJECTED.name)))
        assertFalse(SessionPolicy.isPreviewListed(registered.copy(name = "")))
        assertFalse(SessionPolicy.isPreviewListed(registered.copy(listingSource = DIRECTORY_LISTING_SOURCE)))
    }
}
