package com.athlink.app.ui.screens.player

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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.User
import com.athlink.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    user: User,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Edit, null, tint = AthlinkOrange) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, MaterialTheme.colorScheme.background)))
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
                    Box(
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.name.take(2).uppercase().ifEmpty { "AT" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(user.name.ifEmpty { "Athlete" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(user.email, color = AthlinkMedGray, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = AthlinkOrange.copy(0.2f)) {
                        Text("Player", modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = AthlinkOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Stats
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStatCard("Sessions", "12", Icons.Default.FitnessCenter, Modifier.weight(1f))
                ProfileStatCard("Sports", "3", Icons.Default.SportsSoccer, Modifier.weight(1f))
                ProfileStatCard("Rating", "4.8", Icons.Default.Star, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Personal Info", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    InfoRow(Icons.Default.Person, "Full Name", user.name.ifEmpty { "Not set" })
                    InfoRow(Icons.Default.Email, "Email", user.email.ifEmpty { "Not set" })
                    InfoRow(Icons.Default.LocationOn, "Location", "Mumbai, India")
                    InfoRow(Icons.Default.SportsCricket, "Primary Sport", "Cricket")
                    InfoRow(Icons.Default.Cake, "Age Group", "18–25")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Settings Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsItem(Icons.Default.Notifications, "Notifications") {}
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    SettingsItem(Icons.Default.Security, "Privacy & Security") {}
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    SettingsItem(Icons.Default.Help, "Help & Support") {}
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    SettingsItem(Icons.Default.Logout, "Logout", color = AthlinkRed, onClick = onLogout)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AthlinkOrange)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
