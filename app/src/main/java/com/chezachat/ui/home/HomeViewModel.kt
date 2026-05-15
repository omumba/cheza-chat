package com.chezachat.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.chezachat.ChezaApp
import com.chezachat.data.repository.*
import com.chezachat.data.websocket.ChezaWebSocketManager
import com.chezachat.data.websocket.WsMessage
import com.chezachat.model.User
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = ChezaApp.instance.database
    val convRepo            = ConversationRepository(db)
    private val userRepo    = UserRepository(db)
    private val friendRepo  = FriendRepository()

    val conversations: LiveData<List<com.chezachat.model.Conversation>> =
        convRepo.conversations.asLiveData()

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _searchResults = MutableLiveData<List<User>>(emptyList())
    val searchResults: LiveData<List<User>> = _searchResults

    // Friend/pending ID sets for badge rendering in search results
    private val _friendIds  = MutableLiveData<Set<Int>>(emptySet())
    val friendIds: LiveData<Set<Int>> = _friendIds

    private val _pendingIds = MutableLiveData<Set<Int>>(emptySet())
    val pendingIds: LiveData<Set<Int>> = _pendingIds

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Pending request count for badge on Friends tab
    private val _pendingRequestCount = MutableLiveData(0)
    val pendingRequestCount: LiveData<Int> = _pendingRequestCount

    init {
        refresh()
        loadFriendData()
        observeWebSocket()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            convRepo.refreshConversations()
            _isRefreshing.value = false
        }
    }

    private fun loadFriendData() {
        viewModelScope.launch {
            // Load friends list for badge display
            when (val r = friendRepo.getFriends()) {
                is Result.Success -> _friendIds.value = r.data.map { it.id }.toSet()
                else -> {}
            }
            // Load sent requests for pending badge
            when (val r = friendRepo.getSentRequests()) {
                is Result.Success -> _pendingIds.value = r.data.map { it.receiverId }.toSet()
                else -> {}
            }
            // Load incoming requests count for notification badge
            when (val r = friendRepo.getRequests()) {
                is Result.Success -> _pendingRequestCount.value = r.data.size
                else -> {}
            }
        }
    }

    fun refreshFriendData() { loadFriendData() }

    fun searchUsers(query: String) {
        if (query.length < 2) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            when (val r = userRepo.searchUsers(query)) {
                is Result.Success -> _searchResults.value = r.data
                is Result.Error   -> _error.value = r.message
                else -> {}
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    private fun observeWebSocket() {
        viewModelScope.launch {
            ChezaWebSocketManager.messages.collect { msg ->
                when (msg) {
                    is WsMessage.NewMessage -> {
                        convRepo.updateLastMessage(
                            msg.message.conversationId,
                            msg.message.content,
                            msg.message.createdAt
                        )
                    }
                    is WsMessage.UserPresence -> {
                        convRepo.updateUserOnline(msg.userId, msg.isOnline)
                        userRepo.updatePresence(msg.userId, msg.isOnline, msg.lastSeen)
                    }
                    else -> {}
                }
            }
        }
    }
}
