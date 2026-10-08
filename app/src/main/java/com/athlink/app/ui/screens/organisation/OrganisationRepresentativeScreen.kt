package com.athlink.app.ui.screens.organisation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationRequirements
import com.athlink.app.ui.components.*

/** STEP 3: authorised representative (proof that the person signing up may act for the organisation). */
@Composable
fun OrganisationRepresentativeStep(ctx: StepContext) {
    val d = ctx.draft
    val type = d.organisationType
    val gov = d.isGovernment

    FormSection(
        if (gov) "Authorised officer" else "Authorised representative",
        "The person who manages this organisation on Athlink."
    ) {
        ctx.Input(d.repFullName, if (gov) "Officer full name" else "Full name", OrgField.REP_FULL_NAME,
            icon = Icons.Default.Person, maxLength = 80) { x, v -> x.copy(repFullName = v) }
        ctx.Input(d.repDesignation, if (gov) "Officer designation" else "Designation", OrgField.REP_DESIGNATION,
            icon = Icons.Default.WorkOutline, maxLength = 80, hint = if (gov) "e.g. District Sports Officer" else "e.g. Director, Secretary, Head Coach"
        ) { x, v -> x.copy(repDesignation = v) }
        if (gov) {
            ctx.Input(d.repDepartment, "Department", OrgField.REP_DEPARTMENT, icon = Icons.Default.Domain, maxLength = 120) { x, v -> x.copy(repDepartment = v) }
        }
        ctx.Input(d.repEmail, "Official email", OrgField.REP_EMAIL, kind = InputKind.EMAIL, icon = Icons.Default.Email,
            hint = "Your work email at the organisation") { x, v -> x.copy(repEmail = v) }
        ctx.Input(d.repPhone, "Official phone", OrgField.REP_PHONE, kind = InputKind.PHONE, icon = Icons.Default.Phone, maxLength = 20) { x, v -> x.copy(repPhone = v) }
    }

    FormSection("Your authority", "How you're connected to the organisation, and what proves it.") {
        if (type == null) {
            InfoBanner("Choose your organisation type in step 1 first.")
        } else {
            DropdownField(
                label = fieldLabel("Relationship with organisation", true),
                options = OrganisationRequirements.relationshipOptions(type),
                selected = d.repRelationship,
                optionLabel = { it.label },
                onSelect = { r -> ctx.update(OrgField.REP_RELATIONSHIP) { it.copy(repRelationship = r) } },
                leadingIcon = Icons.Default.Link,
                errorMessage = ctx.error(OrgField.REP_RELATIONSHIP),
                enabled = ctx.enabled
            )
            DropdownField(
                label = fieldLabel("Proof of authority you'll upload", true),
                options = OrganisationRequirements.evidenceOptions(type),
                selected = d.repEvidenceType,
                optionLabel = { it.label },
                onSelect = { e -> ctx.update(OrgField.REP_EVIDENCE) { it.copy(repEvidenceType = e) } },
                leadingIcon = Icons.Default.VerifiedUser,
                errorMessage = ctx.error(OrgField.REP_EVIDENCE),
                enabled = ctx.enabled
            )
            InfoBanner("You'll upload this document in the Documents step.")
        }
        WhyWeNeedThis(
            "Anyone can type an organisation's name. The authorisation document shows the organisation itself has allowed you " +
                "to represent it, which stops impersonation. We don't need your home address or personal ID."
        )
    }
}
