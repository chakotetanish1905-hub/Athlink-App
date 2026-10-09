package com.athlink.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A booking with a coach: `sessions/{sessionId}`.
 *
 * New bookings use a deterministic id, [SessionPolicy.slotId] = "{coachId}_{date}_{HHmm}", so a
 * coach's slot can only be booked once: a second player's write hits an existing document, which
 * firestore.rules reject (unless that booking was REJECTED / CANCELLED, which frees the slot).
 * Older documents with auto ids still deserialize (every field has a default).
 */
data class Session(
    val id: String = "",
    val coachId: String = "",
    val coachName: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val sport: String = "",
    /** "yyyy-MM-dd". */
    val date: String = "",
    /** Display label, e.g. "17:00 – 18:00". */
    val timeSlot: String = "",
    /** "HH:mm" (24h). Empty on legacy sessions. */
    val startTime: String = "",
    /** "HH:mm" (24h). Empty on legacy sessions. */
    val endTime: String = "",
    val status: SessionStatus = SessionStatus.PENDING,
    val price: Double = 0.0,
    val location: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class SessionStatus(val label: String) {
    PENDING("Pending"), CONFIRMED("Confirmed"), REJECTED("Rejected"), COMPLETED("Completed"), CANCELLED("Cancelled")
}

/** Business rules for coach sessions. Mirrored in firestore.rules (`match /sessions`). */
object SessionPolicy {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Statuses that still hold the slot. */
    val ACTIVE = setOf(SessionStatus.PENDING, SessionStatus.CONFIRMED)

    fun slotId(coachId: String, date: String, startTime: String): String =
        "${coachId}_${date}_${startTime.replace(":", "")}"

    /** Start of the session, or null for legacy / malformed sessions. */
    fun startOf(session: Session): LocalDateTime? = try {
        val time = session.startTime.ifBlank { null } ?: return null
        LocalDateTime.of(LocalDate.parse(session.date, DATE), LocalTime.parse(time, TIME))
    } catch (e: Exception) { null }

    /** Pending / confirmed and not yet started (legacy sessions: date today or later). */
    fun isUpcoming(session: Session, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (session.status !in ACTIVE) return false
        val start = startOf(session)
        if (start != null) return start.isAfter(now)
        val day = try { LocalDate.parse(session.date, DATE) } catch (e: Exception) { return false }
        return !day.isBefore(now.toLocalDate())
    }

    /** Upcoming sessions, soonest first. */
    fun upcoming(sessions: List<Session>, now: LocalDateTime = LocalDateTime.now()): List<Session> =
        sessions.filter { isUpcoming(it, now) }
            .sortedWith(compareBy<Session> { it.date }.thenBy { it.startTime.ifBlank { it.timeSlot } })

    /** Everything else (past, rejected, cancelled, completed), most recent first. */
    fun history(sessions: List<Session>, now: LocalDateTime = LocalDateTime.now()): List<Session> =
        sessions.filterNot { isUpcoming(it, now) }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.startTime })

    fun playerMayCancel(session: Session, now: LocalDateTime = LocalDateTime.now()): Boolean =
        isUpcoming(session, now)

    /** Coach transitions (also enforced in rules). */
    fun coachMayTransition(from: SessionStatus, to: SessionStatus): Boolean = when (from) {
        SessionStatus.PENDING -> to == SessionStatus.CONFIRMED || to == SessionStatus.REJECTED
        SessionStatus.CONFIRMED -> to == SessionStatus.COMPLETED || to == SessionStatus.CANCELLED
        else -> false
    }

    /**
     * Debug-build preview: a registered coach (has an account, not an imported directory coach)
     * who is not yet approved but not suspended / rejected / deactivated. Shown to players as
     * "Not verified yet" and never bookable (rules refuse bookings of unverified coaches).
     */
    fun isPreviewListed(coach: Coach): Boolean =
        !isBookable(coach) &&
            coach.listingSource.isBlank() &&
            coach.name.isNotBlank() &&
            coach.profile !in setOf(ProfileStatus.SUSPENDED, ProfileStatus.DEACTIVATED) &&
            coach.verification !in setOf(VerificationStatus.REJECTED, VerificationStatus.SUSPENDED)

    /** A coach can be booked only when ACTIVE + VERIFIED (same as discoverable). */
    fun isBookable(coach: Coach): Boolean =
        CoachVerificationPolicy.isDiscoverable(coach.profile, coach.verification)
}
