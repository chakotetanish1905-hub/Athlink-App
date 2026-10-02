package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.User
import com.athlink.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerChatListScreen(
    user: User,
    onThreadClick: (threadId: String, receiverName: String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(DummyData.coaches.take(4)) { coach ->
                val threadId = "${user.uid}_${coach.uid}"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onThreadClick(threadId, coach.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(coach.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(coach.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("2h ago", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("Tap to start chatting...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Divider(modifier = Modifier.padding(horizontal = 80.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
            }
        }
    }
}
