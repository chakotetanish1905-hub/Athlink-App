package com.athlink.app.data.model

/**
 * Form model for coach registration.
 *
 * Holds the raw values typed into the signup form (numbers are kept as Strings so the UI can
 * show exactly what the user typed). [validate] checks every field, and [toUser] / [toCoach]
 * map a valid form into the two Firestore documents that make up a coach account:
 *
 *  - `users/{uid}`        -> [User]  (shared account record, role = COACH)
 *  - `coaches/{uid}`      -> [Coach] (public coaching profile; DRAFT / NOT_SUBMITTED)
 *  - `coachPrivate/{uid}` -> [CoachPrivateProfile] (email + phone; never public)
 *
 * Signup is "Step 1 — Account" of coach onboarding. The rest of the professional profile is
 * completed in the onboarding flow, which pre-fills from what was entered here.
 */
data class CoachRegistration(
    // Account
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",

    // Location
    val city: String = "",
    val state: String = "",

    // Coaching profile
    val sport: String = "",
    val specializations: String = "",      // comma separated, e.g. "Batting, Fielding"
    val experienceYears: String = "",
    val coachingLevels: Set<CoachingLevel> = emptySet(),
    val certifications: String = "",       // comma separated, e.g. "BCCI Level 1"
    val hourlyRate: String = "",
    val bio: String = ""
) {

    fun validate(): Map<CoachField, String> {
        val errors = mutableMapOf<CoachField, String>()

        if (name.isBlank()) errors[CoachField.NAME] = "Enter your full name"
        else if (name.trim().length < 3) errors[CoachField.NAME] = "Name must be at least 3 characters"

        if (email.isBlank()) errors[CoachField.EMAIL] = "Enter your email"
        else if (!EMAIL_REGEX.matches(email.trim())) errors[CoachField.EMAIL] = "Enter a valid email address"

        if (password.length < MIN_PASSWORD_LENGTH) {
            errors[CoachField.PASSWORD] = "Password must be at least $MIN_PASSWORD_LENGTH characters"
        } else if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            errors[CoachField.PASSWORD] = "Use at least one letter and one number"
        }
        if (confirmPassword != password) errors[CoachField.CONFIRM_PASSWORD] = "Passwords do not match"

        val digits = normalizedPhone()
        if (digits.isEmpty()) errors[CoachField.PHONE] = "Enter your phone number"
        else if (digits.length !in 10..13) errors[CoachField.PHONE] = "Enter a valid phone number"

        if (city.isBlank()) errors[CoachField.CITY] = "Enter your city"
        if (state.isBlank()) errors[CoachField.STATE] = "Enter your state"

        if (sport.isBlank()) errors[CoachField.SPORT] = "Select the sport you coach"

        val years = experienceYears.trim().toIntOrNull()
        if (years == null) errors[CoachField.EXPERIENCE] = "Enter years of experience"
        else if (years !in 0..MAX_EXPERIENCE_YEARS) errors[CoachField.EXPERIENCE] = "Enter a value between 0 and $MAX_EXPERIENCE_YEARS"

        if (coachingLevels.isEmpty()) errors[CoachField.LEVELS] = "Select at least one level you coach"

        val rate = hourlyRate.trim().toDoubleOrNull()
        if (rate == null) errors[CoachField.HOURLY_RATE] = "Enter your hourly rate"
        else if (rate <= 0 || rate > MAX_HOURLY_RATE) errors[CoachField.HOURLY_RATE] = "Enter a rate between 1 and ${MAX_HOURLY_RATE.toInt()}"

        if (bio.trim().length < MIN_BIO_LENGTH) errors[CoachField.BIO] = "Write at least $MIN_BIO_LENGTH characters about your coaching"
        else if (bio.length > MAX_BIO_LENGTH) errors[CoachField.BIO] = "Keep your bio under $MAX_BIO_LENGTH characters"

        return errors
    }

    fun toUser(uid: String, now: Long = System.currentTimeMillis()): User = User(
        uid = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = UserRole.COACH,
        createdAt = now
    )

    fun toCoach(uid: String, now: Long = System.currentTimeMillis()): Coach = Coach(
        uid = uid,
        name = name.trim(),
        sport = sport.trim(),
        bio = bio.trim(),
        experience = experienceYears.trim().toInt(),
        hourlyRate = hourlyRate.trim().toDouble(),
        city = city.trim(),
        state = state.trim(),
        country = DEFAULT_COUNTRY,
        location = "${city.trim()}, ${state.trim()}",
        specializations = specializations.splitToList(),
        certifications = certifications.splitToList(),
        coachingLevels = CoachingLevel.entries.filter { it in coachingLevels }.map { it.name },
        profileStatus = ProfileStatus.DRAFT.name,
        verificationStatus = VerificationStatus.NOT_SUBMITTED.name,
        verificationLevel = VerificationLevel.LEVEL_0_REGISTERED.name,
        isAvailable = true,
        createdAt = now,
        updatedAt = now
    )

    /** Private contact details. Kept out of the public `coaches/{uid}` document. */
    fun toPrivateProfile(uid: String, now: Long = System.currentTimeMillis()): CoachPrivateProfile =
        CoachPrivateProfile(
            uid = uid,
            fullLegalName = name.trim(),
            email = email.trim().lowercase(),
            phone = normalizedPhone(),
            createdAt = now,
            updatedAt = now
        )

    /** Keeps a leading '+' and digits only, e.g. "+91 98765-43210" -> "+919876543210". */
    private fun normalizedPhone(): String {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun String.splitToList(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_EXPERIENCE_YEARS = 60
        const val MAX_HOURLY_RATE = 100_000.0
        const val MIN_BIO_LENGTH = 30
        const val MAX_BIO_LENGTH = 500
        const val DEFAULT_COUNTRY = "India"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

/** Every validated field of the coach form; used as the key for per-field error messages. */
enum class CoachField {
    NAME, EMAIL, PASSWORD, CONFIRM_PASSWORD, PHONE,
    CITY, STATE,
    SPORT, EXPERIENCE, LEVELS, HOURLY_RATE, BIO
}

enum class CoachingLevel(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    PROFESSIONAL("Professional")
}

/** Sports a coach can register for. */
object Sports {
    val ALL = listOf(
        "Cricket", "Football", "Badminton", "Tennis", "Swimming",
        "Basketball", "Kabaddi", "Volleyball", "Hockey", "Athletics",
        "Table Tennis", "Chess", "Kho Kho", "Wrestling", "Boxing"
    )
}
