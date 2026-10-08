package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.athlink.app.data.model.OrgField
import com.athlink.app.ui.components.*

/**
 * STEP 5: organisation / facility location. This is where the ORGANISATION operates; we never ask
 * for the representative's personal or real-time location. Only the public label is shown to players.
 */
@Composable
fun OrganisationLocationStep(ctx: StepContext) {
    val d = ctx.draft

    FormSection("Where you operate", "Your organisation's facility, not your personal location.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ctx.Input(d.country, "Country", OrgField.COUNTRY, modifier = Modifier.weight(1f), maxLength = 60) { x, v -> x.copy(country = v) }
            ctx.Input(d.state, "State", OrgField.STATE, modifier = Modifier.weight(1f), maxLength = 60) { x, v -> x.copy(state = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ctx.Input(d.district, "District", OrgField.DISTRICT, modifier = Modifier.weight(1f), maxLength = 60) { x, v -> x.copy(district = v) }
            ctx.Input(d.city, "City", OrgField.CITY, modifier = Modifier.weight(1f), maxLength = 60) { x, v -> x.copy(city = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ctx.Input(d.locality, "Locality", OrgField.LOCALITY, required = false, modifier = Modifier.weight(1f), maxLength = 80) { x, v -> x.copy(locality = v) }
            ctx.Input(d.pincode, "PIN code", OrgField.PINCODE, kind = InputKind.NUMBER, modifier = Modifier.weight(1f), maxLength = 12) { x, v -> x.copy(pincode = v.trim()) }
        }
        ctx.Input(d.facilityAddress, "Organisation / facility address", OrgField.FACILITY_ADDRESS, kind = InputKind.SENTENCES,
            multiline = true, icon = Icons.Default.Place, hint = "Private: for the address check only") { x, v -> x.copy(facilityAddress = v) }
    }

    FormSection("Public location", "What players see. Keep it general.") {
        ctx.Input(d.publicAddress, "Public location", OrgField.PUBLIC_ADDRESS, required = false, icon = Icons.Default.LocationOn,
            maxLength = 120, hint = "Leave blank to show \"${d.resolvedPublicAddress().ifBlank { "Locality, City" }}\"") { x, v -> x.copy(publicAddress = v) }
        WhyWeNeedThis("Players see only the area (e.g. \"Andheri West, Mumbai\"). The full facility and registered addresses stay private and are only used by reviewers.")
    }
}
