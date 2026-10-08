package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.Sports
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.InfoBanner
import com.athlink.app.ui.components.OutlineButton
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.AthlinkOrange
import com.athlink.app.viewmodel.EventAction
import com.athlink.app.viewmodel.OrganisationEventsViewModel

/**
 * Create (or edit a draft of) an event.
 *  - "Save as draft" is available to every organisation except a suspended one; drafts are private.
 *  - "Publish event" is enabled only for VERIFIED / OFFICIAL_GOVERNMENT, unexpired organisations;
 *    firestore.rules enforce the same check server-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    user: User,
    onBack: () -> Unit,
    onEventCreated: () -> Unit,
    draftId: String? = null,
    onOpenVerification: () -> Unit = {},
    viewModel: OrganisationEventsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.load(user) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var sportExpanded by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    // Editing a draft: prefill once it has loaded.
    val draft = viewModel.draftById(draftId)
    LaunchedEffect(draft?.id) {
        if (draft != null && !prefilled) {
            title = draft.title; description = draft.description; sport = draft.sport; date = draft.date
            location = draft.location; fees = if (draft.fees > 0) draft.fees.toInt().toString() else ""
            maxParticipants = if (draft.maxParticipants > 0) draft.maxParticipants.toString() else ""
            prefilled = true
        }
    }
    LaunchedEffect(state.lastAction) {
        if (state.lastAction == EventAction.PUBLISHED || state.lastAction == EventAction.DRAFT_SAVED) {
            viewModel.consumeAction()
            onEventCreated()
        }
    }

    val sportOptions = (state.organisation?.sports.orEmpty() + Sports.ALL).distinct()

    fun buildEvent() = Event(
        id = draftId.orEmpty(),
        title = title.trim(),
        description = description.trim(),
        sport = sport,
        date = date.trim(),
        location = location.trim(),
        fees = fees.toDoubleOrNull() ?: 0.0,
        maxParticipants = maxParticipants.toIntOrNull() ?: 0,
        createdAt = draft?.createdAt ?: System.currentTimeMillis()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draftId != null) "Edit Draft" else "Create Event", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.publishBlockedReason?.let { reason ->
                InfoBanner(reason, Icons.Default.Lock, AthlinkOrange)
                if (state.canDraft) TextButton(onClick = onOpenVerification) { Text("Go to verification", color = AthlinkOrange) }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Event Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    AppTextField(title, { title = it }, "Event Name *", leadingIcon = Icons.Default.EmojiEvents,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))

                    AppTextField(description, { description = it }, "Description", leadingIcon = Icons.Default.Description,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        singleLine = false, maxLines = 3)

                    // Sport Dropdown (the organisation's own sports first)
                    ExposedDropdownMenuBox(expanded = sportExpanded, onExpandedChange = { sportExpanded = it }) {
                        OutlinedTextField(
                            value = sport,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sport *") },
                            leadingIcon = { Icon(Icons.Default.SportsCricket, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AthlinkOrange)
                        )
                        ExposedDropdownMenu(expanded = sportExpanded, onDismissRequest = { sportExpanded = false }) {
                            sportOptions.forEach { s ->
                                DropdownMenuItem(text = { Text(s) }, onClick = { sport = s; sportExpanded = false })
                            }
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Schedule & Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AppTextField(date, { date = it }, "Event Date (YYYY-MM-DD) *", leadingIcon = Icons.Default.CalendarMonth,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                    AppTextField(location, { location = it }, "Location / Venue *", leadingIcon = Icons.Default.LocationOn,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Registration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AppTextField(fees, { fees = it }, "Registration Fees (₹) *", leadingIcon = Icons.Default.CurrencyRupee,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                    AppTextField(maxParticipants, { maxParticipants = it }, "Max Participants *", leadingIcon = Icons.Default.People,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                }
            }

            state.error?.let { ErrorCard(it) }
            state.message?.let {
                ErrorCard(it)
                LaunchedEffect(it) { kotlinx.coroutines.delay(4000); viewModel.clearMessage() }
            }

            val isValid = title.isNotBlank() && sport.isNotEmpty() && date.isNotBlank() && location.isNotBlank() &&
                fees.toDoubleOrNull() != null && (maxParticipants.toIntOrNull() ?: 0) > 0
            // Drafts may be incomplete: a title is enough.
            if (state.canDraft) {
                OutlineButton(
                    text = "Save as draft",
                    onClick = { viewModel.saveDraft(buildEvent()) },
                    enabled = title.isNotBlank() && !state.isSaving
                )
            }
            PrimaryButton(
                text = if (state.canPublish) "Publish Event" else "Publish (verification required)",
                onClick = { viewModel.publish(buildEvent().copy(id = ""), fromDraftId = draftId) },
                enabled = isValid && state.canPublish,
                isLoading = state.isSaving
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
