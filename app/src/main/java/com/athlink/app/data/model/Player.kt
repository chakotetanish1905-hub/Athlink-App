package com.athlink.app.data.model

import java.util.Date

/**
 * Public player profile: `players/{uid}` (document id = Firebase Auth uid).
 *
 * DISCOVERABLE data only: any signed-in user (e.g. a coach) may read it. Private account data
 * (email, date of birth, phone, gender, consents, notification preferences) lives on the private
 * `users/{uid}` document and must never be copied here; firestore.rules reject those keys.
 *
 * [displayName] and [photoUrl] are the public copies of the player's name and photo (users/{uid}
 * is private, so coaches cannot read the name from there). They are written in the same batch as
 * `users/{uid}.name`, so the two never drift apart.
 *
 * Every field has a default so documents written by older app versions load without crashing.
 * Enum-like fields are stored as enum names (strings) and parsed leniently ([enumOrNull]).
 *
 * Future map/discovery work can add `latitude`, `longitude` and `geohash` here without a
 * migration; nothing about location beyond city/state/region is collected today.
 */
data class PlayerProfile(
    val uid: String = "",
    val displayName: String = "",
    /** Small `data:image/jpeg;base64,...` URI (Spark plan, no Cloud Storage) or an http(s) URL. */
    val photoUrl: String = "",

    // Location (coarse only: never an address or GPS position)
    val country: String = "",
    val state: String = "",
    val city: String = "",
    /** Geographic zone derived from the state (e.g. "West India"); "International" outside India. */
    val region: String = "",
    /** Optional [AreaType] name (metro / tier-2 / tier-3 / rural), the dimension Athlink's research uses. */
    val areaType: String = "",

    // Sports
    val primarySport: String = "",
    val secondarySports: List<String> = emptyList(),
    /** Sport-specific answers for [primarySport], keyed by [SportAttribute.key] (see [SportProfiles]). */
    val sportProfile: Map<String, String> = emptyMap(),
    /** [SkillLevel] name. */
    val skillLevel: String = "",
    /** [PlayerGoal] names. */
    val goals: List<String> = emptyList(),
    val coachingPreferences: CoachingPreferences = CoachingPreferences(),

    // Experience (optional)
    val yearsOfExperience: Int? = null,
    val currentTeam: String = "",
    val academy: String = "",
    val achievements: List<String> = emptyList(),
    val bio: String = "",
    /** Free text: ranking systems differ per sport, so this is never structured or required. */
    val ranking: String = "",

    val createdAt: Date? = null,
    val updatedAt: Date? = null
)

/** Designed to feed future coach discovery (format, time-of-day and radius filters). */
data class CoachingPreferences(
    /** [CoachingFormat] name. */
    val format: String = "",
    /** [TrainingTime] name. */
    val trainingTime: String = "",
    /** Maximum travel distance in km. `0` = any distance, `null` = not answered. */
    val maxDistanceKm: Int? = null
)

/** Lifecycle of the player's profile. Kept on the private `users/{uid}` document. */
enum class PlayerProfileStatus { INCOMPLETE, COMPLETE }

enum class SkillLevel(val label: String, val description: String) {
    BEGINNER("Beginner", "Just starting out or learning the basics"),
    INTERMEDIATE("Intermediate", "Comfortable with the fundamentals"),
    ADVANCED("Advanced", "Strong technique, play regularly"),
    COMPETITIVE("Competitive", "Compete in leagues or tournaments"),
    PROFESSIONAL("Professional", "Play at state, national or pro level")
}

enum class PlayerGoal(val label: String) {
    LEARN_NEW_SPORT("Learn a new sport"),
    IMPROVE_SKILLS("Improve my skills"),
    FIND_COACH("Find a coach"),
    PREPARE_COMPETITION("Prepare for competition"),
    JOIN_TOURNAMENTS("Join tournaments"),
    IMPROVE_FITNESS("Improve fitness"),
    COMPETITIVE_TRAINING("Competitive training"),
    RECRUITMENT_SCOUTING("Recruitment / scouting"),
    CASUAL_PLAY("Casual play")
}

enum class CoachingFormat(val label: String) {
    ONE_TO_ONE("One-to-one"),
    GROUP("Group"),
    EITHER("Either")
}

enum class TrainingTime(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    FLEXIBLE("Flexible")
}

/** Travel radius choices. [km] `0` means "any distance". */
enum class TravelDistance(val km: Int, val label: String) {
    KM_5(5, "5 km"),
    KM_10(10, "10 km"),
    KM_25(25, "25 km"),
    KM_50(50, "50 km"),
    ANY(0, "Any distance");

    companion object {
        fun fromKm(km: Int?): TravelDistance? = km?.let { value -> entries.firstOrNull { it.km == value } }
    }
}

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary / other"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

/** Same bands as the Phase 1 player survey (`region_type`), so app data and research data line up. */
enum class AreaType(val label: String) {
    METRO("Metro / Tier-1 city"),
    TIER_2("Tier-2 city"),
    TIER_3("Tier-3 / small town"),
    RURAL("Rural area / village")
}

/** Lenient enum parsing for values read from Firestore: unknown or blank -> null, never a crash. */
inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name.isNullOrBlank()) null else enumValues<T>().firstOrNull { it.name == name }
