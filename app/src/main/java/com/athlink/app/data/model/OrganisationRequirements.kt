package com.athlink.app.data.model

/**
 * What a given organisation type must provide. This is the single source of truth for the
 * dynamic onboarding form (which fields to show / require) and for the document checklist.
 *
 * Principles:
 *  - Government bodies never need CIN / PAN / GST; they prove authority with an official order.
 *  - GSTIN is only asked for when the organisation says it is GST-registered.
 *  - Small academies / clubs follow a light "business" path (address proof + PAN or any
 *    registration) instead of being treated like a Pvt Ltd company.
 *  - Federation affiliation is never mandatory for a private academy; any sports credential works.
 *  - No Aadhaar, bank details or personal identity documents are required anywhere.
 */
data class LegalProfile(
    val group: OrganisationLegalGroup,
    /** Stored as `organisationVerification.registrationType`. */
    val registrationTypeLabel: String,
    val showRegistrationNumber: Boolean,
    val registrationNumberRequired: Boolean,
    val registrationNumberLabel: String,
    val registrationNumberFormat: RegistrationNumberFormat,
    val showIssuingAuthority: Boolean,
    val issuingAuthorityRequired: Boolean,
    val issuingAuthorityLabel: String,
    val issuingAuthorityHint: String,
    val showPan: Boolean,
    val panRequired: Boolean,
    val showGst: Boolean,
    val showProprietor: Boolean,
    val proprietorRequired: Boolean,
    val showGovernmentFields: Boolean,
    val websiteRequired: Boolean,
    val registeredAddressLabel: String
)

enum class RegistrationNumberFormat { FREE_TEXT, CIN, LLPIN }

/**
 * One line of the document checklist. Satisfied when at least one uploaded document has a type in
 * [acceptedTypes] (so "PAN card OR registration certificate" is one requirement with two types).
 */
data class DocumentRequirement(
    val key: String,
    val title: String,
    val why: String,
    val acceptedTypes: List<OrganisationDocumentType>,
    val required: Boolean
)

object OrganisationRequirements {

    private val SPORTS_CREDENTIAL_TYPES = listOf(
        OrganisationDocumentType.SPORTS_AFFILIATION,
        OrganisationDocumentType.SPORTS_RECOGNITION,
        OrganisationDocumentType.SPORTS_LICENSE,
        OrganisationDocumentType.ACCREDITATION,
        OrganisationDocumentType.OTHER_SUPPORTING_DOCUMENT
    )

    fun legalProfile(type: OrganisationType): LegalProfile = when (type.group) {
        OrganisationLegalGroup.GOVERNMENT -> base(type.group, "Government body").copy(
            showRegistrationNumber = false, showIssuingAuthority = false, showPan = false, showGst = false,
            showGovernmentFields = true, websiteRequired = true,
            registeredAddressLabel = "Official government office address"
        )
        OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION -> base(type.group, "Registered sports body").copy(
            registrationNumberRequired = true,
            registrationNumberLabel = "Registration number (society / trust / company)",
            issuingAuthorityRequired = true,
            issuingAuthorityHint = "e.g. Registrar of Societies, Maharashtra"
        )
        OrganisationLegalGroup.COMPANY -> base(type.group, "Company incorporation (CIN)").copy(
            registrationNumberRequired = true, registrationNumberLabel = "CIN (Corporate Identification Number)",
            registrationNumberFormat = RegistrationNumberFormat.CIN,
            issuingAuthorityRequired = true, issuingAuthorityHint = "e.g. Registrar of Companies, Mumbai",
            panRequired = true
        )
        OrganisationLegalGroup.LLP -> base(type.group, "LLP incorporation (LLPIN)").copy(
            registrationNumberRequired = true, registrationNumberLabel = "LLPIN",
            registrationNumberFormat = RegistrationNumberFormat.LLPIN,
            issuingAuthorityRequired = true, issuingAuthorityHint = "e.g. Registrar of Companies, Bengaluru",
            panRequired = true
        )
        OrganisationLegalGroup.PARTNERSHIP -> base(type.group, "Partnership firm").copy(
            registrationNumberLabel = "Firm registration number (if registered)",
            issuingAuthorityHint = "e.g. Registrar of Firms, Pune",
            panRequired = true
        )
        OrganisationLegalGroup.SOCIETY -> base(type.group, "Society registration").copy(
            registrationNumberRequired = true, registrationNumberLabel = "Society registration number",
            issuingAuthorityRequired = true, issuingAuthorityHint = "e.g. Registrar of Societies, Delhi"
        )
        OrganisationLegalGroup.TRUST -> base(type.group, "Trust registration").copy(
            registrationNumberRequired = true, registrationNumberLabel = "Trust registration number",
            issuingAuthorityRequired = true, issuingAuthorityHint = "e.g. Charity Commissioner / Sub-Registrar"
        )
        OrganisationLegalGroup.EDUCATIONAL_INSTITUTION -> base(type.group, "Educational institution recognition").copy(
            registrationNumberRequired = true,
            registrationNumberLabel = "Recognition / affiliation number (board, university or UGC)",
            issuingAuthorityRequired = true, issuingAuthorityLabel = "Recognising board / university",
            issuingAuthorityHint = "e.g. CBSE, State Board, University of Mumbai"
        )
        OrganisationLegalGroup.SMALL_SPORTS_BUSINESS -> base(type.group, "Sports business / academy").copy(
            registrationNumberLabel = "Business registration number, if any (Udyam, Shop & Establishment)",
            issuingAuthorityHint = "e.g. Udyam, Municipal Corporation",
            showProprietor = true, proprietorRequired = true
        )
        OrganisationLegalGroup.OTHER -> base(type.group, "Other organisation").copy(
            registrationNumberLabel = "Registration number, if any",
            showProprietor = true
        )
    }

    private fun base(group: OrganisationLegalGroup, registrationType: String) = LegalProfile(
        group = group,
        registrationTypeLabel = registrationType,
        showRegistrationNumber = true,
        registrationNumberRequired = false,
        registrationNumberLabel = "Registration number",
        registrationNumberFormat = RegistrationNumberFormat.FREE_TEXT,
        showIssuingAuthority = true,
        issuingAuthorityRequired = false,
        issuingAuthorityLabel = "Issuing authority",
        issuingAuthorityHint = "",
        showPan = true,
        panRequired = false,
        showGst = true,
        showProprietor = false,
        proprietorRequired = false,
        showGovernmentFields = false,
        websiteRequired = false,
        registeredAddressLabel = "Registered address"
    )

    /** Document checklist for [type]. [gstRegistered] adds the (optional) GST certificate line. */
    fun documents(type: OrganisationType, gstRegistered: Boolean): List<DocumentRequirement> = buildList {
        when (type.group) {
            OrganisationLegalGroup.GOVERNMENT -> {
                add(authority(
                    "Government authorisation / order / official letter",
                    "Proves the department exists and that the officer is authorised to represent it on Athlink.",
                    listOf(OrganisationDocumentType.GOVERNMENT_ORDER, OrganisationDocumentType.AUTHORIZATION_LETTER)
                ))
                add(DocumentRequirement(
                    "sports", "Sports mandate or recognition (optional)",
                    "Any notification showing the body's sports responsibilities speeds up review.",
                    listOf(OrganisationDocumentType.SPORTS_RECOGNITION, OrganisationDocumentType.OTHER_SUPPORTING_DOCUMENT),
                    required = false
                ))
            }
            OrganisationLegalGroup.FEDERATION_OR_ASSOCIATION -> {
                add(registration("Registration certificate", "Proves the body is legally registered."))
                add(standardAuthority())
                add(DocumentRequirement(
                    "sports", "Affiliation or recognition",
                    "Shows the parent federation, sports authority or Olympic association recognises this body.",
                    listOf(
                        OrganisationDocumentType.SPORTS_AFFILIATION,
                        OrganisationDocumentType.SPORTS_RECOGNITION,
                        OrganisationDocumentType.ACCREDITATION
                    ),
                    required = true
                ))
            }
            OrganisationLegalGroup.COMPANY, OrganisationLegalGroup.LLP -> {
                add(DocumentRequirement(
                    "incorporation", "Certificate of incorporation",
                    "Proves the company / LLP is incorporated with the Registrar of Companies.",
                    listOf(OrganisationDocumentType.INCORPORATION_CERTIFICATE), required = true
                ))
                add(pan(required = true))
                add(standardAuthority("Board resolution or authorisation letter"))
                add(sportsCredential())
            }
            OrganisationLegalGroup.PARTNERSHIP -> {
                add(registration("Partnership deed or firm registration certificate", "Proves the partnership exists."))
                add(pan(required = true))
                add(standardAuthority())
                add(sportsCredential())
            }
            OrganisationLegalGroup.SOCIETY -> {
                add(registration("Society registration certificate", "Proves the society is registered with the Registrar of Societies."))
                add(standardAuthority("Committee resolution or authorisation letter"))
                add(sportsCredential())
            }
            OrganisationLegalGroup.TRUST -> {
                add(registration("Trust deed / registration certificate", "Proves the trust is registered."))
                add(standardAuthority("Trustee resolution or authorisation letter"))
                add(sportsCredential())
            }
            OrganisationLegalGroup.EDUCATIONAL_INSTITUTION -> {
                add(registration("Recognition / affiliation certificate", "Proves the school or college is recognised by its board or university."))
                add(standardAuthority("Authorisation letter from the principal / registrar"))
                add(sportsCredential(required = false))
            }
            OrganisationLegalGroup.SMALL_SPORTS_BUSINESS, OrganisationLegalGroup.OTHER -> {
                add(DocumentRequirement(
                    "business_identity", "PAN or any business registration",
                    "Proves the academy or club exists as a business. A Udyam / Shop & Establishment certificate or the PAN card is enough.",
                    listOf(OrganisationDocumentType.PAN_DOCUMENT, OrganisationDocumentType.REGISTRATION_CERTIFICATE),
                    required = true
                ))
                add(DocumentRequirement(
                    "address", "Address proof of the facility",
                    "Proves where the organisation operates (utility bill, rent agreement or property tax receipt).",
                    listOf(OrganisationDocumentType.ADDRESS_PROOF), required = true
                ))
                add(standardAuthority("Declaration of authority or authorisation letter"))
                add(sportsCredential())
            }
        }
        if (gstRegistered && type.group != OrganisationLegalGroup.GOVERNMENT) {
            add(DocumentRequirement(
                "gst", "GST registration certificate (optional)",
                "Helps us match your GSTIN quickly.",
                listOf(OrganisationDocumentType.GST_DOCUMENT), required = false
            ))
        }
    }

    /** The checklist key whose documents prove the representative's authority. */
    const val AUTHORITY_KEY = "authority"

    private fun authority(title: String, why: String, types: List<OrganisationDocumentType>) =
        DocumentRequirement(AUTHORITY_KEY, title, why, types, required = true)

    private fun standardAuthority(title: String = "Authorisation letter") = authority(
        title,
        "Proves that you are authorised to represent this organisation.",
        listOf(OrganisationDocumentType.AUTHORIZATION_LETTER)
    )

    private fun registration(title: String, why: String) = DocumentRequirement(
        "registration", title, why, listOf(OrganisationDocumentType.REGISTRATION_CERTIFICATE), required = true
    )

    private fun pan(required: Boolean) = DocumentRequirement(
        "pan", "PAN card / PAN allotment letter", "Confirms the PAN you entered belongs to this organisation.",
        listOf(OrganisationDocumentType.PAN_DOCUMENT), required
    )

    private fun sportsCredential(required: Boolean = true) = DocumentRequirement(
        "sports", if (required) "Sports credential" else "Sports credential (optional)",
        "Shows you are a genuine sports organisation. Federation affiliation is NOT required: academy " +
            "registration, a sports licence, accreditation, a tournament-organiser record or similar proof also works.",
        SPORTS_CREDENTIAL_TYPES, required
    )

    /** Required checklist lines that no uploaded document satisfies. */
    fun missingDocuments(
        type: OrganisationType,
        gstRegistered: Boolean,
        uploaded: List<OrganisationDocument>
    ): List<DocumentRequirement> {
        val uploadedTypes = uploaded.mapNotNull { it.type }.toSet()
        return documents(type, gstRegistered).filter { req ->
            req.required && req.acceptedTypes.none { it in uploadedTypes }
        }
    }

    /** Authorisation evidence options that make sense for [type]. */
    fun evidenceOptions(type: OrganisationType): List<AuthorisationEvidenceType> = when (type.group) {
        OrganisationLegalGroup.GOVERNMENT -> listOf(
            AuthorisationEvidenceType.GOVERNMENT_AUTHORISATION_LETTER,
            AuthorisationEvidenceType.GOVERNMENT_ORDER,
            AuthorisationEvidenceType.OFFICIAL_DEPARTMENT_LETTER,
            AuthorisationEvidenceType.OTHER_OFFICIAL_PROOF
        )
        OrganisationLegalGroup.SMALL_SPORTS_BUSINESS, OrganisationLegalGroup.OTHER, OrganisationLegalGroup.PARTNERSHIP -> listOf(
            AuthorisationEvidenceType.PROPRIETOR_DECLARATION,
            AuthorisationEvidenceType.LETTERHEAD_AUTHORISATION_LETTER,
            AuthorisationEvidenceType.APPOINTMENT_LETTER,
            AuthorisationEvidenceType.ORGANISATION_ID_WITH_AUTHORISATION,
            AuthorisationEvidenceType.OTHER_OFFICIAL_PROOF
        )
        else -> listOf(
            AuthorisationEvidenceType.LETTERHEAD_AUTHORISATION_LETTER,
            AuthorisationEvidenceType.BOARD_RESOLUTION,
            AuthorisationEvidenceType.APPOINTMENT_LETTER,
            AuthorisationEvidenceType.ORGANISATION_ID_WITH_AUTHORISATION,
            AuthorisationEvidenceType.OTHER_OFFICIAL_PROOF
        )
    }

    fun relationshipOptions(type: OrganisationType): List<RepresentativeRelationship> =
        if (type.isGovernment) listOf(RepresentativeRelationship.GOVERNMENT_OFFICER, RepresentativeRelationship.OTHER)
        else RepresentativeRelationship.entries.filter { it != RepresentativeRelationship.GOVERNMENT_OFFICER }
}
