package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BookingTest {

    // 2026-10-12 is a Monday.
    private val monday = LocalDate.of(2026, 10, 12)
    private val earlyMorning = LocalDateTime.of(2026, 10, 12, 5, 0)

    private fun range(day: Weekday, start: String, end: String, enabled: Boolean = true) =
        CoachAvailability(dayOfWeek = day.name, startTime = start, endTime = end, enabled = enabled)

    @Test
    fun slotsComeOnlyFromTheCoachesAvailability() {
        assertEquals(emptyList<BookingSlot>(), BookingSlots.slotsFor(monday, emptyList(), earlyMorning))
        val slots = BookingSlots.slotsFor(
            monday,
            listOf(range(Weekday.MONDAY, "17:00", "20:00"), range(Weekday.TUESDAY, "06:00", "08:00")),
            earlyMorning
        )
        assertEquals(listOf("17:00", "18:00", "19:00"), slots.map { it.startTime })
        assertEquals("17:00 – 18:00", slots.first().label)
    }

    @Test
    fun partialRangesDisabledRangesAndBadTimesAreSkipped() {
        val slots = BookingSlots.slotsFor(
            monday,
            listOf(
                range(Weekday.MONDAY, "06:00", "07:30"),          // one full hour fits
                range(Weekday.MONDAY, "09:00", "11:00", enabled = false),
                range(Weekday.MONDAY, "bad", "10:00"),
                range(Weekday.MONDAY, "12:00", "11:00")            // end before start
            ),
            earlyMorning
        )
        assertEquals(listOf("06:00"), slots.map { it.startTime })
    }

    @Test
    fun overlappingRangesDoNotDuplicateSlots() {
        val slots = BookingSlots.slotsFor(
            monday, listOf(range(Weekday.MONDAY, "17:00", "19:00"), range(Weekday.MONDAY, "17:00", "18:00")), earlyMorning
        )
        assertEquals(listOf("17:00", "18:00"), slots.map { it.startTime })
    }

    @Test
    fun slotsTooSoonOrInThePastAreHidden() {
        val now = LocalDateTime.of(2026, 10, 12, 16, 30)
        val slots = BookingSlots.slotsFor(monday, listOf(range(Weekday.MONDAY, "16:00", "20:00")), now)
        // 16:00 passed, 17:00 is within the 60-minute lead time.
        assertEquals(listOf("18:00", "19:00"), slots.map { it.startTime })
    }

    @Test
    fun bookableDatesCoverTwoWeeksAndOnlyDaysWithSlots() {
        val dates = BookingSlots.bookableDates(listOf(range(Weekday.SATURDAY, "07:00", "09:00")), monday, earlyMorning)
        assertEquals(listOf(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 24)), dates)
    }

    @Test
    fun slotIdIsDeterministicPerCoachDateAndStart() {
        assertEquals("coach1_2026-10-12_1700", SessionPolicy.slotId("coach1", "2026-10-12", "17:00"))
    }

    private fun session(date: String, start: String, status: SessionStatus = SessionStatus.PENDING) =
        Session(id = date + start, date = date, startTime = start, status = status)

    @Test
    fun upcomingIsPendingOrConfirmedAndInTheFutureSoonestFirst() {
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
        val list = listOf(
            session("2026-10-14", "07:00"),
            session("2026-10-12", "18:00", SessionStatus.CONFIRMED),
            session("2026-10-12", "09:00"),                         // already started
            session("2026-10-13", "08:00", SessionStatus.REJECTED),
            session("2026-10-13", "08:00", SessionStatus.CANCELLED),
            Session(id = "legacy", date = "2026-10-20", timeSlot = "6:00 AM - 7:00 AM")
        )
        assertEquals(
            listOf("2026-10-1218:00", "2026-10-1407:00", "legacy"),
            SessionPolicy.upcoming(list, now).map { it.id }
        )
        assertEquals(3, SessionPolicy.history(list, now).size)
    }

    @Test
    fun coachTransitionsAndPlayerCancel() {
        assertTrue(SessionPolicy.coachMayTransition(SessionStatus.PENDING, SessionStatus.CONFIRMED))
        assertTrue(SessionPolicy.coachMayTransition(SessionStatus.PENDING, SessionStatus.REJECTED))
        assertTrue(SessionPolicy.coachMayTransition(SessionStatus.CONFIRMED, SessionStatus.COMPLETED))
        assertFalse(SessionPolicy.coachMayTransition(SessionStatus.REJECTED, SessionStatus.CONFIRMED))
        assertFalse(SessionPolicy.coachMayTransition(SessionStatus.PENDING, SessionStatus.COMPLETED))
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
        assertTrue(SessionPolicy.playerMayCancel(session("2026-10-13", "08:00"), now))
        assertFalse(SessionPolicy.playerMayCancel(session("2026-10-11", "08:00"), now))
    }

    @Test
    fun onlyActiveVerifiedCoachesAreBookable() {
        val verified = Coach(profileStatus = ProfileStatus.ACTIVE.name, verificationStatus = VerificationStatus.VERIFIED.name)
        assertTrue(SessionPolicy.isBookable(verified))
        assertFalse(SessionPolicy.isBookable(verified.copy(verificationStatus = VerificationStatus.UNDER_REVIEW.name)))
        assertFalse(SessionPolicy.isBookable(verified.copy(profileStatus = ProfileStatus.DEACTIVATED.name)))
        assertFalse(SessionPolicy.isBookable(Coach()))
    }
}
