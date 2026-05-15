package com.chezachat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.db.ChezaDatabase
import com.chezachat.data.websocket.ChezaWebSocketManager
import com.chezachat.utils.SessionManager

class ChezaApp : Application() {

    companion object {
        lateinit var instance: ChezaApp
            private set
    }

    val database: ChezaDatabase by lazy { ChezaDatabase.getDatabase(this) }
    val sessionManager: SessionManager by lazy { SessionManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Init network clients with session before any screen uses them
        RetrofitClient.init(sessionManager)
        ChezaWebSocketManager.init(sessionManager)

        // Reconnect WebSocket on app restart if user is still logged in
        if (sessionManager.isLoggedIn()) {
            ChezaWebSocketManager.connect()
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val messagesChannel = NotificationChannel(
                "cheza_messages",
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableVibration(true)
            }

            val callsChannel = NotificationChannel(
                "cheza_calls",
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
            }

            manager.createNotificationChannels(listOf(messagesChannel, callsChannel))
        }
    }
}
