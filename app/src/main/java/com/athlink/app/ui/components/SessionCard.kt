package com.athlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.ui.theme.*

@Composable
fun SessionCard(session: Session, modifier: Modifier = Modifier) {
    val (statusColor, statusBg, statusLabel) = when (session.status) {
        SessionStatus.CONFIRMED -> Triple(AthlinkGreen, AthlinkGreen.copy(0.12f), "Confirmed")
        SessionStatus.PENDING   -> Triple(AthlinkGold, AthlinkGold.copy(0.12f), "Pending")
        SessionStatus.REJECTED  -> Triple(AthlinkRed, AthlinkRed.copy(0.12f), "Rejected")
        SessionStatus.COMPLETED -> Triple(AthlinkBlueLight, AthlinkBlueLight.copy(0.12f), "Completed")
        SessionStatus.CANCELLED -> Triple(AthlinkDarkGray, AthlinkDarkGray.copy(0.12f), "Cancelled")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(AthlinkOrange.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SportsCricket, null, tint = AthlinkOrange, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(session.sport, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusBg).padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (session.coachName.isNotEmpty()) "with ${session.coachName}" else "for ${session.playerName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Icon(Icons.Default.CalendarMonth, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(session.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Default.Schedule, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(session.timeSlot, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text("₹${session.price.toInt()}", fontWeight = FontWeight.Bold, color = AthlinkOrange, fontSize = 15.sp)
            }
        }
    }
}
