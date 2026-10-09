package com.athlink.app.data.model

import java.util.Date

/**
 * Shared account record: `users/{uid}`. PRIVATE: only the owner (and admins) can read it.
 *
 * [role] says what kind of account this is. It does NOT say whether the account is trusted:
 * an ORGANISATION's verification lives on `organisations/{organisationId}` (ROLE ≠ VERIFICATION).
 *
 * Because this document is private, it is where account-level personal data lives (date of birth,
 * phone, gender, consents, notification preferences). Public player data lives on `players/{uid}`.
 * Every field has a default so older documents (and coach / organisation documents) still load.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.PLAYER,
    val profileImageUrl: String = "",
    /** ORGANISATION accounts only: id of `organisations/{id}` (= uid). Empty for other roles and older accounts. */
    val organisationId: String = "",
    val createdAt: Long = System.currentTimeMillis(),

    // ── Player account data (private) ─────────────────────────────────────
    /** ISO `yyyy-MM-dd`. Source of truth for age; age itself is never stored. Never shown to other users. */
    val dateOfBirth: String = "",
    /** Optional. */
    val phoneNumber: String = "",
    /** Optional [Gender] name; empty = not answered. */
    val gender: String = "",
    /**
     * [PlayerProfileStatus] name. Empty on accounts created before player onboarding existed; for a
     * PLAYER that is treated as INCOMPLETE, so they are taken through onboarding once.
     */
    val profileStatus: String = "",
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    /** Set (server time) when the player accepted the Terms of Service / Privacy Policy. */
    val termsAcceptedAt: Date? = null,
    val privacyAcceptedAt: Date? = null,
    val termsVersion: String = "",
    /** Server time of the last profile change made through player onboarding / Edit Profile. */
    val updatedAt: Date? = null
)

enum class UserRole {
    PLAYER, COACH, ORGANISATION
}

/** In-app notification preferences. The Android notification permission is requested separately. */
data class NotificationPreferences(
    val eventNotifications: Boolean = true,
    val coachingNotifications: Boolean = true,
    val chatNotifications: Boolean = true,
    val contentNotifications: Boolean = false
)
