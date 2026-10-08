package com.athlink.app.ui.screens.organisation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationLegalGroup
import com.athlink.app.data.model.OrganisationType
import com.athlink.app.data.model.OrganisationValidators
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.Sports
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*

/** STEP 1: organisation identity. */
@Composable
fun OrganisationIdentityStep(
    ctx: StepContext,
    logoUploading: Boolean,
    onPickLogo: (PickedFile) -> Unit
) {
    val d = ctx.draft
    val gov = d.isGovernment
    val context = LocalContext.current
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickLogo(readPickedFile(context, uri))
    }

    FormSection("Organisation identity", "This is how your organisation appears on Athlink.") {
        ctx.Input(d.legalName, "Legal organisation name", OrgField.LEGAL_NAME, icon = Icons.Default.Gavel,
            hint = "Exactly as on your registration documents") { x, v -> x.copy(legalName = v) }
        ctx.Input(d.displayName, "Public / display name", OrgField.DISPLAY_NAME, icon = Icons.Default.Business,
            hint = "Shown to players on your events") { x, v -> x.copy(displayName = v) }

        DropdownField(
            label = fieldLabel("Organisation type", true),
            options = OrganisationType.entries,
            selected = d.organisationType,
            optionLabel = { it.label },
            onSelect = { t -> ctx.update(OrgField.ORGANISATION_TYPE) { it.copy(organisationType = t) } },
            leadingIcon = Icons.Default.Category,
            errorMessage = ctx.error(OrgField.ORGANISATION_TYPE),
            enabled = ctx.enabled
        )
        d.organisationType?.let { t ->
            InfoBanner(
                when (t.group) {
                    OrganisationLegalGroup.GOVERNMENT -> "Government bodies verify with an official order or authorisation letter. No PAN, CIN or GST needed."
                    OrganisationLegalGroup.SMALL_SPORTS_BUSINESS -> "Academies and clubs don't need company incorporation or federation affiliation. Address proof plus PAN or any business registration is enough."
                    OrganisationLegalGroup.COMPANY, OrganisationLegalGroup.LLP -> "Companies and LLPs verify with their incorporation certificate and PAN."
                    else -> "We'll only ask for the documents that apply to a ${t.label.lowercase()}."
                }
            )
        }
    }

    FormSection("Sports", "Pick your main sport first.") {
        DropdownField(
            label = fieldLabel("Primary sport", true),
            options = Sports.ALL,
            selected = d.primarySport.ifBlank { null },
            optionLabel = { it },
            onSelect = { s -> ctx.update(OrgField.PRIMARY_SPORT) { it.copy(primarySport = s, otherSports = it.otherSports - s) } },
            leadingIcon = Icons.Default.SportsSoccer,
            errorMessage = ctx.error(OrgField.PRIMARY_SPORT),
            enabled = ctx.enabled
        )
        Text("Other sports (optional)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        MultiSelectChips(
            options = Sports.ALL.filter { it != d.primarySport },
            selected = d.otherSports,
            onToggle = { s -> ctx.update(null) { it.copy(otherSports = if (s in it.otherSports) it.otherSports - s else it.otherSports + s) } },
            enabled = ctx.enabled
        )
    }

    FormSection("About", null) {
        ctx.Input(d.description, "Short description", OrgField.DESCRIPTION, kind = InputKind.SENTENCES, multiline = true,
            icon = Icons.Default.Description, maxLength = OrganisationValidators.MAX_DESCRIPTION,
            hint = "${d.description.length}/${OrganisationValidators.MAX_DESCRIPTION} · what you run, for whom") { x, v -> x.copy(description = v) }
        ctx.Input(d.yearEstablished, "Year established", OrgField.YEAR_ESTABLISHED, required = false, kind = InputKind.NUMBER,
            icon = Icons.Default.CalendarMonth, maxLength = 4) { x, v -> x.copy(yearEstablished = v.filter { it.isDigit() }) }
    }

    FormSection(if (gov) "Official government contact" else "Official contact", "Use the organisation's own contact details, not personal ones.") {
        ctx.Input(d.officialEmail, if (gov) "Official government email" else "Official organisation email", OrgField.OFFICIAL_EMAIL,
            kind = InputKind.EMAIL, icon = Icons.Default.Email) { x, v -> x.copy(officialEmail = v) }
        if (gov && d.officialEmail.contains('@') && OrganisationValidators.isFreeMail(d.officialEmail)) {
            InfoBanner("Government verification normally needs an official address (e.g. .gov.in / .nic.in). A personal mailbox will slow down review.",
                Icons.Default.Warning, AthlinkOrange)
        }
        ctx.Input(d.officialPhone, if (gov) "Official office phone" else "Official phone number", OrgField.OFFICIAL_PHONE,
            kind = InputKind.PHONE, icon = Icons.Default.Phone, maxLength = 20) { x, v -> x.copy(officialPhone = v) }
        ctx.Input(d.website, if (gov) "Official government website" else "Website", OrgField.WEBSITE,
            required = d.legalProfile?.websiteRequired == true, kind = InputKind.URL, icon = Icons.Default.Language) { x, v -> x.copy(website = v.trim()) }
        WhyWeNeedThis("Reviewers contact the organisation through these official channels to confirm it exists and that you represent it. They're kept private and are not shown to players.")
    }

    FormSection("Logo", "Optional. JPG, PNG or WebP under 2 MB.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                if (d.logoUrl.isNotBlank()) {
                    AsyncImage(model = d.logoUrl, contentDescription = "Organisation logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(d.displayName.take(2).uppercase().ifBlank { "OR" }, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(
                onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !logoUploading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkOrange)
            ) {
                if (logoUploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AthlinkOrange)
                else Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (d.logoUrl.isBlank()) "Choose logo" else "Change logo")
            }
        }
    }
}
