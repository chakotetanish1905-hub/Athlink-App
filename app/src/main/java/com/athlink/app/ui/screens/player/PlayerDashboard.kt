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
import com.athlink.app.data.model.Sports
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AcademyViewModel
import com.athlink.app.viewmodel.CoachViewModel
import com.athlink.app.viewmodel.SessionViewModel

@Composable
fun PlayerDashboardScreen(
    user: User,
    navController: NavHostController,
    onCoachClick: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenBookings: () -> Unit = {},
    onOpenAcademies: () -> Unit = {},
    onOpenAcademy: (String) -> Unit = {},
    coachViewModel: CoachViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
    academyViewModel: AcademyViewModel = hiltViewModel()
) {
    val coachState by coachViewModel.state.collectAsState()
    val sessionState by sessionViewModel.state.collectAsState()
    val academyState by academyViewModel.list.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    LaunchedEffect(user.uid) {
        sessionViewModel.loadPlayerSessions(user.uid)
        academyViewModel.loadList(user.uid)
    }
    val upcoming = sessionState.upcoming
    val pendingRequests = sessionState.academyRequests.count { it.status == "PENDING" }

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
            item { StatsRow(upcoming.size, coachState.coaches.size, academyState.all.size) }
            item {
                SectionHeader("Upcoming Sessions", "View All", onOpenBookings)
                when {
                    sessionState.isLoading && !sessionState.loaded -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AthlinkOrange, modifier = Modifier.size(28.dp))
                    }
                    upcoming.isEmpty() -> EmptyCard(
                        "No upcoming sessions",
                        if (pendingRequests > 0) "$pendingRequests academy request(s) waiting for a reply" else "Book a verified coach or request an academy session"
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(upcoming.take(5), key = { it.id }) { session ->
                            Box(modifier = Modifier.width(300.dp)) { SessionCard(session) }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    if (academyState.city != null) "Academies in ${academyState.city}" else "Academies near you",
                    "See All", onOpenAcademies
                )
                val academies = academyState.results.take(5)
                when {
                    academyState.isLoading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AthlinkOrange, modifier = Modifier.size(28.dp))
                    }
                    academies.isEmpty() -> EmptyCard("No academies listed yet", academyState.error ?: "Check back soon")
                    else -> Column { academies.forEach { a -> AcademyCard(a, onClick = { onOpenAcademy(a.organisationId) }) } }
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
                    if (recommended.isEmpty() && !coachState.isLoading) {
                        item {
                            Text(
                                coachState.error ?: "No verified coaches yet. Academies above are already listed.",
                                fontSize = 13.sp, color = AthlinkMedGray, modifier = Modifier.width(300.dp)
                            )
                        }
                    }
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
                    items(Sports.ALL.take(10)) { sport ->
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
                val hour = remember { java.time.LocalTime.now().hour }
                Text(
                    when (hour) { in 5..11 -> "Good morning 👋"; in 12..16 -> "Good afternoon 👋"; else -> "Good evening 👋" },
                    color = AthlinkMedGray, fontSize = 13.sp
                )
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
private fun StatsRow(upcomingCount: Int, coachCount: Int, academyCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Upcoming", upcomingCount.toString(), Icons.Default.FitnessCenter, Modifier.weight(1f))
        StatCard("Coaches", coachCount.toString(), Icons.Default.People, Modifier.weight(1f))
        StatCard("Academies", academyCount.toString(), Icons.Default.School, Modifier.weight(1f))
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
