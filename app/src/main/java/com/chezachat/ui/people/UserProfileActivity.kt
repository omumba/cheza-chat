package com.chezachat.ui.people

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chezachat.R
import com.chezachat.ChezaApp
import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.repository.ConversationRepository
import com.chezachat.data.repository.FriendRepository
import com.chezachat.data.repository.Result
import com.chezachat.model.User
import com.chezachat.ui.chat.ChatActivity
import com.chezachat.ui.chat.ImageViewerActivity
import com.chezachat.utils.*
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen user profile.
 * Shows avatar, name, status, online dot.
 * Buttons change based on friendship state:
 *   - Stranger  → [Add Friend]
 *   - Pending   → [Request Sent] (disabled)
 *   - Friends   → [Message]  [Unfriend]
 */
class UserProfileActivity : AppCompatActivity() {

    private lateinit var ivAvatar: CircleImageView
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var onlineDot: View
    private lateinit var tvOnline: TextView
    private lateinit var btnAddFriend: Button
    private lateinit var btnMessage: Button
    private lateinit var btnUnfriend: Button
    private lateinit var progressBar: ProgressBar

    private val friendRepo = FriendRepository()
    private val session    by lazy { ChezaApp.instance.sessionManager }

    private lateinit var user: User

    // Friendship state
    private var friendState = FriendState.UNKNOWN

    enum class FriendState { UNKNOWN, STRANGER, PENDING_SENT, FRIENDS }

    companion object {
        private const val EXTRA_USER    = "user"
        private const val EXTRA_USER_ID = "user_id"

        fun start(context: Context, user: User) {
            context.startActivity(
                Intent(context, UserProfileActivity::class.java)
                    .putExtra(EXTRA_USER, user)
            )
        }

        fun startById(context: Context, userId: Int) {
            context.startActivity(
                Intent(context, UserProfileActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        ivAvatar    = findViewById(R.id.ivAvatar)
        tvName      = findViewById(R.id.tvName)
        tvStatus    = findViewById(R.id.tvStatus)
        tvEmail     = findViewById(R.id.tvEmail)
        tvPhone     = findViewById(R.id.tvPhone)
        onlineDot   = findViewById(R.id.onlineDot)
        tvOnline    = findViewById(R.id.tvOnline)
        btnAddFriend= findViewById(R.id.btnAddFriend)
        btnMessage  = findViewById(R.id.btnMessage)
        btnUnfriend = findViewById(R.id.btnUnfriend)
        progressBar = findViewById(R.id.progressBar)

        // Get user from intent
        val passedUser: User? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_USER, User::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_USER)
        }
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)

        if (passedUser != null) {
            user = passedUser
            bindUser()
            checkFriendshipState()
        } else if (userId != -1) {
            progressBar.show()
            loadUserById(userId)
        } else {
            finish()
            return
        }

        btnAddFriend.setOnClickListener { sendFriendRequest() }
        btnMessage.setOnClickListener   { openChat() }
        btnUnfriend.setOnClickListener  { unfriend() }
    }

    private fun loadUserById(userId: Int) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getUserProfile(userId)
                }
                progressBar.hide()
                if (response.isSuccessful && response.body()?.success == true) {
                    user = response.body()!!.data!!
                    bindUser()
                    checkFriendshipState()
                } else {
                    toast("User not found"); finish()
                }
            } catch (e: Exception) {
                progressBar.hide(); toast("Error: ${e.message}"); finish()
            }
        }
    }

    private fun bindUser() {
        ivAvatar.loadAvatar(user.avatarUrl)
        ivAvatar.setOnClickListener {
            val url = user.avatarUrl ?: return@setOnClickListener
            ImageViewerActivity.start(this, url)
        }
        tvName.text = user.name
        tvStatus.text = user.status?.takeIf { it.isNotBlank() } ?: "Hey there! I'm using Cheza Chat"
        tvEmail.text = user.email
        tvPhone.text = user.phone?.takeIf { it.isNotBlank() } ?: ""
        tvPhone.visibility = if (tvPhone.text.isNotEmpty()) View.VISIBLE else View.GONE

        val isOnline = user.isOnline
        onlineDot.setBackgroundResource(
            if (isOnline) R.drawable.bg_online_dot else R.drawable.bg_offline_dot
        )
        tvOnline.text = if (isOnline) "Online" else {
            val ago = System.currentTimeMillis() - user.lastSeen
            when {
                ago < 60_000    -> "Just now"
                ago < 3_600_000 -> "Last seen ${ago / 60_000}m ago"
                ago < 86_400_000-> "Last seen ${ago / 3_600_000}h ago"
                else            -> "Last seen ${ago / 86_400_000}d ago"
            }
        }
        tvOnline.setTextColor(
            getColor(if (isOnline) R.color.primary else R.color.text_secondary)
        )
    }

    private fun checkFriendshipState() {
        val myId = session.getUserId()
        if (user.id == myId) {
            // Viewing own profile — hide all buttons
            btnAddFriend.hide(); btnMessage.hide(); btnUnfriend.hide()
            return
        }

        lifecycleScope.launch {
            progressBar.show()
            try {
                // Check friends list
                val friendsResult = withContext(Dispatchers.IO) { friendRepo.getFriends() }
                val isFriend = friendsResult is Result.Success &&
                               friendsResult.data.any { it.id == user.id }

                if (isFriend) {
                    friendState = FriendState.FRIENDS
                    applyState()
                    progressBar.hide()
                    return@launch
                }

                // Check pending sent requests
                val sentResult = withContext(Dispatchers.IO) { friendRepo.getSentRequests() }
                val isPending = sentResult is Result.Success &&
                                sentResult.data.any { it.receiverId == user.id }

                friendState = if (isPending) FriendState.PENDING_SENT else FriendState.STRANGER
                applyState()
            } catch (e: Exception) {
                friendState = FriendState.STRANGER
                applyState()
            }
            progressBar.hide()
        }
    }

    private fun applyState() {
        when (friendState) {
            FriendState.STRANGER -> {
                btnAddFriend.show(); btnAddFriend.isEnabled = true
                btnAddFriend.text = "➕  Add Friend"
                btnMessage.hide()
                btnUnfriend.hide()
            }
            FriendState.PENDING_SENT -> {
                btnAddFriend.show(); btnAddFriend.isEnabled = false
                btnAddFriend.text = "✓  Request Sent"
                btnMessage.hide()
                btnUnfriend.hide()
            }
            FriendState.FRIENDS -> {
                btnAddFriend.hide()
                btnMessage.show()
                btnUnfriend.show()
            }
            FriendState.UNKNOWN -> {
                btnAddFriend.hide(); btnMessage.hide(); btnUnfriend.hide()
            }
        }
    }

    private fun sendFriendRequest() {
        btnAddFriend.isEnabled = false
        lifecycleScope.launch {
            progressBar.show()
            when (val r = friendRepo.sendRequest(user.id)) {
                is Result.Success -> {
                    friendState = FriendState.PENDING_SENT
                    applyState()
                    toast("Friend request sent to ${user.name}!")
                }
                is Result.Error -> {
                    btnAddFriend.isEnabled = true
                    toast(r.message)
                }
                else -> { btnAddFriend.isEnabled = true }
            }
            progressBar.hide()
        }
    }

    private fun openChat() {
        lifecycleScope.launch {
            progressBar.show()
            val db     = ChezaApp.instance.database
            val repo   = ConversationRepository(db)
            val result = repo.createDirectConversation(user.id)
            progressBar.hide()
            if (result is Result.Success) {
                ChatActivity.start(this@UserProfileActivity, result.data)
            } else {
                toast("Could not open chat")
            }
        }
    }

    private fun unfriend() {
        lifecycleScope.launch {
            progressBar.show()
            when (val r = friendRepo.removeFriend(user.id)) {
                is Result.Success -> {
                    friendState = FriendState.STRANGER
                    applyState()
                    toast("Removed ${user.name} from friends")
                }
                is Result.Error -> toast(r.message)
                else -> {}
            }
            progressBar.hide()
        }
    }
}
