package com.chezachat.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.data.websocket.ChezaWebSocketManager
import com.chezachat.data.websocket.WsState
import com.chezachat.databinding.ActivityMainBinding
import com.chezachat.model.User
import com.chezachat.ui.auth.AuthActivity
import com.chezachat.ui.chat.ChatActivity
import com.chezachat.ui.friends.FriendsActivity
import com.chezachat.ui.people.UserProfileActivity
import com.chezachat.ui.profile.ProfileActivity
import com.chezachat.ui.settings.ChatSettingsActivity
import com.chezachat.utils.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var userSearchAdapter: UserSearchAdapter
    private val viewModel: HomeViewModel by viewModels()
    private val session by lazy { ChezaApp.instance.sessionManager }

    // Refresh friend data when returning from profile/friends screen
    private val refreshLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* data refreshed by onResume */ }

    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!session.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish(); return
        }

        RetrofitClient.init(session)
        ChezaWebSocketManager.init(session)
        ChezaWebSocketManager.connect()

        setupConversationList()
        setupSearch()
        setupFab()
        setupBottomNav()
        observeViewModel()
        observeWsState()
    }

    override fun onResume() {
        super.onResume()
        // Skip first onResume — ViewModel init already loaded data.
        // Subsequent resumes (returning from another screen) do refresh.
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.refreshFriendData()
    }

    override fun onDestroy() {
        super.onDestroy()
        ChezaWebSocketManager.disconnect()
    }

    private fun setupConversationList() {
        conversationAdapter = ConversationAdapter { conversation ->
            ChatActivity.start(this, conversation)
        }
        binding.rvConversations.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = conversationAdapter
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun setupSearch() {
        // FIX: adapter now opens UserProfileActivity, not chat directly
        userSearchAdapter = UserSearchAdapter { user ->
            openUserProfile(user)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userSearchAdapter
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isEmpty()) {
                    binding.rvSearchResults.hide()
                    binding.rvConversations.show()
                    viewModel.clearSearch()
                } else {
                    binding.rvSearchResults.show()
                    binding.rvConversations.hide()
                    viewModel.searchUsers(query)
                }
            }
        })
    }

    private fun setupFab() {
        binding.fabNewChat.setOnClickListener {
            binding.etSearch.requestFocus()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats    -> true
                R.id.nav_friends  -> {
                    refreshLauncher.launch(Intent(this, FriendsActivity::class.java))
                    false
                }
                R.id.nav_profile  -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, ChatSettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        viewModel.conversations.observe(this) { conversations ->
            conversationAdapter.submitList(conversations)
            binding.swipeRefresh.isRefreshing = false
            binding.tvEmpty.visibility =
                if (conversations.isEmpty()) View.VISIBLE else View.GONE
        }

        // Update search results WITH friend badges whenever either changes
        viewModel.searchResults.observe(this) { updateSearchList() }
        viewModel.friendIds.observe(this)     { updateSearchList() }
        viewModel.pendingIds.observe(this)    { updateSearchList() }

        viewModel.isRefreshing.observe(this) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
        }

        viewModel.error.observe(this) { err -> err?.let { toast(it) } }

        // Show badge on Friends tab for incoming requests
        viewModel.pendingRequestCount.observe(this) { count ->
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_friends)
            if (count > 0) {
                badge.isVisible = true
                badge.number    = count
            } else {
                badge.isVisible = false
            }
        }
    }

    private fun updateSearchList() {
        val users   = viewModel.searchResults.value ?: return
        val friends = viewModel.friendIds.value  ?: emptySet()
        val pending = viewModel.pendingIds.value ?: emptySet()
        userSearchAdapter.submitUsersWithRelations(users, friends, pending)
    }

    private fun observeWsState() {
        lifecycleScope.launch {
            ChezaWebSocketManager.state.collect { state ->
                when (state) {
                    is WsState.Connected    -> {
                        ChezaWebSocketManager.resetReconnectCounter()
                        binding.wsStatusBar.hide()
                    }
                    is WsState.Connecting   -> {
                        binding.wsStatusBar.show()
                        binding.tvWsStatus.text = "Connecting..."
                    }
                    is WsState.Disconnected,
                    is WsState.Error        -> {
                        binding.wsStatusBar.show()
                        binding.tvWsStatus.text = "Reconnecting..."
                    }
                }
            }
        }
    }

    private fun openUserProfile(user: User) {
        // Use refreshLauncher so friend data updates when user returns
        refreshLauncher.launch(
            Intent(this, UserProfileActivity::class.java)
                .putExtra("user", user)
        )
    }
}
