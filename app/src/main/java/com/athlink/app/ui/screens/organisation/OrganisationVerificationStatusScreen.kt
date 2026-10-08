package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.User
import com.athlink.app.data.model.level
import com.athlink.app.data.model.reviewStatus
import com.athlink.app.data.model.type
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.OrganisationViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Shows where the organisation is in the verification lifecycle, with the reviewer's rejection
 * reason, and the right next action (continue, correct & resubmit, or go to the dashboard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganisationVerificationStatusScreen(
    user: User,
    onEditVerification: () -> Unit,
    onOpenDashboard: () -> Unit,
    onLogout: () -> Unit,
    viewModel: OrganisationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.refresh(user) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification status", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh(user) }, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh status", tint = AthlinkOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading && !state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) }
            return@Scaffold
        }
        if (state.error != null && !state.loaded) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.error!!, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Retry", onClick = { viewModel.refresh(user) })
            }
            return@Scaffold
        }
        val org = state.organisation
        val v = state.snapshot?.verification
        val status = state.status
        val badge = state.badge
        val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
        fun Date?.show() = this?.let { fmt.format(it) } ?: "—"

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(badge.icon(), null, tint = badge.accentColor(), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(org?.displayName?.ifBlank { null } ?: user.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    OrganisationBadgeChip(badge)
                    Spacer(Modifier.height(12.dp))
                    Text(headline(status), textAlign = TextAlign.Center, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (status == OrganisationVerificationStatus.REJECTED && !v?.rejectionReason.isNullOrBlank()) {
                InfoBanner("Reason: ${v?.rejectionReason}", Icons.Default.ErrorOutline, AthlinkRed)
            }
            if (!v?.reviewNotes.isNullOrBlank() && status != OrganisationVerificationStatus.UNDER_REVIEW) {
                InfoBanner("Reviewer notes: ${v?.reviewNotes}", Icons.Default.Notes)
            }

            FormSection("Details", null) {
                KeyValueRow("Status", status.label)
                KeyValueRow("Trust level", org?.level?.label ?: "Unverified")
                KeyValueRow("Type", org?.type?.label.orEmpty())
                KeyValueRow("Submitted", v?.submittedAt.show())
                KeyValueRow("Last reviewed", v?.reviewedAt.show())
                if (org?.verifiedAt != null) KeyValueRow("Verified on", org.verifiedAt.show())
                if (org?.verificationExpiresAt != null) KeyValueRow("Valid until", org.verificationExpiresAt.show())
            }

            val docs = state.snapshot?.documents.orEmpty()
            if (docs.isNotEmpty()) {
                FormSection("Documents", "Reviewed manually by Athlink") {
                    docs.forEach { d -> KeyValueRow(d.type?.label ?: d.documentType, d.reviewStatus.label) }
                }
            }

            when {
                state.canEditVerification -> PrimaryButton(
                    text = when (status) {
                        OrganisationVerificationStatus.REJECTED -> "Correct & resubmit"
                        OrganisationVerificationStatus.EXPIRED -> "Re-verify"
                        else -> "Complete verification"
                    },
                    onClick = onEditVerification
                )
                status == OrganisationVerificationStatus.UNDER_REVIEW -> OutlineButton("View submitted details", onClick = onEditVerification)
                else -> Unit
            }
            // The dashboard stays reachable in every state (drafts, profile); publishing is gated there.
            OutlineButton("Go to dashboard", onClick = onOpenDashboard)
            TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Log out", color = AthlinkRed) }
        }
    }
}

private fun headline(status: OrganisationVerificationStatus): String = when (status) {
    OrganisationVerificationStatus.UNVERIFIED -> "Complete your organisation verification to publish events. You can save event drafts meanwhile."
    OrganisationVerificationStatus.CONTACT_VERIFIED -> "Your email is verified. Finish the verification form and submit it for review."
    OrganisationVerificationStatus.UNDER_REVIEW -> "Thanks! Athlink is reviewing your documents. This usually takes a few working days. You can prepare event drafts meanwhile."
    OrganisationVerificationStatus.VERIFIED -> "Your organisation is verified. You can publish public events."
    OrganisationVerificationStatus.OFFICIAL_GOVERNMENT -> "Verified as an official government sports body. You can publish public events."
    OrganisationVerificationStatus.REJECTED -> "We couldn't verify your organisation. Fix the issue below and resubmit."
    OrganisationVerificationStatus.SUSPENDED -> "Your organisation is suspended. Publishing is disabled. Contact Athlink support for help."
    OrganisationVerificationStatus.EXPIRED -> "Your verification has expired. Re-verify with current documents to publish again."
}
