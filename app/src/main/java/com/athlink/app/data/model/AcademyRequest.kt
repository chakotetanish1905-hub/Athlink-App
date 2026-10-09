package com.athlink.app.data.model

import java.time.LocalDate
import java.util.Date

/**
 * A player's request for a session at an academy: `academyRequests/{requestId}`.
 *
 * Readable only by the player, the academy's owner (organisations that signed up in the app) and
 * admins. Imported directory academies have no owner, so Athlink follows those up through the
 * admin tool (`node admin.js requests`). Mirrored in firestore.rules (`match /academyRequests`).
 */
data class AcademyRequest(
    val requestId: String = "",
    val organisationId: String = "",
    val organisationName: String = "",
    /** "Locality, City" of the academy, for the player's list. */
    val organisationLocation: String = "",
    /** Empty for directory listings (no account to notify). */
    val organisationOwnerUid: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val sport: String = "",
    /** "yyyy-MM-dd". */
    val preferredDate: String = "",
    /** [PreferredTime] name. */
    val preferredTime: String = PreferredTime.EVENING.name,
    val message: String = "",
    /** Optional phone the player chose to share for this request only. */
    val contactPhone: String = "",
    /** [AcademyRequestStatus] name. */
    val status: String = AcademyRequestStatus.PENDING.name,
    /** Reply from the academy / Athlink. */
    val responseNote: String = "",
    val createdAt: Date? = null,
    val updatedAt: Date? = null
)

enum class AcademyRequestStatus(val label: String) {
    PENDING("Requested"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    CANCELLED("Cancelled");

    companion object {
        fun fromStored(v: String?): AcademyRequestStatus = entries.firstOrNull { it.name == v } ?: PENDING
    }
}

enum class PreferredTime(val label: String) {
    MORNING("Morning (6–10 am)"),
    AFTERNOON("Afternoon (12–4 pm)"),
    EVENING("Evening (4–8 pm)");

    companion object {
        fun fromStored(v: String?): PreferredTime = entries.firstOrNull { it.name == v } ?: EVENING
    }
}

val AcademyRequest.statusEnum: AcademyRequestStatus get() = AcademyRequestStatus.fromStored(status)

/** Form + validation for a new request. Limits match firestore.rules. */
data class AcademyRequestForm(
    val sport: String = "",
    val preferredDate: String = "",
    val preferredTime: PreferredTime = PreferredTime.EVENING,
    val message: String = "",
    val contactPhone: String = ""
) {
    enum class Field { SPORT, DATE, MESSAGE, PHONE }

    fun validate(org: Organisation, today: LocalDate = LocalDate.now()): Map<Field, String> {
        val errors = linkedMapOf<Field, String>()
        if (sport.isBlank()) errors[Field.SPORT] = "Choose a sport"
        else if (org.sports.isNotEmpty() && sport !in org.sports) errors[Field.SPORT] = "This academy doesn't list $sport"
        val date = try { LocalDate.parse(preferredDate, SessionPolicy.DATE) } catch (e: Exception) { null }
        when {
            date == null -> errors[Field.DATE] = "Choose a preferred date"
            date.isBefore(today) -> errors[Field.DATE] = "Pick today or a later date"
            date.isAfter(today.plusDays(MAX_DAYS_AHEAD)) -> errors[Field.DATE] = "Pick a date within the next $MAX_DAYS_AHEAD days"
        }
        if (message.trim().length > MAX_MESSAGE) errors[Field.MESSAGE] = "Keep the message under $MAX_MESSAGE characters"
        val phone = contactPhone.filter { !it.isWhitespace() }
        if (phone.isNotEmpty() && !PHONE.matches(phone)) errors[Field.PHONE] = "Enter a 10-digit mobile number"
        return errors
    }

    companion object {
        const val MAX_MESSAGE = 300
        const val MAX_DAYS_AHEAD = 60L
        /** Indian mobile, optional +91. */
        val PHONE = Regex("^(\\+91)?[6-9][0-9]{9}$")
    }
}

/** Player-facing academy directory: who is listed and how the list is filtered. */
object AcademyDirectory {
    /** Verified (or official), unexpired organisations are listed to players. */
    fun isListed(org: Organisation, now: Date = Date()): Boolean =
        org.effectiveStatus(now) in setOf(OrganisationVerificationStatus.VERIFIED, OrganisationVerificationStatus.OFFICIAL_GOVERNMENT)

    fun cities(orgs: List<Organisation>): List<String> =
        orgs.map { it.city.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun sports(orgs: List<Organisation>): List<String> =
        orgs.flatMap { it.sports }.distinct().sortedWith(compareBy { Sports.ALL.indexOf(it).let { i -> if (i < 0) 999 else i } })

    /**
     * Filters (null = any) and sorts: rated academies by rating, then review count, then name;
     * unrated ones after them.
     */
    fun filter(
        orgs: List<Organisation>,
        city: String? = null,
        sport: String? = null,
        venueCategory: String? = null,
        query: String = ""
    ): List<Organisation> {
        val q = query.trim()
        return orgs.asSequence()
            .filter { city == null || it.city.equals(city, ignoreCase = true) }
            .filter { sport == null || it.sports.any { s -> s.equals(sport, ignoreCase = true) } }
            .filter { venueCategory == null || it.venueCategory.equals(venueCategory, ignoreCase = true) }
            .filter {
                q.isEmpty() || it.displayName.contains(q, true) || it.locality.contains(q, true) ||
                    it.sports.any { s -> s.contains(q, true) }
            }
            .sortedWith(
                compareBy<Organisation> { it.googleRating == null }
                    .thenByDescending { it.googleRating ?: 0.0 }
                    .thenByDescending { it.googleReviewCount ?: 0 }
                    .thenBy { it.displayName.lowercase() }
            )
            .toList()
    }

    /** The player's city if any academy is listed there, else null (show all). */
    fun defaultCity(playerCity: String?, orgs: List<Organisation>): String? {
        val c = playerCity?.trim().orEmpty()
        if (c.isEmpty()) return null
        return cities(orgs).firstOrNull { it.equals(c, ignoreCase = true) }
    }
}
