package com.athlink.app.data.model

data class Session(
    val id: String = "",
    val coachId: String = "",
    val coachName: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val sport: String = "",
    val date: String = "",
    val timeSlot: String = "",
    val status: SessionStatus = SessionStatus.PENDING,
    val price: Double = 0.0,
    val location: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class SessionStatus {
    PENDING, CONFIRMED, REJECTED, COMPLETED, CANCELLED
}
