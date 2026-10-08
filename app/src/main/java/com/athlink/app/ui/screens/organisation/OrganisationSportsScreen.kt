package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationLevel
import com.athlink.app.ui.components.*

/** STEP 4: sports legitimacy. Federation affiliation is optional; any credential works. */
@Composable
fun OrganisationSportsStep(ctx: StepContext) {
    val d = ctx.draft

    FormSection("Sports profile", "Athlink is a sports platform, so we check you're a genuine sports organisation.") {
        Text("Sports: " + d.sports.joinToString(", ").ifBlank { "none chosen yet (set them in step 1)" }, fontSize = 13.sp)
        FieldError(ctx.error(OrgField.PRIMARY_SPORT))
        DropdownField(
            label = fieldLabel("Organisation level", true),
            options = OrganisationLevel.entries,
            selected = d.organisationLevel,
            optionLabel = { it.label },
            onSelect = { l -> ctx.update(OrgField.ORGANISATION_LEVEL) { it.copy(organisationLevel = l) } },
            leadingIcon = Icons.Default.Leaderboard,
            errorMessage = ctx.error(OrgField.ORGANISATION_LEVEL),
            enabled = ctx.enabled
        )
        ctx.Input(d.yearsActive, "Approximate years active", OrgField.YEARS_ACTIVE, required = false, kind = InputKind.NUMBER,
            icon = Icons.Default.Timeline, maxLength = 3) { x, v -> x.copy(yearsActive = v.filter { it.isDigit() }) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ctx.Input(d.approxCoaches, "Coaches", OrgField.APPROX_COACHES, required = false, kind = InputKind.NUMBER,
                modifier = Modifier.weight(1f), maxLength = 7) { x, v -> x.copy(approxCoaches = v.filter { it.isDigit() }) }
            ctx.Input(d.approxAthletes, "Athletes / members", OrgField.APPROX_ATHLETES, required = false, kind = InputKind.NUMBER,
                modifier = Modifier.weight(1f), maxLength = 7) { x, v -> x.copy(approxAthletes = v.filter { it.isDigit() }) }
        }
    }

    FormSection("Affiliation & recognition", null) {
        ToggleRow(
            title = "Affiliated to a federation / governing body",
            description = "Not required. Many academies and clubs aren't affiliated, and that's fine.",
            checked = d.hasFederationAffiliation,
            onCheckedChange = { on -> ctx.update(OrgField.GOVERNING_BODY) { it.copy(hasFederationAffiliation = on) } },
            enabled = ctx.enabled
        )
        if (d.hasFederationAffiliation) {
            ctx.Input(d.governingBodyName, "Federation / governing body", OrgField.GOVERNING_BODY, icon = Icons.Default.EmojiEvents,
                hint = "e.g. Maharashtra Cricket Association") { x, v -> x.copy(governingBodyName = v) }
            ctx.Input(d.affiliationNumber, "Affiliation number", OrgField.AFFILIATION_NUMBER, required = false, kind = InputKind.CAPS,
                icon = Icons.Default.Numbers, maxLength = 50) { x, v -> x.copy(affiliationNumber = v) }
        }
        ctx.Input(d.accreditationDetails, "Accreditation / licence / recognition", OrgField.ACCREDITATION, required = false,
            kind = InputKind.SENTENCES, icon = Icons.Default.WorkspacePremium, maxLength = 200,
            hint = "e.g. SAI-recognised centre, tournament organiser since 2018") { x, v -> x.copy(accreditationDetails = v) }
        Text("You'll upload the matching credential in the Documents step.", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        WhyWeNeedThis("A legal registration shows the organisation exists; a sports credential shows it's genuinely involved in sport. Together they let players trust your events.")
    }
}
