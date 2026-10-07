package com.athlink.app.ui.screens.coach

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.athlink.app.data.model.AgeGroup
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachProfileCompletionResult
import com.athlink.app.data.model.CoachingLevel
import com.athlink.app.data.model.User
import com.athlink.app.data.model.VerificationStatus
import com.athlink.app.data.model.displayLocation
import com.athlink.app.data.model.level
import com.athlink.app.data.model.verification
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.CoachProfileViewModel

/**
 * The signed-in coach's own profile. Everything shown comes from `coaches/{uid}` and
 * `coachPrivate/{uid}`. There is no dummy data here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachProfileScreen(
    user: User,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    viewModel: CoachProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.load(user) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh(user) }, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, "Reload profile", tint = AthlinkOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val profile = state.profile
        when {
            state.isLoading && profile == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthlinkOrange, modifier = Modifier.semantics { contentDescription = "Loading profile" })
            }
            state.error != null && profile == null -> ProfileMessage(
                padding, Icons.Default.CloudOff, state.error!!, onRetry = { viewModel.refresh(user) }, onLogout = onLogout
            )
            state.notFound || profile == null -> ProfileMessage(
                padding, Icons.Default.PersonSearch,
                "We couldn't find a coach profile for this account. If you just signed up, tap Retry.",
                onRetry = { viewModel.refresh(user) }, onLogout = onLogout
            )
            else -> ProfileContent(
                padding = padding,
                coach = profile.coach,
                fallbackName = user.name,
                email = profile.privateProfile.email,
                phone = profile.privateProfile.phone,
                completion = state.completion,
                completedSessions = state.completedSessions,
                onLogout = onLogout
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    padding: PaddingValues,
    coach: Coach,
    fallbackName: String,
    email: String,
    phone: String,
    completion: CoachProfileCompletionResult?,
    completedSessions: Int,
    onLogout: () -> Unit
) {
    val displayName = coach.name.ifBlank { fallbackName }.ifBlank { "Coach" }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, MaterialTheme.colorScheme.background)))
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp)) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    if (coach.profileImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = coach.profileImageUrl,
                            contentDescription = "Profile photo of $displayName",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(displayName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                if (coach.sport.isNotBlank()) {
                    Text(coach.sport, color = AthlinkOrange, fontWeight = FontWeight.SemiBold)
                }
                val location = coach.displayLocation()
                if (location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.LocationOn, null, tint = AthlinkMedGray, modifier = Modifier.size(14.dp))
                        Text(" $location", color = Color.White.copy(0.8f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                VerificationChip(coach)
            }
        }

        // ── Stats ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rating = if (coach.reviewCount > 0) String.format("%.1f", coach.rating) else "New"
            listOf(
                "Experience" to "${coach.experience} yrs",
                "Sessions" to "$completedSessions",
                if (coach.reviewCount > 0) "${coach.reviewCount} reviews" to rating else "Rating" to rating
            ).forEach { (label, value) ->
                Card(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AthlinkOrange)
                        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Completion ──────────────────────────────────────────────────
        if (completion != null && !completion.canSubmit) {
            SectionCard("Profile ${completion.percentage}% complete") {
                LinearProgressIndicator(
                    progress = { completion.percentage / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = AthlinkOrange,
                    trackColor = AthlinkOrange.copy(0.15f)
                )
                Spacer(Modifier.height(12.dp))
                Text("Still needed before you can apply for verification:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                completion.missingFields.take(5).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(Icons.Default.RadioButtonUnchecked, null, tint = AthlinkGold, modifier = Modifier.size(14.dp))
                        Text("  $item", fontSize = 13.sp)
                    }
                }
                val more = completion.missingFields.size - 5
                if (more > 0) Text("  + $more more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── About ───────────────────────────────────────────────────────
        SectionCard("About Me") {
            Text(
                coach.bio.ifBlank { "No bio added yet." },
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp
            )
        }

        // ── Coaching ────────────────────────────────────────────────────
        if (coach.specializations.isNotEmpty() || coach.coachingLevels.isNotEmpty() || coach.ageGroups.isNotEmpty()) {
            SectionCard("Coaching") {
                LabeledChips("Specializations", coach.specializations)
                LabeledChips("Skill levels", coach.coachingLevels.map { stored ->
                    CoachingLevel.entries.firstOrNull { it.name == stored }?.label ?: stored
                })
                LabeledChips("Age groups", coach.ageGroups.mapNotNull { AgeGroup.fromStored(it)?.label })
                LabeledChips("Other sports", coach.secondarySports)
            }
        }

        // ── Certifications (self-declared at signup) ────────────────────
        if (coach.certifications.isNotEmpty()) {
            SectionCard("Certifications") {
                coach.certifications.forEach { cert ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = AthlinkOrange, modifier = Modifier.size(18.dp))
                        Text("  $cert", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Self-declared, not verified by Athlink yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Rate ────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Hourly Rate", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Per 1-hour session", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    if (coach.hourlyRate > 0) "₹${coach.hourlyRate.toInt()}" else "Not set",
                    fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 26.sp
                )
            }
        }

        // ── Private contact ─────────────────────────────────────────────
        SectionCard("Contact details") {
            InfoRow(Icons.Default.Email, "Email", email.ifBlank { "Not set" })
            InfoRow(Icons.Default.Phone, "Phone", phone.ifBlank { "Not set" })
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AthlinkMedGray, modifier = Modifier.size(12.dp))
                Text(" Only you can see this. Players don't see your email or phone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        TextButton(onClick = onLogout, modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)) {
            Icon(Icons.Default.Logout, null, tint = AthlinkRed); Spacer(Modifier.width(6.dp))
            Text("Logout", color = AthlinkRed, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Small reusable pieces ──────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabeledChips(label: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items.forEach { item ->
            Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.1f)) {
                Text(item, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = AthlinkOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(18.dp))
        Text("  $label: ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Says exactly what has been verified. "Not verified" until an admin approves. */
@Composable
private fun VerificationChip(coach: Coach) {
    val status = coach.verification
    val (text, color) = when (status) {
        VerificationStatus.VERIFIED -> coach.level.label to AthlinkGreen
        VerificationStatus.UNDER_REVIEW -> "Verification under review" to AthlinkGold
        VerificationStatus.ACTION_REQUIRED -> "Verification: action required" to AthlinkOrange
        VerificationStatus.REJECTED, VerificationStatus.SUSPENDED -> "Verification ${status.label.lowercase()}" to AthlinkRed
        VerificationStatus.EXPIRED -> "Verification expired" to AthlinkMedGray
        VerificationStatus.NOT_SUBMITTED -> "Not verified yet" to AthlinkMedGray
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.18f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
            Icon(
                if (status == VerificationStatus.VERIFIED) Icons.Default.Verified else Icons.Default.Shield,
                null, tint = color, modifier = Modifier.size(14.dp)
            )
            Text(" $text", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileMessage(
    padding: PaddingValues,
    icon: ImageVector,
    message: String,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = AthlinkMedGray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AthlinkOrange)) { Text("Retry") }
        TextButton(onClick = onLogout, modifier = Modifier.padding(top = 8.dp)) {
            Text("Logout", color = AthlinkRed, fontWeight = FontWeight.SemiBold)
        }
    }
}
