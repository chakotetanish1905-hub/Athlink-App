package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.OnboardingStep
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.InfoBanner
import com.athlink.app.ui.components.OutlineButton
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.OrganisationOnboardingViewModel

/**
 * Multi-step organisation onboarding / verification (7 steps). All state lives in
 * [OrganisationOnboardingViewModel], so going back never loses data, and the draft is saved to
 * Firestore on every Next (and on "Save draft"), so the user can leave and resume later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganisationOnboardingScreen(
    user: User,
    onExit: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: OrganisationOnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    LaunchedEffect(user.uid) { viewModel.load(user) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(state.submitted) { if (state.submitted) onSubmitted() }
    LaunchedEffect(state.step) { scroll.scrollTo(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Organisation verification", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Step ${state.step.ordinal + 1} of ${OnboardingStep.entries.size} · ${state.step.title}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Finish later") } },
                actions = {
                    if (state.editable && !state.isLoading) {
                        TextButton(onClick = viewModel::saveDraft, enabled = !state.busy) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp), tint = AthlinkOrange)
                            Spacer(Modifier.width(4.dp))
                            Text("Save draft", color = AthlinkOrange)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading && state.loadError == null) {
                Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.step != OnboardingStep.IDENTITY) {
                            OutlineButton("Back", onClick = viewModel::back, modifier = Modifier.weight(1f), enabled = !state.isSubmitting)
                        }
                        val last = state.step == OnboardingStep.REVIEW
                        PrimaryButton(
                            text = when {
                                !last -> "Next"
                                !state.editable -> "View status"
                                state.isResubmission -> "Resubmit for review"
                                else -> "Submit for review"
                            },
                            onClick = {
                                when {
                                    !last -> viewModel.next()
                                    !state.editable -> onSubmitted()
                                    else -> viewModel.submit()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy || (last && !state.editable),
                            isLoading = state.isSaving || state.isSubmitting
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthlinkOrange, modifier = Modifier.semantics { contentDescription = "Loading verification" })
            }
            state.loadError != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, tint = AthlinkMedGray, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(state.loadError!!)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Retry", onClick = viewModel::retry)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onExit) { Text("Go to dashboard") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StepProgress(state.step, state.furthestStep, onStepClick = viewModel::goTo)

                if (!state.editable) {
                    InfoBanner(
                        "Your verification is ${state.status.label.lowercase()}, so these details are read-only.",
                        Icons.Default.Lock, AthlinkBlueLight
                    )
                }
                state.rejectionReason?.let { reason ->
                    if (state.isResubmission) InfoBanner("Reviewer's note: $reason. Update the details below and resubmit.", Icons.Default.ErrorOutline, AthlinkRed)
                }

                val ctx = StepContext(state.draft, state.errors, state.editable) { field, transform -> viewModel.update(field, transform) }
                when (state.step) {
                    OnboardingStep.IDENTITY -> OrganisationIdentityStep(ctx, state.logoUploading, viewModel::uploadLogo)
                    OnboardingStep.LEGAL -> OrganisationLegalStep(ctx)
                    OnboardingStep.REPRESENTATIVE -> OrganisationRepresentativeStep(ctx)
                    OnboardingStep.SPORTS -> OrganisationSportsStep(ctx)
                    OnboardingStep.LOCATION -> OrganisationLocationStep(ctx)
                    OnboardingStep.DOCUMENTS -> OrganisationDocumentsStep(
                        ctx = ctx,
                        documents = state.documents,
                        uploads = state.uploads,
                        onPick = viewModel::uploadDocument,
                        onRemove = viewModel::removeDocument,
                        onDismissUpload = viewModel::dismissUpload,
                        onSaveDetails = viewModel::updateDocumentDetails
                    )
                    OnboardingStep.REVIEW -> OrganisationReviewStep(
                        ctx = ctx,
                        documents = state.documents,
                        emailVerified = state.emailVerified,
                        accountEmail = state.accountEmail,
                        onEdit = viewModel::goTo,
                        onSendVerificationEmail = viewModel::sendVerificationEmail,
                        onRefreshEmail = viewModel::refreshEmailVerified
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Numbered step dots + progress bar. Reached steps are tappable. */
@Composable
private fun StepProgress(current: OnboardingStep, furthest: OnboardingStep, onStepClick: (OnboardingStep) -> Unit) {
    val steps = OnboardingStep.entries
    Column {
        LinearProgressIndicator(
            progress = { (current.ordinal + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AthlinkOrange, trackColor = AthlinkOrange.copy(0.15f)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            steps.forEach { step ->
                val reached = step.ordinal <= furthest.ordinal
                val isCurrent = step == current
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> AthlinkOrange
                                reached -> AthlinkOrange.copy(0.25f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable(enabled = reached) { onStepClick(step) }
                        .semantics { contentDescription = "Step ${step.ordinal + 1}: ${step.title}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${step.ordinal + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(current.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
