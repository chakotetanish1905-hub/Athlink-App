package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.*
import com.athlink.app.ui.components.PlayerAvatar
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.PlayerFormMode
import com.athlink.app.viewmodel.PlayerProfileViewModel
import com.athlink.app.viewmodel.SessionViewModel

/**
 * The signed-in player's own profile, built from `players/{uid}` (public part) and `users/{uid}`
 * (private part, clearly labelled "Only you can see this"). No dummy or hardcoded values.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileScreen(
    user: User,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    viewModel: PlayerProfileViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sessionState by sessionViewModel.state.collectAsState()
    // Keyed on the whole user, so returning from Edit Profile (which updates the user) reloads.
    LaunchedEffect(user) { viewModel.load(user.uid, PlayerFormMode.EDIT, force = true) }
    LaunchedEffect(user.uid) { sessionViewModel.loadPlayerSessions(user.uid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onEditProfile) { Icon(Icons.Default.Edit, "Edit profile", tint = AthlinkOrange) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val account = state.user ?: user
        val player = state.player
        when {
            state.isLoading && state.snapshot == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthlinkOrange)
            }
            state.loadError != null && state.snapshot == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(state.loadError!!, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Retry", onClick = viewModel::retry)
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileHeader(account, player)

                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileStatCard("Sessions", sessionState.sessions.size.toString(), Icons.Default.FitnessCenter, Modifier.weight(1f))
                    val sportCount = player?.let { (if (it.primarySport.isBlank()) 0 else 1) + it.secondarySports.size } ?: 0
                    ProfileStatCard("Sports", sportCount.toString(), Icons.Default.SportsSoccer, Modifier.weight(1f))
                    ProfileStatCard("Years", player?.yearsOfExperience?.toString() ?: "—", Icons.Default.Timeline, Modifier.weight(1f))
                }

                if (player == null) {
                    ProfileCard("Your profile") {
                        Text("You haven't filled in your player profile yet.", fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Complete profile", onClick = onEditProfile)
                    }
                } else {
                    if (player.bio.isNotBlank()) {
                        ProfileCard("About") { Text(player.bio, fontSize = 14.sp) }
                    }

                    ProfileCard("Sport") {
                        InfoRow(Icons.Default.SportsSoccer, "Main sport", player.primarySport.ifBlank { "Not set" })
                        enumOrNull<SkillLevel>(player.skillLevel)?.let { InfoRow(Icons.Default.TrendingUp, "Level", it.label) }
                        SportProfiles.describe(player.primarySport, player.sportProfile).forEach { (label, value) ->
                            InfoRow(Icons.Default.Sports, label, value)
                        }
                        if (player.secondarySports.isNotEmpty()) {
                            InfoRow(Icons.Default.Add, "Also plays", player.secondarySports.joinToString(", "))
                        }
                    }

                    val goals = player.goals.mapNotNull { enumOrNull<PlayerGoal>(it) }
                    if (goals.isNotEmpty()) {
                        ProfileCard("Goals") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                goals.forEach { g ->
                                    Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.12f)) {
                                        Text(g.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = AthlinkOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    val prefs = player.coachingPreferences
                    ProfileCard("Training preferences") {
                        InfoRow(Icons.Default.Groups, "Coaching format", enumOrNull<CoachingFormat>(prefs.format)?.label ?: "Not set")
                        InfoRow(Icons.Default.Schedule, "Training time", enumOrNull<TrainingTime>(prefs.trainingTime)?.label ?: "Not set")
                        InfoRow(Icons.Default.NearMe, "Travel distance", TravelDistance.fromKm(prefs.maxDistanceKm)?.label ?: "Not set")
                    }

                    if (player.currentTeam.isNotBlank() || player.academy.isNotBlank() || player.ranking.isNotBlank() || player.achievements.isNotEmpty()) {
                        ProfileCard("Experience") {
                            if (player.currentTeam.isNotBlank()) InfoRow(Icons.Default.Groups, "Team / club", player.currentTeam)
                            if (player.academy.isNotBlank()) InfoRow(Icons.Default.School, "Academy", player.academy)
                            if (player.ranking.isNotBlank()) InfoRow(Icons.Default.Leaderboard, "Ranking", player.ranking)
                            player.achievements.forEach { InfoRow(Icons.Default.EmojiEvents, "Achievement", it) }
                        }
                    }
                }

                ProfileCard("Private details", subtitle = "Only you can see this") {
                    InfoRow(Icons.Default.Email, "Email", account.email.ifBlank { "Not set" })
                    InfoRow(Icons.Default.Cake, "Date of birth", PlayerAge.display(account.dateOfBirth).ifBlank { "Not set" })
                    if (account.phoneNumber.isNotBlank()) InfoRow(Icons.Default.Phone, "Phone", account.phoneNumber)
                    enumOrNull<Gender>(account.gender)?.let { InfoRow(Icons.Default.Wc, "Gender", it.label) }
                }

                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingsItem(Icons.Default.Edit, "Edit profile", onClick = onEditProfile)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                        SettingsItem(Icons.Default.Logout, "Logout", color = AthlinkRed, onClick = onLogout)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeader(account: User, player: PlayerProfile?) {
    val name = player?.displayName?.ifBlank { null } ?: account.name.ifBlank { "Athlete" }
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, MaterialTheme.colorScheme.background)))
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
            PlayerAvatar(player?.photoUrl.orEmpty(), name, 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            val sportLine = listOfNotNull(
                player?.primarySport?.ifBlank { null },
                enumOrNull<SkillLevel>(player?.skillLevel)?.label
            ).joinToString(" · ")
            if (sportLine.isNotBlank()) Text(sportLine, color = AthlinkOrange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            val place = listOf(player?.city.orEmpty(), player?.state.orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = AthlinkMedGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(place, color = AthlinkMedGray, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.2f)) {
                Text("Player", modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = AthlinkOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ProfileCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            subtitle?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProfileStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AthlinkOrange)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
