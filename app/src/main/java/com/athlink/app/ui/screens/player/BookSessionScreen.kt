package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.CoachViewModel
import com.athlink.app.viewmodel.SessionViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSessionScreen(
    coachId: String,
    user: User,
    onBack: () -> Unit,
    onBookingConfirmed: () -> Unit,
    coachViewModel: CoachViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val coachState by coachViewModel.state.collectAsState()
    val sessionState by sessionViewModel.state.collectAsState()

    val coach = coachState.coaches.find { it.uid == coachId }
        ?: coachState.selectedCoach

    LaunchedEffect(sessionState.bookingSuccess) {
        if (sessionState.bookingSuccess) {
            sessionViewModel.clearBookingSuccess()
            onBookingConfirmed()
        }
    }

    // Generate next 14 days
    val dates = remember {
        (0..13).map { LocalDate.now().plusDays(it.toLong()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Session", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (coach == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AthlinkOrange)
                }
                return@Column
            }

            // Coach Summary Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = AthlinkGold, modifier = Modifier.size(16.dp))
                            Text(" ${coach.rating} • ${coach.experience} yrs exp", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("₹${coach.hourlyRate.toInt()}\n/hr", fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Date Picker
            Text("Select Date", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dates) { date ->
                    val formatted = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val dayName = date.format(DateTimeFormatter.ofPattern("EEE"))
                    val dayNum = date.format(DateTimeFormatter.ofPattern("dd"))
                    val month = date.format(DateTimeFormatter.ofPattern("MMM"))
                    val selected = sessionState.selectedDate == formatted

                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Brush.linearGradient(listOf(GradientStart, GradientEnd)) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                            .border(if (!selected) 1.dp else 0.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(14.dp))
                            .clickable { sessionViewModel.selectDate(formatted) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayName, fontSize = 11.sp, color = if (selected) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Text(dayNum, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                            Text(month, fontSize = 11.sp, color = if (selected) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Time Slot
            Text("Select Time Slot", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sessionState.availableTimeSlots.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { slot ->
                            val selected = sessionState.selectedTimeSlot == slot
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Brush.horizontalGradient(listOf(GradientStart, GradientEnd)) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                                    .border(if (!selected) 1.dp else 0.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(12.dp))
                                    .clickable { sessionViewModel.selectTimeSlot(slot) }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(slot, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Booking Summary
            if (sessionState.selectedDate.isNotEmpty() && sessionState.selectedTimeSlot.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AthlinkOrange.copy(0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Booking Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AthlinkOrange)
                        Spacer(Modifier.height(10.dp))
                        SummaryRow(Icons.Default.Person, "Coach", coach.name)
                        SummaryRow(Icons.Default.CalendarMonth, "Date", sessionState.selectedDate)
                        SummaryRow(Icons.Default.Schedule, "Time", sessionState.selectedTimeSlot)
                        SummaryRow(Icons.Default.LocationOn, "Location", coach.location)
                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = AthlinkOrange.copy(0.2f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Amount", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("₹${coach.hourlyRate.toInt()}", fontWeight = FontWeight.ExtraBold, color = AthlinkOrange, fontSize = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton(
                    text = "Confirm Booking",
                    onClick = { sessionViewModel.bookSession(coach, user.uid, user.name) },
                    enabled = sessionState.selectedDate.isNotEmpty() && sessionState.selectedTimeSlot.isNotEmpty(),
                    isLoading = sessionState.isLoading
                )
            }

            sessionState.error?.let {
                Text(it, color = AthlinkRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }

            Spacer(Modifier.height(24.dp))
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
