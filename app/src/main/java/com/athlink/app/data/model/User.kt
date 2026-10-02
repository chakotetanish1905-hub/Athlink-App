package com.athlink.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.PLAYER,
    val profileImageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    PLAYER, COACH, ORGANISATION
}
