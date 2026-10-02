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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.UserRole
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AuthViewModel

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

                    AppTextField(name, { name = it }, "Full Name", leadingIcon = Icons.Default.Person,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(14.dp))

                    AppTextField(email, { email = it }, "Email Address", leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(14.dp))

                    AppTextField(
                        password, { password = it }, "Password", leadingIcon = Icons.Default.Lock,
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
                        confirmPassword, { confirmPassword = it }, "Confirm Password", leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                        errorMessage = "Passwords do not match",
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
                        RoleChip(Icons.Default.DirectionsRun, "Player", selectedRole == UserRole.PLAYER) { selectedRole = UserRole.PLAYER }
                        RoleChip(Icons.Default.EmojiPeople, "Coach", selectedRole == UserRole.COACH) { selectedRole = UserRole.COACH }
                        RoleChip(Icons.Default.Business, "Organisation", selectedRole == UserRole.ORGANISATION) { selectedRole = UserRole.ORGANISATION }
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
                    PrimaryButton("Create Account", onClick = { viewModel.register(name, email, password, selectedRole) }, enabled = isValid, isLoading = state.isLoading)
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
