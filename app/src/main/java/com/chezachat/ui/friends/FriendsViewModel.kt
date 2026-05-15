package com.chezachat.ui.friends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chezachat.data.repository.FriendRepository
import com.chezachat.data.repository.Result
import com.chezachat.model.FriendRequest
import com.chezachat.model.User
import kotlinx.coroutines.launch

class FriendsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FriendRepository()

    private val _friends = MutableLiveData<List<User>>(emptyList())
    val friends: LiveData<List<User>> = _friends

    private val _requests = MutableLiveData<List<FriendRequest>>(emptyList())
    val requests: LiveData<List<FriendRequest>> = _requests

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            loadFriends()
            loadRequests()
            _isLoading.value = false
        }
    }

    private suspend fun loadFriends() {
        when (val r = repo.getFriends()) {
            is Result.Success -> _friends.value = r.data
            is Result.Error   -> _message.value = r.message
            else -> {}
        }
    }

    private suspend fun loadRequests() {
        when (val r = repo.getRequests()) {
            is Result.Success -> _requests.value = r.data
            is Result.Error   -> {} // silent — might just be empty
            else -> {}
        }
    }

    fun sendRequest(userId: Int) {
        viewModelScope.launch {
            when (val r = repo.sendRequest(userId)) {
                is Result.Success -> _message.value = "Friend request sent!"
                is Result.Error   -> _message.value = r.message
                else -> {}
            }
        }
    }

    fun acceptRequest(requestId: Int) {
        viewModelScope.launch {
            when (val r = repo.respond(requestId, true)) {
                is Result.Success -> { _message.value = "Friend request accepted!"; refresh() }
                is Result.Error   -> _message.value = r.message
                else -> {}
            }
        }
    }

    fun rejectRequest(requestId: Int) {
        viewModelScope.launch {
            when (val r = repo.respond(requestId, false)) {
                is Result.Success -> { _message.value = "Request declined"; refresh() }
                is Result.Error   -> _message.value = r.message
                else -> {}
            }
        }
    }

    fun removeFriend(userId: Int) {
        viewModelScope.launch {
            when (val r = repo.removeFriend(userId)) {
                is Result.Success -> { _message.value = "Removed from friends"; loadFriends() }
                is Result.Error   -> _message.value = r.message
                else -> {}
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
