package com.athlink.app.data.model

data class Event(
    val id: String = "",
    val organisationId: String = "",
    val organisationName: String = "",
    val title: String = "",
    val description: String = "",
    val sport: String = "",
    val date: String = "",
    val location: String = "",
    val fees: Double = 0.0,
    val maxParticipants: Int = 0,
    val registeredCount: Int = 0,
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class ChatThread(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0
)
