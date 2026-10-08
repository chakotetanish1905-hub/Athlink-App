package com.athlink.app.data.model

/**
 * Shared account record: `users/{uid}`.
 *
 * [role] says what kind of account this is. It does NOT say whether the account is trusted:
 * an ORGANISATION's verification lives on `organisations/{organisationId}` (ROLE ≠ VERIFICATION).
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.PLAYER,
    val profileImageUrl: String = "",
    /** ORGANISATION accounts only: id of `organisations/{id}` (= uid). Empty for other roles and older accounts. */
    val organisationId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    PLAYER, COACH, ORGANISATION
}
