package com.athlink.app.data.model

/**
 * A sports event. Published events live in `events/{id}` (readable by every signed-in user);
 * an organisation's unpublished drafts live in `eventDrafts/{id}` (owner only) with the same shape.
 * Only VERIFIED / OFFICIAL_GOVERNMENT organisations may write to `events` (enforced in firestore.rules).
 *
 * [organisationVerificationLevel] and [publishedByUid] were added with organisation verification;
 * older event documents simply don't have them (defaults below), and rules check them against
 * the organisation document so they can't be faked.
 */
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
    /** [OrganisationVerificationLevel] name copied from the organisation at publish time. */
    val organisationVerificationLevel: String = "",
    val publishedByUid: String = "",
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
