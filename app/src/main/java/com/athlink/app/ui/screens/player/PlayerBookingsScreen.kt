package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.AcademyRequestStatus
import com.athlink.app.data.model.PreferredTime
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.User
import com.athlink.app.data.model.statusEnum
import com.athlink.app.ui.components.SessionCard
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.SessionViewModel

/** The player's real coach bookings and academy requests (no dummy data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBookingsScreen(
    user: User,
    onBack: () -> Unit,
    onFindCoach: () -> Unit,
    onFindAcademy: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Upcoming", "Academy requests", "Past")
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(user.uid) { viewModel.loadPlayerSessions(user.uid) }
    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) { snackbar.showSnackbar(text); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My bookings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { viewModel.loadPlayerSessions(user.uid) }) { Icon(Icons.Default.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, contentColor = AthlinkOrange) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            if (state.isLoading && !state.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        if (state.upcoming.isEmpty()) item { Empty("No upcoming sessions", "Book a verified coach", onFindCoach) }
                        items(state.upcoming, key = { it.id }) { session ->
                            Column {
                                SessionCard(session)
                                if (SessionPolicy.playerMayCancel(session)) {
                                    TextButton(
                                        onClick = { viewModel.cancelBooking(session.id) },
                                        enabled = state.updatingId != session.id,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Cancel booking", color = AthlinkRed) }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (state.academyRequests.isEmpty()) item { Empty("No academy requests", "Ask an academy for a session", onFindAcademy) }
                        items(state.academyRequests, key = { it.requestId }) { request ->
                            AcademyRequestCard(
                                request,
                                cancelling = state.updatingId == request.requestId,
                                onCancel = { viewModel.cancelAcademyRequest(request.requestId) }
                            )
                        }
                    }
                    else -> {
                        if (state.history.isEmpty()) item { Empty("Nothing here yet", "Finished, cancelled and declined sessions show here", null) }
                        items(state.history, key = { it.id }) { SessionCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(title: String, action: String, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.EventNote, null, tint = AthlinkMedGray, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        if (onAction != null) TextButton(onClick = onAction) { Text(action, color = AthlinkOrange) }
        else Text(action, fontSize = 12.sp, color = AthlinkMedGray)
    }
}

@Composable
fun AcademyRequestCard(
    request: AcademyRequest,
    cancelling: Boolean = false,
    onCancel: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val status = request.statusEnum
    val color = when (status) {
        AcademyRequestStatus.PENDING -> AthlinkGold
        AcademyRequestStatus.ACCEPTED -> AthlinkGreen
        AcademyRequestStatus.DECLINED -> AthlinkRed
        AcademyRequestStatus.CANCELLED -> AthlinkDarkGray
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.organisationName.ifBlank { request.playerName }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(request.sport, color = AthlinkOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(status.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                Text(" ${request.preferredDate}   ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.Schedule, null, tint = AthlinkOrange, modifier = Modifier.size(14.dp))
                Text(" ${PreferredTime.fromStored(request.preferredTime).label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (request.organisationLocation.isNotBlank()) {
                Text(request.organisationLocation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (request.message.isNotBlank()) {
                Text("“${request.message}”", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (request.responseNote.isNotBlank()) {
                Surface(color = color.copy(0.08f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Reply: ${request.responseNote}", fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
            } else if (status == AcademyRequestStatus.PENDING && request.organisationOwnerUid.isBlank()) {
                Text(
                    "The Athlink team is contacting this academy for you.",
                    fontSize = 11.sp, color = AthlinkMedGray, modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (onCancel != null && status == AcademyRequestStatus.PENDING) {
                TextButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.align(Alignment.End)) {
                    Text("Withdraw request", color = AthlinkRed)
                }
            }
            footer?.invoke()
        }
    }
}

