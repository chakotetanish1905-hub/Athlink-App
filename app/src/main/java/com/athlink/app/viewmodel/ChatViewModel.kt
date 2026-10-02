package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.Message
import com.athlink.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun loadMessages(threadId: String) {
        viewModelScope.launch {
            chatRepository.getMessages(threadId).collect { messages ->
                _state.value = _state.value.copy(messages = messages)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun sendMessage(threadId: String, senderId: String, receiverId: String) {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true, inputText = "")
            val message = Message(senderId = senderId, receiverId = receiverId, content = text)
            chatRepository.sendMessage(threadId, message)
            _state.value = _state.value.copy(isSending = false)
        }
    }
}
