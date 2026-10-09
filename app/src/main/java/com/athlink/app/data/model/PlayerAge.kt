package com.athlink.app.data.model

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Age is always DERIVED from the stored date of birth (ISO `yyyy-MM-dd`), never stored, so it
 * can't go stale. The ISO string avoids time-zone shifts that a timestamp at midnight would cause.
 */
object PlayerAge {

    /** Youngest age the onboarding accepts. Younger children are expected to be signed up by a guardian later. */
    const val MIN_AGE = 5
    const val MAX_AGE = 100
    /** Under this age a player is a minor (India: DPDP Act 2023 defines a child as under 18). */
    const val ADULT_AGE = 18

    private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun parse(iso: String): LocalDate? =
        if (iso.isBlank()) null
        else try { LocalDate.parse(iso.trim()) } catch (e: DateTimeParseException) { null }

    fun format(date: LocalDate): String = date.toString()

    fun display(iso: String): String = parse(iso)?.format(DISPLAY).orEmpty()

    fun ageOn(dateOfBirth: LocalDate, today: LocalDate): Int = Period.between(dateOfBirth, today).years

    fun ageOf(iso: String, today: LocalDate = LocalDate.now()): Int? = parse(iso)?.let { ageOn(it, today) }

    /** True when the player is under 18. Unknown DOB is treated as NOT known to be an adult. */
    fun isMinor(iso: String, today: LocalDate = LocalDate.now()): Boolean =
        ageOf(iso, today)?.let { it < ADULT_AGE } ?: true
}
