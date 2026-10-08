package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrganisationDraftTest {

    private fun valid(type: OrganisationType) = OrganisationDraft(
        legalName = "Andheri Sports Academy",
        displayName = "ASA Mumbai",
        organisationType = type,
        primarySport = "Cricket",
        otherSports = listOf("Football", "Cricket"),
        description = "Grassroots cricket and football coaching for ages 8-18 in Andheri.",
        officialEmail = "contact@asa.org.in",
        officialPhone = "+91 22 2345 6789",
        website = "https://asa.org.in",
        yearEstablished = "2015",
        registrationNumber = when (OrganisationRequirements.legalProfile(type).registrationNumberFormat) {
            RegistrationNumberFormat.CIN -> "U92490MH2015PTC123456"
            RegistrationNumberFormat.LLPIN -> "AAB-1234"
            RegistrationNumberFormat.FREE_TEXT -> "MH/2015/0042"
        },
        issuingAuthority = "Registrar",
        pan = "AAAPA1234A",
        registeredAddress = "12 Link Road, Andheri West, Mumbai 400053",
        proprietorName = "R. Kulkarni",
        governmentDepartmentName = "Department of Sports & Youth Services",
        governmentLevel = GovernmentLevel.STATE,
        ministryOrDepartment = "Sports Department, Govt of Maharashtra",
        repFullName = "Ravi Kulkarni",
        repDesignation = "Director",
        repDepartment = "Sports Development",
        repEmail = "ravi@asa.org.in",
        repPhone = "9876543210",
        repRelationship = OrganisationRequirements.relationshipOptions(type).first(),
        repEvidenceType = OrganisationRequirements.evidenceOptions(type).first(),
        organisationLevel = OrganisationLevel.DISTRICT,
        state = "Maharashtra", district = "Mumbai Suburban", city = "Mumbai", locality = "Andheri West",
        pincode = "400053", facilityAddress = "ASA Ground, Link Road",
        declarationAccepted = true
    )

    private fun docsFor(type: OrganisationType, gst: Boolean = false): List<OrganisationDocument> =
        OrganisationRequirements.documents(type, gst).filter { it.required }
            .mapIndexed { i, r -> OrganisationDocument(documentId = "d$i", documentType = r.acceptedTypes.first().name) }

    @Test
    fun everyTypeHasAConsistentValidPath() {
        for (type in OrganisationType.entries) {
            val draft = valid(type)
            val errors = draft.validate(OnboardingStep.REVIEW, docsFor(type))
            assertTrue("$type: $errors", errors.isEmpty())
            assertNull(draft.firstInvalidStep(docsFor(type)))
        }
    }

    @Test
    fun governmentSeesGovernmentFieldsAndNoCinPanGst() { // TEST 8
        val p = OrganisationRequirements.legalProfile(OrganisationType.GOVERNMENT_SPORTS_DEPARTMENT)
        assertTrue(p.showGovernmentFields)
        assertFalse(p.showPan)
        assertFalse(p.showGst)
        assertFalse(p.showRegistrationNumber)
        assertTrue(p.websiteRequired)
        val draft = valid(OrganisationType.GOVERNMENT_SPORTS_DEPARTMENT).copy(
            governmentDepartmentName = "", governmentLevel = null, ministryOrDepartment = "", repDepartment = "",
            website = "", pan = "", registrationNumber = ""
        )
        val legal = draft.validate(OnboardingStep.LEGAL)
        assertTrue(OrgField.GOVERNMENT_DEPARTMENT_NAME in legal)
        assertTrue(OrgField.GOVERNMENT_LEVEL in legal)
        assertTrue(OrgField.MINISTRY in legal)
        assertFalse(OrgField.PAN in legal)
        assertFalse(OrgField.REGISTRATION_NUMBER in legal)
        assertTrue(OrgField.WEBSITE in draft.validate(OnboardingStep.IDENTITY))
        assertTrue(OrgField.REP_DEPARTMENT in draft.validate(OnboardingStep.REPRESENTATIVE))
        // A proprietor declaration is not acceptable authority for a government body.
        assertTrue(OrgField.REP_EVIDENCE in draft.copy(repEvidenceType = AuthorisationEvidenceType.PROPRIETOR_DECLARATION)
            .validate(OnboardingStep.REPRESENTATIVE))
        // Non-applicable fields are written empty, never leaked from a previous type.
        val fields = draft.copy(pan = "AAAPA1234A").verificationFields("o1")
        assertEquals("", fields["pan"])
        assertEquals("", fields["gstin"])
        // Government authority proof is a government order or letter.
        val req = OrganisationRequirements.documents(OrganisationType.GOVERNMENT_SPORTS_DEPARTMENT, false)
        assertTrue(req.any { it.required && OrganisationDocumentType.GOVERNMENT_ORDER in it.acceptedTypes })
        assertTrue(req.none { it.required && OrganisationDocumentType.PAN_DOCUMENT in it.acceptedTypes })
    }

    @Test
    fun companySeesCompanyFields() { // TEST 9
        val p = OrganisationRequirements.legalProfile(OrganisationType.COMPANY_PRIVATE_LIMITED)
        assertTrue(p.registrationNumberRequired)
        assertEquals(RegistrationNumberFormat.CIN, p.registrationNumberFormat)
        assertTrue(p.panRequired)
        assertFalse(p.showGovernmentFields)
        val bad = valid(OrganisationType.COMPANY_PRIVATE_LIMITED).copy(registrationNumber = "12345", pan = "")
        val errors = bad.validate(OnboardingStep.LEGAL)
        assertTrue(OrgField.REGISTRATION_NUMBER in errors)
        assertTrue(OrgField.PAN in errors)
        val missing = OrganisationRequirements.missingDocuments(OrganisationType.COMPANY_PRIVATE_LIMITED, false, emptyList()).map { it.key }
        assertTrue("incorporation" in missing)
        assertTrue("pan" in missing)
    }

    @Test
    fun academyGetsLightBusinessPath() { // TEST 10
        val type = OrganisationType.SPORTS_ACADEMY
        val p = OrganisationRequirements.legalProfile(type)
        assertFalse(p.registrationNumberRequired)
        assertFalse(p.panRequired)
        assertTrue(p.proprietorRequired)
        val draft = valid(type).copy(registrationNumber = "", issuingAuthority = "", pan = "")
        assertTrue(draft.validate(OnboardingStep.LEGAL).isEmpty())
        // No federation affiliation needed: any sports credential (even "other") satisfies the sports line.
        val docs = listOf(
            OrganisationDocument(documentType = OrganisationDocumentType.REGISTRATION_CERTIFICATE.name),
            OrganisationDocument(documentType = OrganisationDocumentType.ADDRESS_PROOF.name),
            OrganisationDocument(documentType = OrganisationDocumentType.AUTHORIZATION_LETTER.name),
            OrganisationDocument(documentType = OrganisationDocumentType.OTHER_SUPPORTING_DOCUMENT.name)
        )
        assertTrue(OrganisationRequirements.missingDocuments(type, false, docs).isEmpty())
        assertTrue(draft.copy(hasFederationAffiliation = false).validate(OnboardingStep.SPORTS).isEmpty())
        assertTrue(OrgField.GOVERNING_BODY in draft.copy(hasFederationAffiliation = true).validate(OnboardingStep.SPORTS))
    }

    @Test
    fun gstinNotRequiredUnlessRegistered() { // TEST 11
        val draft = valid(OrganisationType.COMPANY_PRIVATE_LIMITED).copy(gstRegistered = false, gstin = "")
        assertFalse(OrgField.GSTIN in draft.validate(OnboardingStep.LEGAL))
        assertTrue(OrgField.GSTIN in draft.copy(gstRegistered = true).validate(OnboardingStep.LEGAL))
        assertFalse(OrgField.GSTIN in draft.copy(gstRegistered = true, gstin = "27AAAPA1234A1Z5").validate(OnboardingStep.LEGAL))
        // GSTIN must contain the PAN.
        assertTrue(OrgField.GSTIN in draft.copy(gstRegistered = true, gstin = "27AAAPB1234A1Z5").validate(OnboardingStep.LEGAL))
        // The GST certificate is never a required document.
        assertTrue(OrganisationRequirements.documents(OrganisationType.COMPANY_PRIVATE_LIMITED, true).filter { it.key == "gst" }.none { it.required })
    }

    @Test
    fun requiredDocumentsEnforced() { // TEST 7
        val type = OrganisationType.SOCIETY
        val draft = valid(type)
        assertTrue(OrgField.DOCUMENTS in draft.validate(OnboardingStep.DOCUMENTS, emptyList()))
        assertTrue(OrgField.DOCUMENTS in draft.validate(OnboardingStep.REVIEW, docsFor(type).drop(1)))
        assertTrue(draft.validate(OnboardingStep.DOCUMENTS, docsFor(type)).isEmpty())
        assertEquals(OnboardingStep.DOCUMENTS, draft.firstInvalidStep(emptyList()))
    }

    @Test
    fun declarationRequiredForSubmit() {
        val type = OrganisationType.TRUST_NGO
        assertTrue(OrgField.DECLARATION in valid(type).copy(declarationAccepted = false).validate(OnboardingStep.REVIEW, docsFor(type)))
    }

    @Test
    fun mappedFieldsNeverContainAdminFields() { // TEST 12 (client side)
        val adminFields = setOf(
            "verificationStatus", "verificationLevel", "verifiedAt", "verificationExpiresAt", "reviewedBy", "reviewedAt",
            "rejectionReason", "reviewNotes", "status", "contactVerified", "legalEntityVerified", "representativeVerified",
            "addressVerified", "sportsCredentialVerified", "submittedAt", "createdAt", "updatedAt", "verificationStatus"
        )
        val d = valid(OrganisationType.SPORTS_CLUB)
        val all = d.organisationFields("o1").keys + d.verificationFields("o1").keys +
            d.representativeFields("o1", "doc1").keys + d.affiliationFields("o1").keys
        assertTrue((all intersect adminFields).toString(), (all intersect adminFields).isEmpty())
    }

    @Test
    fun mappingNormalisesValues() {
        val d = valid(OrganisationType.SPORTS_CLUB).copy(pan = "aaapa 1234a", officialPhone = "+91 22-2345 6789", publicAddress = "")
        assertEquals("AAAPA1234A", d.verificationFields("o1")["pan"])
        assertEquals("+912223456789", d.verificationFields("o1")["officialPhone"])
        assertEquals(listOf("Cricket", "Football"), d.organisationFields("o1")["sports"])
        assertEquals("Andheri West, Mumbai", d.organisationFields("o1")["publicAddress"])
        assertEquals(2015, d.organisationFields("o1")["yearEstablished"])
    }

    @Test
    fun resumeFromSnapshotPrefills() { // TEST 4 (round trip)
        val org = Organisation(organisationId = "o1", displayName = "ASA", sports = listOf("Cricket", "Hockey"), organisationType = "SOCIETY", city = "Pune")
        val v = OrganisationVerification(officialEmail = "", pan = "AAAPA1234A", pincode = "411001")
        val d = OrganisationDraft.fromSnapshot(OrganisationSnapshot(org, v, null, null, emptyList()), "Account", "acct@x.org")
        assertEquals("ASA", d.displayName)
        assertEquals(OrganisationType.SOCIETY, d.organisationType)
        assertEquals("Cricket", d.primarySport)
        assertEquals(listOf("Hockey"), d.otherSports)
        assertEquals("acct@x.org", d.officialEmail)
        assertEquals("411001", d.pincode)
        assertFalse(d.declarationAccepted)
        val fresh = OrganisationDraft.fromSnapshot(null, "New Org", "new@x.org")
        assertEquals("New Org", fresh.displayName)
        assertNull(fresh.organisationType)
    }
}
