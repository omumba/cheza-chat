package com.chezachat.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chezachat.ChezaApp
import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.repository.AuthRepository
import com.chezachat.data.repository.Result
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val session = ChezaApp.instance.sessionManager
    private val repo = AuthRepository()

    init { RetrofitClient.init(session) }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _authResult = MutableLiveData<AuthUiState>()
    val authResult: LiveData<AuthUiState> = _authResult

    // 1 = enter email, 2 = enter OTP, 3 = enter new password
    private val _forgotPasswordStep = MutableLiveData(1)
    val forgotPasswordStep: LiveData<Int> = _forgotPasswordStep

    fun login(email: String, password: String, fcmToken: String?) {
        if (email.isBlank() || password.isBlank()) {
            _authResult.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.login(email.trim(), password, fcmToken)) {
                is Result.Success -> {
                    session.saveToken(r.data.token!!)
                    session.saveUser(r.data.user!!)
                    _authResult.value = AuthUiState.Success
                }
                is Result.Error -> _authResult.value = AuthUiState.Error(r.message)
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun register(name: String, email: String, phone: String, password: String, confirm: String) {
        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            _authResult.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        if (password != confirm) {
            _authResult.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (password.length < 6) {
            _authResult.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.register(name.trim(), email.trim(), phone.trim(), password)) {
                is Result.Success -> {
                    session.saveToken(r.data.token!!)
                    session.saveUser(r.data.user!!)
                    _authResult.value = AuthUiState.Success
                }
                is Result.Error -> _authResult.value = AuthUiState.Error(r.message)
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.forgotPassword(email)) {
                is Result.Success -> _forgotPasswordStep.value = 2
                is Result.Error   -> _authResult.value = AuthUiState.Error(r.message)
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun verifyOtp(email: String, otp: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.verifyOtp(email, otp)) {
                is Result.Success -> _forgotPasswordStep.value = 3
                is Result.Error   -> _authResult.value = AuthUiState.Error(r.message)
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.resetPassword(email, otp, newPassword)) {
                is Result.Success -> _authResult.value = AuthUiState.Success
                is Result.Error   -> _authResult.value = AuthUiState.Error(r.message)
                else -> {}
            }
            _isLoading.value = false
        }
    }
}

sealed class AuthUiState {
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
