package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.OnboardingStep
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationRequirements
import com.athlink.app.data.model.OrganisationValidators
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*

/** STEP 7: review everything, verify the account email, accept the declaration, submit. */
@Composable
fun OrganisationReviewStep(
    ctx: StepContext,
    documents: List<OrganisationDocument>,
    emailVerified: Boolean,
    accountEmail: String,
    onEdit: (OnboardingStep) -> Unit,
    onSendVerificationEmail: () -> Unit,
    onRefreshEmail: () -> Unit
) {
    val d = ctx.draft
    val type = d.organisationType
    val profile = d.legalProfile
    val incomplete = OnboardingStep.entries.filter { it != OnboardingStep.REVIEW && d.validate(it, documents).isNotEmpty() }

    // ── Contact verification (level 1) ─────────────────────────────────
    FormSection("1. Verify your account email", "Required before you can submit.") {
        if (emailVerified) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MarkEmailRead, null, tint = AthlinkGreen)
                Spacer(Modifier.width(8.dp))
                Text("$accountEmail is verified", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("We sent a verification link to $accountEmail. Open it, then tap \"I've verified\".", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSendVerificationEmail, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkOrange)) { Text("Resend email") }
                Button(onClick = onRefreshEmail, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AthlinkOrange)) { Text("I've verified") }
            }
        }
    }

    // ── Completeness ───────────────────────────────────────────────────
    if (incomplete.isNotEmpty()) {
        FormSection("2. Finish these steps", null) {
            incomplete.forEach { step ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(step) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AthlinkRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(step.title, modifier = Modifier.weight(1f))
                    Text("Fix", color = AthlinkOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── Summary ────────────────────────────────────────────────────────
    ReviewSection("Identity", OnboardingStep.IDENTITY, onEdit) {
        KeyValueRow("Legal name", d.legalName)
        KeyValueRow("Display name", d.displayName)
        KeyValueRow("Type", type?.label.orEmpty())
        KeyValueRow("Sports", d.sports.joinToString(", "))
        KeyValueRow("Official email", d.officialEmail)
        KeyValueRow("Official phone", d.officialPhone)
        KeyValueRow("Website", d.website)
        if (d.isGovernment && OrganisationValidators.isFreeMail(d.officialEmail)) {
            InfoBanner("Free email domain on a government application. Expect a slower review.", Icons.Default.Warning, AthlinkOrange)
        }
    }
    ReviewSection("Legal / registration", OnboardingStep.LEGAL, onEdit) {
        if (profile?.showGovernmentFields == true) {
            KeyValueRow("Department", d.governmentDepartmentName)
            KeyValueRow("Level", d.governmentLevel?.label.orEmpty())
            KeyValueRow("Ministry", d.ministryOrDepartment)
            KeyValueRow("Reference no.", d.authorisationReferenceNumber)
        } else if (profile != null) {
            KeyValueRow("Registration", profile.registrationTypeLabel)
            if (profile.showRegistrationNumber) KeyValueRow("Number", d.registrationNumber)
            if (profile.showIssuingAuthority) KeyValueRow("Issued by", d.issuingAuthority)
            if (profile.showPan) KeyValueRow("PAN", d.pan.uppercase())
            if (profile.showGst) KeyValueRow("GSTIN", if (d.gstRegistered) d.gstin.uppercase() else "Not applicable")
            if (profile.showProprietor) KeyValueRow("Proprietor", d.proprietorName)
        }
        KeyValueRow("Address", d.registeredAddress)
    }
    ReviewSection("Representative", OnboardingStep.REPRESENTATIVE, onEdit) {
        KeyValueRow("Name", d.repFullName)
        KeyValueRow("Designation", d.repDesignation)
        KeyValueRow("Email", d.repEmail)
        KeyValueRow("Relationship", d.repRelationship?.label.orEmpty())
        KeyValueRow("Proof", d.repEvidenceType?.label.orEmpty())
    }
    ReviewSection("Sports", OnboardingStep.SPORTS, onEdit) {
        KeyValueRow("Level", d.organisationLevel?.label.orEmpty())
        KeyValueRow("Affiliation", if (d.hasFederationAffiliation) "${d.governingBodyName} ${d.affiliationNumber}".trim() else "None (not required)")
        KeyValueRow("Recognition", d.accreditationDetails)
    }
    ReviewSection("Location", OnboardingStep.LOCATION, onEdit) {
        KeyValueRow("Public location", d.resolvedPublicAddress())
        KeyValueRow("District / State", listOf(d.district, d.state).filter { it.isNotBlank() }.joinToString(", "))
        KeyValueRow("PIN", d.pincode)
    }
    ReviewSection("Documents", OnboardingStep.DOCUMENTS, onEdit) {
        if (type != null) {
            val missing = OrganisationRequirements.missingDocuments(type, d.gstRegistered, documents).map { it.key }.toSet()
            OrganisationRequirements.documents(type, d.gstRegistered).filter { it.required }.forEach { req ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    val ok = req.key !in missing
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (ok) AthlinkGreen else AthlinkRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(req.title, fontSize = 13.sp)
                }
            }
        }
        Text("${documents.size} file(s) uploaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // ── Declaration ────────────────────────────────────────────────────
    FormSection("Declaration", null) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = d.declarationAccepted,
                onCheckedChange = { c -> ctx.update(OrgField.DECLARATION) { it.copy(declarationAccepted = c) } },
                enabled = ctx.enabled,
                colors = CheckboxDefaults.colors(checkedColor = AthlinkOrange)
            )
            Text(
                "I confirm the information and documents are genuine, and that I'm authorised to represent " +
                    "${d.legalName.ifBlank { "this organisation" }}. I understand Athlink reviews them manually and may " +
                    "reject or suspend the account if anything is false.",
                fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
            )
        }
        FieldError(ctx.error(OrgField.DECLARATION))
    }
}

@Composable
private fun ReviewSection(title: String, step: OnboardingStep, onEdit: (OnboardingStep) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    FormSection(title, null) {
        content()
        TextButton(onClick = { onEdit(step) }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit", color = AthlinkOrange)
        }
    }
}
