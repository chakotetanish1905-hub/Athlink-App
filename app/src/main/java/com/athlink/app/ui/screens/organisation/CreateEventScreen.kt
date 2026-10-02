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
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.AthlinkOrange
import com.athlink.app.viewmodel.EventViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    user: User,
    onBack: () -> Unit,
    onEventCreated: () -> Unit,
    viewModel: EventViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var sportExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            viewModel.clearCreateSuccess()
            onEventCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event", fontWeight = FontWeight.Bold) },
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
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Event Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    AppTextField(title, { title = it }, "Event Name *", leadingIcon = Icons.Default.EmojiEvents,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))

                    AppTextField(description, { description = it }, "Description", leadingIcon = Icons.Default.Description,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        singleLine = false, maxLines = 3)

                    // Sport Dropdown
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
                            DummyData.sports.forEach { s ->
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

            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                    Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val isValid = title.isNotEmpty() && sport.isNotEmpty() && date.isNotEmpty() && location.isNotEmpty() && fees.isNotEmpty() && maxParticipants.isNotEmpty()
            PrimaryButton(
                text = "Create Event",
                onClick = {
                    viewModel.createEvent(Event(
                        organisationId = user.uid,
                        organisationName = user.name,
                        title = title,
                        description = description,
                        sport = sport,
                        date = date,
                        location = location,
                        fees = fees.toDoubleOrNull() ?: 0.0,
                        maxParticipants = maxParticipants.toIntOrNull() ?: 0
                    ))
                },
                enabled = isValid,
                isLoading = state.isLoading
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
