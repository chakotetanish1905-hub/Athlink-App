package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.AcademyRequestForm
import com.athlink.app.data.model.OrganisationVerificationPolicy
import com.athlink.app.data.model.PreferredTime
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.User
import com.athlink.app.data.model.displayLocation
import com.athlink.app.data.model.isDirectoryListing
import com.athlink.app.ui.components.OrganisationBadgeChip
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AcademyViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One academy and the "request a session" form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyDetailScreen(
    academyId: String,
    user: User,
    onBack: () -> Unit,
    onOpenBookings: () -> Unit,
    viewModel: AcademyViewModel = hiltViewModel()
) {
    val state by viewModel.detail.collectAsState()
    LaunchedEffect(academyId) { viewModel.openAcademy(academyId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academy", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val academy = state.academy
        if (state.isLoading || academy == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator(color = AthlinkOrange)
                else Text(state.error ?: "This academy is no longer listed.", color = AthlinkMedGray)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            // ── Header ───────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AcademyAvatar(academy.displayName, size = 60)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(academy.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(academy.sports.joinToString(" · "), color = AthlinkOrange, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        RatingPill(academy.googleRating, academy.googleReviewCount)
                    }
                    Spacer(Modifier.height(10.dp))
                    OrganisationBadgeChip(OrganisationVerificationPolicy.badge(academy))
                    Spacer(Modifier.height(10.dp))
                    InfoLine(Icons.Default.LocationOn, academy.displayLocation)
                    if (academy.venueCategory.isNotBlank()) {
                        InfoLine(Icons.Default.Domain, if (academy.venueCategory == "INDOOR") "Indoor venue" else "Outdoor venue")
                    }
                    if (academy.website.isNotBlank()) InfoLine(Icons.Default.Language, academy.website)
                    if (academy.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(academy.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (academy.googleRating != null && academy.ratingSource.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(academy.ratingSource, fontSize = 11.sp, color = AthlinkMedGray)
                    }
                }
            }

            // ── Request form / confirmation ───────────────────────────────
            if (state.sent) {
                SentCard(
                    directory = academy.isDirectoryListing,
                    onOpenBookings = onOpenBookings,
                    onAnother = viewModel::resetRequest
                )
                return@Column
            }

            Text("Request a session", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            Text(
                if (academy.isDirectoryListing)
                    "This academy isn't on Athlink yet. The Athlink team will contact the academy for you and update your request."
                else "The academy will accept or decline your request in the app.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            val form = state.form
            val errors = state.errors

            Label("Sport")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(academy.sports) { sport ->
                    FilterChip(selected = form.sport == sport, onClick = { viewModel.setRequestSport(sport) }, label = { Text(sport) })
                }
            }
            FieldError(errors[AcademyRequestForm.Field.SPORT])

            Label("Preferred date")
            val dates = remember { (0 until 21).map { LocalDate.now().plusDays(it.toLong()) } }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dates) { date ->
                    val value = date.format(SessionPolicy.DATE)
                    DateChip(date, selected = form.preferredDate == value, onClick = { viewModel.setDate(value) })
                }
            }
            FieldError(errors[AcademyRequestForm.Field.DATE])

            Label("Preferred time")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferredTime.entries.forEach { t ->
                    FilterChip(
                        selected = form.preferredTime == t, onClick = { viewModel.setTime(t) },
                        label = { Text(t.label.substringBefore(" ("), fontSize = 13.sp) }
                    )
                }
            }
            Text(form.preferredTime.label, fontSize = 11.sp, color = AthlinkMedGray, modifier = Modifier.padding(horizontal = 20.dp))

            Label("Message (optional)")
            OutlinedTextField(
                value = form.message, onValueChange = viewModel::setMessage,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Your level, goals, days that suit you…") },
                minLines = 3, shape = RoundedCornerShape(12.dp),
                isError = errors.containsKey(AcademyRequestForm.Field.MESSAGE),
                supportingText = { Text("${form.message.trim().length}/${AcademyRequestForm.MAX_MESSAGE}") }
            )
            FieldError(errors[AcademyRequestForm.Field.MESSAGE])

            Label("Phone for this request (optional)")
            OutlinedTextField(
                value = form.contactPhone, onValueChange = viewModel::setPhone,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("10-digit mobile") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = errors.containsKey(AcademyRequestForm.Field.PHONE),
                supportingText = { Text("Shared only with this academy and the Athlink team, so they can call you back.") }
            )
            FieldError(errors[AcademyRequestForm.Field.PHONE])

            state.error?.let {
                Text(it, color = AthlinkRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PrimaryButton(
                    text = "Send request",
                    onClick = { viewModel.sendRequest(user.uid, user.name) },
                    isLoading = state.isSending
                )
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AthlinkOrange, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun FieldError(message: String?) {
    if (message != null) Text(message, color = AthlinkRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

@Composable
internal fun DateChip(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.linearGradient(listOf(GradientStart, GradientEnd))
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            )
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val sub = if (selected) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            Text(date.format(DateTimeFormatter.ofPattern("EEE")), fontSize = 11.sp, color = sub)
            Text(
                date.format(DateTimeFormatter.ofPattern("dd")), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(date.format(DateTimeFormatter.ofPattern("MMM")), fontSize = 11.sp, color = sub)
        }
    }
}

@Composable
private fun SentCard(directory: Boolean, onOpenBookings: () -> Unit, onAnother: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AthlinkGreen.copy(0.10f))
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, tint = AthlinkGreen, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("Request sent", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                if (directory) "The Athlink team will contact the academy and update your request. You can follow it in My bookings."
                else "The academy will reply in the app. You can follow it in My bookings.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            PrimaryButton(text = "Go to My bookings", onClick = onOpenBookings)
            TextButton(onClick = onAnother) { Text("Send another request", color = AthlinkOrange) }
        }
    }
}
