package com.chezachat.data.db

import android.content.Context
import androidx.room.*
import com.chezachat.model.Conversation
import com.chezachat.model.Message
import com.chezachat.model.Reaction
import com.chezachat.model.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─── Type Converters ──────────────────────────────────────────────────────────
class Converters {
    private val gson = Gson()

    @TypeConverter fun userListToJson(v: List<User>?): String = gson.toJson(v ?: emptyList<User>())
    @TypeConverter fun jsonToUserList(v: String): List<User>? =
        gson.fromJson(v, object : TypeToken<List<User>>() {}.type)

    @TypeConverter fun reactionListToJson(v: List<Reaction>?): String = gson.toJson(v ?: emptyList<Reaction>())
    @TypeConverter fun jsonToReactionList(v: String): List<Reaction>? =
        gson.fromJson(v, object : TypeToken<List<Reaction>>() {}.type)
}

// ─── ConversationDao ──────────────────────────────────────────────────────────
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY last_message_time DESC")
    fun getAllConversations(): kotlinx.coroutines.flow.Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Int): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<Conversation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation)

    @Query("UPDATE conversations SET last_message = :message, last_message_time = :time WHERE id = :id")
    suspend fun updateLastMessage(id: Int, message: String, time: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :id")
    suspend fun clearUnread(id: Int)

    @Query("UPDATE conversations SET is_online = :online WHERE other_user_id = :userId")
    suspend fun updateUserOnlineStatus(userId: Int, online: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Int)
}

// ─── MessageDao ───────────────────────────────────────────────────────────────
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY created_at ASC")
    fun getMessages(convId: Int): kotlinx.coroutines.flow.Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(convId: Int, limit: Int, offset: Int): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("UPDATE messages SET is_deleted = 1, content = 'This message was deleted' WHERE id = :id")
    suspend fun softDelete(id: Int)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: Int): Message?

    @Query("DELETE FROM messages WHERE id = :tempId")
    suspend fun deleteTempById(tempId: Int)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Int, status: String)

    @Query("UPDATE messages SET status = 'read' WHERE conversation_id = :convId AND sender_id != :myUserId AND status != 'read'")
    suspend fun markConversationRead(convId: Int, myUserId: Int)

    // Atomic swap: delete optimistic temp row and insert confirmed server row.
    // Must live in the DAO for @Transaction to open a real SQLite transaction.
    @Transaction
    suspend fun replaceTempWithReal(tempId: Int, realMessage: Message) {
        deleteTempById(tempId)
        insert(realMessage)
    }
}

// ─── UserDao ──────────────────────────────────────────────────────────────────
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<User>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUser(id: Int): User?

    @Query("UPDATE users SET is_online = :online, last_seen = :lastSeen WHERE id = :id")
    suspend fun updatePresence(id: Int, online: Boolean, lastSeen: Long)
}

// ─── Database ─────────────────────────────────────────────────────────────────
@Database(
    entities     = [User::class, Conversation::class, Message::class],
    version      = 2,   // bumped because we changed schema handling
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChezaDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: ChezaDatabase? = null

        fun getDatabase(context: Context): ChezaDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, ChezaDatabase::class.java, "cheza_chat.db")
                    .fallbackToDestructiveMigration()   // dev only — change to proper migration before release
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
