package com.athlink.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** One bookable slot generated from a coach's weekly availability. */
data class BookingSlot(val startTime: String, val endTime: String) {
    val label: String get() = "$startTime – $endTime"
}

/**
 * Turns `coaches/{uid}/availability` (weekly ranges such as MONDAY 17:00-20:00) into concrete
 * bookable slots for a date. No dummy time slots anywhere: a coach with no availability has no slots.
 */
object BookingSlots {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Days ahead a player can book (today included). */
    const val BOOKING_WINDOW_DAYS = 14

    /** A slot must start at least this many minutes from now. */
    const val MIN_LEAD_MINUTES = 60L

    fun weekdayOf(date: LocalDate): Weekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> Weekday.MONDAY
        DayOfWeek.TUESDAY -> Weekday.TUESDAY
        DayOfWeek.WEDNESDAY -> Weekday.WEDNESDAY
        DayOfWeek.THURSDAY -> Weekday.THURSDAY
        DayOfWeek.FRIDAY -> Weekday.FRIDAY
        DayOfWeek.SATURDAY -> Weekday.SATURDAY
        DayOfWeek.SUNDAY -> Weekday.SUNDAY
        null -> Weekday.MONDAY
    }

    private fun parse(t: String): LocalTime? = try { LocalTime.parse(t, TIME) } catch (e: Exception) { null }

    /**
     * Slots of [durationMinutes] inside every enabled range for [date]'s weekday, sorted and
     * de-duplicated, skipping slots that start less than [MIN_LEAD_MINUTES] from [now].
     */
    fun slotsFor(
        date: LocalDate,
        availability: List<CoachAvailability>,
        now: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60
    ): List<BookingSlot> {
        if (durationMinutes <= 0) return emptyList()
        val day = weekdayOf(date).name
        val earliest = now.plusMinutes(MIN_LEAD_MINUTES)
        val starts = sortedSetOf<Int>() // minutes of day
        for (range in availability) {
            if (!range.enabled || range.dayOfWeek != day) continue
            val start = parse(range.startTime)?.toSecondOfDay()?.div(60) ?: continue
            val end = parse(range.endTime)?.toSecondOfDay()?.div(60) ?: continue
            var s = start
            while (s + durationMinutes <= end) {
                val startAt = LocalDateTime.of(date, LocalTime.of(s / 60, s % 60))
                if (!startAt.isBefore(earliest)) starts.add(s)
                s += durationMinutes
            }
        }
        fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        return starts.map { BookingSlot(fmt(it), fmt(it + durationMinutes)) }
    }

    /** Dates in the booking window that have at least one slot. */
    fun bookableDates(
        availability: List<CoachAvailability>,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60
    ): List<LocalDate> = (0 until BOOKING_WINDOW_DAYS).map { today.plusDays(it.toLong()) }
        .filter { slotsFor(it, availability, now, durationMinutes).isNotEmpty() }
}
