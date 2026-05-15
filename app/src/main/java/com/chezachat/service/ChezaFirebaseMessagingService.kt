package com.chezachat.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.ui.home.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChezaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val session = ChezaApp.instance.sessionManager
        if (session.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.init(session)
                    RetrofitClient.api.updateFcmToken(mapOf("fcm_token" to token))
                } catch (e: Exception) { /* silent */ }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data    = message.data
        val title   = data["title"]  ?: message.notification?.title ?: "Cheza Chat"
        val body    = data["body"]   ?: message.notification?.body  ?: ""
        val convId  = data["conversation_id"]?.toIntOrNull()
        showNotification(title, body, convId)
    }

    private fun showNotification(title: String, body: String, conversationId: Int?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            conversationId?.let { putExtra("conversation_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, Random.nextInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "cheza_messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(conversationId ?: Random.nextInt(), notification)
    }
}
