package com.athlink.app.data.model

/*
 * Status / category types for organisation verification.
 *
 * ROLE ≠ VERIFICATION: `users/{uid}.role == ORGANISATION` only says what kind of account this is.
 * Whether Athlink trusts the organisation is [OrganisationVerificationStatus] +
 * [OrganisationVerificationLevel] on `organisations/{orgId}`, which only an admin can raise.
 *
 * Like the coach enums, these are stored in Firestore as their `name` String and must be read via
 * `fromStored`, which maps unknown / legacy / missing values to the SAFEST value (never verified).
 * These are deliberately separate from the coach `VerificationStatus` / `VerificationLevel` enums:
 * coaches and organisations have different lifecycles.
 */

/**
 * Overall verification state of an organisation (`organisations/{id}.verificationStatus`).
 *
 * Owner-initiated:  UNVERIFIED -> CONTACT_VERIFIED (needs a verified account email)
 *                   UNVERIFIED / CONTACT_VERIFIED / REJECTED / EXPIRED -> UNDER_REVIEW (submit)
 * Admin-only:       everything else. See [OrganisationVerificationPolicy].
 */
enum class OrganisationVerificationStatus(val label: String) {
    UNVERIFIED("Verification pending"),
    CONTACT_VERIFIED("Contact verified"),
    UNDER_REVIEW("Under review"),
    VERIFIED("Verified organisation"),
    OFFICIAL_GOVERNMENT("Official government organisation"),
    REJECTED("Verification rejected"),
    SUSPENDED("Suspended"),
    EXPIRED("Verification expired");

    companion object {
        fun fromStored(value: String?): OrganisationVerificationStatus =
            entries.firstOrNull { it.name == value } ?: UNVERIFIED
    }
}

/** What Athlink has actually verified. Each level includes the ones below it. */
enum class OrganisationVerificationLevel(val rank: Int, val label: String) {
    LEVEL_0_UNVERIFIED(0, "Unverified"),
    LEVEL_1_CONTACT_VERIFIED(1, "Contact verified"),
    LEVEL_2_ORGANISATION_VERIFIED(2, "Organisation verified"),
    LEVEL_3_OFFICIAL_GOVERNMENT(3, "Official government");

    companion object {
        fun fromStored(value: String?): OrganisationVerificationLevel =
            entries.firstOrNull { it.name == value } ?: LEVEL_0_UNVERIFIED
    }
}

/**
 * Review state of one uploaded document. Uploads always start as [PENDING_MANUAL_REVIEW]:
 * Athlink has no external registry API, so nothing is "verified" until a person has checked it.
 */
enum class DocumentReviewStatus(val label: String) {
    PENDING_MANUAL_REVIEW("Pending manual review"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    EXPIRED("Expired");

    companion object {
        fun fromStored(value: String?): DocumentReviewStatus =
            entries.firstOrNull { it.name == value } ?: PENDING_MANUAL_REVIEW
    }
}

/**
 * Which legal / verification path an organisation type follows. Drives the dynamic form and the
 * required documents in [OrganisationRequirements].
 */
enum class OrganisationLegalGroup {
    GOVERNMENT,
    FEDERATION_OR_ASSOCIATION,
    COMPANY,
    LLP,
    PARTNERSHIP,
    SOCIETY,
    TRUST,
    EDUCATIONAL_INSTITUTION,
    /** Academies, clubs, training centres: often a proprietorship with no incorporation. */
    SMALL_SPORTS_BUSINESS,
    OTHER
}

enum class OrganisationType(val label: String, val group: OrganisationLegalGroup) {
    GOVERNMENT_SPORTS_DEPARTMENT("Government Sports Department", OrganisationLegalGroup.GOVERNMENT),
    GOVERNMENT_SPORTS_AUTHORITY("Government Sports Authority", OrganisationLegalGroup.GOVERNMENT),
    MUNICIPAL_SPORTS_BODY("Municipal / Local Sports Body", OrganisationLegalGroup.GOVERNMENT),
    NATIONAL_SPORTS_FEDERATION("National Sports Federation", OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION),
    STATE_SPORTS_FEDERATION("State Sports Federation", OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION),
    DISTRICT_SPORTS_ASSOCIATION("District Sports Association", OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION),
    SPORTS_ACADEMY("Sports Academy", OrganisationLegalGroup.SMALL_SPORTS_BUSINESS),
    SPORTS_CLUB("Sports Club", OrganisationLegalGroup.SMALL_SPORTS_BUSINESS),
    SPORTS_TRAINING_CENTRE("Sports Training Centre", OrganisationLegalGroup.SMALL_SPORTS_BUSINESS),
    SPORTS_ASSOCIATION("Sports Association", OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION),
    PRIVATE_SPORTS_ORGANISATION("Private Sports Organisation", OrganisationLegalGroup.SMALL_SPORTS_BUSINESS),
    COMPANY_PRIVATE_LIMITED("Company / Private Limited", OrganisationLegalGroup.COMPANY),
    LLP("LLP", OrganisationLegalGroup.LLP),
    PARTNERSHIP("Partnership", OrganisationLegalGroup.PARTNERSHIP),
    SOCIETY("Society", OrganisationLegalGroup.SOCIETY),
    TRUST_NGO("Trust / NGO", OrganisationLegalGroup.TRUST),
    SCHOOL("School", OrganisationLegalGroup.EDUCATIONAL_INSTITUTION),
    COLLEGE_UNIVERSITY("College / University", OrganisationLegalGroup.EDUCATIONAL_INSTITUTION),
    OTHER("Other", OrganisationLegalGroup.OTHER);

    val isGovernment: Boolean get() = group == OrganisationLegalGroup.GOVERNMENT

    companion object {
        /** Unknown / missing -> null (the form then asks the user to choose). */
        fun fromStored(value: String?): OrganisationType? = entries.firstOrNull { it.name == value }
    }
}

/** Competitive level the organisation operates at. */
enum class OrganisationLevel(val label: String) {
    LOCAL("Local"),
    DISTRICT("District"),
    STATE("State"),
    NATIONAL("National"),
    INTERNATIONAL("International");

    companion object {
        fun fromStored(value: String?): OrganisationLevel? = entries.firstOrNull { it.name == value }
    }
}

enum class GovernmentLevel(val label: String) {
    CENTRAL("Central"),
    STATE("State"),
    DISTRICT("District"),
    MUNICIPAL_LOCAL("Municipal / Local");

    companion object {
        fun fromStored(value: String?): GovernmentLevel? = entries.firstOrNull { it.name == value }
    }
}

/** How the representative is related to the organisation. */
enum class RepresentativeRelationship(val label: String) {
    OWNER_PROPRIETOR("Owner / Proprietor"),
    DIRECTOR_PARTNER("Director / Partner"),
    OFFICE_BEARER("Office bearer (President, Secretary, Treasurer)"),
    AUTHORISED_EMPLOYEE("Authorised employee / Manager"),
    GOVERNMENT_OFFICER("Government officer"),
    PRINCIPAL_OR_HEAD("Principal / Head of institution"),
    OTHER("Other");

    companion object {
        fun fromStored(value: String?): RepresentativeRelationship? = entries.firstOrNull { it.name == value }
    }
}

/** The kind of evidence offered to prove the representative's authority. */
enum class AuthorisationEvidenceType(val label: String) {
    LETTERHEAD_AUTHORISATION_LETTER("Authorisation letter on organisation letterhead"),
    APPOINTMENT_LETTER("Appointment letter"),
    BOARD_RESOLUTION("Board / committee resolution"),
    GOVERNMENT_AUTHORISATION_LETTER("Government authorisation letter"),
    GOVERNMENT_ORDER("Government order / notification"),
    OFFICIAL_DEPARTMENT_LETTER("Official department letter"),
    ORGANISATION_ID_WITH_AUTHORISATION("Organisation ID + authorisation document"),
    PROPRIETOR_DECLARATION("Signed declaration by the proprietor"),
    OTHER_OFFICIAL_PROOF("Other official authority proof");

    companion object {
        fun fromStored(value: String?): AuthorisationEvidenceType? = entries.firstOrNull { it.name == value }
    }
}
