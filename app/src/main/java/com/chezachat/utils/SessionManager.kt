package com.chezachat.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chezachat.model.User
import com.google.gson.Gson

class SessionManager(context: Context) {

    val appContext: android.content.Context = context.applicationContext

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cheza_session_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for emulators / rooted devices where keystore is unavailable
        context.getSharedPreferences("cheza_session", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    companion object {
        private const val KEY_TOKEN             = "jwt_token"
        private const val KEY_USER              = "current_user"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_DARK_MODE         = "dark_mode"
        private const val KEY_NOTIFICATIONS     = "notifications_enabled"
        private const val KEY_READ_RECEIPTS     = "read_receipts"
        private const val KEY_LAST_SEEN_VISIBLE = "last_seen_visible"
    }

    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()
    fun getToken(): String?      = prefs.getString(KEY_TOKEN, null)
    fun clearToken()             = prefs.edit().remove(KEY_TOKEN).apply()

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER, gson.toJson(user))
            .putInt(KEY_USER_ID, user.id)
            .apply()
    }

    fun getUser(): User? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return try { gson.fromJson(json, User::class.java) } catch (e: Exception) { null }
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun isLoggedIn(): Boolean = getToken() != null && getUserId() != -1

    fun logout() = prefs.edit()
        .remove(KEY_TOKEN)
        .remove(KEY_USER)
        .remove(KEY_USER_ID)
        .apply()

    var isDarkMode: Boolean
        get()      = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var notificationsEnabled: Boolean
        get()      = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var readReceiptsEnabled: Boolean
        get()      = prefs.getBoolean(KEY_READ_RECEIPTS, true)
        set(value) = prefs.edit().putBoolean(KEY_READ_RECEIPTS, value).apply()

    var lastSeenVisible: Boolean
        get()      = prefs.getBoolean(KEY_LAST_SEEN_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_LAST_SEEN_VISIBLE, value).apply()
}
