package com.athlink.app.ui.screens.organisation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import com.athlink.app.data.model.GovernmentLevel
import com.athlink.app.data.model.OrgField
import com.athlink.app.ui.components.*

/**
 * STEP 2: legal / registration. Fields are shown and required according to
 * [com.athlink.app.data.model.OrganisationRequirements.legalProfile] for the selected type.
 */
@Composable
fun OrganisationLegalStep(ctx: StepContext) {
    val d = ctx.draft
    val profile = d.legalProfile
    if (profile == null) {
        InfoBanner("Choose your organisation type in step 1 first. The legal details depend on it.")
        return
    }

    if (profile.showGovernmentFields) {
        FormSection("Government organisation", "We cross-check these details against official government sources.") {
            ctx.Input(d.governmentDepartmentName, "Government organisation / department name", OrgField.GOVERNMENT_DEPARTMENT_NAME,
                icon = Icons.Default.AccountBalance) { x, v -> x.copy(governmentDepartmentName = v) }
            DropdownField(
                label = fieldLabel("Government level", true),
                options = GovernmentLevel.entries,
                selected = d.governmentLevel,
                optionLabel = { it.label },
                onSelect = { l -> ctx.update(OrgField.GOVERNMENT_LEVEL) { it.copy(governmentLevel = l) } },
                leadingIcon = Icons.Default.Layers,
                errorMessage = ctx.error(OrgField.GOVERNMENT_LEVEL),
                enabled = ctx.enabled
            )
            ctx.Input(d.ministryOrDepartment, "Ministry / parent department", OrgField.MINISTRY,
                icon = Icons.Default.Domain) { x, v -> x.copy(ministryOrDepartment = v) }
            ctx.Input(d.authorisationReferenceNumber, "Authorisation / order reference number", OrgField.AUTHORISATION_REFERENCE,
                required = false, kind = InputKind.TEXT, icon = Icons.Default.Tag, maxLength = 60) { x, v -> x.copy(authorisationReferenceNumber = v) }
            WhyWeNeedThis("An uploaded letter alone isn't enough for the Official Government badge. Reviewers match the department, level and reference number with the official website and contact details you gave.")
        }
    } else {
        FormSection("Registration", profile.registrationTypeLabel) {
            if (profile.showRegistrationNumber) {
                ctx.Input(d.registrationNumber, profile.registrationNumberLabel, OrgField.REGISTRATION_NUMBER,
                    required = profile.registrationNumberRequired, kind = InputKind.CAPS, icon = Icons.Default.Numbers, maxLength = 50
                ) { x, v -> x.copy(registrationNumber = v) }
            }
            if (profile.showIssuingAuthority) {
                ctx.Input(d.issuingAuthority, profile.issuingAuthorityLabel, OrgField.ISSUING_AUTHORITY,
                    required = profile.issuingAuthorityRequired, icon = Icons.Default.Gavel,
                    hint = profile.issuingAuthorityHint.ifBlank { null }) { x, v -> x.copy(issuingAuthority = v) }
            }
            if (profile.showProprietor) {
                ctx.Input(d.proprietorName, "Proprietor / owner name", OrgField.PROPRIETOR_NAME,
                    required = profile.proprietorRequired, icon = Icons.Default.Person,
                    hint = "For academies and clubs run by an individual") { x, v -> x.copy(proprietorName = v) }
            }
        }

        if (profile.showPan || profile.showGst) {
            FormSection("Tax identifiers", "Only the organisation's identifiers, never personal ones.") {
                if (profile.showPan) {
                    ctx.Input(d.pan, "Organisation PAN", OrgField.PAN, required = profile.panRequired, kind = InputKind.CAPS,
                        icon = Icons.Default.Badge, maxLength = 10, hint = "10 characters, e.g. AAAPA1234A") { x, v -> x.copy(pan = v.uppercase()) }
                }
                if (profile.showGst) {
                    ToggleRow(
                        title = "GST registered",
                        description = "Leave off if GST doesn't apply to you. It's never required otherwise.",
                        checked = d.gstRegistered,
                        onCheckedChange = { on -> ctx.update(OrgField.GSTIN) { it.copy(gstRegistered = on, gstin = if (on) it.gstin else "") } },
                        enabled = ctx.enabled
                    )
                    if (d.gstRegistered) {
                        ctx.Input(d.gstin, "GSTIN", OrgField.GSTIN, kind = InputKind.CAPS, icon = Icons.Default.Receipt, maxLength = 15,
                            hint = "15 characters, e.g. 27AAAPA1234A1Z5") { x, v -> x.copy(gstin = v.uppercase()) }
                    }
                }
                WhyWeNeedThis("PAN / GSTIN let reviewers match your documents to the organisation. We don't collect Aadhaar or any personal tax IDs.")
            }
        }
    }

    FormSection(profile.registeredAddressLabel, "Private: used only for verification, never shown publicly.") {
        ctx.Input(d.registeredAddress, profile.registeredAddressLabel, OrgField.REGISTERED_ADDRESS, kind = InputKind.SENTENCES,
            multiline = true, icon = Icons.Default.Home) { x, v -> x.copy(registeredAddress = v) }
    }
}
