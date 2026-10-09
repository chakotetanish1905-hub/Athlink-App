package com.athlink.app.ui.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.AthlinkBottomBar
import com.athlink.app.ui.components.SessionCard
import com.athlink.app.ui.components.coachNavItems
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.SessionViewModel

@Composable
fun CoachDashboardScreen(
    user: User,
    navController: NavHostController,
    onLogout: () -> Unit,
    sessionViewModel: SessionViewModel = hiltViewModel(),
    scheduleViewModel: com.athlink.app.viewmodel.CoachScheduleViewModel = hiltViewModel()
) {
    val sessionState by sessionViewModel.state.collectAsState()
    val scheduleState by scheduleViewModel.state.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    LaunchedEffect(user.uid) {
        sessionViewModel.loadCoachSessions(user.uid)
        scheduleViewModel.load(user.uid)
    }

    val pending   = sessionState.sessions.filter { it.status == SessionStatus.PENDING }
    val confirmed = sessionState.sessions.filter { it.status == SessionStatus.CONFIRMED }
    val totalEarnings = sessionState.sessions.filter { it.status == SessionStatus.COMPLETED }.sumOf { it.price }

    Scaffold(
        bottomBar = {
            AthlinkBottomBar(coachNavItems, currentRoute) { route ->
                navController.navigate(route) { launchSingleTop = true }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkDeepBlue.copy(0f))))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Coach Dashboard", color = AthlinkMedGray, fontSize = 13.sp)
                            Text(user.name.ifEmpty { "Coach" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.name.take(2).uppercase().ifEmpty { "CO" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Visibility to players + availability
            if (!scheduleState.isLoading) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CoachVisibilityCard(scheduleState, onOpenSchedule = { navController.navigate("coach_schedule") })
                    }
                }
            }

            // Earnings Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Earnings Overview", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EarningsChip("Total Earned", "₹${totalEarnings.toInt()}", AthlinkGreen, Modifier.weight(1f))
                            EarningsChip("Pending", "${pending.size}", AthlinkGold, Modifier.weight(1f))
                            EarningsChip("Confirmed", "${confirmed.size}", AthlinkOrange, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { navController.navigate("coach_sessions") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AthlinkOrange)
                    ) {
                        Icon(Icons.Default.EventNote, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Sessions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("coach_schedule") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AthlinkOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Schedule", color = AthlinkOrange, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // Session Requests
            if (pending.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Session Requests", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Badge(containerColor = AthlinkOrange) { Text("${pending.size}") }
                    }
                }
                items(pending.take(3)) { session ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SessionRequestCard(
                            session = session,
                            onAccept = { sessionViewModel.updateStatus(session.id, SessionStatus.CONFIRMED) },
                            onReject = { sessionViewModel.updateStatus(session.id, SessionStatus.REJECTED) }
                        )
                    }
                }
            }

            // Upcoming Confirmed
            item {
                Text(
                    "Upcoming Sessions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            if (confirmed.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CalendarMonth, null, tint = AthlinkMedGray, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No upcoming sessions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                items(confirmed.take(3)) { session ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SessionCard(session)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun EarningsChip(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
