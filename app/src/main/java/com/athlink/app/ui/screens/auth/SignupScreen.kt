package com.athlink.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.CoachField
import com.athlink.app.data.model.CoachRegistration
import com.athlink.app.data.model.CoachingLevel
import com.athlink.app.data.model.PlayerSignupField
import com.athlink.app.data.model.PlayerSignupForm
import com.athlink.app.data.model.Sports
import com.athlink.app.data.model.UserRole
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.ConsentRow
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.PLAYER) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    // Coach-only fields (name/email/password above are shared by every role)
    var coachForm by remember { mutableStateOf(CoachRegistration()) }
    val isCoach = selectedRole == UserRole.COACH
    val isOrganisation = selectedRole == UserRole.ORGANISATION
    val isPlayer = selectedRole == UserRole.PLAYER
    val fieldErrors = if (isCoach) state.coachFieldErrors else emptyMap()
    // Player-only consent (Terms + Privacy). Coach / organisation flows are unchanged.
    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    val playerErrors = if (isPlayer) state.playerFieldErrors else emptyMap()
    fun clear(field: CoachField) {
        viewModel.clearCoachFieldError(field)
        when (field) {
            CoachField.NAME -> viewModel.clearPlayerFieldError(PlayerSignupField.NAME)
            CoachField.EMAIL -> viewModel.clearPlayerFieldError(PlayerSignupField.EMAIL)
            CoachField.PASSWORD -> viewModel.clearPlayerFieldError(PlayerSignupField.PASSWORD)
            CoachField.CONFIRM_PASSWORD -> viewModel.clearPlayerFieldError(PlayerSignupField.CONFIRM_PASSWORD)
            else -> Unit
        }
    }
    val nameError = fieldErrors[CoachField.NAME] ?: playerErrors[PlayerSignupField.NAME]
    val emailError = fieldErrors[CoachField.EMAIL] ?: playerErrors[PlayerSignupField.EMAIL]
    val passwordError = fieldErrors[CoachField.PASSWORD] ?: playerErrors[PlayerSignupField.PASSWORD]
    val confirmError = fieldErrors[CoachField.CONFIRM_PASSWORD] ?: playerErrors[PlayerSignupField.CONFIRM_PASSWORD]

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onSignupSuccess()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp)
                .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkDeepBlue.copy(0f))))
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToLogin, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Join the Athlink community", fontSize = 13.sp, color = AthlinkMedGray)
                }
            }

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    AppTextField(name, { name = it; clear(CoachField.NAME) }, if (isOrganisation) "Organisation Name" else "Full Name",
                        leadingIcon = if (isOrganisation) Icons.Default.Business else Icons.Default.Person,
                        isError = nameError != null, errorMessage = nameError.orEmpty(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(14.dp))

                    AppTextField(email, { email = it; clear(CoachField.EMAIL) }, "Email Address", leadingIcon = Icons.Default.Email,
                        isError = emailError != null, errorMessage = emailError.orEmpty(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(14.dp))

                    AppTextField(
                        password, { password = it; clear(CoachField.PASSWORD) }, "Password", leadingIcon = Icons.Default.Lock,
                        isError = passwordError != null, errorMessage = passwordError.orEmpty(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        trailingIcon = {
                            IconButton({ passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    Spacer(Modifier.height(14.dp))

                    AppTextField(
                        confirmPassword, { confirmPassword = it; clear(CoachField.CONFIRM_PASSWORD) }, "Confirm Password", leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        isError = (confirmPassword.isNotEmpty() && password != confirmPassword) || confirmError != null,
                        errorMessage = confirmError ?: "Passwords do not match",
                        trailingIcon = {
                            IconButton({ confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    Text("I am a...", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoleChip(Icons.Default.DirectionsRun, "Player", selectedRole == UserRole.PLAYER) { selectedRole = UserRole.PLAYER; viewModel.clearError() }
                        RoleChip(Icons.Default.EmojiPeople, "Coach", selectedRole == UserRole.COACH) { selectedRole = UserRole.COACH; viewModel.clearError() }
                        RoleChip(Icons.Default.Business, "Organisation", selectedRole == UserRole.ORGANISATION) { selectedRole = UserRole.ORGANISATION; viewModel.clearError() }
                    }

                    if (isPlayer) {
                        Spacer(Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = AthlinkBlueLight.copy(0.12f)), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.SportsSoccer, null, tint = AthlinkBlueLight, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Next, a few quick questions about you and your sport (about 2 minutes), " +
                                        "so we can suggest the right coaches and events.",
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ConsentRow(
                            checked = termsAccepted, text = "I agree to the Athlink Terms of Service",
                            error = playerErrors[PlayerSignupField.TERMS],
                            onChange = { termsAccepted = it; viewModel.clearPlayerFieldError(PlayerSignupField.TERMS) }
                        )
                        ConsentRow(
                            checked = privacyAccepted, text = "I have read the Athlink Privacy Policy",
                            error = playerErrors[PlayerSignupField.PRIVACY],
                            onChange = { privacyAccepted = it; viewModel.clearPlayerFieldError(PlayerSignupField.PRIVACY) }
                        )
                    }

                    if (isOrganisation) {
                        Spacer(Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = AthlinkBlueLight.copy(0.12f)), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.VerifiedUser, null, tint = AthlinkBlueLight, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Next, you'll verify your organisation (registration, authorised representative and sports " +
                                        "credentials). You can save event drafts straight away; publishing public events " +
                                        "unlocks once Athlink has verified you.",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (isCoach) {
                        Spacer(Modifier.height(24.dp))
                        CoachProfileSection(
                            form = coachForm,
                            errors = fieldErrors,
                            onChange = { updated, field -> coachForm = updated; field?.let { clear(it) } }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    state.error?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = AthlinkRed.copy(0.1f)), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, null, tint = AthlinkRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = AthlinkRed, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    val isValid = name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && password == confirmPassword
                    PrimaryButton(
                        text = when {
                            isCoach -> "Register as Coach"
                            isOrganisation -> "Register Organisation"
                            else -> "Create Account"
                        },
                        onClick = {
                            if (isCoach) {
                                viewModel.registerCoach(
                                    coachForm.copy(name = name, email = email, password = password, confirmPassword = confirmPassword)
                                )
                            } else if (isOrganisation) {
                                viewModel.registerOrganisation(name, email, password)
                            } else {
                                viewModel.registerPlayer(
                                    PlayerSignupForm(name, email, password, confirmPassword, termsAccepted, privacyAccepted)
                                )
                            }
                        },
                        enabled = isValid,
                        isLoading = state.isLoading
                    )
                    if ((isCoach && fieldErrors.isNotEmpty()) || playerErrors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Please fix the highlighted fields above.", color = AthlinkRed, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text("Login", color = AthlinkOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onNavigateToLogin))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RowScope.RoleChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Brush.linearGradient(listOf(GradientStart, GradientEnd)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(bg) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant))
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachProfileSection(
    form: CoachRegistration,
    errors: Map<CoachField, String>,
    onChange: (CoachRegistration, CoachField?) -> Unit
) {
    Text("Coaching Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Text("Players will see this on your public profile.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))

    AppTextField(
        form.phone, { onChange(form.copy(phone = it), CoachField.PHONE) }, "Phone Number", leadingIcon = Icons.Default.Phone,
        isError = CoachField.PHONE in errors, errorMessage = errors[CoachField.PHONE].orEmpty(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(
            form.city, { onChange(form.copy(city = it), CoachField.CITY) }, "City", modifier = Modifier.weight(1f),
            leadingIcon = Icons.Default.LocationOn,
            isError = CoachField.CITY in errors, errorMessage = errors[CoachField.CITY].orEmpty(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        AppTextField(
            form.state, { onChange(form.copy(state = it), CoachField.STATE) }, "State", modifier = Modifier.weight(1f),
            isError = CoachField.STATE in errors, errorMessage = errors[CoachField.STATE].orEmpty(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
    }
    Spacer(Modifier.height(14.dp))

    // Sport dropdown
    var sportExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = sportExpanded, onExpandedChange = { sportExpanded = it }) {
        OutlinedTextField(
            value = form.sport,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sport you coach") },
            leadingIcon = { Icon(Icons.Default.SportsSoccer, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
            isError = CoachField.SPORT in errors,
            supportingText = errors[CoachField.SPORT]?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        ExposedDropdownMenu(expanded = sportExpanded, onDismissRequest = { sportExpanded = false }) {
            Sports.ALL.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = {
                    onChange(form.copy(sport = s), CoachField.SPORT)
                    sportExpanded = false
                })
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(
            form.experienceYears,
            { v -> if (v.all { it.isDigit() } && v.length <= 2) onChange(form.copy(experienceYears = v), CoachField.EXPERIENCE) },
            "Experience (yrs)", modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Timeline,
            isError = CoachField.EXPERIENCE in errors, errorMessage = errors[CoachField.EXPERIENCE].orEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )
        AppTextField(
            form.hourlyRate,
            { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' } && v.length <= 8) onChange(form.copy(hourlyRate = v), CoachField.HOURLY_RATE) },
            "Rate (₹/hr)", modifier = Modifier.weight(1f), leadingIcon = Icons.Default.CurrencyRupee,
            isError = CoachField.HOURLY_RATE in errors, errorMessage = errors[CoachField.HOURLY_RATE].orEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
        )
    }
    Spacer(Modifier.height(16.dp))

    Text("Levels you coach", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachingLevel.entries.forEach { level ->
            val selected = level in form.coachingLevels
            SelectChip(level.label, selected, Modifier.weight(1f)) {
                val updated = if (selected) form.coachingLevels - level else form.coachingLevels + level
                onChange(form.copy(coachingLevels = updated), CoachField.LEVELS)
            }
        }
    }
    errors[CoachField.LEVELS]?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
    }
    Spacer(Modifier.height(14.dp))

    AppTextField(
        form.specializations, { onChange(form.copy(specializations = it), null) },
        "Specializations (comma separated)", leadingIcon = Icons.Default.Star,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(14.dp))

    AppTextField(
        form.certifications, { onChange(form.copy(certifications = it), null) },
        "Certifications (optional, comma separated)", leadingIcon = Icons.Default.WorkspacePremium,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(14.dp))

    AppTextField(
        form.bio,
        { if (it.length <= CoachRegistration.MAX_BIO_LENGTH) onChange(form.copy(bio = it), CoachField.BIO) },
        "About your coaching", leadingIcon = Icons.Default.Description,
        isError = CoachField.BIO in errors, errorMessage = errors[CoachField.BIO].orEmpty(),
        singleLine = false, maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
    Text(
        "${form.bio.length}/${CoachRegistration.MAX_BIO_LENGTH}",
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
        textAlign = TextAlign.End
    )
}

@Composable
private fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 11.sp, maxLines = 1,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
