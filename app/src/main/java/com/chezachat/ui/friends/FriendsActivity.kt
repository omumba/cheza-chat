package com.chezachat.ui.friends

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.data.repository.ConversationRepository
import com.chezachat.data.repository.Result
import com.chezachat.model.FriendRequest
import com.chezachat.model.User
import com.chezachat.ui.chat.ChatActivity
import com.chezachat.ui.people.UserProfileActivity
import com.chezachat.utils.loadAvatar
import com.chezachat.utils.toast
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class FriendsActivity : AppCompatActivity() {

    private val viewModel: FriendsViewModel by viewModels()

    private lateinit var tabFriends: TextView
    private lateinit var tabRequests: TextView
    private lateinit var rvFriends: RecyclerView
    private lateinit var rvRequests: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyFriends: TextView
    private lateinit var tvEmptyRequests: TextView

    private var showingFriends = true

    private val friendsAdapter = FriendAdapter(
        onViewProfile = { user ->
            startActivity(
                Intent(this, UserProfileActivity::class.java).putExtra("user", user)
            )
        },
        onMessage = { user -> openChat(user) },
        onRemove  = { user ->
            AlertDialog.Builder(this)
                .setTitle("Remove friend?")
                .setMessage("Remove ${user.name} from your friends?")
                .setPositiveButton("Remove") { _, _ -> viewModel.removeFriend(user.id) }
                .setNegativeButton("Cancel", null).show()
        }
    )

    private val requestsAdapter = RequestAdapter(
        onAccept = { req -> viewModel.acceptRequest(req.id) },
        onReject = { req -> viewModel.rejectRequest(req.id) }
    )

    companion object {
        fun start(context: Context) =
            context.startActivity(Intent(context, FriendsActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply { setDisplayHomeAsUpEnabled(true); title = "Friends" }
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        tabFriends      = findViewById(R.id.tabFriends)
        tabRequests     = findViewById(R.id.tabRequests)
        rvFriends       = findViewById(R.id.rvFriends)
        rvRequests      = findViewById(R.id.rvRequests)
        progressBar     = findViewById(R.id.progressBar)
        tvEmptyFriends  = findViewById(R.id.tvEmptyFriends)
        tvEmptyRequests = findViewById(R.id.tvEmptyRequests)

        rvFriends.apply  { layoutManager = LinearLayoutManager(this@FriendsActivity); adapter = friendsAdapter }
        rvRequests.apply { layoutManager = LinearLayoutManager(this@FriendsActivity); adapter = requestsAdapter }

        selectTab(true)
        tabFriends.setOnClickListener  { selectTab(true) }
        tabRequests.setOnClickListener { selectTab(false) }

        val swipe = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe.setOnRefreshListener { viewModel.refresh(); swipe.isRefreshing = false }

        observe()
    }

    private var isFirstLaunch = true

    override fun onResume() {
        super.onResume()
        // Skip the first onResume — ViewModel.init already called refresh().
        // Subsequent resumes (e.g. returning from UserProfileActivity) do refresh.
        if (isFirstLaunch) { isFirstLaunch = false; return }
        viewModel.refresh()
    }

    private fun selectTab(friends: Boolean) {
        showingFriends = friends
        rvFriends.visibility       = if (friends)  View.VISIBLE else View.GONE
        rvRequests.visibility      = if (!friends) View.VISIBLE else View.GONE
        tabFriends.alpha  = if (friends)  1f else 0.5f
        tabRequests.alpha = if (!friends) 1f else 0.5f
        updateEmptyViews()
    }

    private fun updateEmptyViews() {
        tvEmptyFriends.visibility  = if (showingFriends  && friendsAdapter.itemCount  == 0) View.VISIBLE else View.GONE
        tvEmptyRequests.visibility = if (!showingFriends && requestsAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun observe() {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.friends.observe(this) { list ->
            friendsAdapter.submitList(list)
            updateEmptyViews()
        }

        viewModel.requests.observe(this) { list ->
            requestsAdapter.submitList(list)
            val badge = list.size
            tabRequests.text = if (badge > 0) "Requests ($badge)" else "Requests"
            updateEmptyViews()
        }

        viewModel.message.observe(this) { msg ->
            msg?.let { toast(it); viewModel.clearMessage() }
        }
    }

    private fun openChat(user: User) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val db   = ChezaApp.instance.database
            val repo = ConversationRepository(db)
            when (val r = repo.createDirectConversation(user.id)) {
                is Result.Success -> {
                    progressBar.visibility = View.GONE
                    ChatActivity.start(this@FriendsActivity, r.data)
                }
                else -> {
                    progressBar.visibility = View.GONE
                    toast("Could not open chat")
                }
            }
        }
    }
}

// ── Friend list adapter ───────────────────────────────────────────────────────
class FriendAdapter(
    private val onViewProfile: (User) -> Unit,
    private val onMessage: (User) -> Unit,
    private val onRemove:  (User) -> Unit
) : ListAdapter<User, FriendAdapter.VH>(object : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(a: User, b: User) = a.id == b.id
    override fun areContentsTheSame(a: User, b: User) = a == b
}) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivAvatar:  CircleImageView = v.findViewById(R.id.ivAvatar)
        val tvName:    TextView        = v.findViewById(R.id.tvName)
        val tvStatus:  TextView        = v.findViewById(R.id.tvStatus)
        val onlineDot: View            = v.findViewById(R.id.onlineDot)
        val btnMessage:ImageButton     = v.findViewById(R.id.btnMessage)
        val btnRemove: ImageButton     = v.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = getItem(position)
        holder.ivAvatar.loadAvatar(user.avatarUrl)
        holder.tvName.text   = user.name
        holder.tvStatus.text = if (user.isOnline) "Online"
                               else user.status?.takeIf { it.isNotBlank() } ?: ""
        holder.onlineDot.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        // Tap avatar or name → view full profile
        holder.ivAvatar.setOnClickListener  { onViewProfile(user) }
        holder.tvName.setOnClickListener    { onViewProfile(user) }
        holder.tvStatus.setOnClickListener  { onViewProfile(user) }
        holder.btnMessage.setOnClickListener { onMessage(user) }
        holder.btnRemove.setOnClickListener  { onRemove(user) }
        holder.itemView.setOnClickListener  { onViewProfile(user) }
    }
}

// ── Friend request adapter ────────────────────────────────────────────────────
class RequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : ListAdapter<FriendRequest, RequestAdapter.VH>(object : DiffUtil.ItemCallback<FriendRequest>() {
    override fun areItemsTheSame(a: FriendRequest, b: FriendRequest) = a.id == b.id
    override fun areContentsTheSame(a: FriendRequest, b: FriendRequest) = a == b
}) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivAvatar:  CircleImageView = v.findViewById(R.id.ivAvatar)
        val tvName:    TextView        = v.findViewById(R.id.tvName)
        val tvEmail:   TextView        = v.findViewById(R.id.tvEmail)
        val btnAccept: Button          = v.findViewById(R.id.btnAccept)
        val btnReject: Button          = v.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = getItem(position)
        holder.ivAvatar.loadAvatar(req.senderAvatar)
        holder.tvName.text  = req.senderName
        holder.tvEmail.text = req.senderEmail
        holder.btnAccept.setOnClickListener { onAccept(req) }
        holder.btnReject.setOnClickListener { onReject(req) }
    }
}
