package com.athlink.app.data.model

import java.time.LocalDate

/** The onboarding steps, in order. Edit Profile shows the same sections on one page. */
enum class PlayerOnboardingStep(val title: String, val subtitle: String) {
    ABOUT("Tell us about you", "Your name, age and where you play"),
    SPORT("What's your sport?", "Pick one main sport and any others you play"),
    GAME("Tell us about your game", "Optional details that help coaches understand you"),
    LEVEL("What's your level?", "Be honest: it helps match you with the right coach"),
    GOALS("What are your goals?", "Choose everything that applies"),
    TRAINING("How do you want to train?", "We'll use this to suggest coaches and sessions"),
    READY("Your profile is ready", "Check your details, then continue to Athlink")
}

enum class PlayerField {
    FULL_NAME, DATE_OF_BIRTH, PHONE, COUNTRY, STATE, CITY,
    PRIMARY_SPORT, SECONDARY_SPORTS, SPORT_PROFILE,
    EXPERIENCE_YEARS, CURRENT_TEAM, ACADEMY, ACHIEVEMENTS, BIO, RANKING,
    SKILL_LEVEL, GOALS,
    COACHING_FORMAT, TRAINING_TIME, TRAVEL_DISTANCE,
    TERMS, PRIVACY
}

/**
 * Everything the player onboarding / Edit Profile form edits, as one immutable value.
 * The ViewModel holds a single instance, so moving between steps never loses data.
 *
 * Writes are split by visibility:
 *  - [toPlayerFields] -> public `players/{uid}`
 *  - [toUserFields]   -> private `users/{uid}` (date of birth, phone, gender, notifications)
 */
data class PlayerProfileForm(
    // About you
    val fullName: String = "",
    val dateOfBirth: String = "",
    val phoneNumber: String = "",
    val gender: Gender? = null,
    val country: String = PlayerLocation.DEFAULT_COUNTRY,
    val state: String = "",
    val city: String = "",
    val areaType: AreaType? = null,
    val photoUrl: String = "",
    // Sport
    val primarySport: String = "",
    val secondarySports: List<String> = emptyList(),
    val sportProfile: Map<String, String> = emptyMap(),
    // Game / experience (optional)
    val yearsOfExperience: String = "",
    val currentTeam: String = "",
    val academy: String = "",
    /** One achievement per line. */
    val achievementsText: String = "",
    val bio: String = "",
    val ranking: String = "",
    // Level, goals, training
    val skillLevel: SkillLevel? = null,
    val goals: List<PlayerGoal> = emptyList(),
    val coachingFormat: CoachingFormat? = null,
    val trainingTime: TrainingTime? = null,
    val travelDistance: TravelDistance? = null,
    val notifications: NotificationPreferences = NotificationPreferences(),
    // Consent (accounts created before player onboarding existed must accept here)
    val consentAlreadyRecorded: Boolean = false,
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false
) {
    val region: String get() = PlayerLocation.regionFor(country, state)
    val achievements: List<String> get() = achievementsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val sportAttributes: List<SportAttribute> get() = SportProfiles.attributesFor(primarySport)

    fun isMinor(today: LocalDate = LocalDate.now()): Boolean = PlayerAge.isMinor(dateOfBirth, today)

    // ── Edits that keep the form consistent ─────────────────────────────

    /** Changing the main sport drops answers that belong to the old sport and de-duplicates. */
    fun withPrimarySport(sport: String): PlayerProfileForm = copy(
        primarySport = sport,
        secondarySports = secondarySports - sport,
        sportProfile = SportProfiles.sanitise(sport, sportProfile)
    )

    fun toggleSecondarySport(sport: String): PlayerProfileForm = when {
        sport == primarySport -> this
        sport in secondarySports -> copy(secondarySports = secondarySports - sport)
        secondarySports.size >= PlayerValidators.MAX_SECONDARY_SPORTS -> this
        else -> copy(secondarySports = secondarySports + sport)
    }

    fun withSportAnswer(key: String, value: String?): PlayerProfileForm =
        copy(sportProfile = if (value == null) sportProfile - key else sportProfile + (key to value))

    fun toggleGoal(goal: PlayerGoal): PlayerProfileForm =
        copy(goals = if (goal in goals) goals - goal else goals + goal)

    /** Switching country clears a state that only made sense for the old country. */
    fun withCountry(value: String): PlayerProfileForm {
        val stillValid = if (PlayerLocation.isIndia(value)) state in PlayerLocation.INDIAN_STATES
        else state.isNotBlank() && state !in PlayerLocation.INDIAN_STATES
        return copy(country = value, state = if (stillValid) state else "")
    }

    // ── Validation ──────────────────────────────────────────────────────

    fun validate(step: PlayerOnboardingStep, today: LocalDate = LocalDate.now()): Map<PlayerField, String> {
        val errors = mutableMapOf<PlayerField, String>()
        fun check(field: PlayerField, message: String?) { if (message != null) errors[field] = message }
        when (step) {
            PlayerOnboardingStep.ABOUT -> {
                check(PlayerField.FULL_NAME, PlayerValidators.fullName(fullName))
                check(PlayerField.DATE_OF_BIRTH, PlayerValidators.dateOfBirth(dateOfBirth, today))
                check(PlayerField.PHONE, PlayerValidators.optionalPhone(phoneNumber))
                check(PlayerField.COUNTRY, PlayerValidators.country(country))
                check(PlayerField.STATE, PlayerValidators.state(country, state))
                check(PlayerField.CITY, PlayerValidators.city(city))
            }
            PlayerOnboardingStep.SPORT -> {
                check(PlayerField.PRIMARY_SPORT, PlayerValidators.primarySport(primarySport))
                check(PlayerField.SECONDARY_SPORTS, PlayerValidators.secondarySports(primarySport, secondarySports))
            }
            PlayerOnboardingStep.GAME -> {
                check(PlayerField.SPORT_PROFILE, PlayerValidators.sportProfile(primarySport, sportProfile))
                check(PlayerField.EXPERIENCE_YEARS, PlayerValidators.yearsOfExperience(yearsOfExperience, PlayerAge.ageOf(dateOfBirth, today)))
                check(PlayerField.CURRENT_TEAM, PlayerValidators.optionalText(currentTeam, "team name"))
                check(PlayerField.ACADEMY, PlayerValidators.optionalText(academy, "academy name"))
                check(PlayerField.RANKING, PlayerValidators.optionalText(ranking, "ranking", PlayerValidators.RANKING_MAX))
                check(PlayerField.ACHIEVEMENTS, PlayerValidators.achievements(achievements))
                check(PlayerField.BIO, PlayerValidators.optionalText(bio, "your bio", PlayerValidators.BIO_MAX))
            }
            PlayerOnboardingStep.LEVEL -> check(PlayerField.SKILL_LEVEL, PlayerValidators.skillLevel(skillLevel))
            PlayerOnboardingStep.GOALS -> check(PlayerField.GOALS, PlayerValidators.goals(goals))
            PlayerOnboardingStep.TRAINING -> {
                check(PlayerField.COACHING_FORMAT, PlayerValidators.requiredChoice(coachingFormat, "Choose how you'd like to be coached"))
                check(PlayerField.TRAINING_TIME, PlayerValidators.requiredChoice(trainingTime, "Choose when you can train"))
                check(PlayerField.TRAVEL_DISTANCE, PlayerValidators.requiredChoice(travelDistance, "Choose how far you can travel"))
            }
            PlayerOnboardingStep.READY -> {
                if (!consentAlreadyRecorded) {
                    check(PlayerField.TERMS, PlayerValidators.consent(termsAccepted, "Terms of Service"))
                    check(PlayerField.PRIVACY, PlayerValidators.consent(privacyAccepted, "Privacy Policy"))
                }
            }
        }
        return errors
    }

    fun validateAll(today: LocalDate = LocalDate.now()): Map<PlayerField, String> =
        PlayerOnboardingStep.entries.fold(emptyMap<PlayerField, String>()) { acc, step -> acc + validate(step, today) }

    /** First step that still has errors, or null when the whole profile is valid. */
    fun firstInvalidStep(today: LocalDate = LocalDate.now()): PlayerOnboardingStep? =
        PlayerOnboardingStep.entries.firstOrNull { validate(it, today).isNotEmpty() }

    // ── Firestore field maps (explicit, so nothing private can leak by accident) ──

    /** Public `players/{uid}` fields. Timestamps are added by the data source. */
    fun toPlayerFields(uid: String): Map<String, Any?> = mapOf(
        "uid" to uid,
        "displayName" to fullName.trim().replace(Regex("\\s+"), " "),
        "photoUrl" to photoUrl,
        "country" to country.trim(),
        "state" to state.trim(),
        "city" to city.trim(),
        "region" to region,
        "areaType" to (areaType?.name ?: ""),
        "primarySport" to primarySport,
        "secondarySports" to secondarySports,
        "sportProfile" to SportProfiles.sanitise(primarySport, sportProfile),
        "skillLevel" to (skillLevel?.name ?: ""),
        "goals" to goals.map { it.name },
        "coachingPreferences" to mapOf(
            "format" to (coachingFormat?.name ?: ""),
            "trainingTime" to (trainingTime?.name ?: ""),
            "maxDistanceKm" to travelDistance?.km
        ),
        "yearsOfExperience" to yearsOfExperience.trim().toIntOrNull(),
        "currentTeam" to currentTeam.trim(),
        "academy" to academy.trim(),
        "achievements" to achievements,
        "bio" to bio.trim(),
        "ranking" to ranking.trim()
    )

    /** Private `users/{uid}` fields. `name` stays the account's display name used across the app. */
    fun toUserFields(): Map<String, Any?> = mapOf(
        "name" to fullName.trim().replace(Regex("\\s+"), " "),
        "dateOfBirth" to dateOfBirth.trim(),
        "phoneNumber" to phoneNumber.trim(),
        "gender" to (gender?.name ?: ""),
        "notificationPreferences" to mapOf(
            "eventNotifications" to notifications.eventNotifications,
            "coachingNotifications" to notifications.coachingNotifications,
            "chatNotifications" to notifications.chatNotifications,
            "contentNotifications" to notifications.contentNotifications
        )
    )

    companion object {
        /** Builds the form from whatever is stored. Missing fields (older documents) fall back to defaults. */
        fun fromStored(user: User, player: PlayerProfile?): PlayerProfileForm {
            val consent = user.termsAcceptedAt != null && user.privacyAcceptedAt != null
            val prefs = player?.coachingPreferences ?: CoachingPreferences()
            return PlayerProfileForm(
                fullName = user.name.ifBlank { player?.displayName.orEmpty() },
                dateOfBirth = user.dateOfBirth,
                phoneNumber = user.phoneNumber,
                gender = enumOrNull<Gender>(user.gender),
                country = player?.country?.ifBlank { null } ?: PlayerLocation.DEFAULT_COUNTRY,
                state = player?.state.orEmpty(),
                city = player?.city.orEmpty(),
                areaType = enumOrNull<AreaType>(player?.areaType),
                photoUrl = player?.photoUrl.orEmpty(),
                primarySport = player?.primarySport.orEmpty(),
                secondarySports = player?.secondarySports.orEmpty(),
                sportProfile = player?.sportProfile.orEmpty(),
                yearsOfExperience = player?.yearsOfExperience?.toString().orEmpty(),
                currentTeam = player?.currentTeam.orEmpty(),
                academy = player?.academy.orEmpty(),
                achievementsText = player?.achievements.orEmpty().joinToString("\n"),
                bio = player?.bio.orEmpty(),
                ranking = player?.ranking.orEmpty(),
                skillLevel = enumOrNull<SkillLevel>(player?.skillLevel),
                goals = player?.goals.orEmpty().mapNotNull { enumOrNull<PlayerGoal>(it) },
                coachingFormat = enumOrNull<CoachingFormat>(prefs.format),
                trainingTime = enumOrNull<TrainingTime>(prefs.trainingTime),
                travelDistance = TravelDistance.fromKm(prefs.maxDistanceKm),
                notifications = user.notificationPreferences,
                consentAlreadyRecorded = consent,
                termsAccepted = consent,
                privacyAccepted = consent
            )
        }
    }
}

/** Where a signed-in player should land. */
enum class PlayerLanding { ONBOARDING, DASHBOARD }

object PlayerProfilePolicy {
    /**
     * A player's profile is complete only when the private status says COMPLETE (written in the
     * same batch as the public profile) AND the stored data still passes today's validation.
     * The second check means a future new required field sends players back to onboarding
     * instead of crashing a screen that expects it.
     */
    fun isComplete(user: User, player: PlayerProfile?, today: LocalDate = LocalDate.now()): Boolean =
        user.profileStatus == PlayerProfileStatus.COMPLETE.name &&
            player != null &&
            PlayerProfileForm.fromStored(user, player).validateAll(today).isEmpty()

    fun landing(user: User, player: PlayerProfile?, today: LocalDate = LocalDate.now()): PlayerLanding =
        if (isComplete(user, player, today)) PlayerLanding.DASHBOARD else PlayerLanding.ONBOARDING
}
