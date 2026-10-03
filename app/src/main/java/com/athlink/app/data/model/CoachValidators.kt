package com.athlink.app.data.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.format.DateTimeParseException

/**
 * Field validation for the coach onboarding flow.
 * Every function returns `null` when the value is valid, or a user-facing error message.
 * Pure Kotlin (no Android/Firebase) so it is unit-testable and reusable by ViewModels.
 */
object CoachValidators {

    const val MIN_COACH_AGE = 18
    const val MAX_AGE = 100
    const val MAX_EXPERIENCE_YEARS = 60
    const val MAX_PRICE = 100_000.0
    const val MIN_SLOT_MINUTES = 30
    const val MAX_DOCUMENT_BYTES = 5L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 2L * 1024 * 1024

    val DOCUMENT_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
    val PHOTO_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    private val NAME_REGEX = Regex("^[\\p{L} .'-]+$")
    private val CERTIFICATE_REGEX = Regex("^[A-Za-z0-9/._ -]+$")
    private val ID_LAST4_REGEX = Regex("^[A-Za-z0-9]{4}$")
    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    // ── Identity ────────────────────────────────────────────────────────

    fun fullLegalName(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter your full legal name"
            v.length < 3 -> "Name must be at least 3 characters"
            v.length > 80 -> "Name must be under 80 characters"
            !NAME_REGEX.matches(v) -> "Use letters, spaces, dots, apostrophes or hyphens only"
            else -> null
        }
    }

    fun displayName(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter the name players will see"
            v.length < 2 -> "Display name must be at least 2 characters"
            v.length > 40 -> "Display name must be under 40 characters"
            else -> null
        }
    }

    fun dateOfBirth(isoDate: String, today: LocalDate = LocalDate.now()): String? {
        val dob = parseDate(isoDate) ?: return "Enter a valid date of birth"
        if (dob.isAfter(today)) return "Date of birth can't be in the future"
        val age = Period.between(dob, today).years
        return when {
            age < MIN_COACH_AGE -> "Coaches must be at least $MIN_COACH_AGE years old"
            age > MAX_AGE -> "Enter a valid date of birth"
            else -> null
        }
    }

    fun phone(value: String): String? {
        val digits = value.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> "Enter your mobile number"
            digits.length !in 10..13 -> "Enter a valid mobile number"
            else -> null
        }
    }

    fun requiredText(value: String, label: String, maxLength: Int = 100): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter $label"
            v.length > maxLength -> "Keep $label under $maxLength characters"
            else -> null
        }
    }

    fun bio(value: String): String? {
        val v = value.trim()
        return when {
            v.length < CoachRegistration.MIN_BIO_LENGTH ->
                "Write at least ${CoachRegistration.MIN_BIO_LENGTH} characters about your coaching"
            v.length > CoachRegistration.MAX_BIO_LENGTH ->
                "Keep your bio under ${CoachRegistration.MAX_BIO_LENGTH} characters"
            else -> null
        }
    }

    fun nonEmptySelection(values: Collection<*>, label: String): String? =
        if (values.isEmpty()) "Select at least one $label" else null

    // ── Experience ──────────────────────────────────────────────────────

    /** [ageYears] (if known) caps experience so it can't exceed the coach's adult life. */
    fun experienceYears(value: String, ageYears: Int? = null): String? {
        val years = value.trim().toIntOrNull() ?: return "Enter years of experience as a number"
        return when {
            years < 0 -> "Experience can't be negative"
            years > MAX_EXPERIENCE_YEARS -> "Enter a value between 0 and $MAX_EXPERIENCE_YEARS"
            ageYears != null && years > ageYears - 14 -> "Experience is too high for your age"
            else -> null
        }
    }

    // ── Qualifications ──────────────────────────────────────────────────

    fun certificateNumber(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter the certificate / licence number"
            v.length !in 3..40 -> "Certificate number must be 3-40 characters"
            !CERTIFICATE_REGEX.matches(v) -> "Use letters, numbers, / . - or spaces only"
            else -> null
        }
    }

    fun issueDate(isoDate: String, today: LocalDate = LocalDate.now()): String? {
        val d = parseDate(isoDate) ?: return "Enter a valid issue date"
        return if (d.isAfter(today)) "Issue date can't be in the future" else null
    }

    /** Empty expiry = "does not expire" and is valid. */
    fun expiryDate(isoExpiry: String, isoIssue: String): String? {
        if (isoExpiry.isBlank()) return null
        val expiry = parseDate(isoExpiry) ?: return "Enter a valid expiry date"
        val issue = parseDate(isoIssue)
        return if (issue != null && !expiry.isAfter(issue)) "Expiry must be after the issue date" else null
    }

    fun isExpired(isoExpiry: String, today: LocalDate = LocalDate.now()): Boolean {
        val expiry = parseDate(isoExpiry) ?: return false
        return expiry.isBefore(today)
    }

    /** All field errors for one qualification, keyed by field name. Empty map = valid. */
    fun qualification(q: CoachQualification, today: LocalDate = LocalDate.now()): Map<String, String> =
        buildMap {
            requiredText(q.title, "the qualification name")?.let { put("title", it) }
            requiredText(q.issuingOrganization, "the issuing organisation")?.let { put("issuingOrganization", it) }
            certificateNumber(q.certificateNumber)?.let { put("certificateNumber", it) }
            requiredText(q.sport, "the sport")?.let { put("sport", it) }
            issueDate(q.issueDate, today)?.let { put("issueDate", it) }
            expiryDate(q.expiryDate, q.issueDate)?.let { put("expiryDate", it) }
            if (q.expiryDate.isNotBlank() && isExpired(q.expiryDate, today)) put("expiryDate", "This qualification has expired")
            if (q.documentPath.isBlank()) put("document", "Upload the certificate")
        }

    /** True if [candidate] has the same certificate number + issuer as another qualification. */
    fun isDuplicateQualification(candidate: CoachQualification, existing: List<CoachQualification>): Boolean =
        existing.any {
            it.qualificationId != candidate.qualificationId &&
                it.certificateNumber.trim().equals(candidate.certificateNumber.trim(), ignoreCase = true) &&
                it.issuingOrganization.trim().equals(candidate.issuingOrganization.trim(), ignoreCase = true)
        }

    // ── Government ID ───────────────────────────────────────────────────

    fun idLast4(value: String): String? =
        if (ID_LAST4_REGEX.matches(value.trim())) null else "Enter exactly the last 4 characters of the ID"

    // ── Services ────────────────────────────────────────────────────────

    fun price(value: String): String? {
        val p = value.trim().toDoubleOrNull() ?: return "Enter the session price"
        return when {
            p <= 0 -> "Price must be more than 0"
            p > MAX_PRICE -> "Enter a price up to ${MAX_PRICE.toInt()}"
            else -> null
        }
    }

    fun durationMinutes(minutes: Int): String? =
        if (minutes in 15..480 && minutes % 5 == 0) null else "Choose a valid session length"

    fun maxPlayers(value: Int, type: ServiceType): String? =
        if (value in type.minPlayers..type.maxPlayers) null
        else if (type == ServiceType.INDIVIDUAL) "Individual sessions are for 1 player"
        else "${type.label} sessions take ${type.minPlayers}-${type.maxPlayers} players"

    fun service(s: CoachService): Map<String, String> = buildMap {
        val type = ServiceType.fromStored(s.serviceType)
        requiredText(s.title, "a service name", 60)?.let { put("title", it) }
        durationMinutes(s.durationMinutes)?.let { put("durationMinutes", it) }
        price(s.price.toString())?.let { put("price", it) }
        maxPlayers(s.maxPlayers, type)?.let { put("maxPlayers", it) }
        if (s.equipmentRequired && s.equipmentDescription.isBlank()) put("equipmentDescription", "Describe the equipment players need")
        if (ServiceMode.fromStored(s.mode) == ServiceMode.IN_PERSON && s.venueArea.isBlank()) put("venueArea", "Enter the venue or area")
    }

    // ── Availability ────────────────────────────────────────────────────

    fun time(value: String): String? = if (TIME_REGEX.matches(value)) null else "Use HH:mm (24-hour)"

    fun slot(slot: CoachAvailability): String? {
        time(slot.startTime)?.let { return "Start time: $it" }
        time(slot.endTime)?.let { return "End time: $it" }
        val start = LocalTime.parse(slot.startTime)
        val end = LocalTime.parse(slot.endTime)
        return when {
            !end.isAfter(start) -> "End time must be after start time"
            java.time.Duration.between(start, end).toMinutes() < MIN_SLOT_MINUTES -> "Slots must be at least $MIN_SLOT_MINUTES minutes"
            else -> null
        }
    }

    /** Error for each slot id: its own error, or an overlap with another enabled slot on the same day. */
    fun availability(slots: List<CoachAvailability>): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        slots.forEach { s -> slot(s)?.let { errors[s.availabilityId] = it } }
        val valid = slots.filter { it.enabled && it.availabilityId !in errors }
        valid.groupBy { it.dayOfWeek }.values.forEach { daySlots ->
            val sorted = daySlots.sortedBy { it.startTime }
            sorted.zipWithNext().forEach { (a, b) ->
                if (b.startTime < a.endTime) errors[b.availabilityId] = "Overlaps another slot on the same day"
            }
        }
        return errors
    }

    // ── Documents ───────────────────────────────────────────────────────

    fun document(mimeType: String?, sizeBytes: Long): String? = when {
        mimeType == null || mimeType !in DOCUMENT_MIME_TYPES -> "Upload a PDF, JPG or PNG file"
        sizeBytes <= 0 -> "The file is empty"
        sizeBytes > MAX_DOCUMENT_BYTES -> "File must be under ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB"
        else -> null
    }

    fun photo(mimeType: String?, sizeBytes: Long): String? = when {
        mimeType == null || mimeType !in PHOTO_MIME_TYPES -> "Upload a JPG, PNG or WebP image"
        sizeBytes <= 0 -> "The image is empty"
        sizeBytes > MAX_PHOTO_BYTES -> "Image must be under ${MAX_PHOTO_BYTES / (1024 * 1024)} MB"
        else -> null
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    fun parseDate(isoDate: String): LocalDate? = try {
        if (isoDate.isBlank()) null else LocalDate.parse(isoDate.trim())
    } catch (e: DateTimeParseException) {
        null
    }

    fun ageOn(isoDob: String, today: LocalDate = LocalDate.now()): Int? =
        parseDate(isoDob)?.let { Period.between(it, today).years }
}
