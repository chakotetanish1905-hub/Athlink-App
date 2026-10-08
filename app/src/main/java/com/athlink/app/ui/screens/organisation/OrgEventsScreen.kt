package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.AthlinkBottomBar
import com.athlink.app.ui.components.InfoBanner
import com.athlink.app.ui.components.orgNavItems
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.EventAction
import com.athlink.app.viewmodel.OrganisationEventsViewModel

/** The organisation's own events: published (from `events`) and private drafts (from `eventDrafts`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgEventsScreen(
    user: User,
    navController: NavHostController,
    onCreateEvent: () -> Unit,
    onEditDraft: (String) -> Unit,
    viewModel: OrganisationEventsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user.uid) { viewModel.load(user) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(state.lastAction) {
        when (state.lastAction) {
            EventAction.PUBLISHED -> { snackbar.showSnackbar("Event published."); tab = 0 }
            EventAction.DRAFT_DELETED -> snackbar.showSnackbar("Draft deleted.")
            else -> Unit
        }
        if (state.lastAction != null) viewModel.consumeAction()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Events", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh", tint = AthlinkOrange) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { AthlinkBottomBar(orgNavItems, navBackStack?.destination?.route) { r -> navController.navigate(r) { launchSingleTop = true } } },
        floatingActionButton = {
            if (state.canDraft) {
                ExtendedFloatingActionButton(onClick = onCreateEvent, containerColor = AthlinkOrange, contentColor = androidx.compose.ui.graphics.Color.White) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New event", fontWeight = FontWeight.Bold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, contentColor = AthlinkOrange) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Published (${state.published.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Drafts (${state.drafts.size})") })
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.publishBlockedReason?.let { reason -> item { InfoBanner(reason, Icons.Default.Lock, AthlinkOrange) } }
                state.error?.let { item { ErrorCard(it) } }
                if (state.isLoading && state.published.isEmpty() && state.drafts.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp)) { CircularProgressIndicator(color = AthlinkOrange) } }
                }
                val list = if (tab == 0) state.published else state.drafts
                if (!state.isLoading && list.isEmpty()) {
                    item { EmptyEventsCard(if (tab == 0) "No published events yet." else "No drafts. Tap New event to start one.") }
                }
                items(list, key = { it.id.ifBlank { "e${it.createdAt}${it.title}" } }) { event ->
                    if (tab == 0) EventCard(event)
                    else EventCard(event) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEditDraft(event.id) }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.publish(event, fromDraftId = event.id) },
                                enabled = state.canPublish && !state.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = AthlinkOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(if (state.canPublish) Icons.Default.Publish else Icons.Default.Lock, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Publish", fontSize = 12.sp)
                            }
                            IconButton(onClick = { confirmDelete = event.id }) { Icon(Icons.Default.Delete, "Delete draft", tint = AthlinkRed) }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete draft?") },
            text = { Text("This draft will be removed permanently.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDraft(id); confirmDelete = null }) { Text("Delete", color = AthlinkRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}
