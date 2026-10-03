package com.athlink.app.data.model

/**
 * One weekly availability slot: `coaches/{uid}/availability/{availabilityId}`.
 * A day can have several slots (e.g. MONDAY 06:00-08:00 and MONDAY 17:00-20:00).
 * Public once the coach profile is ACTIVE. Only the coach can write their own slots.
 */
data class CoachAvailability(
    val availabilityId: String = "",
    /** [Weekday] name. */
    val dayOfWeek: String = Weekday.MONDAY.name,
    val enabled: Boolean = true,
    /** 24-hour local time, "HH:mm". */
    val startTime: String = "",
    /** 24-hour local time, "HH:mm". Must be after [startTime] (no overnight slots). */
    val endTime: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class Weekday(val label: String, val shortLabel: String) {
    MONDAY("Monday", "Mon"),
    TUESDAY("Tuesday", "Tue"),
    WEDNESDAY("Wednesday", "Wed"),
    THURSDAY("Thursday", "Thu"),
    FRIDAY("Friday", "Fri"),
    SATURDAY("Saturday", "Sat"),
    SUNDAY("Sunday", "Sun");

    companion object {
        fun fromStored(value: String?): Weekday? = entries.firstOrNull { it.name == value }
    }
}
