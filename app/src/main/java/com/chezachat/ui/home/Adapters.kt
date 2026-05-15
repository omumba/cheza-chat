package com.chezachat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chezachat.R
import com.chezachat.model.Conversation
import com.chezachat.model.User
import com.chezachat.utils.*
import de.hdodenhof.circleimageview.CircleImageView

// ─── Conversation Adapter ─────────────────────────────────────────────────────

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.VH>(DiffCallback()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar:      CircleImageView = view.findViewById(R.id.ivAvatar)
        val tvInitials:    TextView        = view.findViewById(R.id.tvInitials)
        val tvName:        TextView        = view.findViewById(R.id.tvName)
        val tvLastMessage: TextView        = view.findViewById(R.id.tvLastMessage)
        val tvTime:        TextView        = view.findViewById(R.id.tvTime)
        val tvBadge:       TextView        = view.findViewById(R.id.tvBadge)
        val onlineDot:     View            = view.findViewById(R.id.onlineDot)
        val typingDots:    View            = view.findViewById(R.id.typingDots)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conv = getItem(position)

        holder.tvName.text = conv.name
        holder.tvTime.text = if (conv.lastMessageTime > 0) conv.lastMessageTime.toMessageTime() else ""

        if (conv.isTyping) {
            holder.tvLastMessage.visibility = View.GONE
            holder.typingDots.visibility    = View.VISIBLE
        } else {
            holder.typingDots.visibility    = View.GONE
            holder.tvLastMessage.visibility = View.VISIBLE
            holder.tvLastMessage.text       = conv.lastMessage ?: "Tap to start chatting"
        }

        if (!conv.avatarUrl.isNullOrEmpty()) {
            holder.ivAvatar.loadAvatar(conv.avatarUrl)
            holder.tvInitials.visibility = View.GONE
        } else {
            holder.ivAvatar.setImageDrawable(null)
            holder.tvInitials.visibility = View.VISIBLE
            holder.tvInitials.text       = conv.name.initials()
        }

        holder.onlineDot.visibility =
            if (conv.type == "direct" && conv.isOnline) View.VISIBLE else View.GONE

        if (conv.unreadCount > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(conv) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
        override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
    }
}

// ─── User Search Adapter ──────────────────────────────────────────────────────
// Tapping a row opens UserProfileActivity — NOT a chat directly.
// The profile screen handles the Add Friend / Message decision.

enum class UserRelation { STRANGER, PENDING, FRIEND }

class UserSearchAdapter(
    private val onViewProfile: (User) -> Unit
) : ListAdapter<Pair<User, UserRelation>, UserSearchAdapter.VH>(DiffCallback()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar:     CircleImageView = view.findViewById(R.id.ivAvatar)
        val tvName:       TextView        = view.findViewById(R.id.tvName)
        val tvStatus:     TextView        = view.findViewById(R.id.tvStatus)
        val onlineDot:    View            = view.findViewById(R.id.onlineDot)
        val tvFriendBadge: TextView       = view.findViewById(R.id.tvFriendBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_user_search, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (user, relation) = getItem(position)

        holder.tvName.text   = user.name
        holder.tvStatus.text = user.status?.takeIf { it.isNotBlank() }
                               ?: "Cheza Chat user"
        holder.ivAvatar.loadAvatar(user.avatarUrl)

        holder.onlineDot.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        when (relation) {
            UserRelation.FRIEND  -> {
                holder.tvFriendBadge.text = "Friends"
                holder.tvFriendBadge.alpha = 0.6f
            }
            UserRelation.PENDING -> {
                holder.tvFriendBadge.text = "Pending"
                holder.tvFriendBadge.alpha = 0.6f
            }
            UserRelation.STRANGER -> {
                holder.tvFriendBadge.text = "Add"
                holder.tvFriendBadge.alpha = 1f
            }
        }

        // Always open profile — profile screen decides what action to show
        holder.itemView.setOnClickListener { onViewProfile(user) }
    }

    fun submitUsersWithRelations(users: List<User>, friends: Set<Int>, pending: Set<Int>) {
        val pairs = users.map { user ->
            val relation = when {
                friends.contains(user.id) -> UserRelation.FRIEND
                pending.contains(user.id) -> UserRelation.PENDING
                else                      -> UserRelation.STRANGER
            }
            user to relation
        }
        submitList(pairs)
    }

    class DiffCallback : DiffUtil.ItemCallback<Pair<User, UserRelation>>() {
        override fun areItemsTheSame(a: Pair<User, UserRelation>, b: Pair<User, UserRelation>) =
            a.first.id == b.first.id
        override fun areContentsTheSame(a: Pair<User, UserRelation>, b: Pair<User, UserRelation>) =
            a == b
    }
}
