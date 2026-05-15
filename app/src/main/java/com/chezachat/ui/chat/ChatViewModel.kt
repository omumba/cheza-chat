package com.chezachat.ui.chat

import android.app.Application
import androidx.lifecycle.*
import com.chezachat.ChezaApp
import com.chezachat.data.repository.ConversationRepository
import com.chezachat.data.repository.MessageRepository
import com.chezachat.data.repository.Result
import com.chezachat.data.websocket.ChezaWebSocketManager
import com.chezachat.data.websocket.WsMessage
import com.chezachat.model.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val fileName: String, val percent: Int) : UploadState()
    object Done : UploadState()
    data class Error(val message: String) : UploadState()
}

class ChatViewModel(
    application: Application,
    private val conversationId: Int
) : AndroidViewModel(application) {

    private val db          = ChezaApp.instance.database
    private val session     = ChezaApp.instance.sessionManager
    private val messageRepo = MessageRepository(db)
    private val convRepo    = ConversationRepository(db)

    val myUserId: Int = session.getUserId()

    val messages: LiveData<List<Message>> = messageRepo.getMessages(conversationId).asLiveData()

    // Upload progress
    private val _uploadState = MutableLiveData<UploadState>(UploadState.Idle)
    val uploadState: LiveData<UploadState> = _uploadState

    fun startUpload(name: String) { _uploadState.postValue(UploadState.Uploading(name, 0)) }
    fun updateUploadProgress(pct: Int) {
        (_uploadState.value as? UploadState.Uploading)?.let { _uploadState.postValue(it.copy(percent = pct)) }
    }
    fun onUploadSuccess() { _uploadState.postValue(UploadState.Done) }
    fun onUploadError(msg: String) {
        _uploadState.postValue(UploadState.Error(msg))
        viewModelScope.launch { delay(3000); _uploadState.postValue(UploadState.Idle) }
    }

    // Typing
    private val _typingUsers = MutableLiveData<Set<Int>>(emptySet())
    val typingUsers: LiveData<Set<Int>> = _typingUsers
    private var typingJob: Job? = null

    // Reply
    private val _replyMessage = MutableLiveData<Message?>(null)
    val replyMessage: LiveData<Message?> = _replyMessage

    // Events
    private val _event = MutableSharedFlow<ChatEvent>()
    val event: SharedFlow<ChatEvent> = _event.asSharedFlow()

    // Temp ID tracking — negative IDs never collide with real server IDs
    private var tempIdCounter = -1_000_000
    private val pendingTempIds = mutableSetOf<Int>()   // tracks temp IDs we sent

    private var currentPage  = 1
    private var hasMorePages = true
    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    init {
        loadMessages()
        observeWebSocket()
        markRead()
    }

    fun loadMessages(page: Int = 1) {
        viewModelScope.launch {
            _isLoadingMore.value = page > 1
            when (val r = messageRepo.loadMessages(conversationId, page)) {
                is Result.Success -> { hasMorePages = r.data.size >= 50; currentPage = page }
                is Result.Error   -> {
                    if (r.code == 403) {
                        convRepo.deleteConversation(conversationId)
                        _event.emit(ChatEvent.NavigateBack)
                    } else {
                        _event.emit(ChatEvent.ShowError(r.message))
                    }
                }
                else -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun loadMoreMessages() {
        if (!hasMorePages || _isLoadingMore.value == true) return
        loadMessages(currentPage + 1)
    }

    fun sendMessage(
        content: String,
        type: String = "text",
        mediaUrl: String? = null,
        mediaSize: Long? = null
    ) {
        if (content.isBlank() && mediaUrl == null) return

        val replyToId    = _replyMessage.value?.id
        val replyPreview = _replyMessage.value?.content?.take(80)
        clearReply()

        val tempId = tempIdCounter--
        pendingTempIds.add(tempId)

        val tempMsg = Message(
            id             = tempId,
            conversationId = conversationId,
            senderId       = myUserId,
            senderName     = session.getUser()?.name ?: "",
            content        = content,
            type           = type,
            mediaUrl       = mediaUrl,
            mediaSize      = mediaSize,
            status         = "sending",
            createdAt      = System.currentTimeMillis(),
            replyToId      = replyToId,
            replyPreview   = replyPreview
        )

        viewModelScope.launch {
            // 1. Insert optimistic bubble with clock icon
            messageRepo.insertLocalMessage(tempMsg)
            convRepo.updateLastMessage(conversationId,
                if (type == "text") content else "📎 $content",
                System.currentTimeMillis())
            stopTyping()

            // 2. Send via WebSocket — include tempId so server echoes it back
            ChezaWebSocketManager.sendChatMessage(
                conversationId = conversationId,
                content        = content,
                type           = type,
                mediaUrl       = mediaUrl,
                replyToId      = replyToId,
                tempId         = tempId
            )
            _event.emit(ChatEvent.ScrollToBottom)
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            ChezaWebSocketManager.messages.collect { wsMsg ->
                when (wsMsg) {
                    is WsMessage.NewMessage -> {
                        val msg    = wsMsg.message
                        val tempId = wsMsg.tempId

                        if (msg.conversationId == conversationId) {
                            if (tempId != 0 && pendingTempIds.contains(tempId)) {
                                // ── OUR own message confirmed by server ──
                                // Atomically: delete temp row → insert real row with status "sent"
                                // This is a single DB transaction — RecyclerView sees ONE item
                                // change (update), not a remove+add, so no duplicate ever appears.
                                pendingTempIds.remove(tempId)
                                messageRepo.replaceTempWithReal(tempId, msg.copy(status = "sent"))
                            } else {
                                // ── Message from someone else ──
                                messageRepo.insertLocalMessage(msg)
                                convRepo.updateLastMessage(conversationId,
                                    if (msg.type == "text") msg.content else "📎 ${msg.content}",
                                    msg.createdAt)
                            }
                            _event.emit(ChatEvent.ScrollToBottom)
                        }
                    }

                    is WsMessage.MessageStatus -> {
                        // clock→sent→delivered→read transitions on the REAL message ID
                        messageRepo.updateMessageStatus(wsMsg.messageId, wsMsg.status)
                    }

                    is WsMessage.TypingIndicator -> {
                        if (wsMsg.conversationId == conversationId) {
                            val s = _typingUsers.value?.toMutableSet() ?: mutableSetOf()
                            if (wsMsg.isTyping) s.add(wsMsg.userId) else s.remove(wsMsg.userId)
                            _typingUsers.postValue(s)
                        }
                    }

                    is WsMessage.MessageDeleted -> {
                        if (wsMsg.conversationId == conversationId)
                            messageRepo.softDeleteMessage(wsMsg.messageId)
                    }

                    else -> {}
                }
            }
        }
    }

    fun onTyping() {
        typingJob?.cancel()
        ChezaWebSocketManager.sendTyping(conversationId, true)
        typingJob = viewModelScope.launch { delay(2000); stopTyping() }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        ChezaWebSocketManager.sendTyping(conversationId, false)
    }

    fun setReply(msg: Message)  { _replyMessage.value = msg }
    fun clearReply()            { _replyMessage.value = null }

    fun deleteMessage(msg: Message) {
        viewModelScope.launch { messageRepo.deleteMessage(msg.id) }
    }

    fun sendReadReceipt(lastMessageId: Int) {
        ChezaWebSocketManager.sendReadReceipt(conversationId, lastMessageId)
        // Mark "read" in DB for all their messages in this conversation
        viewModelScope.launch {
            messageRepo.markMessagesRead(conversationId, myUserId)
            markRead()
        }
    }

    private fun markRead() {
        viewModelScope.launch { convRepo.markAsRead(conversationId) }
    }

    class Factory(private val app: Application, private val conversationId: Int) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(app, conversationId) as T
    }
}

sealed class ChatEvent {
    data class ShowError(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
    object MessageSent    : ChatEvent()
    object NavigateBack   : ChatEvent()
}
