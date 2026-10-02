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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.User
import com.athlink.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachProfileScreen(user: User, onLogout: () -> Unit, onBack: () -> Unit) {
    val coachData = DummyData.coaches.firstOrNull() ?: DummyData.coaches.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = {}) { Icon(Icons.Default.Edit, null, tint = AthlinkOrange) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AthlinkDeepBlue, MaterialTheme.colorScheme.background))).padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
                    Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))), contentAlignment = Alignment.Center) {
                        Text(user.name.take(2).uppercase().ifEmpty { "CO" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(user.name.ifEmpty { "Coach" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(coachData.sport, color = AthlinkOrange, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = AthlinkGold, modifier = Modifier.size(16.dp))
                        Text(" ${coachData.rating} (${coachData.reviewCount} reviews)", color = Color.White.copy(0.85f), fontSize = 13.sp)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Experience" to "${coachData.experience} yrs", "Sessions" to "240+", "Rating" to "${coachData.rating}").forEach { (label, value) ->
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AthlinkOrange)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("About Me", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(coachData.bio, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Specializations", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        coachData.specializations.take(3).forEach { spec ->
                            Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.1f)) {
                                Text(spec, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = AthlinkOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Hourly Rate", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Per 1-hour session", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    Text("₹${coachData.hourlyRate.toInt()}", fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 26.sp)
                }
            }
            TextButton(onClick = onLogout, modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)) {
                Icon(Icons.Default.Logout, null, tint = AthlinkRed); Spacer(Modifier.width(6.dp)); Text("Logout", color = AthlinkRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
