package com.athlink.app.data.model

/**
 * One coaching service (a bookable offering): `coaches/{uid}/services/{serviceId}`.
 * Public once the coach profile is ACTIVE. Only the coach can write their own services.
 */
data class CoachService(
    val serviceId: String = "",
    /** Short name shown to players, e.g. "1-on-1 batting session". */
    val title: String = "",
    /** [ServiceType] name. */
    val serviceType: String = ServiceType.INDIVIDUAL.name,
    /** [ServiceMode] name. */
    val mode: String = ServiceMode.IN_PERSON.name,
    val durationMinutes: Int = 60,
    /** Price per session in [currency]. */
    val price: Double = 0.0,
    val currency: String = "INR",
    val maxPlayers: Int = 1,
    val equipmentRequired: Boolean = false,
    val equipmentDescription: String = "",
    /** Area/venue name for in-person sessions. Never a home address. */
    val venueArea: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class ServiceType(val label: String, val minPlayers: Int, val maxPlayers: Int) {
    INDIVIDUAL("Individual (1-on-1)", 1, 1),
    GROUP("Group", 2, 30),
    TEAM("Team", 2, 60);

    companion object {
        fun fromStored(value: String?): ServiceType =
            entries.firstOrNull { it.name == value } ?: INDIVIDUAL
    }
}

enum class ServiceMode(val label: String) {
    IN_PERSON("In-person"),
    ONLINE("Online");

    companion object {
        fun fromStored(value: String?): ServiceMode =
            entries.firstOrNull { it.name == value } ?: IN_PERSON
    }
}

/** Session lengths offered in the service editor. */
object SessionDurations {
    val OPTIONS_MINUTES = listOf(30, 45, 60, 90, 120)
}
