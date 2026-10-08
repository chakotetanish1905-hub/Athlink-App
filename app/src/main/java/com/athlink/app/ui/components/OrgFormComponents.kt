package com.athlink.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.ui.theme.*

/*
 * Small form building blocks for the organisation onboarding flow, styled like the existing
 * signup / create-event cards (rounded 20dp cards, orange accents, AppTextField inputs).
 */

/** Adds " *" to required labels and " (optional)" to optional ones. */
fun fieldLabel(label: String, required: Boolean): String = if (required) "$label *" else "$label (optional)"

@Composable
fun FormSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

/** Collapsible "Why do we need this?" explanation. */
@Composable
fun WhyWeNeedThis(text: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.HelpOutline, null, tint = AthlinkOrange, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Why do we need this?", color = AthlinkOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(open) {
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        }
    }
}

/** Info / warning banner. */
@Composable
fun InfoBanner(text: String, icon: ImageVector = Icons.Default.Info, color: Color = AthlinkBlueLight) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Read-only dropdown built on ExposedDropdownMenuBox (same look as the signup sport picker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = leadingIcon?.let { { Icon(it, null) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

/** Multi-select chips (wrapping). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectChips(
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option in selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) Modifier.background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    .border(if (isSelected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onToggle(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    option, fontSize = 12.sp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Label + description + switch. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            description?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AthlinkOrange)
        )
    }
}

/** Inline error line (for groups that aren't a single text field). */
@Composable
fun FieldError(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
}

/** Label/value line for review and profile screens. */
@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}
