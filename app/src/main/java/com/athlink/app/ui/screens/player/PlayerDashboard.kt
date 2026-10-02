package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.CoachViewModel
import com.athlink.app.viewmodel.SessionViewModel

@Composable
fun PlayerDashboardScreen(
    user: User,
    navController: NavHostController,
    onCoachClick: (String) -> Unit,
    onLogout: () -> Unit,
    coachViewModel: CoachViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val coachState by coachViewModel.state.collectAsState()
    val sessionState by sessionViewModel.state.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    LaunchedEffect(user.uid) {
        sessionViewModel.loadPlayerSessions(user.uid)
    }

    Scaffold(
        bottomBar = {
            AthlinkBottomBar(
                items = playerNavItems,
                currentRoute = currentRoute,
                onItemClick = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item { PlayerHeader(user = user, onLogout = onLogout) }
            item { StatsRow(sessionState.sessions.size) }
            item {
                SectionHeader("Upcoming Sessions", "View All") {}
                if (sessionState.sessions.isEmpty()) {
                    EmptyCard("No sessions yet", "Book a session with a coach to get started")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sessionState.sessions.take(3)) { session ->
                            Box(modifier = Modifier.width(300.dp)) { SessionCard(session) }
                        }
                    }
                }
            }
            item {
                SectionHeader("Recommended Coaches", "See All") {
                    navController.navigate("player_search")
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val recommended = coachViewModel.getRecommendedCoaches()
                    items(recommended) { coach ->
                        CoachCardCompact(coach = coach, onClick = { onCoachClick(coach.uid) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                SectionHeader("Popular Sports", "") {}
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(DummyData.sports) { sport ->
                        SportChipItem(sport = sport, onClick = {
                            navController.navigate("player_search")
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PlayerHeader(user: User, onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkDeepBlue.copy(0f))))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Good Morning 👋", color = AthlinkMedGray, fontSize = 13.sp)
                Text(user.name.ifEmpty { "Athlete" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Notifications, null, tint = Color.White)
                }
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .clickable(onClick = onLogout),
                    contentAlignment = Alignment.Center
                ) {
                    Text(user.name.take(2).uppercase().ifEmpty { "AT" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(sessionCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Sessions", sessionCount.toString(), Icons.Default.FitnessCenter, Modifier.weight(1f))
        StatCard("Coaches", "5", Icons.Default.People, Modifier.weight(1f))
        StatCard("Events", "2", Icons.Default.EmojiEvents, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AthlinkOrange)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (actionLabel.isNotEmpty()) {
            Text(actionLabel, color = AthlinkOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SportsScore, null, tint = AthlinkMedGray, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, fontSize = 12.sp, color = AthlinkMedGray, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SportChipItem(sport: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(sport, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
