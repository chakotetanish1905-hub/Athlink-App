package com.athlink.app.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top gradient banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkDeepBlue.copy(0f))))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SportsSoccer, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Login to your account", fontSize = 14.sp, color = AthlinkMedGray)

            Spacer(Modifier.height(48.dp))

            // Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    Spacer(Modifier.height(14.dp))

                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Forgot Password?",
                        color = AthlinkOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.End).clickable { }
                    )

                    Spacer(Modifier.height(24.dp))

                    state.error?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AthlinkRed.copy(0.1f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, null, tint = AthlinkRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = AthlinkRed, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    PrimaryButton(
                        text = "Login",
                        onClick = { viewModel.login(email, password) },
                        enabled = email.isNotEmpty() && password.isNotEmpty(),
                        isLoading = state.isLoading
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(
                    "Sign Up",
                    color = AthlinkOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onNavigateToSignup)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
