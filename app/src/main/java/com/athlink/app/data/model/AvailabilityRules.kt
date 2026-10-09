package com.athlink.app.data.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Validation for a coach's weekly availability ranges (`coaches/{uid}/availability`).
 * Mirrored in firestore.rules (`validAvailability`). Players' bookable slots are generated from
 * these ranges by [BookingSlots] (1-hour slots), so a range must be at least one hour long.
 */
object AvailabilityRules {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    const val MAX_RANGES_PER_DAY = 4
    const val EARLIEST = "05:00"
    const val LATEST = "23:00"

    /** Selectable times for the editor: every 30 minutes from [EARLIEST] to [LATEST]. */
    val TIME_OPTIONS: List<String> = generateSequence(LocalTime.parse(EARLIEST)) { it.plusMinutes(30) }
        .takeWhile { !it.isAfter(LocalTime.parse(LATEST)) }
        .map { it.format(TIME) }
        .toList()

    private fun parse(t: String): LocalTime? = try { LocalTime.parse(t, TIME) } catch (e: Exception) { null }

    /** Null when the range can be added, otherwise a user-facing reason. */
    fun problem(day: Weekday, start: String, end: String, existing: List<CoachAvailability>): String? {
        val s = parse(start) ?: return "Choose a start time"
        val e = parse(end) ?: return "Choose an end time"
        if (s.isBefore(LocalTime.parse(EARLIEST)) || e.isAfter(LocalTime.parse(LATEST))) return "Times must be between $EARLIEST and $LATEST"
        if (!e.isAfter(s)) return "End time must be after the start time"
        if (java.time.Duration.between(s, e).toMinutes() < 60) return "A range must be at least 1 hour (sessions are 1 hour)"
        val sameDay = existing.filter { it.dayOfWeek == day.name }
        if (sameDay.size >= MAX_RANGES_PER_DAY) return "Up to $MAX_RANGES_PER_DAY ranges per day"
        val overlaps = sameDay.any { r ->
            val rs = parse(r.startTime); val re = parse(r.endTime)
            rs != null && re != null && s.isBefore(re) && rs.isBefore(e)
        }
        if (overlaps) return "This overlaps another range on ${day.label}"
        return null
    }

    /** Ranges grouped by weekday (Monday first), each day sorted by start time. */
    fun byDay(ranges: List<CoachAvailability>): Map<Weekday, List<CoachAvailability>> =
        Weekday.entries.associateWith { d -> ranges.filter { it.dayOfWeek == d.name }.sortedBy { it.startTime } }
            .filterValues { it.isNotEmpty() }
}
