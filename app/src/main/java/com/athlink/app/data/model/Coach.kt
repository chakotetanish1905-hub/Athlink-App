package com.athlink.app.data.model

data class Coach(
    val uid: String = "",
    val name: String = "",
    val sport: String = "",
    val bio: String = "",
    val experience: Int = 0,
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val hourlyRate: Double = 0.0,
    val profileImageUrl: String = "",
    val location: String = "",
    val specializations: List<String> = emptyList(),
    val totalEarnings: Double = 0.0,
    val isAvailable: Boolean = true
)
