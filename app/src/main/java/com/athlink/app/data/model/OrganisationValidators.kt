package com.athlink.app.data.model

import java.time.LocalDate

/**
 * Field validation for organisation onboarding. Each function returns `null` when valid or a
 * user-facing message. Pure Kotlin, unit-tested. Format checks only: Athlink does not call any
 * external registry, so a well-formed PAN / GSTIN / CIN is still "pending manual review".
 */
object OrganisationValidators {

    const val MIN_NAME = 3
    const val MAX_NAME = 120
    const val MIN_DESCRIPTION = 30
    const val MAX_DESCRIPTION = 500
    const val EARLIEST_YEAR = 1800
    const val MAX_COUNT = 1_000_000
    const val MAX_DOCUMENT_BYTES = DocumentChunks.MAX_STORED_BYTES
    const val MAX_LOGO_BYTES = 10L * 1024 * 1024 // compressed to a small thumbnail before saving

    val DOCUMENT_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
    val LOGO_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL_REGEX = Regex("^(https?://)?([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(:\\d{2,5})?(/\\S*)?$")
    private val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
    private val GSTIN_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    private val CIN_REGEX = Regex("^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$")
    private val LLPIN_REGEX = Regex("^[A-Z]{3}-[0-9]{4}$")
    private val PIN_REGEX = Regex("^[1-9][0-9]{5}$")
    private val REG_NUMBER_REGEX = Regex("^[A-Za-z0-9/._() -]+$")

    /** Free-mail domains. Not blocked, but flagged for reviewers on government applications. */
    private val FREE_MAIL_DOMAINS = setOf(
        "gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "live.com",
        "rediffmail.com", "icloud.com", "aol.com", "protonmail.com", "zoho.com", "ymail.com"
    )

    fun organisationName(value: String, label: String = "the organisation name"): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter $label"
            v.length < MIN_NAME -> "Name must be at least $MIN_NAME characters"
            v.length > MAX_NAME -> "Name must be under $MAX_NAME characters"
            else -> null
        }
    }

    fun required(value: String, label: String, maxLength: Int = 120): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter $label"
            v.length > maxLength -> "Keep $label under $maxLength characters"
            else -> null
        }
    }

    fun optionalMax(value: String, label: String, maxLength: Int = 120): String? =
        if (value.trim().length > maxLength) "Keep $label under $maxLength characters" else null

    fun email(value: String, label: String = "the official email"): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter $label"
            !EMAIL_REGEX.matches(v) -> "Enter a valid email address"
            else -> null
        }
    }

    fun isFreeMail(email: String): Boolean =
        email.substringAfterLast('@', "").trim().lowercase() in FREE_MAIL_DOMAINS

    fun phone(value: String, label: String = "the official phone number"): String? {
        val trimmed = value.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.isEmpty() -> "Enter $label"
            trimmed.any { !(it.isDigit() || it in "+ -()") } -> "Use digits, spaces, + - ( ) only"
            digits.length !in 10..13 -> "Enter a valid phone number (10-13 digits, landline with STD code)"
            else -> null
        }
    }

    /** Website is optional unless [required]; a value, if given, must look like a URL. */
    fun website(value: String, required: Boolean = false): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> if (required) "Enter the official website" else null
            v.contains(' ') || !URL_REGEX.matches(v) -> "Enter a valid website, e.g. https://example.org"
            else -> null
        }
    }

    fun description(value: String): String? {
        val v = value.trim()
        return when {
            v.length < MIN_DESCRIPTION -> "Write at least $MIN_DESCRIPTION characters about the organisation"
            v.length > MAX_DESCRIPTION -> "Keep the description under $MAX_DESCRIPTION characters"
            else -> null
        }
    }

    /** Optional; when given must be between [EARLIEST_YEAR] and this year. */
    fun yearEstablished(value: String, today: LocalDate = LocalDate.now()): String? {
        if (value.isBlank()) return null
        val year = value.trim().toIntOrNull() ?: return "Enter a 4-digit year"
        return if (year in EARLIEST_YEAR..today.year) null else "Enter a year between $EARLIEST_YEAR and ${today.year}"
    }

    /** Optional non-negative whole number (approximate counts, years active). */
    fun optionalCount(value: String, label: String, max: Int = MAX_COUNT): String? {
        if (value.isBlank()) return null
        val n = value.trim().toIntOrNull() ?: return "Enter $label as a whole number"
        return if (n in 0..max) null else "Enter $label between 0 and $max"
    }

    fun pan(value: String, required: Boolean): String? {
        val v = normaliseId(value)
        return when {
            v.isEmpty() -> if (required) "Enter the organisation's PAN" else null
            !PAN_REGEX.matches(v) -> "PAN must look like AAAPA1234A (5 letters, 4 digits, 1 letter)"
            else -> null
        }
    }

    /** Only validated when the organisation says it is GST-registered. */
    fun gstin(value: String, gstRegistered: Boolean, pan: String = ""): String? {
        if (!gstRegistered) return null
        val v = normaliseId(value)
        return when {
            v.isEmpty() -> "Enter your GSTIN, or switch off \"GST registered\""
            !GSTIN_REGEX.matches(v) -> "GSTIN must be 15 characters, e.g. 27AAAPA1234A1Z5"
            normaliseId(pan).let { it.isNotEmpty() && v.substring(2, 12) != it } ->
                "GSTIN doesn't contain the PAN you entered (characters 3-12)"
            else -> null
        }
    }

    fun registrationNumber(value: String, required: Boolean, format: RegistrationNumberFormat, label: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return if (required) "Enter the ${label.substringBefore(" (").lowercase()}" else null
        val upper = normaliseId(v)
        return when (format) {
            RegistrationNumberFormat.CIN ->
                if (CIN_REGEX.matches(upper)) null else "CIN must be 21 characters, e.g. U92490MH2015PTC123456"
            RegistrationNumberFormat.LLPIN ->
                if (LLPIN_REGEX.matches(upper)) null else "LLPIN must look like AAB-1234"
            RegistrationNumberFormat.FREE_TEXT -> when {
                v.length !in 3..50 -> "Registration number must be 3-50 characters"
                !REG_NUMBER_REGEX.matches(v) -> "Use letters, numbers and / . - ( ) only"
                else -> null
            }
        }
    }

    /** Indian PIN codes only; other countries just need a value under 12 characters. */
    fun pincode(value: String, country: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Enter the PIN code"
            country.trim().equals("India", ignoreCase = true) && !PIN_REGEX.matches(v) -> "Enter a valid 6-digit PIN code"
            v.length > 12 -> "Enter a valid postal code"
            else -> null
        }
    }

    /**
     * Pre-upload check. PDFs must fit as-is; photos may be bigger because they are compressed
     * before storing (see DocumentChunks).
     */
    fun document(mimeType: String?, sizeBytes: Long): String? = when {
        mimeType == null || mimeType !in DOCUMENT_MIME_TYPES -> "Upload a PDF, JPG or PNG file"
        sizeBytes == 0L -> "The file is empty"
        mimeType == "application/pdf" && sizeBytes > MAX_DOCUMENT_BYTES -> "PDF must be under ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB"
        sizeBytes > DocumentChunks.MAX_RAW_IMAGE_BYTES -> "Image is too large (max ${DocumentChunks.MAX_RAW_IMAGE_BYTES / (1024 * 1024)} MB)"
        else -> null
    }

    fun logo(mimeType: String?, sizeBytes: Long): String? = when {
        mimeType == null || mimeType !in LOGO_MIME_TYPES -> "Upload a JPG, PNG or WebP image"
        sizeBytes <= 0 -> "The image is empty"
        sizeBytes > MAX_LOGO_BYTES -> "Logo must be under ${MAX_LOGO_BYTES / (1024 * 1024)} MB"
        else -> null
    }

    /** Optional yyyy-MM-dd date. */
    fun optionalDate(value: String?, label: String): String? {
        if (value.isNullOrBlank()) return null
        return if (CoachValidators.parseDate(value) == null) "Enter $label as YYYY-MM-DD" else null
    }

    /** Upper-cases and strips spaces, so "aaapa 1234a" -> "AAAPA1234A". */
    fun normaliseId(value: String): String = value.filterNot { it.isWhitespace() }.uppercase()
}
