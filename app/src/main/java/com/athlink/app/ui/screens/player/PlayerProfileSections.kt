package com.athlink.app.ui.screens.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.*
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The sections of the player profile form. Each one is a step in onboarding and a card on the
 * Edit Profile page, so both flows share the same fields, labels and validation.
 */
class PlayerFormContext(
    val form: PlayerProfileForm,
    val errors: Map<PlayerField, String>,
    val enabled: Boolean,
    /** Date of birth can't be changed by the player once the profile is COMPLETE (minor safety; rules agree). */
    val dobLocked: Boolean = false,
    val onUpdate: (PlayerField?, (PlayerProfileForm) -> PlayerProfileForm) -> Unit
) {
    fun error(field: PlayerField): String? = errors[field]
}

// ── Step 1: About you ───────────────────────────────────────────────────────

@Composable
fun AboutYouSection(
    ctx: PlayerFormContext,
    isPreparingPhoto: Boolean,
    onPickPhoto: (PickedFile) -> Unit,
    onRemovePhoto: () -> Unit
) {
    val f = ctx.form
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickPhoto(readPickedFile(context, uri))
    }

    FormSection("About you", "Your name and photo are shown on your player profile.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(f.photoUrl, f.fullName, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fieldLabel("Profile photo", required = false), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = ctx.enabled && !isPreparingPhoto,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkOrange)
                    ) {
                        if (isPreparingPhoto) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AthlinkOrange)
                        else Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPreparingPhoto) "Preparing…" else if (f.photoUrl.isBlank()) "Add photo" else "Change")
                    }
                    if (f.photoUrl.isNotBlank() && !isPreparingPhoto) {
                        TextButton(onClick = onRemovePhoto, enabled = ctx.enabled) { Text("Remove") }
                    }
                }
            }
        }

        AppTextField(
            f.fullName, { v -> ctx.onUpdate(PlayerField.FULL_NAME) { it.copy(fullName = v) } },
            fieldLabel("Full name", required = true), leadingIcon = Icons.Default.Person,
            isError = ctx.error(PlayerField.FULL_NAME) != null, errorMessage = ctx.error(PlayerField.FULL_NAME).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )

        DateOfBirthField(
            iso = f.dateOfBirth,
            error = ctx.error(PlayerField.DATE_OF_BIRTH),
            enabled = ctx.enabled && !ctx.dobLocked,
            locked = ctx.dobLocked,
            onChange = { iso -> ctx.onUpdate(PlayerField.DATE_OF_BIRTH) { it.copy(dateOfBirth = iso) } }
        )
        if (f.dateOfBirth.isNotBlank() && ctx.error(PlayerField.DATE_OF_BIRTH) == null && f.isMinor()) {
            InfoBanner(
                "You're under 18. Please complete your profile with a parent or guardian. " +
                    "Your date of birth is never shown to coaches or other players.",
                Icons.Default.FamilyRestroom, AthlinkBlueLight
            )
        }

        DropdownField(
            label = fieldLabel("Gender", required = false),
            options = Gender.entries,
            selected = f.gender,
            optionLabel = { it.label },
            onSelect = { g -> ctx.onUpdate(null) { it.copy(gender = g) } },
            leadingIcon = Icons.Default.Wc,
            enabled = ctx.enabled
        )

        AppTextField(
            f.phoneNumber,
            { v -> if (v.length <= 20) ctx.onUpdate(PlayerField.PHONE) { it.copy(phoneNumber = v) } },
            fieldLabel("Phone number", required = false), leadingIcon = Icons.Default.Phone,
            isError = ctx.error(PlayerField.PHONE) != null, errorMessage = ctx.error(PlayerField.PHONE).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
        )
        InfoBanner("Your date of birth, gender and phone number are private: only you can see them.", Icons.Default.Lock, AthlinkGreen)
    }

    FormSection("Where you play", "Used to suggest coaches and events near you. We never ask for your address.") {
        AppTextField(
            f.country, { v -> ctx.onUpdate(PlayerField.COUNTRY) { it.withCountry(v) } },
            fieldLabel("Country", required = true), leadingIcon = Icons.Default.Public,
            isError = ctx.error(PlayerField.COUNTRY) != null, errorMessage = ctx.error(PlayerField.COUNTRY).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        if (PlayerLocation.isIndia(f.country)) {
            DropdownField(
                label = fieldLabel("State / Union Territory", required = true),
                options = PlayerLocation.INDIAN_STATES,
                selected = f.state.ifBlank { null },
                optionLabel = { it },
                onSelect = { s -> ctx.onUpdate(PlayerField.STATE) { it.copy(state = s) } },
                leadingIcon = Icons.Default.Map,
                errorMessage = ctx.error(PlayerField.STATE),
                enabled = ctx.enabled
            )
        } else {
            AppTextField(
                f.state, { v -> ctx.onUpdate(PlayerField.STATE) { it.copy(state = v) } },
                fieldLabel("State / province", required = true), leadingIcon = Icons.Default.Map,
                isError = ctx.error(PlayerField.STATE) != null, errorMessage = ctx.error(PlayerField.STATE).orEmpty(),
                enabled = ctx.enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            )
        }
        AppTextField(
            f.city, { v -> ctx.onUpdate(PlayerField.CITY) { it.copy(city = v) } },
            fieldLabel("City / town", required = true), leadingIcon = Icons.Default.LocationCity,
            isError = ctx.error(PlayerField.CITY) != null, errorMessage = ctx.error(PlayerField.CITY).orEmpty(),
            supportingText = f.region.takeIf { it.isNotBlank() }?.let { "Region: $it" },
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done)
        )
        DropdownField(
            label = fieldLabel("What kind of place is it?", required = false),
            options = AreaType.entries,
            selected = f.areaType,
            optionLabel = { it.label },
            onSelect = { a -> ctx.onUpdate(null) { it.copy(areaType = a) } },
            leadingIcon = Icons.Default.Landscape,
            enabled = ctx.enabled
        )
        WhyWeNeedThis("Athlink is built to help players in smaller towns and rural areas find coaches. Knowing the kind of place you live in helps us see where coaching is hardest to find.")
    }
}

/** Read-only field that opens a Material 3 date picker. Stores ISO yyyy-MM-dd. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthField(iso: String, error: String?, enabled: Boolean, locked: Boolean, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppTextField(
            value = PlayerAge.display(iso),
            onValueChange = {},
            label = fieldLabel("Date of birth", required = true),
            leadingIcon = Icons.Default.Cake,
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date") },
            isError = error != null,
            errorMessage = error.orEmpty(),
            supportingText = if (locked) "Never shown publicly · contact support to correct it" else "Never shown publicly",
            enabled = enabled
        )
        // Transparent overlay so the whole field opens the picker (the text field itself is not typed into).
        Box(Modifier.matchParentSize().clickable(enabled = enabled, onClickLabel = "Choose date of birth") { open = true })
    }
    if (open) {
        val today = LocalDate.now()
        val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val initial = PlayerAge.parse(iso) ?: today.minusYears(18)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = (today.year - PlayerAge.MAX_AGE)..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(PlayerAge.format(picked))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState, title = { Text("Date of birth", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

// ── Step 2: Sport ───────────────────────────────────────────────────────────

@Composable
fun SportSection(ctx: PlayerFormContext) {
    FormSection("Your sports", "Your main sport decides which details we ask for next.") {
        SportSelector(
            primarySport = ctx.form.primarySport,
            secondarySports = ctx.form.secondarySports,
            onPrimarySelected = { s -> ctx.onUpdate(PlayerField.PRIMARY_SPORT) { it.withPrimarySport(s) } },
            onSecondaryToggled = { s -> ctx.onUpdate(PlayerField.SECONDARY_SPORTS) { it.toggleSecondarySport(s) } },
            primaryError = ctx.error(PlayerField.PRIMARY_SPORT),
            secondaryError = ctx.error(PlayerField.SECONDARY_SPORTS)
        )
    }
}

// ── Step 3: Your game (all optional) ────────────────────────────────────────

@Composable
fun GameSection(ctx: PlayerFormContext) {
    val f = ctx.form
    val attributes = f.sportAttributes
    if (attributes.isNotEmpty()) {
        FormSection("${f.primarySport} details", "All optional. Tap an answer again to clear it.") {
            attributes.forEach { attr ->
                Text(attr.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                SingleChoiceChips(
                    options = attr.options,
                    selected = f.sportProfile[attr.key],
                    label = { it },
                    onSelect = { v -> ctx.onUpdate(PlayerField.SPORT_PROFILE) { it.withSportAnswer(attr.key, v) } },
                    allowDeselect = true,
                    enabled = ctx.enabled
                )
            }
            FieldError(ctx.error(PlayerField.SPORT_PROFILE))
        }
    }

    FormSection("Experience", "All optional. Add as much or as little as you like.") {
        AppTextField(
            f.yearsOfExperience,
            { v -> if (v.length <= 2 && v.all { it.isDigit() }) ctx.onUpdate(PlayerField.EXPERIENCE_YEARS) { it.copy(yearsOfExperience = v) } },
            fieldLabel("Years playing ${f.primarySport.ifBlank { "this sport" }}", required = false),
            leadingIcon = Icons.Default.Timeline,
            isError = ctx.error(PlayerField.EXPERIENCE_YEARS) != null, errorMessage = ctx.error(PlayerField.EXPERIENCE_YEARS).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )
        AppTextField(
            f.currentTeam, { v -> ctx.onUpdate(PlayerField.CURRENT_TEAM) { it.copy(currentTeam = v) } },
            fieldLabel("Current team / club", required = false), leadingIcon = Icons.Default.Groups,
            isError = ctx.error(PlayerField.CURRENT_TEAM) != null, errorMessage = ctx.error(PlayerField.CURRENT_TEAM).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        AppTextField(
            f.academy, { v -> ctx.onUpdate(PlayerField.ACADEMY) { it.copy(academy = v) } },
            fieldLabel("Academy", required = false), leadingIcon = Icons.Default.School,
            isError = ctx.error(PlayerField.ACADEMY) != null, errorMessage = ctx.error(PlayerField.ACADEMY).orEmpty(),
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        AppTextField(
            f.ranking, { v -> ctx.onUpdate(PlayerField.RANKING) { it.copy(ranking = v) } },
            fieldLabel("Ranking", required = false), leadingIcon = Icons.Default.Leaderboard,
            isError = ctx.error(PlayerField.RANKING) != null, errorMessage = ctx.error(PlayerField.RANKING).orEmpty(),
            supportingText = "e.g. \"State U-17 #12\" (any ranking system)",
            enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        AppTextField(
            f.achievementsText, { v -> ctx.onUpdate(PlayerField.ACHIEVEMENTS) { it.copy(achievementsText = v) } },
            fieldLabel("Achievements", required = false), leadingIcon = Icons.Default.EmojiEvents,
            isError = ctx.error(PlayerField.ACHIEVEMENTS) != null, errorMessage = ctx.error(PlayerField.ACHIEVEMENTS).orEmpty(),
            supportingText = "One per line, up to ${PlayerValidators.MAX_ACHIEVEMENTS}",
            singleLine = false, maxLines = 6, enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        AppTextField(
            f.bio, { v -> if (v.length <= PlayerValidators.BIO_MAX) ctx.onUpdate(PlayerField.BIO) { it.copy(bio = v) } },
            fieldLabel("About you as a player", required = false), leadingIcon = Icons.Default.Description,
            isError = ctx.error(PlayerField.BIO) != null, errorMessage = ctx.error(PlayerField.BIO).orEmpty(),
            supportingText = "${f.bio.length}/${PlayerValidators.BIO_MAX}",
            singleLine = false, maxLines = 6, enabled = ctx.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}

// ── Step 4: Level ───────────────────────────────────────────────────────────

@Composable
fun LevelSection(ctx: PlayerFormContext) {
    FormSection("Your level in ${ctx.form.primarySport.ifBlank { "your sport" }} *") {
        SkillLevel.entries.forEach { level ->
            ChoiceCard(
                title = level.label,
                description = level.description,
                selected = ctx.form.skillLevel == level,
                onClick = { if (ctx.enabled) ctx.onUpdate(PlayerField.SKILL_LEVEL) { it.copy(skillLevel = level) } }
            )
        }
        FieldError(ctx.error(PlayerField.SKILL_LEVEL))
    }
}

// ── Step 5: Goals ───────────────────────────────────────────────────────────

@Composable
fun GoalsSection(ctx: PlayerFormContext) {
    FormSection("What do you want from Athlink? *", "Choose at least one.") {
        val byLabel = PlayerGoal.entries.associateBy { it.label }
        MultiSelectChips(
            options = PlayerGoal.entries.map { it.label },
            selected = ctx.form.goals.map { it.label },
            onToggle = { label -> byLabel[label]?.let { g -> ctx.onUpdate(PlayerField.GOALS) { it.toggleGoal(g) } } },
            enabled = ctx.enabled
        )
        FieldError(ctx.error(PlayerField.GOALS))
    }
}

// ── Step 6: Training preferences ────────────────────────────────────────────

@Composable
fun TrainingSection(ctx: PlayerFormContext) {
    val f = ctx.form
    FormSection("Coaching preferences") {
        Text(fieldLabel("Coaching format", required = true), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        SingleChoiceChips(CoachingFormat.entries, f.coachingFormat, { it.label },
            { v -> ctx.onUpdate(PlayerField.COACHING_FORMAT) { it.copy(coachingFormat = v) } }, enabled = ctx.enabled)
        FieldError(ctx.error(PlayerField.COACHING_FORMAT))

        Text(fieldLabel("Preferred training time", required = true), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        SingleChoiceChips(TrainingTime.entries, f.trainingTime, { it.label },
            { v -> ctx.onUpdate(PlayerField.TRAINING_TIME) { it.copy(trainingTime = v) } }, enabled = ctx.enabled)
        FieldError(ctx.error(PlayerField.TRAINING_TIME))

        Text(fieldLabel("How far can you travel to train?", required = true), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        SingleChoiceChips(TravelDistance.entries, f.travelDistance, { it.label },
            { v -> ctx.onUpdate(PlayerField.TRAVEL_DISTANCE) { it.copy(travelDistance = v) } }, enabled = ctx.enabled)
        FieldError(ctx.error(PlayerField.TRAVEL_DISTANCE))
    }

    FormSection("Notifications", "What Athlink may notify you about. You can change this any time.") {
        val n = f.notifications
        ToggleRow("Events & tournaments", n.eventNotifications, { v -> ctx.onUpdate(null) { it.copy(notifications = n.copy(eventNotifications = v)) } }, enabled = ctx.enabled)
        ToggleRow("Coaching & bookings", n.coachingNotifications, { v -> ctx.onUpdate(null) { it.copy(notifications = n.copy(coachingNotifications = v)) } }, enabled = ctx.enabled)
        ToggleRow("Chat messages", n.chatNotifications, { v -> ctx.onUpdate(null) { it.copy(notifications = n.copy(chatNotifications = v)) } }, enabled = ctx.enabled)
        ToggleRow("Tips & content", n.contentNotifications, { v -> ctx.onUpdate(null) { it.copy(notifications = n.copy(contentNotifications = v)) } }, enabled = ctx.enabled)
    }
}

// ── Step 7: Ready (summary + consent for older accounts) ────────────────────

@Composable
fun ReadySection(ctx: PlayerFormContext, onEdit: (PlayerOnboardingStep) -> Unit) {
    val f = ctx.form
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerAvatar(f.photoUrl, f.fullName, 88.dp)
        Spacer(Modifier.height(10.dp))
        Text(f.fullName.trim(), fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(
            listOfNotNull(f.primarySport.ifBlank { null }, f.skillLevel?.label).joinToString(" · "),
            color = AthlinkOrange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
        )
        Text(listOf(f.city.trim(), f.state.trim()).filter { it.isNotEmpty() }.joinToString(", "),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SummaryCard("About you", onEdit = { onEdit(PlayerOnboardingStep.ABOUT) }) {
        KeyValueRow("Date of birth", PlayerAge.display(f.dateOfBirth) + "  (private)")
        KeyValueRow("Location", listOf(f.city, f.state, f.country).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "))
        KeyValueRow("Region", f.region)
    }
    SummaryCard("Sports", onEdit = { onEdit(PlayerOnboardingStep.SPORT) }) {
        KeyValueRow("Main sport", f.primarySport)
        KeyValueRow("Other sports", f.secondarySports.joinToString(", "))
        SportProfiles.describe(f.primarySport, f.sportProfile).forEach { (label, value) -> KeyValueRow(label, value) }
    }
    SummaryCard("Level & goals", onEdit = { onEdit(PlayerOnboardingStep.LEVEL) }) {
        KeyValueRow("Level", f.skillLevel?.label.orEmpty())
        KeyValueRow("Goals", f.goals.joinToString(", ") { it.label })
    }
    SummaryCard("Training", onEdit = { onEdit(PlayerOnboardingStep.TRAINING) }) {
        KeyValueRow("Format", f.coachingFormat?.label.orEmpty())
        KeyValueRow("Time", f.trainingTime?.label.orEmpty())
        KeyValueRow("Distance", f.travelDistance?.label.orEmpty())
    }

    if (!f.consentAlreadyRecorded) {
        FormSection("Before you continue") {
            ConsentRow(
                checked = f.termsAccepted, text = "I agree to the Athlink Terms of Service", error = ctx.error(PlayerField.TERMS),
                onChange = { v -> ctx.onUpdate(PlayerField.TERMS) { it.copy(termsAccepted = v) } }
            )
            ConsentRow(
                checked = f.privacyAccepted, text = "I have read the Athlink Privacy Policy", error = ctx.error(PlayerField.PRIVACY),
                onChange = { v -> ctx.onUpdate(PlayerField.PRIVACY) { it.copy(privacyAccepted = v) } }
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, onEdit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit", color = AthlinkOrange) }
            }
            content()
        }
    }
}
