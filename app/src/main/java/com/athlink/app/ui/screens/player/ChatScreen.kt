package com.athlink.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.athlink.app.data.model.Message
import com.athlink.app.data.model.User
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: String,
    receiverName: String,
    user: User,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(threadId) {
        viewModel.loadMessages(threadId)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(receiverName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(receiverName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Online", fontSize = 11.sp, color = AthlinkGreen)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.VideoCall, null, tint = AthlinkOrange) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Call, null, tint = AthlinkOrange) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ChatBubbleOutline, null, tint = AthlinkMedGray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No messages yet", color = AthlinkMedGray, fontWeight = FontWeight.Medium)
                                Text("Say hello to $receiverName!", fontSize = 13.sp, color = AthlinkMedGray)
                            }
                        }
                    }
                } else {
                    items(state.messages) { message ->
                        MessageBubble(message = message, isMe = message.senderId == user.uid)
                    }
                }
            }

            // Input bar
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { viewModel.onInputChange(it) },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AthlinkOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (state.inputText.isNotBlank()) Brush.linearGradient(listOf(GradientStart, GradientEnd)) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                            .run { if (state.inputText.isNotBlank()) this else this },
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.sendMessage(threadId, user.uid, threadId.replace(user.uid, "").replace("_", "")) },
                            enabled = state.inputText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, null, tint = if (state.inputText.isNotBlank()) Color.White else AthlinkMedGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMe: Boolean) {
    val time = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isMe) Brush.linearGradient(listOf(GradientStart, GradientEnd))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(message.content, color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Text(time, color = if (isMe) Color.White.copy(0.7f) else AthlinkMedGray, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 3.dp))
            }
        }
    }
}
