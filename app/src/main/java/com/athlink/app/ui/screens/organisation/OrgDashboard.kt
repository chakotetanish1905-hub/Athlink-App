package com.athlink.app.ui.screens.organisation

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
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.AthlinkBottomBar
import com.athlink.app.ui.components.orgNavItems
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.EventViewModel

@Composable
fun OrgDashboardScreen(
    user: User,
    navController: NavHostController,
    onLogout: () -> Unit,
    onCreateEvent: () -> Unit,
    eventViewModel: EventViewModel = hiltViewModel()
) {
    val eventState by eventViewModel.state.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = { AthlinkBottomBar(orgNavItems, currentRoute) { route -> navController.navigate(route) { launchSingleTop = true } } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateEvent, containerColor = AthlinkOrange, contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Post Event", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 90.dp)) {
            item {
                Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkDeepBlue.copy(0f)))).padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Organisation", color = AthlinkMedGray, fontSize = 13.sp)
                            Text(user.name.ifEmpty { "Organisation" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))), contentAlignment = Alignment.Center) {
                            Text(user.name.take(2).uppercase().ifEmpty { "OR" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrgStatCard("Events", "${eventState.events.size}", Icons.Default.EmojiEvents, Modifier.weight(1f))
                    OrgStatCard("Registered", "${eventState.events.sumOf { it.registeredCount }}", Icons.Default.People, Modifier.weight(1f))
                    OrgStatCard("Revenue", "₹${(eventState.events.sumOf { it.fees * it.registeredCount } / 1000).toInt()}k", Icons.Default.AccountBalance, Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Events", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${eventState.events.size} events", color = AthlinkOrange, fontSize = 13.sp)
                }
            }
            if (eventState.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) } }
            } else if (eventState.events.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEvents, null, tint = AthlinkMedGray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No events posted yet", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(eventState.events) { event -> Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { EventCard(event) } }
            }
        }
    }
}

@Composable
private fun OrgStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AthlinkOrange)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EventCard(event: Event) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.12f)) {
                    Text(event.sport, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = AthlinkOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                Text("  ${event.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.LocationOn, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                Text("  ${event.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, null, tint = AthlinkBlueLight, modifier = Modifier.size(16.dp))
                    Text("  ${event.registeredCount}/${event.maxParticipants} registered", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("₹${event.fees.toInt()}", fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (event.maxParticipants > 0) event.registeredCount.toFloat() / event.maxParticipants else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AthlinkOrange, trackColor = AthlinkOrange.copy(0.15f)
            )
        }
    }
}
