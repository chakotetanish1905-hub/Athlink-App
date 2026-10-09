package com.athlink.app.ui.screens.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.PlayerLanding
import com.athlink.app.data.model.PlayerOnboardingStep
import com.athlink.app.data.model.PlayerProfileStatus
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.InfoBanner
import com.athlink.app.ui.components.OutlineButton
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.PlayerFormMode
import com.athlink.app.viewmodel.PlayerProfileUiState
import com.athlink.app.viewmodel.PlayerProfileViewModel

/**
 * Start of the player graph. Loads `users/{uid}` + `players/{uid}` and routes:
 * profile COMPLETE -> dashboard, otherwise -> onboarding (new players, players who left midway,
 * and accounts created before onboarding existed).
 */
@Composable
fun PlayerGateScreen(
    user: User,
    onRoute: (PlayerLanding, User?) -> Unit,
    onLogout: () -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.resolveLanding(user.uid) }
    var routed by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.landing, state.loadError) {
        val landing = state.landing
        if (!routed && !state.isLoading && state.loadError == null && landing != null) {
            routed = true
            onRoute(landing, state.user)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        if (state.loadError != null) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudOff, null, tint = AthlinkMedGray, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(state.loadError!!, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Retry", onClick = viewModel::retry)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onLogout) { Text("Log out") }
            }
        } else {
            CircularProgressIndicator(color = AthlinkOrange, modifier = Modifier.semantics { contentDescription = "Loading your profile" })
        }
    }
}

/**
 * Multi-step player onboarding (7 short steps). All answers live in [PlayerProfileViewModel], so
 * moving back and forth never loses data, and progress is saved on every Next, so a player who
 * leaves can resume where they stopped after logging in again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOnboardingScreen(
    user: User,
    onCompleted: (User) -> Unit,
    onLogout: () -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) { viewModel.load(user.uid, PlayerFormMode.ONBOARDING) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    LaunchedEffect(state.savedUser) { state.savedUser?.let { viewModel.consumeSavedUser(); onCompleted(it) } }
    LaunchedEffect(state.exitAfterSave) { if (state.exitAfterSave) onLogout() }

    BackHandler(enabled = state.step != PlayerOnboardingStep.ABOUT && !state.isLoading) { viewModel.back() }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Finish later?") },
            text = { Text("We'll save what you've entered so far. Log in again any time to pick up where you left off.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; viewModel.saveAndExit() }) { Text("Save & log out") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.step.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Step ${state.step.ordinal + 1} of ${PlayerOnboardingStep.entries.size}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (state.step != PlayerOnboardingStep.ABOUT) {
                        IconButton(onClick = viewModel::back, enabled = !state.busy) { Icon(Icons.Default.ArrowBack, "Previous step") }
                    }
                },
                actions = {
                    if (!state.isLoading && state.loadError == null) {
                        TextButton(onClick = { confirmExit = true }, enabled = !state.busy) { Text("Finish later", color = AthlinkOrange) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading && state.loadError == null) {
                Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.step != PlayerOnboardingStep.ABOUT) {
                            OutlineButton("Back", onClick = viewModel::back, modifier = Modifier.weight(1f), enabled = !state.busy)
                        }
                        val last = state.step == PlayerOnboardingStep.READY
                        PrimaryButton(
                            text = if (last) "Continue to Athlink" else "Next",
                            onClick = {
                                focus.clearFocus()
                                if (last) viewModel.finishOnboarding() else viewModel.next()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                            isLoading = state.isSaving
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthlinkOrange)
            }
            state.loadError != null -> LoadError(state.loadError!!, Modifier.padding(padding), viewModel::retry, onLogout)
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StepProgress(state.step, state.furthestStep, onStepClick = viewModel::goTo)
                StepContent(state, viewModel)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StepContent(state: PlayerProfileUiState, viewModel: PlayerProfileViewModel) {
    val ctx = PlayerFormContext(state.form, state.errors, enabled = !state.isSaving) { field, transform -> viewModel.update(field, transform) }
    when (state.step) {
        PlayerOnboardingStep.ABOUT -> AboutYouSection(ctx, state.isPreparingPhoto, viewModel::pickPhoto, viewModel::removePhoto)
        PlayerOnboardingStep.SPORT -> SportSection(ctx)
        PlayerOnboardingStep.GAME -> GameSection(ctx)
        PlayerOnboardingStep.LEVEL -> LevelSection(ctx)
        PlayerOnboardingStep.GOALS -> GoalsSection(ctx)
        PlayerOnboardingStep.TRAINING -> TrainingSection(ctx)
        PlayerOnboardingStep.READY -> ReadySection(ctx, onEdit = viewModel::goTo)
    }
}

/**
 * Edit Profile: the same sections and validation as onboarding, on one scrolling page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEditProfileScreen(
    user: User,
    onSaved: (User) -> Unit,
    onBack: () -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current

    LaunchedEffect(user.uid) { viewModel.load(user.uid, PlayerFormMode.EDIT) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(state.savedUser) { state.savedUser?.let { viewModel.consumeSavedUser(); onSaved(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack, enabled = !state.isSaving) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading && state.loadError == null) {
                Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        PrimaryButton("Save changes", onClick = { focus.clearFocus(); viewModel.saveEdits() },
                            enabled = !state.busy, isLoading = state.isSaving)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthlinkOrange)
            }
            state.loadError != null -> LoadError(state.loadError!!, Modifier.padding(padding), viewModel::retry, onBack)
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.errors.isNotEmpty()) {
                    InfoBanner("Some details need fixing: look for the highlighted fields below.", Icons.Default.ErrorOutline, AthlinkRed)
                }
                val ctx = PlayerFormContext(
                    state.form, state.errors, enabled = !state.isSaving,
                    dobLocked = state.user?.let { it.profileStatus == PlayerProfileStatus.COMPLETE.name && it.dateOfBirth.isNotBlank() } ?: false
                ) { field, transform -> viewModel.update(field, transform) }
                AboutYouSection(ctx, state.isPreparingPhoto, viewModel::pickPhoto, viewModel::removePhoto)
                SportSection(ctx)
                GameSection(ctx)
                LevelSection(ctx)
                GoalsSection(ctx)
                TrainingSection(ctx)
                if (!state.form.consentAlreadyRecorded) {
                    ReadySection(ctx, onEdit = {})
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LoadError(message: String, modifier: Modifier, onRetry: () -> Unit, onSecondary: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, null, tint = AthlinkMedGray, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Retry", onClick = onRetry)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSecondary) { Text("Back") }
    }
}

/** Progress bar + numbered step dots. Steps already reached can be tapped. */
@Composable
private fun StepProgress(current: PlayerOnboardingStep, furthest: PlayerOnboardingStep, onStepClick: (PlayerOnboardingStep) -> Unit) {
    val steps = PlayerOnboardingStep.entries
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
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> AthlinkOrange
                                reached -> AthlinkOrange.copy(0.25f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable(enabled = reached && !isCurrent) { onStepClick(step) }
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
