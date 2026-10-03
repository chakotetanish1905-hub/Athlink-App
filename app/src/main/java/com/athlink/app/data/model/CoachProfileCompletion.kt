package com.athlink.app.data.model

import java.time.LocalDate

/** Everything the onboarding flow knows about a coach, gathered from all their documents. */
data class CoachProfileSnapshot(
    val coach: Coach = Coach(),
    val privateProfile: CoachPrivateProfile = CoachPrivateProfile(),
    val verification: CoachVerification = CoachVerification(),
    val qualifications: List<CoachQualification> = emptyList(),
    val services: List<CoachService> = emptyList(),
    val availability: List<CoachAvailability> = emptyList(),
    val references: List<CoachReference> = emptyList()
)

enum class CoachProfileSection(val label: String) {
    IDENTITY("Identity"),
    SPORTS("Sports"),
    EXPERIENCE("Experience"),
    QUALIFICATIONS("Qualifications"),
    SAFETY("Safety"),
    SERVICES("Services"),
    AVAILABILITY("Availability"),
    LOCATION("Location")
}

/** One checklist item. [required] items gate submission and count toward the percentage. */
data class CompletionCheck(
    val section: CoachProfileSection,
    val label: String,
    val done: Boolean,
    val required: Boolean = true
)

data class CoachProfileCompletionResult(
    val percentage: Int,
    val checks: List<CompletionCheck>,
    /** Required items not done yet, in display order. */
    val missingFields: List<String>,
    /** Optional items not done yet ("recommended"). */
    val recommendations: List<String>,
    val completedSections: Set<CoachProfileSection>
) {
    val canSubmit: Boolean get() = missingFields.isEmpty()

    fun isSectionComplete(section: CoachProfileSection): Boolean = section in completedSections

    fun missingIn(section: CoachProfileSection): List<String> =
        checks.filter { it.section == section && it.required && !it.done }.map { it.label }
}

/**
 * Calculates profile completeness dynamically from the coach's real data.
 * Percentage = done required checks / all required checks, rounded down
 * (so it only reaches 100 when the profile can actually be submitted).
 */
object CoachProfileCompletion {

    fun calculate(s: CoachProfileSnapshot, today: LocalDate = LocalDate.now()): CoachProfileCompletionResult {
        val c = s.coach
        val p = s.privateProfile
        val v = s.verification
        val checks = mutableListOf<CompletionCheck>()

        fun add(section: CoachProfileSection, label: String, done: Boolean, required: Boolean = true) {
            checks += CompletionCheck(section, label, done, required)
        }

        // Identity
        add(CoachProfileSection.IDENTITY, "Full legal name", CoachValidators.fullLegalName(p.fullLegalName) == null)
        add(CoachProfileSection.IDENTITY, "Display name", CoachValidators.displayName(c.name) == null)
        add(CoachProfileSection.IDENTITY, "Date of birth", CoachValidators.dateOfBirth(p.dateOfBirth, today) == null)
        add(CoachProfileSection.IDENTITY, "Profile photo", c.profileImageUrl.isNotBlank())
        add(CoachProfileSection.IDENTITY, "Mobile number", CoachValidators.phone(p.phone) == null)
        add(CoachProfileSection.IDENTITY, "Email address", p.email.isNotBlank())
        add(CoachProfileSection.IDENTITY, "Languages", c.languages.isNotEmpty())
        add(CoachProfileSection.IDENTITY, "Professional bio", CoachValidators.bio(c.bio) == null)
        add(
            CoachProfileSection.IDENTITY, "Government ID document",
            v.identity.idType.isNotBlank() && CoachValidators.idLast4(v.identity.idLast4) == null &&
                v.identity.documentPath.isNotBlank()
        )

        // Sports
        add(CoachProfileSection.SPORTS, "Primary sport", c.sport.isNotBlank())
        add(CoachProfileSection.SPORTS, "Specialization", c.specializations.isNotEmpty())
        add(CoachProfileSection.SPORTS, "Age groups", c.ageGroups.isNotEmpty())
        add(CoachProfileSection.SPORTS, "Skill levels", c.coachingLevels.isNotEmpty())
        add(CoachProfileSection.SPORTS, "Secondary sports", c.secondarySports.isNotEmpty(), required = false)

        // Experience
        // `experience` defaults to 0 (a valid value), so it only counts once it can be checked
        // against the date of birth.
        val age = CoachValidators.ageOn(p.dateOfBirth, today)
        add(CoachProfileSection.EXPERIENCE, "Years of experience",
            age != null && CoachValidators.experienceYears(c.experience.toString(), age) == null)
        add(CoachProfileSection.EXPERIENCE, "Coaching position / role", c.coachingPosition.isNotBlank())
        add(CoachProfileSection.EXPERIENCE, "Clubs or academies",
            c.currentOrganisation.isNotBlank() || c.previousOrganisations.isNotEmpty(), required = false)
        add(CoachProfileSection.EXPERIENCE, "Competition / team experience", c.competitionExperience.isNotBlank(), required = false)
        add(CoachProfileSection.EXPERIENCE, "Coaching philosophy", c.coachingPhilosophy.isNotBlank(), required = false)
        add(CoachProfileSection.EXPERIENCE, "Achievements", c.achievements.isNotEmpty(), required = false)
        add(CoachProfileSection.EXPERIENCE, "Professional reference",
            s.references.isNotEmpty(), required = CoachProfileRules.REQUIRE_REFERENCE)

        // Qualifications
        val validQualifications = s.qualifications.filter { CoachValidators.qualification(it, today).isEmpty() }
        add(CoachProfileSection.QUALIFICATIONS, "At least one qualification with certificate",
            validQualifications.isNotEmpty(), required = CoachProfileRules.REQUIRE_QUALIFICATION)
        val incomplete = s.qualifications.size - validQualifications.size
        if (incomplete > 0) {
            add(CoachProfileSection.QUALIFICATIONS,
                if (incomplete == 1) "1 qualification has missing or invalid details"
                else "$incomplete qualifications have missing or invalid details",
                done = false)
        }

        // Safety
        add(CoachProfileSection.SAFETY, "Code of Conduct accepted",
            v.codeOfConductAccepted && v.codeOfConductVersion == CodeOfConduct.VERSION)
        val needsSafeguarding = CoachProfileRules.safeguardingRequired(c.ageGroups)
        add(CoachProfileSection.SAFETY, "Child protection / safeguarding certificate",
            safetyDone(v.childProtection, today), required = needsSafeguarding)
        add(CoachProfileSection.SAFETY, "Police / background verification",
            safetyDone(v.policeVerification, today), required = needsSafeguarding)
        add(CoachProfileSection.SAFETY, "First aid / CPR certificate", safetyDone(v.firstAid, today), required = false)

        // Services
        add(CoachProfileSection.SERVICES, "At least one service with price",
            s.services.any { it.active && CoachValidators.service(it).isEmpty() })

        // Availability
        val slotErrors = CoachValidators.availability(s.availability)
        add(CoachProfileSection.AVAILABILITY, "Weekly availability",
            s.availability.any { it.enabled && it.availabilityId !in slotErrors })
        if (slotErrors.isNotEmpty()) {
            add(CoachProfileSection.AVAILABILITY, "Fix overlapping or invalid time slots", done = false)
        }

        // Location
        add(CoachProfileSection.LOCATION, "City", c.city.isNotBlank())
        add(CoachProfileSection.LOCATION, "State", c.state.isNotBlank())
        add(CoachProfileSection.LOCATION, "Country", c.country.isNotBlank())
        add(CoachProfileSection.LOCATION, "Coaching area", c.coachingArea.isNotBlank())
        add(CoachProfileSection.LOCATION, "Training venue", c.trainingVenue.isNotBlank(), required = false)

        val required = checks.filter { it.required }
        val percentage = if (required.isEmpty()) 100 else required.count { it.done } * 100 / required.size
        val completedSections = CoachProfileSection.entries.filter { section ->
            checks.filter { it.section == section && it.required }.all { it.done }
        }.toSet()

        return CoachProfileCompletionResult(
            percentage = percentage,
            checks = checks,
            missingFields = required.filterNot { it.done }.map { it.label },
            recommendations = checks.filter { !it.required && !it.done }.map { it.label },
            completedSections = completedSections
        )
    }

    private fun safetyDone(e: SafetyEvidence, today: LocalDate): Boolean =
        e.provided && e.documentPath.isNotBlank() &&
            !(e.expiryDate.isNotBlank() && CoachValidators.isExpired(e.expiryDate, today))
}
