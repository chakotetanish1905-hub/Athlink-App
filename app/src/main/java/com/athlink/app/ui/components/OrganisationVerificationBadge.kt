package com.athlink.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.OrganisationBadge
import com.athlink.app.ui.theme.*

/** Colour + icon for each badge. Only OFFICIAL_GOVERNMENT uses the government (bank) icon and gold. */
private data class BadgeStyle(val color: Color, val icon: ImageVector)

private fun OrganisationBadge.style(): BadgeStyle = when (this) {
    OrganisationBadge.OFFICIAL_GOVERNMENT -> BadgeStyle(AthlinkGold, Icons.Default.AccountBalance)
    OrganisationBadge.VERIFIED -> BadgeStyle(AthlinkGreen, Icons.Default.Verified)
    OrganisationBadge.UNDER_REVIEW -> BadgeStyle(AthlinkBlueLight, Icons.Default.HourglassTop)
    OrganisationBadge.REJECTED -> BadgeStyle(AthlinkRed, Icons.Default.ErrorOutline)
    OrganisationBadge.SUSPENDED -> BadgeStyle(AthlinkRed, Icons.Default.Block)
    OrganisationBadge.EXPIRED -> BadgeStyle(AthlinkOrange, Icons.Default.EventBusy)
    OrganisationBadge.PENDING -> BadgeStyle(AthlinkDarkGray, Icons.Default.PendingActions)
}

/**
 * Verification badge chip. The badge is always DERIVED from stored status + level
 * ([com.athlink.app.data.model.OrganisationVerificationPolicy.badge]); the user can never pick it.
 * Tapping shows what the badge means.
 */
@Composable
fun OrganisationBadgeChip(badge: OrganisationBadge, modifier: Modifier = Modifier, onDark: Boolean = false) {
    var showInfo by remember { mutableStateOf(false) }
    val style = badge.style()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.color.copy(alpha = if (onDark) 0.25f else 0.14f),
        modifier = modifier
            .clickable { showInfo = true }
            .semantics { contentDescription = "${badge.title}. Tap for details" }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, null, tint = style.color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(badge.title, color = if (onDark) Color.White else style.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(style.icon, null, tint = style.color) },
            title = { Text(badge.title) },
            text = { Text(badge.explanation) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } }
        )
    }
}

/** Colour used for a badge, for cards that want to match it. */
fun OrganisationBadge.accentColor(): Color = style().color
fun OrganisationBadge.icon(): ImageVector = style().icon
