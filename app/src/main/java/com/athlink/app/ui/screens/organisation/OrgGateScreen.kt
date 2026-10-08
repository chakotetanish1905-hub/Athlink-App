package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.OrganisationLanding
import com.athlink.app.data.model.User
import com.athlink.app.ui.components.PrimaryButton
import com.athlink.app.ui.theme.AthlinkOrange
import com.athlink.app.viewmodel.OrganisationViewModel

/**
 * Start destination of the organisation graph. Loads `organisations/{uid}` and routes:
 * incomplete -> onboarding, under review / rejected / suspended / expired -> status,
 * verified -> dashboard. (ROLE = ORGANISATION alone never lands on a "verified" state.)
 */
@Composable
fun OrgGateScreen(
    user: User,
    onRoute: (OrganisationLanding) -> Unit,
    viewModel: OrganisationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(user.uid) { viewModel.refresh(user) }
    var routed by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded, state.isLoading) {
        if (state.loaded && !state.isLoading && !routed) { routed = true; onRoute(state.landing) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.error != null && !state.loaded) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Retry", onClick = { viewModel.refresh(user) })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onRoute(OrganisationLanding.DASHBOARD) }) { Text("Continue to dashboard") }
            }
        } else {
            CircularProgressIndicator(color = AthlinkOrange)
        }
    }
}
