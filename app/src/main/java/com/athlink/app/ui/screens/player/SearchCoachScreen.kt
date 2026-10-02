package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.DummyData
import com.athlink.app.ui.components.CoachCard
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.CoachViewModel

@Composable
fun SearchCoachScreen(
    onBack: () -> Unit,
    onBookCoach: (Coach) -> Unit,
    viewModel: CoachViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sports = listOf("All") + DummyData.sports.take(8)

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Top Bar + Search
        Column(
            Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(CardDark).padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CardDark2)
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Find Coaches", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(14.dp))

            // Search Input
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name or sport...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AthBlue,
                    unfocusedBorderColor = DividerDark,
                    focusedContainerColor = CardDark2,
                    unfocusedContainerColor = CardDark2,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.height(12.dp))

            // Sport Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sports) { sport ->
                    val isSelected = if (sport == "All") state.selectedSport == null
                                    else state.selectedSport == sport
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.filterBySport(if (sport == "All") null else sport) },
                        label = { Text(sport, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AthBlue,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                            containerColor = CardDark2,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = isSelected,
                            selectedBorderColor = AthBlue, borderColor = DividerDark
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Results count
        Row(Modifier.padding(horizontal = 20.dp)) {
            Text("${state.filteredCoaches.size} coaches found", color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))

        // Coach List
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AthBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.filteredCoaches) { coach ->
                    CoachCard(
                        coach = coach,
                        onClick = {
                            viewModel.selectCoach(coach)
                            onBookCoach(coach)
                        },
                        onBookClick = {
                            viewModel.selectCoach(coach)
                            onBookCoach(coach)
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
