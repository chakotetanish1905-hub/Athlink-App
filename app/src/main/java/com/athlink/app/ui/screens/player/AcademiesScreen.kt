package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.User
import com.athlink.app.data.model.displayLocation
import com.athlink.app.ui.components.AthlinkBottomBar
import com.athlink.app.ui.components.playerNavItems
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.AcademyViewModel

/**
 * Player-facing academy directory: verified organisations only (incl. the imported Vadodara /
 * Surat academies). Filters: city (defaults to the player's city), sport, indoor / outdoor, search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademiesScreen(
    user: User,
    navController: NavHostController,
    onOpenAcademy: (String) -> Unit,
    viewModel: AcademyViewModel = hiltViewModel()
) {
    val state by viewModel.list.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    LaunchedEffect(user.uid) { viewModel.loadList(user.uid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academies", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadList(user.uid, refresh = true) }) { Icon(Icons.Default.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AthlinkBottomBar(
                items = playerNavItems,
                currentRoute = navBackStack?.destination?.route,
                onItemClick = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search by name, area or sport") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            item {
                FilterRow(
                    label = "City",
                    options = state.cities,
                    selected = state.city,
                    onSelect = viewModel::setCity
                )
                FilterRow(
                    label = "Sport",
                    options = state.sports,
                    selected = state.sport,
                    onSelect = viewModel::setSport
                )
                FilterRow(
                    label = "Venue",
                    options = listOf("INDOOR", "OUTDOOR"),
                    selected = state.venueCategory,
                    onSelect = viewModel::setVenueCategory,
                    display = { if (it == "INDOOR") "Indoor" else "Outdoor" }
                )
            }
            item {
                Text(
                    when {
                        state.isLoading -> "Loading academies…"
                        else -> "${state.results.size} ${if (state.results.size == 1) "academy" else "academies"}" +
                            (state.city?.let { " in $it" } ?: "")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            state.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) { Text(err, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AthlinkOrange)
                    }
                }
                state.results.isEmpty() && state.error == null -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = AthlinkMedGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No academies match these filters", fontWeight = FontWeight.SemiBold)
                        Text("Try another city or sport.", color = AthlinkMedGray, fontSize = 13.sp)
                    }
                }
                else -> items(state.results, key = { it.organisationId }) { academy ->
                    AcademyCard(academy, onClick = { onOpenAcademy(academy.organisationId) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    display: (String) -> String = { it }
) {
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(42.dp)) }
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(display(option)) }
            )
        }
    }
}

/** Card used in the list and on the dashboard. */
@Composable
fun AcademyCard(academy: Organisation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AcademyAvatar(academy.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(academy.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    academy.sports.joinToString(" · "),
                    color = AthlinkOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = AthlinkMedGray, modifier = Modifier.size(14.dp))
                    Text(
                        " ${academy.displayLocation}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (academy.venueCategory.isNotBlank()) {
                        Text(
                            "  •  ${if (academy.venueCategory == "INDOOR") "Indoor" else "Outdoor"}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            RatingPill(academy.googleRating, academy.googleReviewCount)
        }
    }
}

@Composable
internal fun AcademyAvatar(name: String, size: Int = 48) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
        contentAlignment = Alignment.Center
    ) {
        val initials = name.split(" ").filter { it.isNotBlank() && it.first().isLetter() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "A" }
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp)
    }
}

@Composable
internal fun RatingPill(rating: Double?, reviews: Int?) {
    if (rating == null) {
        Text("Not rated", fontSize = 11.sp, color = AthlinkMedGray)
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = AthlinkGold, modifier = Modifier.size(15.dp))
            Text(" ${"%.1f".format(rating)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (reviews != null) Text("$reviews reviews", fontSize = 10.sp, color = AthlinkMedGray)
    }
}
