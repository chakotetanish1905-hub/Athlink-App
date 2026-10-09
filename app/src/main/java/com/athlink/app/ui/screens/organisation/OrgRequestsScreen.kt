package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.athlink.app.data.model.User
import com.athlink.app.ui.screens.player.AcademyRequestCard
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.OrgRequestsViewModel

/** Session requests players sent to this organisation; it accepts or declines with a short reply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgRequestsScreen(
    user: User,
    onBack: () -> Unit,
    viewModel: OrgRequestsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.load(user.uid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session requests", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { viewModel.load(user.uid) }) { Icon(Icons.Default.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AthlinkOrange) }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.error?.let { item { ErrorCard(it) } }
            item { Text("Waiting for your answer (${state.pending.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            if (state.pending.isEmpty()) {
                item { Text("No new requests. Players find you in the Academies directory once you're verified.", color = AthlinkMedGray, fontSize = 13.sp) }
            }
            items(state.pending, key = { it.requestId }) { request ->
                AcademyRequestCard(
                    request.copy(organisationName = request.playerName),
                    footer = {
                        if (request.contactPhone.isNotBlank()) {
                            Text("Phone: ${request.contactPhone}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        OutlinedTextField(
                            value = state.replyDrafts[request.requestId].orEmpty(),
                            onValueChange = { viewModel.setReply(request.requestId, it) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            placeholder = { Text("Reply (time, venue, fees…)") },
                            shape = RoundedCornerShape(12.dp), minLines = 2
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.respond(request.requestId, accept = false) },
                                enabled = state.updatingId == null, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkRed)
                            ) { Text("Decline") }
                            Button(
                                onClick = { viewModel.respond(request.requestId, accept = true) },
                                enabled = state.updatingId == null, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AthlinkGreen)
                            ) { Text("Accept") }
                        }
                    }
                )
            }
            if (state.answered.isNotEmpty()) {
                item { Text("Answered", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
                items(state.answered, key = { it.requestId }) { AcademyRequestCard(it.copy(organisationName = it.playerName)) }
            }
        }
    }
}
