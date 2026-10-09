package com.athlink.app.data.model

import java.time.LocalDate

/**
 * The single place where player signup / profile rules live. Pure Kotlin (no Android types), so
 * the same checks run in the ViewModel, the repository and the JVM unit tests.
 * Each function returns a user-facing error message, or null when the value is valid.
 */
object PlayerValidators {

    const val NAME_MIN = 2
    const val NAME_MAX = 60
    const val CITY_MAX = 60
    const val TEXT_MAX = 80
    const val RANKING_MAX = 60
    const val BIO_MAX = 500
    const val ACHIEVEMENT_MAX = 100
    const val MAX_ACHIEVEMENTS = 10
    const val MAX_SECONDARY_SPORTS = 5
    const val MAX_EXPERIENCE_YEARS = 80

    private val NAME_REGEX = Regex("^\\p{L}[\\p{L} .'-]*$")
    private val PLACE_REGEX = Regex("^\\p{L}[\\p{L} .'()-]*$")
    // Deliberately simple: Firebase Auth is the final authority on email validity.
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun fullName(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter your full name"
            v.length < NAME_MIN -> "Name must be at least $NAME_MIN characters"
            v.length > NAME_MAX -> "Name must be under $NAME_MAX characters"
            !NAME_REGEX.matches(v) -> "Use letters, spaces, dots, apostrophes or hyphens only"
            else -> null
        }
    }

    fun email(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter your email address"
            !EMAIL_REGEX.matches(v) -> "Enter a valid email address"
            else -> null
        }
    }

    /** Password strength is enforced by Firebase Auth; here we only check presence. */
    fun password(value: String): String? = if (value.isEmpty()) "Enter a password" else null

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Confirm your password"
        confirm != password -> "Passwords do not match"
        else -> null
    }

    fun dateOfBirth(iso: String, today: LocalDate = LocalDate.now()): String? {
        if (iso.isBlank()) return "Enter your date of birth"
        val dob = PlayerAge.parse(iso) ?: return "Enter a valid date of birth"
        if (dob.isAfter(today)) return "Date of birth can't be in the future"
        val age = PlayerAge.ageOn(dob, today)
        return when {
            age < PlayerAge.MIN_AGE -> "Players must be at least ${PlayerAge.MIN_AGE} years old"
            age > PlayerAge.MAX_AGE -> "Enter a valid date of birth"
            else -> null
        }
    }

    fun country(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter your country"
            v.length > TEXT_MAX || !PLACE_REGEX.matches(v) -> "Enter a valid country"
            else -> null
        }
    }

    /** Required everywhere; inside India it must be one of [PlayerLocation.INDIAN_STATES]. */
    fun state(country: String, value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> if (PlayerLocation.isIndia(country)) "Choose your state" else "Enter your state / province"
            PlayerLocation.isIndia(country) && v !in PlayerLocation.INDIAN_STATES -> "Choose your state from the list"
            v.length > TEXT_MAX || !PLACE_REGEX.matches(v) -> "Enter a valid state / province"
            else -> null
        }
    }

    fun city(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter your city or town"
            v.length < 2 -> "Enter a valid city or town"
            v.length > CITY_MAX -> "City must be under $CITY_MAX characters"
            !PLACE_REGEX.matches(v) -> "Use letters only for the city"
            else -> null
        }
    }

    /** Optional. Indian 10-digit mobile, or an international number with 8-15 digits. */
    fun optionalPhone(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (!v.all { it.isDigit() || it in " +-()" }) return "Enter a valid phone number"
        val digits = v.filter { it.isDigit() }
        return if (digits.length in 8..15) null else "Enter a valid phone number"
    }

    fun primarySport(value: String): String? = when {
        value.isBlank() -> "Choose your main sport"
        value !in Sports.ALL -> "Choose a sport from the list"
        else -> null
    }

    fun secondarySports(primary: String, values: List<String>): String? = when {
        values.size > MAX_SECONDARY_SPORTS -> "Choose up to $MAX_SECONDARY_SPORTS other sports"
        values.any { it !in Sports.ALL } -> "Choose sports from the list"
        primary.isNotBlank() && primary in values -> "Your main sport is already selected"
        else -> null
    }

    fun sportProfile(sport: String, answers: Map<String, String>): String? =
        if (SportProfiles.sanitise(sport, answers).size == answers.size) null
        else "Some answers don't match your main sport. Please choose again."

    fun skillLevel(value: SkillLevel?): String? = if (value == null) "Choose your level" else null

    fun goals(values: Collection<PlayerGoal>): String? = if (values.isEmpty()) "Choose at least one goal" else null

    fun <T> requiredChoice(value: T?, message: String): String? = if (value == null) message else null

    /** Optional whole number of years, not more than the player's age. */
    fun yearsOfExperience(value: String, ageYears: Int?): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val years = v.toIntOrNull() ?: return "Enter a whole number of years"
        return when {
            years < 0 || years > MAX_EXPERIENCE_YEARS -> "Enter a valid number of years"
            ageYears != null && years > ageYears -> "Experience can't be more than your age"
            else -> null
        }
    }

    fun optionalText(value: String, label: String, max: Int = TEXT_MAX): String? =
        if (value.trim().length > max) "Keep $label under $max characters" else null

    fun achievements(lines: List<String>): String? = when {
        lines.size > MAX_ACHIEVEMENTS -> "Add up to $MAX_ACHIEVEMENTS achievements"
        lines.any { it.length > ACHIEVEMENT_MAX } -> "Keep each achievement under $ACHIEVEMENT_MAX characters"
        else -> null
    }

    fun consent(accepted: Boolean, what: String): String? = if (accepted) null else "You must accept the $what to continue"
}
