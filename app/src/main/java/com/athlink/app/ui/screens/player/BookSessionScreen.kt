package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.BookingSlots
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.User
import com.athlink.app.data.model.isDirectoryCoach
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.BookingViewModel
import java.time.format.DateTimeFormatter

/**
 * Book a real coach. Dates and times come only from the coach's weekly availability
 * (`coaches/{uid}/availability`); a slot already held by another booking is refused by the rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSessionScreen(
    coachId: String,
    user: User,
    onBack: () -> Unit,
    onBookingConfirmed: () -> Unit,
    onRequestAtAcademy: (academyId: String, coachId: String) -> Unit = { _, _ -> },
    viewModel: BookingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(coachId) { viewModel.load(coachId) }
    LaunchedEffect(state.booked) {
        if (state.booked != null) {
            viewModel.consumeBooked()
            onBookingConfirmed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Session", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val coach = state.coach
        if (state.isLoading || coach == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator(color = AthlinkOrange)
                else Text(state.error ?: "This coach couldn't be loaded.", color = AthlinkMedGray, modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // ── Coach summary ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(coach.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(coach.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(coach.sport, color = AthlinkOrange, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                if (coach.reviewCount > 0) append("★ ${coach.rating} (${coach.reviewCount}) • ")
                                append(if (coach.experience > 0) "${coach.experience} yrs exp" else coach.location)
                            },
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (coach.hourlyRate > 0) {
                        Text("₹${coach.hourlyRate.toInt()}\n/hr", fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 16.sp)
                    }
                }
            }

            if (state.bookable && coach.isDirectoryCoach) {
                DirectoryCoachCentres(
                    sport = coach.sport,
                    academyIds = coach.academyIds,
                    academyNames = coach.academyNames,
                    onRequest = { academyId -> onRequestAtAcademy(academyId, coach.uid) }
                )
                return@Column
            }
            if (!state.bookable) {
                Notice("This coach isn't taking bookings right now (their profile isn't verified and active).")
                return@Column
            }
            if (state.dates.isEmpty()) {
                Notice(
                    if (state.availability.isEmpty()) "This coach hasn't added their weekly availability yet. Try again later or message them."
                    else "No free times in the next ${BookingSlots.BOOKING_WINDOW_DAYS} days."
                )
                state.error?.let { Notice(it) }
                return@Column
            }

            // ── Date ───────────────────────────────────────────────────
            Text("Select date", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.dates) { date ->
                    DateChip(date, selected = state.selectedDate == date, onClick = { viewModel.selectDate(date) })
                }
            }

            // ── Time ───────────────────────────────────────────────────
            Text("Select time", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
            if (state.slots.isEmpty()) {
                Text("No free times left on this day. Pick another date.", color = AthlinkMedGray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { slot ->
                            val selected = state.selectedSlot == slot
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                                        else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                                    )
                                    .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectSlot(slot) }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    slot.label, fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                value = state.notes, onValueChange = viewModel::updateNotes,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                label = { Text("Note for the coach (optional)") }, minLines = 2, shape = RoundedCornerShape(12.dp)
            )

            // ── Summary ────────────────────────────────────────────────
            val date = state.selectedDate
            val slot = state.selectedSlot
            if (date != null && slot != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AthlinkOrange.copy(0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Booking summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AthlinkOrange)
                        Spacer(Modifier.height(10.dp))
                        SummaryRow(Icons.Default.Person, "Coach", coach.name)
                        SummaryRow(Icons.Default.CalendarMonth, "Date", date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                        SummaryRow(Icons.Default.Schedule, "Time", slot.label)
                        if (coach.location.isNotBlank()) SummaryRow(Icons.Default.LocationOn, "Location", coach.location)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AthlinkOrange.copy(0.2f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Price", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                if (coach.hourlyRate > 0) "₹${coach.hourlyRate.toInt()}" else "Ask coach",
                                fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 18.sp
                            )
                        }
                        Text(
                            "The coach confirms or declines your request. Pay the coach directly; no payment is taken in the app.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            state.error?.let {
                Text(it, color = AthlinkRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton(
                    text = "Request booking",
                    onClick = { viewModel.book(user.uid, user.name) },
                    enabled = date != null && slot != null,
                    isLoading = state.isBooking
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EventBusy, null, tint = AthlinkMedGray)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Coaches imported with their academy have no app account or weekly calendar: the player picks a
 * centre and sends a session request (date + time window), which the academy / Athlink confirms.
 */
@Composable
private fun DirectoryCoachCentres(
    sport: String,
    academyIds: List<String>,
    academyNames: List<String>,
    onRequest: (String) -> Unit
) {
    Text(
        if (academyIds.size > 1) "Coaches at ${academyIds.size} centres" else "Coaches at",
        fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
    Text(
        "Request a $sport session at the centre that suits you. You choose a date and time window; the academy confirms the time with you.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)
    )
    Spacer(Modifier.height(8.dp))
    academyIds.forEachIndexed { i, id ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = AthlinkOrange)
                Spacer(Modifier.width(10.dp))
                Text(academyNames.getOrNull(i) ?: "Academy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onRequest(id) },
                    colors = ButtonDefaults.buttonColors(containerColor = AthlinkOrange),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Request") }
            }
        }
    }
    if (academyIds.isEmpty()) {
        Notice("This coach isn't linked to an academy yet.")
    }
    Spacer(Modifier.height(24.dp))
}
