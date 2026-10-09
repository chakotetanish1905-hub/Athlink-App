package com.athlink.app.ui.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.SessionCard
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSessionsScreen(
    user: User,
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Requests", "Confirmed", "All")

    LaunchedEffect(user.uid) { viewModel.loadCoachSessions(user.uid) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) { snackbar.showSnackbar(text); viewModel.clearMessage() }
    }

    val filteredSessions = when (selectedTab) {
        0 -> state.sessions.filter { it.status == SessionStatus.PENDING }
        1 -> state.sessions.filter { it.status == SessionStatus.CONFIRMED }
        else -> state.sessions.sortedWith(compareByDescending<com.athlink.app.data.model.Session> { it.date }.thenByDescending { it.startTime })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Sessions", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AthlinkOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AthlinkOrange
                    )
                }
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = {
                            Text(
                                tab,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AthlinkOrange)
                }
            } else if (filteredSessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            null,
                            tint = AthlinkMedGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No sessions here", color = AthlinkMedGray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSessions) { session ->
                        if (session.status == SessionStatus.PENDING) {
                            SessionRequestCard(
                                session = session,
                                onAccept = { viewModel.updateStatus(session.id, SessionStatus.CONFIRMED) },
                                onReject = { viewModel.updateStatus(session.id, SessionStatus.REJECTED) }
                            )
                        } else if (session.status == SessionStatus.CONFIRMED) {
                            Column {
                                SessionCard(session)
                                Row(Modifier.align(Alignment.End)) {
                                    TextButton(
                                        onClick = { viewModel.updateStatus(session.id, SessionStatus.CANCELLED) },
                                        enabled = state.updatingId != session.id
                                    ) { Text("Cancel", color = AthlinkRed) }
                                    TextButton(
                                        onClick = { viewModel.updateStatus(session.id, SessionStatus.COMPLETED) },
                                        enabled = state.updatingId != session.id
                                    ) { Text("Mark completed", color = AthlinkGreen) }
                                }
                            }
                        } else {
                            SessionCard(session)
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}

@Composable
fun SessionRequestCard(
    session: com.athlink.app.data.model.Session,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(session.playerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${session.sport} Session",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AthlinkGold.copy(0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Pending",
                        color = AthlinkGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    null,
                    tint = AthlinkOrange,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    " ${session.date}  ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Default.Schedule,
                    null,
                    tint = AthlinkOrange,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    " ${session.timeSlot}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "₹${session.price.toInt()}",
                color = AthlinkOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkRed)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AthlinkGreen)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Accept", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
