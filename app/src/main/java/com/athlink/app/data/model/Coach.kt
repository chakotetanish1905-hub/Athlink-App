package com.athlink.app.data.model

/**
 * Public coaching profile, stored at `coaches/{uid}` (same uid as the `users/{uid}` account doc).
 * Created by [CoachRegistration.toCoach] during coach signup. All fields have defaults so
 * Firestore can deserialize documents with `toObject(Coach::class.java)`.
 */
data class Coach(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val sport: String = "",
    val bio: String = "",
    val experience: Int = 0,
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val hourlyRate: Double = 0.0,
    val profileImageUrl: String = "",
    val city: String = "",
    val state: String = "",
    val location: String = "",
    val specializations: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val coachingLevels: List<String> = emptyList(),   // CoachingLevel names
    val verificationStatus: String = VerificationStatus.PENDING.name,
    val totalEarnings: Double = 0.0,
    val isAvailable: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
