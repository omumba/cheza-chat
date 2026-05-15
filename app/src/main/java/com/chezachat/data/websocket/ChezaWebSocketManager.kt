package com.chezachat.data.websocket

import android.util.Log
import com.chezachat.BuildConfig
import com.chezachat.model.*
import com.chezachat.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class WsState {
    object Connecting : WsState()
    object Connected : WsState()
    data class Disconnected(val reason: String) : WsState()
    data class Error(val error: Throwable) : WsState()
}

sealed class WsMessage {
    data class NewMessage(val message: Message, val tempId: Int = 0) : WsMessage()
    data class MessageStatus(val messageId: Int, val status: String) : WsMessage()
    data class TypingIndicator(val conversationId: Int, val userId: Int, val isTyping: Boolean) : WsMessage()
    data class UserPresence(val userId: Int, val isOnline: Boolean, val lastSeen: Long) : WsMessage()
    data class MessageReaction(val messageId: Int, val reaction: Reaction) : WsMessage()
    data class MessageDeleted(val messageId: Int, val conversationId: Int) : WsMessage()
    data class NewConversation(val conversation: Conversation) : WsMessage()
}

object ChezaWebSocketManager {

    private const val TAG = "ChezaWS"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var sessionManager: SessionManager? = null

    private val _state = MutableSharedFlow<WsState>(replay = 1)
    val state: SharedFlow<WsState> = _state.asSharedFlow()

    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<WsMessage> = _messages.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // no timeout for WS
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun init(sm: SessionManager) {
        sessionManager = sm
    }

    fun connect() {
        val token = sessionManager?.getToken() ?: return
        val request = Request.Builder()
            .url("${BuildConfig.WS_URL}?token=$token")
            .build()

        _state.tryEmit(WsState.Connecting)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _state.tryEmit(WsState.Connected)
                sendPresence(true)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "WS received: $text")
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _state.tryEmit(WsState.Error(t))
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _state.tryEmit(WsState.Disconnected(reason))
            }
        })
    }

    fun disconnect() {
        sendPresence(false)
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun sendChatMessage(
        conversationId: Int,
        content: String,
        type: String = "text",
        mediaUrl: String? = null,
        replyToId: Int? = null,
        tempId: Int = 0
    ) {
        val payload = SendMessagePayload(
            conversationId = conversationId,
            content = content,
            messageType = type,
            mediaUrl = mediaUrl,
            replyToId = replyToId,
            tempId = tempId
        )
        send(gson.toJson(payload))
    }

    fun sendTyping(conversationId: Int, isTyping: Boolean) {
        val payload = TypingPayload(conversationId = conversationId, isTyping = isTyping)
        send(gson.toJson(payload))
    }

    fun sendReadReceipt(conversationId: Int, lastMessageId: Int) {
        val payload = ReadReceiptPayload(
            conversationId = conversationId,
            lastMessageId = lastMessageId
        )
        send(gson.toJson(payload))
    }

    private fun sendPresence(online: Boolean) {
        val payload = mapOf("type" to "presence", "is_online" to online)
        send(gson.toJson(payload))
    }

    private fun send(json: String): Boolean {
        return webSocket?.send(json) ?: false
    }

    private fun handleMessage(text: String) {
        try {
            val json: JsonObject = JsonParser.parseString(text).asJsonObject
            when (val type = json.get("type")?.asString) {
                "new_message" -> {
                    val message = gson.fromJson(json.get("message"), Message::class.java)
                    val tempId  = json.get("temp_id")?.asInt ?: 0
                    _messages.tryEmit(WsMessage.NewMessage(message, tempId))
                }
                "message_status" -> {
                    val messageId = json.get("message_id").asInt
                    val status = json.get("status").asString
                    _messages.tryEmit(WsMessage.MessageStatus(messageId, status))
                }
                "typing" -> {
                    val convId = json.get("conversation_id").asInt
                    val userId = json.get("user_id").asInt
                    val isTyping = json.get("is_typing").asBoolean
                    _messages.tryEmit(WsMessage.TypingIndicator(convId, userId, isTyping))
                }
                "presence" -> {
                    val userId = json.get("user_id").asInt
                    val isOnline = json.get("is_online").asBoolean
                    val lastSeen = json.get("last_seen")?.asLong ?: 0L
                    _messages.tryEmit(WsMessage.UserPresence(userId, isOnline, lastSeen))
                }
                "reaction" -> {
                    val messageId = json.get("message_id").asInt
                    val reaction = gson.fromJson(json.get("reaction"), Reaction::class.java)
                    _messages.tryEmit(WsMessage.MessageReaction(messageId, reaction))
                }
                "message_deleted" -> {
                    val messageId = json.get("message_id").asInt
                    val convId = json.get("conversation_id").asInt
                    _messages.tryEmit(WsMessage.MessageDeleted(messageId, convId))
                }
                "new_conversation" -> {
                    val conversation = gson.fromJson(json.get("conversation"), Conversation::class.java)
                    _messages.tryEmit(WsMessage.NewConversation(conversation))
                }
                else -> Log.w(TAG, "Unknown WS event type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WS message: ${e.message}")
        }
    }

    private var reconnectAttempts = 0
    private fun scheduleReconnect() {
        if (reconnectAttempts >= 5) return
        reconnectAttempts++
        val delay = (reconnectAttempts * 3000L).coerceAtMost(30000L)
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            connect()
        }, delay)
    }

    fun resetReconnectCounter() { reconnectAttempts = 0 }
}
