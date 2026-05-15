package com.chezachat.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chezachat.ChezaApp
import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.repository.AuthRepository
import com.chezachat.model.User
import com.chezachat.utils.toMultipart
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val session  = ChezaApp.instance.sessionManager
    private val authRepo = AuthRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _updateResult = MutableLiveData<User?>()
    val updateResult: LiveData<User?> = _updateResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun updateStatus(status: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.api.updateProfile(mapOf("status" to status))
                if (response.isSuccessful && response.body()?.success == true) {
                    _updateResult.value = response.body()?.data
                } else {
                    _error.value = response.body()?.message ?: "Update failed"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
            _isLoading.value = false
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val part = uri.toMultipart(getApplication(), "avatar")
            if (part == null) {
                _error.value = "Failed to read image"
                _isLoading.value = false
                return@launch
            }
            try {
                val response = RetrofitClient.api.updateAvatar(part)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Refresh profile from server to get updated avatar_url
                    val profileResp = RetrofitClient.api.getProfile()
                    if (profileResp.isSuccessful && profileResp.body()?.success == true) {
                        _updateResult.value = profileResp.body()?.data
                    }
                } else {
                    _error.value = "Failed to upload avatar"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { authRepo.logout() } catch (e: Exception) { /* silent */ }
        }
    }
}
