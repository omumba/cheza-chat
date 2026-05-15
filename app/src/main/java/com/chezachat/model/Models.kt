package com.chezachat.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ─── User ─────────────────────────────────────────────────────────────────────

@Parcelize
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "email")
    val email: String = "",

    @ColumnInfo(name = "phone")
    val phone: String = "",

    @ColumnInfo(name = "avatar_url")
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,

    @ColumnInfo(name = "status")
    val status: String = "Hey there! I'm using Cheza Chat",

    @ColumnInfo(name = "last_seen")
    @SerializedName("last_seen")
    val lastSeen: Long = 0L,

    @ColumnInfo(name = "is_online")
    @SerializedName("is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "fcm_token")
    @SerializedName("fcm_token")
    val fcmToken: String? = null
) : Parcelable

// ─── Conversation ─────────────────────────────────────────────────────────────

@Parcelize
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "type")
    val type: String = "direct",

    @ColumnInfo(name = "avatar_url")
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,

    @ColumnInfo(name = "last_message")
    @SerializedName("last_message")
    val lastMessage: String? = null,

    @ColumnInfo(name = "last_message_time")
    @SerializedName("last_message_time")
    val lastMessageTime: Long = 0L,

    @ColumnInfo(name = "unread_count")
    @SerializedName("unread_count")
    val unreadCount: Int = 0,

    @ColumnInfo(name = "other_user_id")
    @SerializedName("other_user_id")
    val otherUserId: Int? = null,

    @ColumnInfo(name = "is_online")
    @SerializedName("is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "is_typing")
    @SerializedName("is_typing")
    val isTyping: Boolean = false,

    // Stored as JSON string via TypeConverter
    @ColumnInfo(name = "members")
    @SerializedName("members")
    val members: List<User>? = null
) : Parcelable

// ─── Reaction ─────────────────────────────────────────────────────────────────

@Parcelize
data class Reaction(
    val emoji: String = "",

    @SerializedName("user_id")
    val userId: Int = 0,

    @SerializedName("user_name")
    val userName: String = ""
) : Parcelable

// ─── Message ──────────────────────────────────────────────────────────────────

@Parcelize
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "conversation_id")
    @SerializedName("conversation_id")
    val conversationId: Int = 0,

    @ColumnInfo(name = "sender_id")
    @SerializedName("sender_id")
    val senderId: Int = 0,

    @ColumnInfo(name = "sender_name")
    @SerializedName("sender_name")
    val senderName: String = "",

    @ColumnInfo(name = "sender_avatar")
    @SerializedName("sender_avatar")
    val senderAvatar: String? = null,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "type")
    val type: String = "text",

    @ColumnInfo(name = "media_url")
    @SerializedName("media_url")
    val mediaUrl: String? = null,

    @ColumnInfo(name = "media_size")
    @SerializedName("media_size")
    val mediaSize: Long? = null,

    @ColumnInfo(name = "status")
    val status: String = "sending",

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "reply_to_id")
    @SerializedName("reply_to_id")
    val replyToId: Int? = null,

    @ColumnInfo(name = "reply_preview")
    @SerializedName("reply_preview")
    val replyPreview: String? = null,

    @ColumnInfo(name = "is_deleted")
    @SerializedName("is_deleted")
    val isDeleted: Boolean = false,

    // Stored as JSON string via TypeConverter
    @ColumnInfo(name = "reactions")
    @SerializedName("reactions")
    val reactions: List<Reaction>? = null
) : Parcelable

// ─── Auth / API DTOs ──────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("fcm_token") val fcmToken: String?
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String?,
    val user: User?
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

data class ConversationsResponse(
    val success: Boolean,
    val conversations: List<Conversation>
)

data class MessagesResponse(
    val success: Boolean,
    val messages: List<Message>,
    val total: Int,
    val page: Int
)

data class UsersResponse(
    val success: Boolean,
    val users: List<User>
)

// ─── Friend Request ───────────────────────────────────────────────────────────

@Parcelize
data class FriendRequest(
    val id: Int = 0,
    @SerializedName("sender_id")   val senderId: Int = 0,
    @SerializedName("receiver_id") val receiverId: Int = 0,
    @SerializedName("sender_name") val senderName: String = "",
    @SerializedName("sender_email") val senderEmail: String = "",
    @SerializedName("sender_avatar") val senderAvatar: String? = null,
    val status: String = "pending",   // pending | accepted | rejected
    @SerializedName("created_at") val createdAt: Long = 0L
) : Parcelable

data class FriendRequestsResponse(
    val success: Boolean,
    val requests: List<FriendRequest>
)

data class FriendsResponse(
    val success: Boolean,
    val friends: List<User>
)

// ─── Forgot Password DTOs ─────────────────────────────────────────────────────

data class RespondFriendRequest(
    @SerializedName("request_id") val requestId: Int,
    val accept: Int   // 1 = accept, 0 = decline
)

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    @SerializedName("new_password") val newPassword: String
)

// ─── WebSocket Payloads ───────────────────────────────────────────────────────

data class SendMessagePayload(
    val type: String = "send_message",
    @SerializedName("conversation_id") val conversationId: Int,
    val content: String,
    @SerializedName("message_type") val messageType: String = "text",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("temp_id") val tempId: Int = 0,
    @SerializedName("reply_to_id") val replyToId: Int? = null
)

data class TypingPayload(
    val type: String = "typing",
    @SerializedName("conversation_id") val conversationId: Int,
    @SerializedName("is_typing") val isTyping: Boolean
)

data class ReadReceiptPayload(
    val type: String = "read_receipt",
    @SerializedName("conversation_id") val conversationId: Int,
    @SerializedName("last_message_id") val lastMessageId: Int
)
