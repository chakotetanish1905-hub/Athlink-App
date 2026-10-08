package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.athlink.app.data.model.User
import com.athlink.app.data.model.displayLocation
import com.athlink.app.data.model.type
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.OrganisationViewModel

/**
 * The organisation's own profile, from `organisations/{uid}` and its private verification docs.
 * Replaces the old reuse of PlayerProfileScreen (which showed "Player" and hardcoded stats).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrgProfileScreen(
    user: User,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenVerification: (editable: Boolean) -> Unit,
    viewModel: OrganisationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.load(user) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organisation Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { viewModel.refresh(user) }) { Icon(Icons.Default.Refresh, "Refresh", tint = AthlinkOrange) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading && !state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) }
            return@Scaffold
        }
        val org = state.organisation
        val v = state.snapshot?.verification
        val rep = state.snapshot?.representative
        val name = org?.displayName?.ifBlank { null } ?: user.name

        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AthlinkDeepBlue, MaterialTheme.colorScheme.background))).padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp)) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))), contentAlignment = Alignment.Center) {
                        if (!org?.logoUrl.isNullOrBlank()) {
                            OrgLogoImage(org?.logoUrl.orEmpty(), "Logo of $name", Modifier.fillMaxSize())
                        } else {
                            Text(name.take(2).uppercase().ifBlank { "OR" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                    org?.type?.let { Text(it.label, color = AthlinkMedGray, fontSize = 14.sp) }
                    Spacer(Modifier.height(10.dp))
                    OrganisationBadgeChip(state.badge, onDark = true)
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.error?.let { ErrorCard(it) }
                if (org == null && state.loaded) {
                    InfoBanner("You haven't set up your organisation profile yet.")
                }
                if (!state.canPublish) {
                    PrimaryButton(
                        if (state.canEditVerification) "Complete verification" else "View verification status",
                        onClick = { onOpenVerification(state.canEditVerification) }
                    )
                }
                org?.let { o ->
                    FormSection("About", null) {
                        if (o.description.isNotBlank()) Text(o.description, fontSize = 14.sp)
                        if (o.sports.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                o.sports.forEachIndexed { i, s ->
                                    Surface(shape = CircleShape, color = AthlinkOrange.copy(if (i == 0) 0.25f else 0.12f)) {
                                        Text(s, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = AthlinkOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        KeyValueRow("Legal name", o.legalName)
                        KeyValueRow("Location", o.displayLocation)
                        KeyValueRow("Established", o.yearEstablished?.toString().orEmpty())
                        KeyValueRow("Website", o.website)
                    }
                }
                if (v != null || rep != null) {
                    FormSection("Private contact", "Only you and Athlink reviewers see this.") {
                        KeyValueRow("Official email", v?.officialEmail.orEmpty())
                        KeyValueRow("Official phone", v?.officialPhone.orEmpty())
                        KeyValueRow("Representative", listOfNotNull(rep?.fullName, rep?.designation?.ifBlank { null }).joinToString(", "))
                        KeyValueRow("Account email", state.accountEmail.ifBlank { user.email })
                    }
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkRed)) {
                    Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Log out", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
