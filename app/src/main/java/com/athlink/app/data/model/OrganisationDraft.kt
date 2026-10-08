package com.athlink.app.data.model

/**
 * Everything typed into the organisation onboarding flow, held by the ViewModel so nothing is
 * lost when moving between steps. Numbers stay Strings so the UI shows exactly what was typed.
 *
 * [validate] checks one step (dynamic per [organisationType]); the `*Fields()` functions turn a
 * draft into the OWNER-EDITABLE fields of each Firestore document. They never contain status,
 * level, review or timestamp fields, so the client cannot even attempt to self-verify
 * (and firestore.rules reject it anyway).
 */
data class OrganisationDraft(
    // ── Step 1: identity ────────────────────────────────────────────────
    val legalName: String = "",
    val displayName: String = "",
    val organisationType: OrganisationType? = null,
    val primarySport: String = "",
    val otherSports: List<String> = emptyList(),
    val description: String = "",
    val officialEmail: String = "",
    val officialPhone: String = "",
    val website: String = "",
    val yearEstablished: String = "",
    val logoUrl: String = "",

    // ── Step 2: legal / registration ────────────────────────────────────
    val registrationNumber: String = "",
    val issuingAuthority: String = "",
    val pan: String = "",
    val gstRegistered: Boolean = false,
    val gstin: String = "",
    val registeredAddress: String = "",
    val proprietorName: String = "",
    val governmentDepartmentName: String = "",
    val governmentLevel: GovernmentLevel? = null,
    val ministryOrDepartment: String = "",
    val authorisationReferenceNumber: String = "",

    // ── Step 3: authorised representative ───────────────────────────────
    val repFullName: String = "",
    val repDesignation: String = "",
    val repDepartment: String = "",
    val repEmail: String = "",
    val repPhone: String = "",
    val repRelationship: RepresentativeRelationship? = null,
    val repEvidenceType: AuthorisationEvidenceType? = null,

    // ── Step 4: sports legitimacy ───────────────────────────────────────
    val organisationLevel: OrganisationLevel? = null,
    val hasFederationAffiliation: Boolean = false,
    val governingBodyName: String = "",
    val affiliationNumber: String = "",
    val accreditationDetails: String = "",
    val yearsActive: String = "",
    val approxCoaches: String = "",
    val approxAthletes: String = "",

    // ── Step 5: location ────────────────────────────────────────────────
    val country: String = CoachRegistration.DEFAULT_COUNTRY,
    val state: String = "",
    val district: String = "",
    val city: String = "",
    val locality: String = "",
    val pincode: String = "",
    val facilityAddress: String = "",
    val publicAddress: String = "",
    /** Not collected in the UI yet (no map picker); preserved for when maps arrive. */
    val latitude: Double? = null,
    val longitude: Double? = null,

    // ── Step 7: review ──────────────────────────────────────────────────
    val declarationAccepted: Boolean = false
) {

    val legalProfile: LegalProfile? get() = organisationType?.let { OrganisationRequirements.legalProfile(it) }
    val isGovernment: Boolean get() = organisationType?.isGovernment == true

    /** Primary sport first, then the others, without duplicates. */
    val sports: List<String> get() = (listOf(primarySport) + otherSports).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun validate(
        step: OnboardingStep,
        documents: List<OrganisationDocument> = emptyList()
    ): Map<OrgField, String> = buildMap {
        when (step) {
            OnboardingStep.IDENTITY -> validateIdentity()
            OnboardingStep.LEGAL -> validateLegal()
            OnboardingStep.REPRESENTATIVE -> validateRepresentative()
            OnboardingStep.SPORTS -> validateSports()
            OnboardingStep.LOCATION -> validateLocation()
            OnboardingStep.DOCUMENTS -> validateDocuments(documents)
            OnboardingStep.REVIEW -> {
                OnboardingStep.entries.filter { it != OnboardingStep.REVIEW }.forEach { putAll(validate(it, documents)) }
                if (!declarationAccepted) put(OrgField.DECLARATION, "Confirm the declaration to submit")
            }
        }
    }

    /** The first step (in order) that has errors, or null if everything up to review is valid. */
    fun firstInvalidStep(documents: List<OrganisationDocument>): OnboardingStep? =
        OnboardingStep.entries.filter { it != OnboardingStep.REVIEW }.firstOrNull { validate(it, documents).isNotEmpty() }

    private fun MutableMap<OrgField, String>.validateIdentity() {
        OrganisationValidators.organisationName(legalName, "the legal organisation name")?.let { put(OrgField.LEGAL_NAME, it) }
        OrganisationValidators.organisationName(displayName, "the public / display name")?.let { put(OrgField.DISPLAY_NAME, it) }
        if (organisationType == null) put(OrgField.ORGANISATION_TYPE, "Select the organisation type")
        if (primarySport.isBlank()) put(OrgField.PRIMARY_SPORT, "Select the primary sport")
        OrganisationValidators.description(description)?.let { put(OrgField.DESCRIPTION, it) }
        OrganisationValidators.email(officialEmail, if (isGovernment) "the official government email" else "the official email")
            ?.let { put(OrgField.OFFICIAL_EMAIL, it) }
        OrganisationValidators.phone(officialPhone, if (isGovernment) "the official office phone" else "the official phone number")
            ?.let { put(OrgField.OFFICIAL_PHONE, it) }
        OrganisationValidators.website(website, required = legalProfile?.websiteRequired == true)?.let { put(OrgField.WEBSITE, it) }
        OrganisationValidators.yearEstablished(yearEstablished)?.let { put(OrgField.YEAR_ESTABLISHED, it) }
    }

    private fun MutableMap<OrgField, String>.validateLegal() {
        val profile = legalProfile ?: run { put(OrgField.ORGANISATION_TYPE, "Select the organisation type first"); return }
        if (profile.showRegistrationNumber) {
            OrganisationValidators.registrationNumber(
                registrationNumber, profile.registrationNumberRequired, profile.registrationNumberFormat, profile.registrationNumberLabel
            )?.let { put(OrgField.REGISTRATION_NUMBER, it) }
        }
        if (profile.showIssuingAuthority) {
            if (profile.issuingAuthorityRequired) {
                OrganisationValidators.required(issuingAuthority, "the issuing authority")?.let { put(OrgField.ISSUING_AUTHORITY, it) }
            } else OrganisationValidators.optionalMax(issuingAuthority, "the issuing authority")?.let { put(OrgField.ISSUING_AUTHORITY, it) }
        }
        if (profile.showPan) OrganisationValidators.pan(pan, profile.panRequired)?.let { put(OrgField.PAN, it) }
        if (profile.showGst) OrganisationValidators.gstin(gstin, gstRegistered, pan)?.let { put(OrgField.GSTIN, it) }
        if (profile.showProprietor && profile.proprietorRequired) {
            OrganisationValidators.required(proprietorName, "the proprietor / owner name", 80)?.let { put(OrgField.PROPRIETOR_NAME, it) }
        }
        OrganisationValidators.required(registeredAddress, profile.registeredAddressLabel.lowercase(), 300)
            ?.let { put(OrgField.REGISTERED_ADDRESS, it) }
        if (profile.showGovernmentFields) {
            OrganisationValidators.required(governmentDepartmentName, "the government organisation / department name")
                ?.let { put(OrgField.GOVERNMENT_DEPARTMENT_NAME, it) }
            if (governmentLevel == null) put(OrgField.GOVERNMENT_LEVEL, "Select the government level")
            OrganisationValidators.required(ministryOrDepartment, "the ministry / parent department")
                ?.let { put(OrgField.MINISTRY, it) }
            OrganisationValidators.optionalMax(authorisationReferenceNumber, "the reference number", 60)
                ?.let { put(OrgField.AUTHORISATION_REFERENCE, it) }
        }
    }

    private fun MutableMap<OrgField, String>.validateRepresentative() {
        OrganisationValidators.required(repFullName, "the representative's full name", 80)?.let { put(OrgField.REP_FULL_NAME, it) }
        OrganisationValidators.required(repDesignation, "the designation", 80)?.let { put(OrgField.REP_DESIGNATION, it) }
        if (isGovernment) OrganisationValidators.required(repDepartment, "the officer's department", 120)?.let { put(OrgField.REP_DEPARTMENT, it) }
        OrganisationValidators.email(repEmail, "the representative's official email")?.let { put(OrgField.REP_EMAIL, it) }
        OrganisationValidators.phone(repPhone, "the representative's official phone")?.let { put(OrgField.REP_PHONE, it) }
        val type = organisationType
        when {
            repRelationship == null -> put(OrgField.REP_RELATIONSHIP, "Select your relationship with the organisation")
            type != null && repRelationship !in OrganisationRequirements.relationshipOptions(type) ->
                put(OrgField.REP_RELATIONSHIP, "Choose a relationship that fits a ${type.label.lowercase()}")
        }
        when {
            repEvidenceType == null -> put(OrgField.REP_EVIDENCE, "Select the proof of authority you will upload")
            type != null && repEvidenceType !in OrganisationRequirements.evidenceOptions(type) ->
                put(OrgField.REP_EVIDENCE, "This proof isn't accepted for a ${type.label.lowercase()}. Choose another")
        }
    }

    private fun MutableMap<OrgField, String>.validateSports() {
        if (primarySport.isBlank()) put(OrgField.PRIMARY_SPORT, "Select the primary sport")
        if (organisationLevel == null) put(OrgField.ORGANISATION_LEVEL, "Select the level you operate at")
        if (hasFederationAffiliation) {
            OrganisationValidators.required(governingBodyName, "the federation / governing body")?.let { put(OrgField.GOVERNING_BODY, it) }
            OrganisationValidators.optionalMax(affiliationNumber, "the affiliation number", 50)?.let { put(OrgField.AFFILIATION_NUMBER, it) }
        }
        OrganisationValidators.optionalMax(accreditationDetails, "the accreditation details", 200)?.let { put(OrgField.ACCREDITATION, it) }
        OrganisationValidators.optionalCount(yearsActive, "years active", 300)?.let { put(OrgField.YEARS_ACTIVE, it) }
        OrganisationValidators.optionalCount(approxCoaches, "the number of coaches")?.let { put(OrgField.APPROX_COACHES, it) }
        OrganisationValidators.optionalCount(approxAthletes, "the number of athletes")?.let { put(OrgField.APPROX_ATHLETES, it) }
    }

    private fun MutableMap<OrgField, String>.validateLocation() {
        OrganisationValidators.required(country, "the country", 60)?.let { put(OrgField.COUNTRY, it) }
        OrganisationValidators.required(state, "the state", 60)?.let { put(OrgField.STATE, it) }
        OrganisationValidators.required(district, "the district", 60)?.let { put(OrgField.DISTRICT, it) }
        OrganisationValidators.required(city, "the city", 60)?.let { put(OrgField.CITY, it) }
        OrganisationValidators.optionalMax(locality, "the locality", 80)?.let { put(OrgField.LOCALITY, it) }
        OrganisationValidators.pincode(pincode, country)?.let { put(OrgField.PINCODE, it) }
        OrganisationValidators.required(facilityAddress, "the organisation / facility address", 300)?.let { put(OrgField.FACILITY_ADDRESS, it) }
        OrganisationValidators.optionalMax(publicAddress, "the public location", 120)?.let { put(OrgField.PUBLIC_ADDRESS, it) }
    }

    private fun MutableMap<OrgField, String>.validateDocuments(documents: List<OrganisationDocument>) {
        val type = organisationType ?: run { put(OrgField.ORGANISATION_TYPE, "Select the organisation type first"); return }
        val missing = OrganisationRequirements.missingDocuments(type, gstRegistered, documents)
        if (missing.isNotEmpty()) put(OrgField.DOCUMENTS, "Upload: " + missing.joinToString(", ") { it.title })
    }

    // ── Firestore mapping (owner-editable fields only) ──────────────────

    /** Public location label: the user's own text, else "Locality, City". */
    fun resolvedPublicAddress(): String = publicAddress.trim().ifBlank {
        listOf(locality, city).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }

    fun organisationFields(organisationId: String): Map<String, Any?> = mapOf(
        "organisationId" to organisationId,
        "ownerUid" to organisationId,
        "legalName" to legalName.trim(),
        "displayName" to displayName.trim(),
        "organisationType" to (organisationType?.name ?: ""),
        "sports" to sports,
        "description" to description.trim(),
        "website" to website.trim(),
        "organisationLevel" to (organisationLevel?.name ?: ""),
        "country" to country.trim(),
        "state" to state.trim(),
        "district" to district.trim(),
        "city" to city.trim(),
        "locality" to locality.trim(),
        "publicAddress" to resolvedPublicAddress(),
        "latitude" to latitude,
        "longitude" to longitude,
        "yearEstablished" to yearEstablished.trim().toIntOrNull()
    )

    fun verificationFields(organisationId: String): Map<String, Any?> {
        val profile = legalProfile
        val gov = profile?.showGovernmentFields == true
        return mapOf(
            "verificationId" to organisationId,
            "organisationId" to organisationId,
            "ownerUid" to organisationId,
            "officialEmail" to officialEmail.trim().lowercase(),
            "officialPhone" to normalisePhone(officialPhone),
            "registrationNumber" to if (profile?.showRegistrationNumber == true) registrationNumber.trim() else "",
            "registrationType" to (profile?.registrationTypeLabel ?: ""),
            "issuingAuthority" to if (profile?.showIssuingAuthority == true) issuingAuthority.trim() else "",
            "pan" to if (profile?.showPan == true) OrganisationValidators.normaliseId(pan) else "",
            "gstRegistered" to (profile?.showGst == true && gstRegistered),
            "gstin" to if (profile?.showGst == true && gstRegistered) OrganisationValidators.normaliseId(gstin) else "",
            "registeredAddress" to registeredAddress.trim(),
            "proprietorName" to if (profile?.showProprietor == true) proprietorName.trim() else "",
            "governmentDepartmentName" to if (gov) governmentDepartmentName.trim() else "",
            "governmentLevel" to if (gov) (governmentLevel?.name ?: "") else "",
            "ministryOrDepartment" to if (gov) ministryOrDepartment.trim() else "",
            "authorisationReferenceNumber" to if (gov) authorisationReferenceNumber.trim() else "",
            "facilityAddress" to facilityAddress.trim(),
            "pincode" to pincode.trim(),
            "yearsActive" to yearsActive.trim().toIntOrNull(),
            "approxCoaches" to approxCoaches.trim().toIntOrNull(),
            "approxAthletes" to approxAthletes.trim().toIntOrNull(),
            "declarationAccepted" to declarationAccepted
        )
    }

    fun representativeFields(organisationId: String, authorizationDocumentId: String): Map<String, Any?> = mapOf(
        "representativeId" to organisationId,
        "organisationId" to organisationId,
        "ownerUid" to organisationId,
        "fullName" to repFullName.trim(),
        "designation" to repDesignation.trim(),
        "department" to if (isGovernment) repDepartment.trim() else "",
        "officialEmail" to repEmail.trim().lowercase(),
        "officialPhone" to normalisePhone(repPhone),
        "relationshipToOrganisation" to (repRelationship?.name ?: ""),
        "authorisationEvidenceType" to (repEvidenceType?.name ?: ""),
        "authorizationDocumentId" to authorizationDocumentId
    )

    fun affiliationFields(organisationId: String): Map<String, Any?> = mapOf(
        "affiliationId" to organisationId,
        "organisationId" to organisationId,
        "ownerUid" to organisationId,
        "hasFederationAffiliation" to hasFederationAffiliation,
        "governingBodyName" to if (hasFederationAffiliation) governingBodyName.trim() else "",
        "affiliationNumber" to if (hasFederationAffiliation) affiliationNumber.trim() else "",
        "accreditationDetails" to accreditationDetails.trim()
    )

    companion object {
        /** Keeps a leading '+' and the digits, e.g. "+91 22-2345 6789" -> "+912223456789". */
        fun normalisePhone(value: String): String {
            val trimmed = value.trim()
            val digits = trimmed.filter { it.isDigit() }
            return if (trimmed.startsWith("+")) "+$digits" else digits
        }

        /** Pre-fills the form from what is stored (resume later), falling back to the account. */
        fun fromSnapshot(snapshot: OrganisationSnapshot?, accountName: String, accountEmail: String): OrganisationDraft {
            val org = snapshot?.organisation
            val v = snapshot?.verification
            val rep = snapshot?.representative
            val aff = snapshot?.affiliation
            val sports = org?.sports.orEmpty()
            return OrganisationDraft(
                legalName = org?.legalName.orEmpty(),
                displayName = org?.displayName?.ifBlank { null } ?: accountName,
                organisationType = OrganisationType.fromStored(org?.organisationType),
                primarySport = sports.firstOrNull().orEmpty(),
                otherSports = sports.drop(1),
                description = org?.description.orEmpty(),
                officialEmail = v?.officialEmail?.ifBlank { null } ?: accountEmail,
                officialPhone = v?.officialPhone.orEmpty(),
                website = org?.website.orEmpty(),
                yearEstablished = org?.yearEstablished?.toString().orEmpty(),
                logoUrl = org?.logoUrl.orEmpty(),
                registrationNumber = v?.registrationNumber.orEmpty(),
                issuingAuthority = v?.issuingAuthority.orEmpty(),
                pan = v?.pan.orEmpty(),
                gstRegistered = v?.gstRegistered ?: false,
                gstin = v?.gstin.orEmpty(),
                registeredAddress = v?.registeredAddress.orEmpty(),
                proprietorName = v?.proprietorName.orEmpty(),
                governmentDepartmentName = v?.governmentDepartmentName.orEmpty(),
                governmentLevel = GovernmentLevel.fromStored(v?.governmentLevel),
                ministryOrDepartment = v?.ministryOrDepartment.orEmpty(),
                authorisationReferenceNumber = v?.authorisationReferenceNumber.orEmpty(),
                repFullName = rep?.fullName.orEmpty(),
                repDesignation = rep?.designation.orEmpty(),
                repDepartment = rep?.department.orEmpty(),
                repEmail = rep?.officialEmail?.ifBlank { null } ?: accountEmail,
                repPhone = rep?.officialPhone.orEmpty(),
                repRelationship = RepresentativeRelationship.fromStored(rep?.relationshipToOrganisation),
                repEvidenceType = AuthorisationEvidenceType.fromStored(rep?.authorisationEvidenceType),
                organisationLevel = OrganisationLevel.fromStored(org?.organisationLevel),
                hasFederationAffiliation = aff?.hasFederationAffiliation ?: false,
                governingBodyName = aff?.governingBodyName.orEmpty(),
                affiliationNumber = aff?.affiliationNumber.orEmpty(),
                accreditationDetails = aff?.accreditationDetails.orEmpty(),
                yearsActive = v?.yearsActive?.toString().orEmpty(),
                approxCoaches = v?.approxCoaches?.toString().orEmpty(),
                approxAthletes = v?.approxAthletes?.toString().orEmpty(),
                country = org?.country?.ifBlank { null } ?: CoachRegistration.DEFAULT_COUNTRY,
                state = org?.state.orEmpty(),
                district = org?.district.orEmpty(),
                city = org?.city.orEmpty(),
                locality = org?.locality.orEmpty(),
                pincode = v?.pincode.orEmpty(),
                facilityAddress = v?.facilityAddress.orEmpty(),
                publicAddress = org?.publicAddress.orEmpty(),
                latitude = org?.latitude,
                longitude = org?.longitude,
                // The declaration must be re-confirmed on every (re)submission.
                declarationAccepted = false
            )
        }
    }
}

enum class OnboardingStep(val title: String, val subtitle: String) {
    IDENTITY("Organisation identity", "Who you are"),
    LEGAL("Legal / registration", "Prove the organisation exists"),
    REPRESENTATIVE("Authorised representative", "Prove you can act for it"),
    SPORTS("Sports information", "Prove sports legitimacy"),
    LOCATION("Location", "Where you operate"),
    DOCUMENTS("Documents", "Upload the evidence"),
    REVIEW("Review & submit", "Check and send for review")
}

/** Every validated field of the onboarding form; key for per-field errors. */
enum class OrgField {
    LEGAL_NAME, DISPLAY_NAME, ORGANISATION_TYPE, PRIMARY_SPORT, DESCRIPTION, OFFICIAL_EMAIL, OFFICIAL_PHONE,
    WEBSITE, YEAR_ESTABLISHED,
    REGISTRATION_NUMBER, ISSUING_AUTHORITY, PAN, GSTIN, REGISTERED_ADDRESS, PROPRIETOR_NAME,
    GOVERNMENT_DEPARTMENT_NAME, GOVERNMENT_LEVEL, MINISTRY, AUTHORISATION_REFERENCE,
    REP_FULL_NAME, REP_DESIGNATION, REP_DEPARTMENT, REP_EMAIL, REP_PHONE, REP_RELATIONSHIP, REP_EVIDENCE,
    ORGANISATION_LEVEL, GOVERNING_BODY, AFFILIATION_NUMBER, ACCREDITATION, YEARS_ACTIVE, APPROX_COACHES, APPROX_ATHLETES,
    COUNTRY, STATE, DISTRICT, CITY, LOCALITY, PINCODE, FACILITY_ADDRESS, PUBLIC_ADDRESS,
    DOCUMENTS, DECLARATION
}
