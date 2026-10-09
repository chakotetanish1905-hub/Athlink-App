package com.athlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.PlayerValidators
import com.athlink.app.data.model.Sports
import com.athlink.app.ui.theme.*

/*
 * Building blocks for player onboarding / Edit Profile. They follow the existing Athlink look:
 * gradient-filled selected chips (like the signup role chips), rounded 10-20dp shapes, orange accents.
 */

/** Circular player photo (data URI or URL), falling back to initials on the brand gradient. */
@Composable
fun PlayerAvatar(photoUrl: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            OrgLogoImage(photoUrl, contentDescription = "Profile photo of $name", modifier = Modifier.fillMaxSize())
        } else {
            val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "AT" }
            Text(initials, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 3).sp)
        }
    }
}

/**
 * Wrapping single-choice chips (radio semantics for accessibility). Tapping the selected chip
 * again calls [onSelect] with null when [allowDeselect] is true (for optional questions).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SingleChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowDeselect: Boolean = false,
    enabled: Boolean = true
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            ChoiceChip(
                text = label(option),
                selected = isSelected,
                enabled = enabled,
                onClick = { onSelect(if (isSelected && allowDeselect) null else option) }
            )
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), RoundedCornerShape(10.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 13.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Large selectable card with a title and a description (skill level and similar choices). */
@Composable
fun ChoiceCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) AthlinkOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) AthlinkOrange else MaterialTheme.colorScheme.outline.copy(0.3f)
        )
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AthlinkOrange)
        }
    }
}

/**
 * Sport picker: exactly one PRIMARY sport (required, from a dropdown) plus optional secondary
 * sports (chips). Two different controls so the main sport can't be confused with "other sports".
 * Uses the same sport list as coaches ([Sports.ALL]) so the two sides can be matched later.
 */
@Composable
fun SportSelector(
    primarySport: String,
    secondarySports: List<String>,
    onPrimarySelected: (String) -> Unit,
    onSecondaryToggled: (String) -> Unit,
    primaryError: String? = null,
    secondaryError: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownField(
            label = fieldLabel("Main sport", required = true),
            options = Sports.ALL,
            selected = primarySport.ifBlank { null },
            optionLabel = { it },
            onSelect = onPrimarySelected,
            leadingIcon = Icons.Default.SportsSoccer,
            errorMessage = primaryError
        )

        Text(
            fieldLabel("Other sports you play", required = false) + " · up to ${PlayerValidators.MAX_SECONDARY_SPORTS}",
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp
        )
        if (primarySport.isBlank()) {
            Text("Choose your main sport first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MultiSelectChips(
                options = Sports.ALL - primarySport,
                selected = secondarySports,
                onToggle = onSecondaryToggled
            )
        }
        FieldError(secondaryError)
    }
}

/** Checkbox + label; the whole row is the touch target. */
@Composable
fun ConsentRow(checked: Boolean, text: String, error: String?, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = AthlinkOrange))
            Text(text, fontSize = 13.sp)
        }
        FieldError(error)
    }
}
