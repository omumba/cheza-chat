package com.chezachat.data.repository

import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.db.ChezaDatabase
import com.chezachat.model.*
import kotlinx.coroutines.flow.Flow

// ─── Result Wrapper ───────────────────────────────────────────────────────────

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// ─── Auth Repository ──────────────────────────────────────────────────────────

class AuthRepository {
    private val api = RetrofitClient.api

    suspend fun login(email: String, password: String, fcmToken: String?): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password, fcmToken))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(response.body()?.message ?: "Login failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun register(name: String, email: String, phone: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.register(RegisterRequest(name, email, phone, password))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(response.body()?.message ?: "Registration failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }


    suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            val r = api.forgotPassword(com.chezachat.model.ForgotPasswordRequest(email))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Email not found")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun verifyOtp(email: String, otp: String): Result<Unit> {
        return try {
            val r = api.verifyOtp(mapOf("email" to email, "otp" to otp))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Invalid or expired OTP")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<Unit> {
        return try {
            val r = api.resetPassword(com.chezachat.model.ResetPasswordRequest(email, otp, newPassword))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Reset failed")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            api.logout()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Success(Unit)  // Always succeed locally
        }
    }
}

// ─── Conversation Repository ──────────────────────────────────────────────────

class ConversationRepository(private val db: ChezaDatabase) {
    private val api = RetrofitClient.api
    private val dao = db.conversationDao()

    val conversations: Flow<List<Conversation>> = dao.getAllConversations()

    suspend fun refreshConversations(): Result<List<Conversation>> {
        return try {
            val response = api.getConversations()
            if (response.isSuccessful) {
                val list = response.body()?.conversations ?: emptyList()
                dao.insertAll(list)
                Result.Success(list)
            } else {
                Result.Error("Failed to load conversations")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun createDirectConversation(userId: Int): Result<Conversation> {
        return try {
            val response = api.createDirectConversation(mapOf("user_id" to userId))
            if (response.isSuccessful && response.body()?.success == true) {
                val conv = response.body()!!.data!!
                dao.insert(conv)
                Result.Success(conv)
            } else {
                Result.Error(response.body()?.message ?: "Failed to create conversation")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun createGroupConversation(name: String, memberIds: List<Int>): Result<Conversation> {
        return try {
            val body = mapOf<String, Any>("name" to name, "member_ids" to memberIds)
            val response = api.createGroupConversation(body)
            if (response.isSuccessful && response.body()?.success == true) {
                val conv = response.body()!!.data!!
                dao.insert(conv)
                Result.Success(conv)
            } else {
                Result.Error(response.body()?.message ?: "Failed to create group")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun updateLastMessage(convId: Int, message: String, time: Long) {
        dao.updateLastMessage(convId, message, time)
    }

    suspend fun markAsRead(convId: Int) = dao.clearUnread(convId)

    suspend fun updateUserOnline(userId: Int, online: Boolean) =
        dao.updateUserOnlineStatus(userId, online)
}

// ─── Message Repository ───────────────────────────────────────────────────────

class MessageRepository(private val db: ChezaDatabase) {
    private val api = RetrofitClient.api
    private val messageDao = db.messageDao()
    private val convDao = db.conversationDao()

    fun getMessages(conversationId: Int): Flow<List<Message>> =
        messageDao.getMessages(conversationId)

    suspend fun loadMessages(conversationId: Int, page: Int = 1): Result<List<Message>> {
        return try {
            val response = api.getMessages(conversationId, page)
            if (response.isSuccessful) {
                val messages = response.body()?.messages ?: emptyList()
                messageDao.insertAll(messages)
                Result.Success(messages)
            } else {
                Result.Error("Failed to load messages")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun insertLocalMessage(message: Message) {
        messageDao.insert(message)
    }

    suspend fun replaceTempWithReal(tempId: Int, realMessage: Message) {
        messageDao.replaceTempWithReal(tempId, realMessage)
    }

    suspend fun updateMessageStatus(messageId: Int, status: String) {
        messageDao.updateStatus(messageId, status)
    }


    // Mark all received messages in a conversation as "read" (triggers blue tick for sender)
    suspend fun markMessagesRead(conversationId: Int, myUserId: Int) {
        try {
            // Update locally
            db.messageDao().markConversationRead(conversationId, myUserId)
            // Notify server
            RetrofitClient.api.markMessagesRead(mapOf(
                "conversation_id" to conversationId,
                "reader_id" to myUserId
            ))
        } catch (e: Exception) { /* non-critical */ }
    }

    suspend fun softDeleteMessage(messageId: Int) {
        messageDao.softDelete(messageId)
    }

    suspend fun deleteMessage(messageId: Int): Result<Unit> {
        return try {
            val response = api.deleteMessage(mapOf("message_id" to messageId))
            if (response.isSuccessful) {
                messageDao.softDelete(messageId)
                Result.Success(Unit)
            } else {
                Result.Error("Failed to delete message")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}

// ─── User Repository ──────────────────────────────────────────────────────────

class UserRepository(private val db: ChezaDatabase) {
    private val api = RetrofitClient.api
    private val dao = db.userDao()

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val response = api.searchUsers(query)
            if (response.isSuccessful) {
                val users = response.body()?.users ?: emptyList()
                dao.insertAll(users)
                Result.Success(users)
            } else {
                Result.Error("Search failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getProfile(): Result<User> {
        return try {
            val response = api.getProfile()
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!
                dao.insert(user)
                Result.Success(user)
            } else {
                Result.Error("Failed to load profile")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun updatePresence(userId: Int, online: Boolean, lastSeen: Long) {
        dao.updatePresence(userId, online, lastSeen)
    }
}

// ─── Friend Repository ────────────────────────────────────────────────────────

class FriendRepository {
    private val api = RetrofitClient.api

    suspend fun sendRequest(toUserId: Int): Result<Unit> {
        return try {
            val r = api.sendFriendRequest(mapOf("user_id" to toUserId))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Failed to send request")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun getSentRequests(): Result<List<FriendRequest>> {
        return try {
            val r = RetrofitClient.api.getSentFriendRequests()
            if (r.isSuccessful) Result.Success(r.body()?.requests ?: emptyList())
            else Result.Error("Failed")
        } catch (e: Exception) { Result.Error(e.message ?: "Error") }
    }

    suspend fun getRequests(): Result<List<FriendRequest>> {
        return try {
            val r = api.getFriendRequests()
            if (r.isSuccessful) Result.Success(r.body()?.requests ?: emptyList())
            else Result.Error("Failed to load requests")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun respond(requestId: Int, accept: Boolean): Result<Unit> {
        return try {
            val r = api.respondFriendRequest(RespondFriendRequest(requestId, if (accept) 1 else 0))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Failed")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun getFriends(): Result<List<User>> {
        return try {
            val r = api.getFriends()
            if (r.isSuccessful) Result.Success(r.body()?.friends ?: emptyList())
            else Result.Error("Failed to load friends")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }

    suspend fun removeFriend(userId: Int): Result<Unit> {
        return try {
            val r = api.removeFriend(mapOf("user_id" to userId))
            if (r.isSuccessful && r.body()?.success == true) Result.Success(Unit)
            else Result.Error(r.body()?.message ?: "Failed")
        } catch (e: Exception) { Result.Error(e.message ?: "Network error") }
    }
}
