package com.athlink.app.ui.screens.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.AvailabilityRules
import com.athlink.app.data.model.User
import com.athlink.app.data.model.Weekday
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.CoachScheduleState
import com.athlink.app.viewmodel.CoachScheduleViewModel

/**
 * The coach's weekly availability. Players see 1-hour slots inside these ranges for the next
 * 14 days (BookingSlots); a slot already booked is held for that player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScheduleScreen(
    user: User,
    onBack: () -> Unit,
    viewModel: CoachScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(user.uid) { viewModel.load(user.uid) }
    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) { snackbar.showSnackbar(text); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly availability", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { CoachVisibilityCard(state) }

            item { Text("Add a time range", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Weekday.entries) { d ->
                        FilterChip(selected = state.day == d, onClick = { viewModel.setDay(d) }, label = { Text(d.shortLabel) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeDropdown("From", state.start, Modifier.weight(1f), viewModel::setStart)
                    TimeDropdown("To", state.end, Modifier.weight(1f), viewModel::setEnd)
                }
            }
            state.formError?.let { item { Text(it, color = AthlinkRed, fontSize = 13.sp) } }
            item { PrimaryButton(text = "Add ${state.day.label} ${state.start}–${state.end}", onClick = { viewModel.addRange(user.uid) }, isLoading = state.isSaving) }

            item { Text("Your week", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
            val byDay = AvailabilityRules.byDay(state.ranges)
            if (byDay.isEmpty()) {
                item {
                    Text(
                        "No times yet. Players can't book you until you add at least one range.",
                        color = AthlinkMedGray, fontSize = 13.sp
                    )
                }
            }
            byDay.forEach { (day, ranges) ->
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(day.label, fontWeight = FontWeight.SemiBold)
                            ranges.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = AthlinkOrange, modifier = Modifier.size(16.dp))
                                    Text("  ${r.startTime} – ${r.endTime}", modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.deleteRange(user.uid, r) }) {
                                        Icon(Icons.Default.Delete, "Remove", tint = AthlinkRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tells the coach whether players can find and book them, and why not. */
@Composable
fun CoachVisibilityCard(state: CoachScheduleState, onOpenSchedule: (() -> Unit)? = null) {
    val visible = state.visibleToPlayers
    val color = if (visible) AthlinkGreen else AthlinkGold
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (visible) Icons.Default.Visibility else Icons.Default.HourglassTop, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (visible) "Players can find and book you" else "Players can't see you yet",
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                when {
                    !visible -> "Your profile is waiting for Athlink approval. Once approved you'll appear in Find Coaches."
                    state.ranges.isEmpty() -> "Add your weekly availability so players have times to book."
                    else -> "${state.ranges.size} weekly time range(s) set."
                },
                fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
            if (onOpenSchedule != null) {
                TextButton(onClick = onOpenSchedule, contentPadding = PaddingValues(0.dp)) {
                    Text(if (state.ranges.isEmpty()) "Set availability" else "Edit availability", color = AthlinkOrange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(label: String, value: String, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AvailabilityRules.TIME_OPTIONS.forEach { t ->
                DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(t); open = false })
            }
        }
    }
}
